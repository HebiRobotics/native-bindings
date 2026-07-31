@echo off
rem Runs the per-language runtime suites against the built shared library.
rem
rem   scripts\run-tests.bat              all four suites
rem   scripts\run-tests.bat c cpp        only the ones named
rem
rem Suites: c, cpp, python, matlab

setlocal enabledelayedexpansion

set "REPO_ROOT=%~dp0.."
set "MODULE_DIR=%REPO_ROOT%\bindings-tests"
set "LIB_NAME=bindings_tests"
set "PLATFORM=win_amd64"
set "RELEASE_LIB_DIR=%MODULE_DIR%\target\native-build\release\%LIB_NAME%\%LIB_NAME%\lib\%PLATFORM%"

if not exist "%RELEASE_LIB_DIR%\%LIB_NAME%.dll" (
    echo Shared library not found at %RELEASE_LIB_DIR%\%LIB_NAME%.dll
    echo Run scripts\build-native.bat first.
    exit /b 1
)

set "SUITES=%*"
if "%SUITES%"=="" set "SUITES=c cpp python matlab"

set "FAILED="
for %%s in (%SUITES%) do call :run_suite %%s

echo.
if "!FAILED!"=="" (
    echo All requested suites passed.
    exit /b 0
)
echo Failed:!FAILED!
exit /b 1

rem ---------------------------------------------------------------------------
rem One subroutine with plain if blocks. Dispatching with goto is avoided on
rem purpose: cmd resolves labels by scanning the file and is sensitive to how it
rem is laid out, which made the jumps unreliable.

:run_suite
set "SUITE=%~1"
set "SUITE_RC=0"

if /i "%SUITE%"=="c"   set "CMAKE_SUITE=1"
if /i "%SUITE%"=="cpp" set "CMAKE_SUITE=1"

if defined CMAKE_SUITE (
    set "CMAKE_SUITE="
    set "BUILD_DIR=%MODULE_DIR%\target\ctest-%SUITE%"
    echo.
    echo ==^> Building the %SUITE% suite
    cmake -S "%MODULE_DIR%\src\test\%SUITE%" -B "!BUILD_DIR!"
    if !errorlevel! neq 0 set "SUITE_RC=1"
    if !SUITE_RC! equ 0 (
        cmake --build "!BUILD_DIR!" --config Release
        if !errorlevel! neq 0 set "SUITE_RC=1"
    )
    if !SUITE_RC! equ 0 (
        echo.
        echo ==^> Running the %SUITE% suite
        ctest --test-dir "!BUILD_DIR!" --build-config Release --output-on-failure
        if !errorlevel! neq 0 set "SUITE_RC=1"
    )
) else if /i "%SUITE%"=="python" (
    echo.
    echo ==^> Running the Python suite
    pushd "%MODULE_DIR%"
    python -m unittest discover -s src/test/python -v
    if !errorlevel! neq 0 set "SUITE_RC=1"
    popd
) else if /i "%SUITE%"=="matlab" (
    echo.
    echo ==^> Running the MATLAB suite
    where matlab >nul 2>&1
    if !errorlevel! neq 0 (
        echo matlab is not on PATH, skipping.
    ) else (
        pushd "%MODULE_DIR%"
        matlab -batch "results = runtests('src/test/matlab'); disp(results); assertSuccess(results);"
        if !errorlevel! neq 0 set "SUITE_RC=1"
        popd
    )
) else (
    echo Unknown suite: %SUITE%
    set "SUITE_RC=1"
)

if !SUITE_RC! neq 0 set "FAILED=!FAILED! %SUITE%"
exit /b 0
