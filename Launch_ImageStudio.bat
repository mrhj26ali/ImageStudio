@echo off
title Parallel Image Studio

REM Use JAVA_HOME if it's already set and valid, otherwise use the default path
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" (
        echo Using existing JAVA_HOME: %JAVA_HOME%
        goto :run
    ) else (
        echo WARNING: JAVA_HOME is set but invalid: %JAVA_HOME%
        echo Falling back to default JDK path...
    )
)

REM Default JDK path (fallback)
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-25.0.2.10-hotspot"
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo ERROR: JDK not found at %JAVA_HOME%
    echo Please set JAVA_HOME environment variable to your JDK 25 installation.
    pause
    exit /b 1
)
echo Using default JDK: %JAVA_HOME%

:run
set "PATH=%JAVA_HOME%\bin;%PATH%"
echo.
echo ========================================
echo   Parallel Image Studio - Launching...
echo ========================================
echo.
echo Loading JavaFX and dependencies...
echo.

call .\mvnw.cmd clean javafx:run

if %errorlevel% neq 0 (
    echo.
    echo ========================================
    echo   ERROR: Failed to launch application
    echo ========================================
    echo.
    pause
)