#!/bin/sh
set -e

cd "$(dirname "$0")"

mvn -q install
exec mvn -q -f examples/example/pom.xml compile exec:java
