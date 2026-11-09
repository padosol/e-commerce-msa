#!/usr/bin/env bash
# 게이트웨이를 통해 사용자 흐름 전체(가입 -> 상품 -> 장바구니 -> 결제 거절/성공 -> 주문 내역)를 걷는다.
set -euo pipefail
source "$(dirname "$0")/lib.sh"

BODY=$(mktemp)
ORIGINAL_CONFIG=""
restore_config() {
  rm -f "$BODY"
  if [ -n "$ORIGINAL_CONFIG" ]; then
    curl -fsS -X PUT "$PAYMENT_URL/internal/payments/config" \
      -H 'Content-Type: application/json' -d "$ORIGINAL_CONFIG" >/dev/null 2>&1 || true
  fi
}
trap restore_config EXIT

fail() { # fail <단계> [http 코드]
  echo "SHOP SMOKE FAIL: $1 ${2:--} $(head -c 300 "$BODY" 2>/dev/null | tr '\n' ' ')" >&2
  exit 1
}

# call <METHOD> <path> [json] : 응답 본문은 $BODY, 상태 코드는 $CODE. 토큰이 있으면 Authorization 을 붙인다.
TOKEN=""
CODE=""
call() {
  local args=(-sS -o "$BODY" -w '%{http_code}' -X "$1" "$GATEWAY_URL$2")
  [ -n "$TOKEN" ] && args+=(-H "Authorization: Bearer $TOKEN")
  [ -n "${3:-}" ] && args+=(-H 'Content-Type: application/json' -d "$3")
  CODE=$(curl "${args[@]}") || { : >"$BODY"; CODE="curl-error"; }
}

# expect <단계> <http 코드> <jq 조건> : 상태 코드와 본문 조건을 확인한다.
expect() {
  [ "$CODE" = "$2" ] || fail "$1" "$CODE"
  if [ -n "${3:-}" ]; then
    jq -e "$3" "$BODY" >/dev/null 2>&1 || fail "$1 (본문 조건 $3)" "$CODE"
  fi
}

stock_of() { curl -fsS "$INVENTORY_URL/internal/inventory/$1" | jq -r '.quantity'; }

# 1. 준비
wait_healthy || fail "준비: 서비스 대기"
ORIGINAL_CONFIG=$(payment_config | jq -c .) || fail "준비: 결제 설정 저장"
set_payment_failure_rate 0 || fail "준비: 결제 실패율 0 고정"
stamp="$(date +%s)-$RANDOM"
A=$(create_product "smoke-A-$stamp" 1000) || fail "준비: 상품 A"
B=$(create_product "smoke-B-$stamp" 500) || fail "준비: 상품 B"
set_stock "$A" 5 || fail "준비: 재고 A"
set_stock "$B" 5 || fail "준비: 재고 B"
read -r _event_id EVENT_PRODUCT <<<"$(create_event 1)" || fail "준비: 이벤트"
[ -n "${EVENT_PRODUCT:-}" ] || fail "준비: 이벤트 상품 id"

# 2. 회원
email="smoke-${stamp}@test.local"
creds="{\"email\":\"$email\",\"password\":\"password-1234\",\"name\":\"smoke\"}"
call POST /api/users/signup "$creds"; expect "회원가입" 201 '.userId != null'
call POST /api/users/login "$creds"; expect "로그인" 200 '.accessToken != null'
TOKEN=$(jq -r '.accessToken' "$BODY")

# 3. 상품 조회
call GET "/api/products/$A"; expect "상품 상세" 200 '.price == 1000'
call GET "/api/products/$EVENT_PRODUCT"; expect "이벤트 상품 상세는 404" 404 '.code == "PRODUCT_NOT_FOUND"'
call GET "/api/products?page=0&size=20"; expect "상품 목록" 200 '.products | type == "array"'

# 4. 장바구니 담기
call PUT "/api/cart/items/$EVENT_PRODUCT" '{"quantity":1}'; expect "이벤트 상품 담기는 400" 400 '.code == "PRODUCT_UNAVAILABLE"'
call PUT "/api/cart/items/$A" '{"quantity":2}'; expect "A 담기" 200
call PUT "/api/cart/items/$B" '{"quantity":1}'; expect "B 담기" 200
call GET /api/cart; expect "장바구니 조회" 200 '.total == 2500 and (.items | length) == 2'

# 5. 결제 거절 경로
set_payment_failure_rate 1.0 || fail "결제 실패율 1.0"
call POST /api/cart/checkout; expect "결제 거절 checkout" 402 '.reason == "PAYMENT_DECLINED"'
[ "$(stock_of "$A")" = 5 ] || fail "거절 뒤 재고 A 가 5 가 아니다"
[ "$(stock_of "$B")" = 5 ] || fail "거절 뒤 재고 B 가 5 가 아니다"
call GET /api/cart; expect "거절 뒤 장바구니" 200 '(.items | length) == 2'

# 6. 결제 성공 경로
set_payment_failure_rate 0 || fail "결제 실패율 0"
call POST /api/cart/checkout; expect "결제 성공 checkout" 201 '.status == "PAID"'
[ "$(stock_of "$A")" = 3 ] || fail "성공 뒤 재고 A 가 3 이 아니다"
[ "$(stock_of "$B")" = 4 ] || fail "성공 뒤 재고 B 가 4 가 아니다"
call GET /api/cart; expect "성공 뒤 장바구니" 200 '(.items | length) == 0'

# 7. 주문 내역
call GET "/api/orders?page=0&size=2"
expect "주문 목록" 200 '.orders[0].status == "PAID" and .orders[0].amount == 2500 and .orders[1].status == "FAILED" and .orders[1].reason == "PAYMENT_DECLINED"'
first_order=$(jq -r '.orders[0].orderId' "$BODY")
call GET "/api/orders/$first_order"; expect "주문 상세" 200 '(.items | length) == 2'

echo "SHOP SMOKE OK"
