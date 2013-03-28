@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements.  See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership.  The ASF licenses this file
@REM to you under the Apache License, Version 2.0 (the
@REM "License"); you may not use this file except in compliance
@REM with the License.  You may obtain a copy of the License at
@REM
@REM    http://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing,
@REM software distributed under the License is distributed on an
@REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@REM KIND, either express or implied.  See the License for the
@REM specific language governing permissions and limitations
@REM under the License.
@REM ----------------------------------------------------------------------------

@REM ----------------------------------------------------------------------------
@REM MusicMount Start Up Batch script
@REM
@REM Optional ENV vars
@REM MUSICMOUNT_HOME - location of musicmount's installed home dir
@REM MUSICMOUNT_OPTS - parameters passed to the Java VM when running MusicMount
@REM                   e.g. export MUSICMOUNT_OPTS="-Xms128m -Xmx256m"
@REM ----------------------------------------------------------------------------

@echo off

@REM set %HOME% to equivalent of $HOME
if "%HOME%" == "" (set "HOME=%HOMEDRIVE%%HOMEPATH%")

set ERROR_CODE=0

@REM set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" @setlocal
if "%OS%"=="WINNT" @setlocal

@REM ==== START VALIDATION ====
if not "%JAVA_HOME%" == "" goto OkJHome

goto chkMHome

:OkJHome
if exist "%JAVA_HOME%\bin\java.exe" goto chkMHome

echo.
echo ERROR: JAVA_HOME is set to an invalid directory.
echo JAVA_HOME = "%JAVA_HOME%"
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation
echo.
goto error

:chkMHome
if not "%MUSICMOUNT_HOME%"=="" goto valMHome

if "%OS%"=="Windows_NT" SET "MUSICMOUNT_HOME=%~dp0.."
if "%OS%"=="WINNT" SET "MUSICMOUNT_HOME=%~dp0.."
if not "%MUSICMOUNT_HOME%"=="" goto valMHome

echo.
echo ERROR: MUSICMOUNT_HOME not found in your environment.
echo Please set the MUSICMOUNT_HOME variable in your environment to match the
echo location of the MusicMount installation
echo.
goto error

:valMHome

:stripMHome
if not "_%MUSICMOUNT_HOME:~-1%"=="_\" goto checkMBat
set "MUSICMOUNT_HOME=%MUSICMOUNT_HOME:~0,-1%"
goto stripMHome

:checkMBat
if exist "%MUSICMOUNT_HOME%\bin\musicmount.bat" goto init

echo.
echo ERROR: MUSICMOUNT_HOME is set to an invalid directory.
echo MUSICMOUNT_HOME = "%MUSICMOUNT_HOME%"
echo Please set the MUSICMOUNT_HOME variable in your environment to match the
echo location of the MusicMount installation
echo.
goto error
@REM ==== END VALIDATION ====

:init
@REM Decide how to startup depending on the version of windows

@REM -- Windows NT with Novell Login
if "%OS%"=="WINNT" goto WinNTNovell

@REM -- Win98ME
if NOT "%OS%"=="Windows_NT" goto Win9xArg

:WinNTNovell

@REM -- 4NT shell
if "%@eval[2+2]" == "4" goto 4NTArgs

@REM -- Regular WinNT shell
set MAVEN_CMD_LINE_ARGS=%*
goto endInit

@REM The 4NT Shell from jp software
:4NTArgs
set MAVEN_CMD_LINE_ARGS=%$
goto endInit

:Win9xArg
@REM Slurp the command line arguments.  This loop allows for an unlimited number
@REM of arguments (up to the command line limit, anyway).
set MAVEN_CMD_LINE_ARGS=
:Win9xApp
if %1a==a goto endInit
set MAVEN_CMD_LINE_ARGS=%MAVEN_CMD_LINE_ARGS% %1
shift
goto Win9xApp

@REM Reaching here means variables are defined and arguments have been captured
:endInit

if not "%JAVA_HOME%" == "" goto JHomeSet

SET JAVACMD=java
goto runm2

:JHomeSet
SET JAVACMD="%JAVA_HOME%\bin\java.exe"

@REM Start MUSICMOUNT
:runm2
%JAVACMD% %MUSICMOUNT_OPTS% "-Dmusicmount.home=%MUSICMOUNT_HOME%" -jar "${MUSICMOUNT_HOME}"/lib/musicmount-*.jar %MUSICMOUNT_CMD_LINE_ARGS%
if ERRORLEVEL 1 goto error
goto end

:error
if "%OS%"=="Windows_NT" @endlocal
if "%OS%"=="WINNT" @endlocal
set ERROR_CODE=1

:end
@REM set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" goto endNT
if "%OS%"=="WINNT" goto endNT

@REM For old DOS remove the set variables from ENV - we assume they were not set
@REM before we started - at least we don't leave any baggage around
set MUSICMOUNT_JAVA_EXE=
set MUSICMOUNT_CMD_LINE_ARGS=
goto postExec

:endNT
@endlocal & set ERROR_CODE=%ERROR_CODE%

:postExec

cmd /C exit /B %ERROR_CODE%
