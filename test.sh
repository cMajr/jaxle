#!/bin/sh
set -e

cd "$(dirname "$0")"
bin="${JAVA_HOME:+$JAVA_HOME/bin/}"

rm -rf out/test
"${bin}javac" -d out/test src/jaxle/*.java test/jaxle/*.java
"${bin}java" -cp out/test jaxle.TestRunner
