#!/usr/bin/env bash
# tools/timeline.sh <run_dir> : k6 시작 기준 상대 시각(초)별 증가량. ovf=ListenOverflows, majf=major fault,
#   refault=swap 에서 다시 읽은 anon page, swap=cgroup swap 사용량, maxev=memory.max 도달 횟수, full=memory full stall
r="$1"; ks=$(awk '/k6_start_epoch/{print $2}' "$r/times.txt")
awk -v ks="$ks" 'NR==1{e=$2; u=$4; next} NF==9 {t=e+($1-u)-ks; if (n++) printf "%6.2f ovf %6d majf %6d refault %6d swap %4dMiB maxev %5d full %5dms\n", t, $2-o2, $5-o5, $6-o6, $7/1048576, $8-o8, ($9-o9)/1000; o2=$2;o5=$5;o6=$6;o8=$8;o9=$9}' "$r/gw.txt"
