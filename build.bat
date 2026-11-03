@echo off
chcp 65001 >nul
cd /d %~dp0
if not exist out mkdir out
dir /s /b chat-rmi\src\*.java db-sync-rmi\src\*.java > sources.txt
javac -encoding UTF-8 -d out -cp "lib/*" @sources.txt
del sources.txt
echo Build xong - thu muc out\
