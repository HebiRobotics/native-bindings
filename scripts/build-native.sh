#!/usr/bin/env bash
# Builds the shared library in two stages and stages the results where the generated
# Python and MATLAB wrappers expect to find them.
#
#   scripts/build-native.sh
#
# Requires a GraalVM JDK on JAVA_HOME, a native toolchain and CMake. MATLAB is optional:
# without it the MEX gateway is skipped. Any extra arguments are passed through to Maven,
# for example: scripts/build-native.sh -o

source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

# Stage one. native-image runs through scripts/graal/compiler-<os>, which builds the
# usual shared library and additionally emits the static library, libs.cmake and
# opts.cmake next to the generated CMakeLists.txt in target/image/static.
step "Building the shared library (this takes a few minutes)"
"$MVN" -B -f "$REPO_ROOT/pom.xml" clean verify -Pnative "$@"

# Stage two. CMake links that static library into the final shared library and, if
# MATLAB is available, builds the MEX gateway that the MATLAB wrapper calls into.
find_matlabroot
if [ -n "${MATLABROOT:-}" ]; then
    echo
    echo "Using MATLABROOT: $MATLABROOT"
    export MATLABROOT
else
    echo
    echo "MATLAB not found. Set MATLABROOT to build the MEX gateway."
    echo "The C, C++ and Python suites do not need it, so the build continues."
fi

step "Linking the release library with CMake"
cmake -S "$STATIC_DIR" -B "$NATIVE_BUILD_DIR" -DCMAKE_BUILD_TYPE=Release
cmake --build "$NATIVE_BUILD_DIR" --config Release

# The wrappers look under <bindings>/lib/<platform>/ so that a released binding
# directory is self contained. Without this the loaders fall back to downloading.
step "Staging $LIB_FILE for $PLATFORM"
mkdir -p "$BINDINGS_DIR/lib/$PLATFORM"
cp "$RELEASE_LIB_DIR/$LIB_FILE" "$BINDINGS_DIR/lib/$PLATFORM/$LIB_FILE"

# build_native.m compiles the gateway into +bindings_tests/private, so a prebuilt one
# has to sit in the same directory for MATLAB to pick it up without a compiler.
MEX_FILE="$MEX_NAME.$MEX_EXT"
if [ -f "$RELEASE_LIB_DIR/$MEX_FILE" ]; then
    step "Staging $MEX_FILE"
    cp "$RELEASE_LIB_DIR/$MEX_FILE" "$BINDINGS_DIR/+$LIB_NAME/private/$MEX_FILE"
fi

echo
echo "Done. Run scripts/run-tests.sh to exercise the bindings."
