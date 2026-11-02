#!/usr/bin/env bash
# wait_tcp_drained 단위 테스트. 서비스 없이 가짜 netstat 만 쓴다.
set -uo pipefail
cd "$(dirname "$0")/.."
source scripts/lib.sh

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
mkdir "$work/bin"
# 가짜 netstat: $FAKE_COUNTER 의 값만큼 TIME_WAIT 줄(macOS `netstat -an -p tcp` 모양)을 찍고,
# 호출마다 FAKE_STEP 만큼 줄인다(0 아래로는 내려가지 않는다). FAKE_ESTABLISHED 줄은 ESTABLISHED.
cat > "$work/bin/netstat" <<'FAKE'
#!/usr/bin/env bash
n=$(cat "$FAKE_COUNTER")
echo "Active Internet connections (including servers)"
echo "Proto Recv-Q Send-Q  Local Address          Foreign Address        (state)"
echo "tcp4       0      0  127.0.0.1.8080         *.*                    LISTEN"
echo "tcp4       0      0  127.0.0.1.8080         127.0.0.1.50001        ESTABLISHED"
for ((i = 0; i < ${FAKE_ESTABLISHED:-0}; i++)); do
  echo "tcp4       0      0  127.0.0.1.8080         127.0.0.1.$((20000 + i))       ESTABLISHED"
done
for ((i = 0; i < n; i++)); do
  echo "tcp4       0      0  127.0.0.1.$((30000 + i % 30000))      127.0.0.1.8080         TIME_WAIT"
done
n=$(( n - ${FAKE_STEP:-0} )); [ "$n" -lt 0 ] && n=0
echo "$n" > "$FAKE_COUNTER"
FAKE
chmod +x "$work/bin/netstat"
export PATH="$work/bin:$PATH"
export FAKE_COUNTER="$work/counter"

pass=0; fail=0
check() { # name condition-result(0 = ok) detail
  if [ "$2" -eq 0 ]; then pass=$((pass+1)); else echo "FAIL $1: $3"; fail=$((fail+1)); fi
}

run() { # start step established threshold timeout
  echo "$1" > "$FAKE_COUNTER"
  export FAKE_STEP="$2" FAKE_ESTABLISHED="$3"
  out=$(wait_tcp_drained "$4" "$5" 2>"$work/err"); rc=$?
  read -r waited final <<<"$out"
}

run 700 100 0 500 10
[ "$rc" -eq 0 ] && [ "${waited:-0}" -ge 2 ] && [ "${final:-9999}" -lt 500 ]
check drainsBelowThreshold $? "rc=$rc out='$out'"

run 10 0 0 500 10
[ "$rc" -eq 0 ] && [ "$waited" = 0 ] && [ "$final" = 10 ]
check alreadyDrained $? "rc=$rc out='$out'"

s=$(date +%s)
run 900 0 0 500 2
e=$(date +%s)
[ "$rc" -eq 0 ] && [ "$final" = 900 ] && [ "$waited" -ge 2 ] && [ $(( e - s )) -le 4 ] && grep -q WARN "$work/err"
check timesOutAndWarns $? "rc=$rc out='$out' elapsed=$((e - s)) err='$(cat "$work/err")'"

run 0 0 600 500 10
[ "$rc" -eq 0 ] && [ "$waited" = 0 ] && [ "$final" = 0 ]
check establishedNotCounted $? "rc=$rc out='$out'"

if [ "$fail" -ne 0 ]; then echo "wait-drained tests: $fail failed, $pass passed"; exit 1; fi
echo "wait-drained tests: $pass passed"
