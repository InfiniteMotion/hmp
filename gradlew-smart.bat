@echo off
@rem ##########################################################################
@rem  HMP Gradle wrapper: picks a heap profile from free physical memory.
@rem
@rem    high     free >= 10 GB   reuse daemon / parallel / inherit gradle.properties
@rem    balanced free 6-10 GB    3072m / parallel / workers=4
@rem    low      free 3-6 GB     2048m / serial / workers=2 / kotlin in-process
@rem    rescue   free < 3 GB     1024m / --no-daemon / workers=1 / in-process
@rem
@rem  Usage:
@rem    gradlew-smart.bat testUi
@rem    set HMP_MEM_PROFILE=high
@rem    gradlew-smart.bat testUi
@rem
@rem  Constraints:
@rem    - CRLF line endings only: cmd.exe mis-parses LF continuations
@rem    - pure ASCII only: non-ASCII gets mangled under the default GBK code page
@rem ##########################################################################

setlocal EnableDelayedExpansion
set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.

@rem ---- detect free physical memory (GB, rounded down) ----
set FREE_GB=
for /f "usebackq delims=" %%A in (`powershell -NoProfile -Command "try { [math]::Floor((Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory / 1MB) } catch { '' }"`) do set FREE_GB=%%A
if not defined FREE_GB (
  for /f "usebackq tokens=2 delims==" %%A in (`wmic OS get FreePhysicalMemory /value 2^>nul ^| findstr "="`) do (
    set /a FREE_GB=%%A/1048576
  )
)

@rem ---- pick profile: explicit override first, otherwise by free memory ----
set PROFILE=%HMP_MEM_PROFILE%
if /i "%PROFILE%"=="high"     goto :profile_high
if /i "%PROFILE%"=="balanced" goto :profile_balanced
if /i "%PROFILE%"=="low"      goto :profile_low
if /i "%PROFILE%"=="rescue"   goto :profile_rescue
if defined HMP_MEM_PROFILE echo [gradle-profile] unknown HMP_MEM_PROFILE="%HMP_MEM_PROFILE%", auto-selecting 1>&2

set PROFILE=rescue
if not defined FREE_GB goto :profile_rescue
if !FREE_GB! GEQ 10 set PROFILE=high
if !FREE_GB! GEQ 6 if !FREE_GB! LSS 10 set PROFILE=balanced
if !FREE_GB! GEQ 3 if !FREE_GB! LSS 6 set PROFILE=low
if !FREE_GB! LSS 3 set PROFILE=rescue
if /i "%PROFILE%"=="high"     goto :profile_high
if /i "%PROFILE%"=="balanced" goto :profile_balanced
if /i "%PROFILE%"=="low"      goto :profile_low
goto :profile_rescue

:profile_high
@rem do not override jvmargs: reuse the 4096m/1024m daemon from gradle.properties
set PROFILE_DESC=reuse daemon, parallel, heap from gradle.properties
set COMMON=--console=plain
set JVM_ARGS=
goto :run

:profile_balanced
set PROFILE_DESC=reuse daemon, parallel, workers=4, heap 3072m
set COMMON=--console=plain -Dorg.gradle.parallel=true -Dorg.gradle.workers.max=4
set JVM_ARGS=-Dorg.gradle.jvmargs="-Xmx3072m -XX:MaxMetaspaceSize=768m -Dfile.encoding=UTF-8"
goto :run

:profile_low
set PROFILE_DESC=no-daemon, serial, workers=2, kotlin in-process, heap 2048m
set COMMON=--no-daemon --console=plain -Dorg.gradle.parallel=false -Dorg.gradle.workers.max=2 -Dkotlin.compiler.execution.strategy=in-process
set JVM_ARGS=-Dorg.gradle.jvmargs="-Xmx2048m -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8"
goto :run

:profile_rescue
set PROFILE_DESC=no-daemon, serial, workers=1, kotlin in-process, heap 1024m
set COMMON=--no-daemon --console=plain -Dorg.gradle.parallel=false -Dorg.gradle.workers.max=1 -Dkotlin.compiler.execution.strategy=in-process
set JVM_ARGS=-Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=384m -Dfile.encoding=UTF-8"
goto :run

:run
echo [gradle-profile] profile=%PROFILE% free=%FREE_GB%GB -- %PROFILE_DESC% 1>&2
call "%DIRNAME%gradlew.bat" %* %COMMON% %JVM_ARGS%
endlocal & exit /b %ERRORLEVEL%
