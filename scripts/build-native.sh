#!/usr/bin/env bash
# Builds the shared library in two stages and stages the results where the generated
# Python and MATLAB wrappers expect to find them.
#
#   scripts/build-native.sh
#
# Requires a GraalVM JDK on JAVA_HOME, a native toolchain and CMake. MATLAB is optional:
# without it the MEX gateway builds against the bundled MEX SDK. Any extra arguments are
# passed through to Maven, for example: scripts/build-native.sh -o

source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

# Stage one. native-image runs through the generated target/image/static/compiler-<os>,
# which builds the usual shared library and additionally emits the static library,
# libs.cmake and opts.cmake next to the generated CMakeLists.txt in target/image/static.
step "Building the shared library (this takes a few minutes)"
"$MVN" -B -f "$REPO_ROOT/pom.xml" clean verify -Pnative "$@"

# Stage two. CMake links that static library into the final shared library and builds
# the MEX gateway that the MATLAB wrapper calls into, against MATLAB's SDK if one is
# found and the bundled one otherwise.
find_matlabroot
if [ -n "${MATLABROOT:-}" ]; then
    echo
    echo "Using MATLABROOT: $MATLABROOT"
    export MATLABROOT
else
    echo
    echo "MATLAB not found, building the MEX gateway against the bundled MEX SDK."
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

# The static package has to link and run with no shared library present, which is
# what the consumer project verifies through find_package against the staged tree.
step "Verifying the static package consumer"
CONSUMER_BUILD_DIR="$MODULE_DIR/target/ctest-static"
cmake -S "$MODULE_DIR/src/test/static" -B "$CONSUMER_BUILD_DIR" -DCMAKE_BUILD_TYPE=Release
cmake --build "$CONSUMER_BUILD_DIR" --config Release
ctest --test-dir "$CONSUMER_BUILD_DIR" --build-config Release --output-on-failure

echo
echo "Done. Run scripts/run-tests.sh to exercise the bindings."
