@echo off
REM Adjust the classpath if needed. For example, if your classes are in "target\classes"
set CLASSPATH=C:\Users\Micha\OneDrive\Carleton OneDrive Files\WINTER 2025\SYSC 3303\FighterDrones\target\classes;C:\Users\Micha\.m2\repository\org\junit\jupiter\junit-jupiter-api\5.10.0\junit-jupiter-api-5.10.0.jar;C:\Users\Micha\.m2\repository\org\opentest4j\opentest4j\1.3.0\opentest4j-1.3.0.jar;C:\Users\Micha\.m2\repository\org\junit\platform\junit-platform-commons\1.10.0\junit-platform-commons-1.10.0.jar;C:\Users\Micha\.m2\repository\org\apiguardian\apiguardian-api\1.1.2\apiguardian-api-1.1.2.jar

REM Start FireIncidentSubsystem on localhost:5001 (for receiving completions)
start "FireIncidentSubsystem" java -cp "%CLASSPATH%" main.FireIncidentMain

REM Start Scheduler on localhost:5000
start "Scheduler" java -cp "%CLASSPATH%" main.SchedulerMain

REM Start three DroneSubsystem processes.
start "DroneSubsystem-0" java -cp "%CLASSPATH%" main.DroneMain 0
start "DroneSubsystem-1" java -cp "%CLASSPATH%" main.DroneMain 1
start "DroneSubsystem-2" java -cp "%CLASSPATH%" main.DroneMain 2

pause
