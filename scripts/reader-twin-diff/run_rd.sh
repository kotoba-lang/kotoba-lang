#!/bin/zsh
# nbb with the selfhost harness classpath, the compat twins and this directory's probe/ on the roots.
ulimit -s 65520
D=${0:A:h}
cd /Users/junkawasaki/github/kotoba-lang/amu-measure
K=/private/tmp/wt-K-kotoba-lang
CPF=${WALL_CP:-/private/tmp/wall-cp-5.txt}
ROOTS="$(cat $CPF | tr ':' '\n' | grep '/src$' | tr '\n' ':')/private/tmp/wt-A-amu-measure/src:$K/lang/compat:$D"
KROOTS="$ROOTS" RD_DIR="$D" node --stack-size=60000 node_modules/nbb/cli.js --classpath "$(cat $CPF)" "$@"
