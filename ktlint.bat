@echo off
@rem ##########################################################################
@rem  HMP ktlint wrapper (Windows). ASCII only, no trailing-^ continuations.
@rem
@rem    ktlint.bat            check all Kotlin sources
@rem    ktlint.bat format     auto-format all Kotlin sources
@rem    ktlint.bat staged     check staged .kt files only (pre-commit hook)
@rem
@rem  Override the JVM with: set HMP_KTLINT_JAVA=C:\path\to\bin\java.exe
@rem ##########################################################################

@rem Delayed expansion MUST stay off for the check/format calls: cmd.exe eats
@rem the leading "!" of a negation glob, turning "!**/build/**" into an
@rem INCLUDE of all generated sources. Only :staged enables it locally.
setlocal DisableDelayedExpansion
set DIRNAME=%~dp0
pushd "%DIRNAME%"

set KTLINT_VERSION=1.8.0
set KTLINT_SHA256=369ad2b789f95a011f807e1fcb690ccef80bd7cd014fd139e73ae82dcc0baeab
set KTLINT_URL=https://repo1.maven.org/maven2/com/pinterest/ktlint/ktlint-cli/%KTLINT_VERSION%/ktlint-cli-%KTLINT_VERSION%-all.jar
set JAR_DIR=%CD%\tools\ktlint
set JAR=%JAR_DIR%\ktlint-cli-%KTLINT_VERSION%-all.jar
set JAVA_BIN=java
if defined HMP_KTLINT_JAVA set JAVA_BIN=%HMP_KTLINT_JAVA%

set ACTION=%1
if "%ACTION%"=="" set ACTION=check

if not exist "%JAR%" goto :download
call :sha256 "%JAR%"
if /i "%ACTUAL_SHA%"=="%KTLINT_SHA256%" goto :run
echo [ktlint] cached jar failed SHA256 check, re-downloading 1>&2

:download
if not exist "%JAR_DIR%" mkdir "%JAR_DIR%"
echo [ktlint] downloading %KTLINT_URL% 1>&2
curl -fSL --retry 3 -o "%JAR%.tmp" "%KTLINT_URL%"
if errorlevel 1 goto :fail
move /y "%JAR%.tmp" "%JAR%" >nul
call :sha256 "%JAR%"
if /i "%ACTUAL_SHA%"=="%KTLINT_SHA256%" goto :run
echo [ktlint] ERROR: SHA256 mismatch 1>&2
echo [ktlint]   expected %KTLINT_SHA256% 1>&2
echo [ktlint]   actual   %ACTUAL_SHA% 1>&2
del /q "%JAR%"
goto :fail

:run
if /i "%ACTION%"=="check" goto :check
if /i "%ACTION%"=="format" goto :format
if /i "%ACTION%"=="staged" goto :staged
echo usage: ktlint.bat [check / format / staged] 1>&2
goto :fail

:check
"%JAVA_BIN%" -jar "%JAR%" "**/*.kt" "!**/build/**" "!**/*.gradle.kts"
goto :end

:format
"%JAVA_BIN%" -jar "%JAR%" -F "**/*.kt" "!**/build/**" "!**/*.gradle.kts"
goto :end

:staged
setlocal EnableDelayedExpansion
set FILES=
for /f "delims=" %%F in ('git diff --cached --name-only --diff-filter=ACMR -- "*.kt"') do set FILES=!FILES! "%%F"
if "!FILES!"=="" (
  echo [ktlint] no staged .kt files 1>&2
  endlocal
  goto :ok
)
"%JAVA_BIN%" -jar "%JAR%" !FILES!
endlocal & set RC=%ERRORLEVEL% & goto :exit

:sha256
set ACTUAL_SHA=
for /f "delims=" %%A in ('powershell -NoProfile -Command "(Get-FileHash -Algorithm SHA256 -LiteralPath '%~1').Hash.ToLower()"') do set ACTUAL_SHA=%%A
goto :eof

:ok
set RC=0
goto :exit
:fail
set RC=1
goto :exit
:end
set RC=%ERRORLEVEL%

:exit
popd
endlocal & exit /b %RC%
