@echo off
rem SmartMed Standalone Priority Scheduling Engine - Windows Batch Runner
echo Starting SmartMed Standalone Priority Scheduling Engine...
if exist mvnw.cmd (
    call .\mvnw.cmd compile exec:java
) else (
    if not exist bin mkdir bin
    javac -d bin src\models\*.java src\heap\*.java src\scheduler\*.java src\service\*.java src\input\*.java src\output\*.java src\benchmark\*.java src\Main.java
    java -cp bin Main
)
pause
