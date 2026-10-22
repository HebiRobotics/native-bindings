@echo off
rem Builds the shared library in two stages and stages the results where the generated
rem Python and MATLAB wrappers expect to find them.
rem
rem   scripts\build-native.bat
rem
rem Run from a shell that already has cl.exe on PATH. MATLAB is optional: without it the
rem MEX gateway is skipped. Any extra arguments are passed through to Maven, for example:
rem scripts\build-native.bat -o

setlocal

set "REPO_ROOT=%~dp0.."
set "MODULE_DIR=%REPO_ROOT%\bindings-tests"
set "IMAGE_DIR=%MODULE_DIR%\target\image"
set "STATIC_DIR=%IMAGE_DIR%\static"
set "BINDINGS_DIR=%STATIC_DIR%\bindings"
set "NATIVE_BUILD_DIR=%MODULE_DIR%\target\native-build"
set "LIB_NAME=bindings_tests"
set "LIB_FILE=%LIB_NAME%.dll"
set "PLATFORM=win_amd64"
set "STAGE_DIR=%BINDINGS_DIR%\lib\%PLATFORM%"
set "MEX_FILE=%LIB_NAME%_native.mexw64"
set "MEX_STAGE_DIR=%BINDINGS_DIR%\+%LIB_NAME%\private"

rem CMakeLists.txt copies everything it built into this directory below the build tree
set "RELEASE_LIB_DIR=%NATIVE_BUILD_DIR%\release\%LIB_NAME%\%LIB_NAME%\lib\%PLATFORM%"

where cl.exe >nul 2>&1
if errorlevel 1 (
    echo cl.exe is not on PATH. native-image needs the MSVC toolchain.
    exit /b 1
)

rem Stage one. native-image runs through the generated target\image\static\compiler-windows.bat,
rem which builds the usual shared library and additionally emits the static library, libs.cmake
rem and opts.cmake next to the generated CMakeLists.txt in target\image\static.
echo.
echo ==^> Building the shared library ^(this takes a few minutes^)
call mvn -B -f "%REPO_ROOT%\pom.xml" clean verify -Pnative %*
if errorlevel 1 exit /b 1

rem Stage two. CMake links that static library into the final shared library and builds
rem the MEX gateway that the MATLAB wrapper calls into, against MATLAB's SDK if one is
rem found and the bundled one otherwise.
if not defined MATLABROOT call :find_matlabroot
if defined MATLABROOT (
    echo.
    echo Using MATLABROOT: %MATLABROOT%
) else (
    echo.
    echo MATLAB not found, building the MEX gateway against the bundled MEX SDK.
)

echo.
echo ==^> Linking the release library with CMake
cmake -S "%STATIC_DIR%" -B "%NATIVE_BUILD_DIR%"
if errorlevel 1 exit /b 1
cmake --build "%NATIVE_BUILD_DIR%" --config Release
if errorlevel 1 exit /b 1

rem The wrappers look under <bindings>\lib\<platform>\ so that a released binding
rem directory is self contained. Without this the loaders fall back to downloading.
echo.
echo ==^> Staging %LIB_FILE% for %PLATFORM%
if not exist "%STAGE_DIR%" mkdir "%STAGE_DIR%"
copy /y "%RELEASE_LIB_DIR%\%LIB_FILE%" "%STAGE_DIR%\%LIB_FILE%" >nul
if errorlevel 1 (
    echo Failed to copy %RELEASE_LIB_DIR%\%LIB_FILE%
    exit /b 1
)

rem build_native.m compiles the gateway into +bindings_tests\private, so a prebuilt one
rem has to sit in the same directory for MATLAB to pick it up without a compiler.
if exist "%RELEASE_LIB_DIR%\%MEX_FILE%" (
    echo.
    echo ==^> Staging %MEX_FILE%
    copy /y "%RELEASE_LIB_DIR%\%MEX_FILE%" "%MEX_STAGE_DIR%\%MEX_FILE%" >nul
)

rem The static package has to link and run with no shared library present, which is
rem what the consumer project verifies through find_package against the staged tree.
echo.
echo ==^> Verifying the static package consumer
set "CONSUMER_BUILD_DIR=%MODULE_DIR%\target\ctest-static"
cmake -S "%MODULE_DIR%\src\test\static" -B "%CONSUMER_BUILD_DIR%"
if errorlevel 1 exit /b 1
cmake --build "%CONSUMER_BUILD_DIR%" --config Release
if errorlevel 1 exit /b 1
ctest --test-dir "%CONSUMER_BUILD_DIR%" --build-config Release --output-on-failure
if errorlevel 1 exit /b 1

echo.
echo Done. Run scripts\run-tests.bat to exercise the bindings.
exit /b 0

rem The matlab.exe on PATH is <matlabroot>\bin\matlab.exe, so two directory levels up is
rem the MATLAB root. Asking MATLAB itself via "matlab -batch" also works but is slow.
:find_matlabroot
for %%I in (matlab.exe) do set "MATLAB_EXE=%%~$PATH:I"
if not defined MATLAB_EXE goto :eof
for %%I in ("%MATLAB_EXE%") do set "MATLAB_BIN=%%~dpI."
for %%I in ("%MATLAB_BIN%\..") do set "MATLAB_CANDIDATE=%%~fI"
if exist "%MATLAB_CANDIDATE%\extern\include" set "MATLABROOT=%MATLAB_CANDIDATE%"
goto :eof
