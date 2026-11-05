# alpine sh, --pid=host --cgroupns=host --privileged. env P = gateway host pid. 0.25초마다 한 줄:
# uptime ListenOverflows SyncookiesRecv TCPReqQFullDoCookies pgmajfault refault_anon swap_current events_max mem_full_us cpu_usage_usec nr_throttled
CG=/sys/fs/cgroup$(cut -d: -f3 /proc/$P/cgroup)
echo "epoch $(date +%s) uptime $(cut -d' ' -f1 /proc/uptime)"
while :; do
  up=$(cut -d' ' -f1 /proc/uptime)
  net=$(nsenter -t $P -n awk '/^TcpExt:/{if(!h){for(i=1;i<=NF;i++)k[i]=$i;h=1}else{for(i=1;i<=NF;i++)v[k[i]]=$i}} END{print v["ListenOverflows"], v["SyncookiesRecv"], v["TCPReqQFullDoCookies"]}' /proc/net/netstat)
  mem=$(awk '$1=="pgmajfault"{a=$2} $1=="workingset_refault_anon"{b=$2} END{print a, b}' $CG/memory.stat)
  sw=$(cat $CG/memory.swap.current)
  ev=$(awk '$1=="max"{print $2}' $CG/memory.events)
  pr=$(awk '/^full/{split($5,x,"="); print x[2]}' $CG/memory.pressure)
  cpu=$(awk '$1=="usage_usec"{u=$2} $1=="nr_throttled"{t=$2} END{print u, t}' $CG/cpu.stat)
  echo "$up $net $mem $sw $ev $pr $cpu"
  sleep 0.25
done
