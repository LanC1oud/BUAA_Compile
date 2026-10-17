@echo off
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"

rem ============================================================
rem  run.bat -- one entry point: build when needed, then launch.
rem
rem    run.bat               compile if needed, then open the GUI
rem    run.bat --build-only  compile if needed, do not launch
rem    run.bat --force       force a rebuild, then open the GUI
rem    run.bat --help        show this help
rem
rem  "Needs a build" means: no build output yet, no .stamp marker yet, or
rem  some *.java is newer than the marker (i.e. sources changed since the
rem  last successful build). The marker is written only after javac succeeds,
rem  and it is deleted before every build, so a failed build always retries.
rem
rem  Needs JDK 17+. A JDK is picked automatically, in this order:
rem    1) JAVA_HOME
rem    2) machine-wide install dirs (Program Files\Java, ...)
rem    3) per-user / IDE-managed JDKs (%USERPROFILE%\.jdks, ...)
rem    4) javac found on PATH
rem  Within one tier the highest version wins, so a JDK 8 on PATH is skipped.
rem
rem  NOTE: keep this file ASCII-only. A UTF-8 .bat with non-ASCII text gets
rem  mangled when cmd.exe parses it under a non-UTF-8 code page (lines shift
rem  and parts of lines get executed as commands).
rem ============================================================

if not defined MIN_MAJOR set "MIN_MAJOR=17"
set "OUT_DIR=build\classes"
set "STAMP=%OUT_DIR%\.stamp"
set "MAIN_CLASS=compilerui.gui.CompilerUi"
set "MAIN_CLASS_FILE=%OUT_DIR%\compilerui\gui\CompilerUi.class"
set "PROBE_LOG=%TEMP%\buaa_run_jdk_probe.txt"

set "FORCE=0"
set "BUILD_ONLY=0"
set "GUI_ARGS="

rem --- arguments ---
:parse
if "%~1"=="" goto :parsed
if /i "%~1"=="--build-only" ( set "BUILD_ONLY=1" & shift & goto :parse )
if /i "%~1"=="--force"      ( set "FORCE=1"      & shift & goto :parse )
if /i "%~1"=="--rebuild"    ( set "FORCE=1"      & shift & goto :parse )
if /i "%~1"=="-h"           ( call :usage & exit /b 0 )
if /i "%~1"=="--help"       ( call :usage & exit /b 0 )
set "GUI_ARGS=!GUI_ARGS! %~1"
shift
goto :parse
:parsed

rem --- find a JDK 17+ (also used to run) ---
call :find_jdk
if not defined BEST_ROOT (
  echo [run] No JDK %MIN_MAJOR%+ found - the build and the GUI need 17+.
  echo [run] Install JDK 17+ or point JAVA_HOME at one, then retry.
  if exist "%PROBE_LOG%" (
    echo [run] Probe log:
    type "%PROBE_LOG%"
  )
  exit /b 1
)

rem --- decide whether a build is needed ---
set "NEED_BUILD=0"
if "%FORCE%"=="1" set "NEED_BUILD=1"
if "%NEED_BUILD%"=="0" if not exist "%OUT_DIR%" set "NEED_BUILD=1"
if "%NEED_BUILD%"=="0" if not exist "%STAMP%" set "NEED_BUILD=1"
if "%NEED_BUILD%"=="0" (
  rem exit code 1 means "a .java is newer than the stamp"; any failure also
  rem rebuilds, so a missing/broken PowerShell can never leave stale output.
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$s=Get-Item -LiteralPath '%STAMP%'; if (@(Get-ChildItem -LiteralPath 'src','ui' -Recurse -Filter *.java | Where-Object { $_.LastWriteTimeUtc -gt $s.LastWriteTimeUtc }).Count -gt 0) { exit 1 }" >nul 2>&1
  if errorlevel 1 set "NEED_BUILD=1"
)

if "%NEED_BUILD%"=="1" (
  call :build
  if errorlevel 1 exit /b 1
) else (
  echo [run] Up to date, skipping compile.
)

if "%BUILD_ONLY%"=="1" (
  echo [run] Build-only done. Output: %OUT_DIR%
  exit /b 0
)

if not exist "%MAIN_CLASS_FILE%" (
  echo [run] Missing %MAIN_CLASS_FILE%
  echo [run] Build output looks incomplete - try: run.bat --force
  exit /b 1
)

echo [run] JDK : %BEST_ROOT%
echo [run] Start %MAIN_CLASS% ...
"%BEST_ROOT%\bin\java.exe" -Dfile.encoding=UTF-8 -cp "%OUT_DIR%" %MAIN_CLASS% !GUI_ARGS!
exit /b %errorlevel%


rem ============================================================
rem  :build -- compile src/ + ui/ into build\classes
rem ============================================================
:build
echo [run] JDK : %BEST_ROOT%
echo [run] javac : %BEST_VER%
if exist "%STAMP%" del /q "%STAMP%" >nul 2>&1

rem javac treats a backslash in an @argfile as an escape, so use forward slashes.
set "ARGFILE=%TEMP%\buaa_run_srcs.txt"
if exist "%ARGFILE%" del /q "%ARGFILE%" >nul 2>&1
set "COUNT=0"
for /f "delims=" %%F in ('dir /s /b /a-d "src\*.java" "ui\*.java" 2^>nul') do (
  set "P=%%~fF"
  set "P=!P:\=/!"
  echo "!P!">>"%ARGFILE%"
  set /a COUNT+=1
)
if "%COUNT%"=="0" (
  echo [run] No .java sources found under src\ or ui\. Working dir: %CD%
  exit /b 1
)

if not exist "%OUT_DIR%" mkdir "%OUT_DIR%" >nul 2>&1
if not exist "%OUT_DIR%" (
  echo [run] Cannot create output dir %OUT_DIR%
  exit /b 1
)

echo [run] javac %COUNT% files -^> %OUT_DIR%
"%BEST_ROOT%\bin\javac.exe" -encoding UTF-8 -d "%OUT_DIR%" "@%ARGFILE%"
if errorlevel 1 (
  echo.
  echo [run] Compilation FAILED - see javac output above.
  exit /b 1
)

echo built>"%STAMP%"
echo [run] Compilation OK
exit /b 0


rem ============================================================
rem  :usage
rem ============================================================
:usage
echo Usage: run.bat [options]
echo.
echo   (no option)     compile if needed, then open the GUI
echo   --build-only    compile if needed, do not launch
echo   --force         force a rebuild, then open the GUI
echo   -h, --help      show this help
echo.
echo Needs JDK 17+ (auto-detected: JAVA_HOME, Program Files\Java, .jdks, PATH).
exit /b 0


rem ============================================================
rem  :find_jdk -- fills BEST_ROOT / BEST_VER / BEST_MAJOR / BEST_TIER
rem ============================================================
:find_jdk
if exist "%PROBE_LOG%" del /q "%PROBE_LOG%" >nul 2>&1
set "BEST_ROOT="
set "BEST_VER="
set "BEST_MAJOR=0"
set "BEST_TIER=99"
if defined JAVA_HOME call :probe 1 "%JAVA_HOME%"
for /d %%D in ("C:\Program Files\Java\*" "C:\Program Files\Eclipse Adoptium\*" "C:\Program Files\Amazon Corretto\*" "C:\Program Files\Zulu\*" "C:\Program Files\Microsoft\*") do call :probe 2 "%%~fD"
for /d %%D in ("%LOCALAPPDATA%\Programs\Java\*" "%USERPROFILE%\.jdks\*") do call :probe 3 "%%~fD"
for /f "delims=" %%J in ('where javac 2^>nul') do call :probe 4 "%%~dpJ"
exit /b 0


rem ============================================================
rem  :probe <tier> <path> -- consider this JDK if it is version 17+
rem  <path> may be a JDK root or the bin dir holding javac.exe (PATH case).
rem  Lower tier number wins; within a tier the higher version wins.
rem ============================================================
:probe
set "TIER=%~1"
set "CAND=%~2"
set "JDKROOT="
if exist "%CAND%\bin\javac.exe" set "JDKROOT=%CAND%"
if not defined JDKROOT if exist "%CAND%\javac.exe" for %%P in ("%CAND%\..") do set "JDKROOT=%%~fP"
if not defined JDKROOT exit /b 0
if not exist "%JDKROOT%\bin\java.exe" exit /b 0

rem "javac -version" goes to stderr on JDK 8 and to stdout on newer releases.
set "VERFILE=%TEMP%\buaa_run_javacver.txt"
"%JDKROOT%\bin\javac.exe" -version >"%VERFILE%" 2>&1
set "VER="
set /p VER=<"%VERFILE%"
if not defined VER exit /b 0

rem "javac 21.0.8" / "javac 1.8.0_482" -> the second field is the major version
set "MAJ="
for /f "tokens=2 delims=. " %%a in ("!VER!") do set "MAJ=%%a"
if not defined MAJ exit /b 0
echo !MAJ!| findstr /r /c:"^[0-9][0-9]*$" >nul
if errorlevel 1 (
  echo   %JDKROOT% : unrecognised version ^(!VER!^) >>"%PROBE_LOG%"
  exit /b 0
)
if !MAJ! LSS %MIN_MAJOR% (
  echo   %JDKROOT% : !VER! ^(major !MAJ!^) too old, skipped >>"%PROBE_LOG%"
  exit /b 0
)
if !TIER! GTR !BEST_TIER! exit /b 0
if !TIER! EQU !BEST_TIER! if !MAJ! LEQ !BEST_MAJOR! exit /b 0

set "BEST_TIER=!TIER!"
set "BEST_ROOT=!JDKROOT!"
set "BEST_VER=!VER!"
set "BEST_MAJOR=!MAJ!"
exit /b 0
