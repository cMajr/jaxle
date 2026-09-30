#!/bin/sh
set -e

cd "$(dirname "$0")"
bin="${JAVA_HOME:+$JAVA_HOME/bin/}"

rm -rf out/example
"${bin}javac" -d out/example src/jaxle/*.java examples/example/*.java
exec "${bin}java" -cp out/example example.Main
