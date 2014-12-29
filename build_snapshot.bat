@echo off
gradle build
for /f %%a in ('powershell -Command "Get-Date -format yyMMdd_HHmm"') do set datetime=%%a
ren "build\libs\tum-1.7.10_*.jar" "tum-SNAPSHOT_%datetime%.jar"