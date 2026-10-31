#!/bin/sh
# Fail if a *.sh under the given folders uses a command the head unit's shell (mksh/toolbox)
# lacks: printf, tail, head, seq, awk, grep, or [[ ]].
# usage: tools/lint-sh.sh <dir>...
# Commands are matched at the start of a command (after ; & | ( ) { ` !, a case arm's ")" or
# a keyword such as then, eval or time), outside comments, so words like "header" or "the
# head unit" in text pass.
[ $# -gt 0 ] || { echo "usage: lint-sh.sh <dir>..." >&2; exit 2; }
CMD='(^|[;&|(){`!]|(^|[[:space:]])(then|do|else|elif|if|while|until|exec|command|xargs|eval|time))[[:space:]]*(printf|tail|head|seq|awk|grep)([[:space:];&|)`]|$)'
DBL='(^|[[:space:];&|(!])\[\[([[:space:]]|$)'
_bad=0
for _d in "$@"; do
    for _f in $(find "$_d" -name '*.sh' | LC_ALL=C sort); do
        _hits=$(sed -e 's/^[[:space:]]*#.*//' -e 's/[[:space:]]#.*//' "$_f" | grep -nE -e "$CMD" -e "$DBL" || true)
        if [ -n "$_hits" ]; then
            echo "$_hits" | sed "s|^|$_f:|" >&2
            _bad=1
        fi
    done
done
if [ $_bad = 1 ]; then
    echo "lint-sh: printf, tail, head, seq, awk, grep or [[ in scripts (not on the head unit's shell)" >&2
    exit 1
fi
