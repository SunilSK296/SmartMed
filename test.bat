@echo off
rem SmartMed Standalone Priority Scheduling Engine - Automated Test Runner
echo Running Automated Unit Tests...
if exist mvnw.cmd (
    call .\mvnw.cmd test
) else (
    echo Maven wrapper not found; running with standalone TestRunner...
    if not exist bin mkdir bin
    javac -d bin src\models\*.java src\heap\*.java src\scheduler\*.java src\service\*.java src\input\*.java src\output\*.java src\benchmark\*.java tests\*.java
    java -cp bin TestRunner
)
pause
