#!/bin/bash
set -e
cd "$(dirname "$0")"
mkdir -p out
javac -encoding UTF-8 -d out -cp "lib/*" $(find chat-rmi/src db-sync-rmi/src -name "*.java")
echo "Build xong -> thư mục out/"
