#!/bin/zsh
# usage: par.sh CHUNK_BYTES JOBSFILE   (JOBSFILE lines: "<file> <P>")
D=${0:A:h}
export CHUNK=$1
while read f P; do for sel in $(seq 0 $((P-1))); do echo "$f $P $sel"; done; done < $2 | \
  xargs -P 9 -L 1 zsh -c 'P=$1 SEL=$2 ENTRY=_$$ '$D'/run_rd.sh '$D'/rd_diff.cljs $0 2>&1 | tail -1'
