#!/bin/bash
# Start TileServer
set -e

REALPATH=$(python -c "import os; print(os.path.realpath('$0'))")
BINDIR=$(dirname "$REALPATH")

JARFILE=$("$BINDIR"/build.sh "$@")

java -Djava.net.preferIPv4Stack=true -Xmx512m -jar "$JARFILE"
