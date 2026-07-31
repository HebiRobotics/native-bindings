#!/usr/bin/env bash
# Shared paths and platform detection. Sourced by the other scripts, not run directly.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODULE_DIR="$REPO_ROOT/bindings-tests"
IMAGE_DIR="$MODULE_DIR/target/image"
STATIC_DIR="$IMAGE_DIR/static"
BINDINGS_DIR="$STATIC_DIR/bindings"
LIB_NAME="bindings_tests"

# Stage two: cmake links the static library from stage one into the final shared
# library and, if MATLABROOT is set, the MEX gateway.
NATIVE_BUILD_DIR="$MODULE_DIR/target/native-build"
MEX_NAME="${LIB_NAME}_native"

case "$(uname -s)" in
    Darwin)
        LIB_FILE="lib${LIB_NAME}.dylib"
        case "$(uname -m)" in
            arm64|aarch64) PLATFORM="osx_arm64"; MEX_EXT="mexmaca64" ;;
            *)             PLATFORM="osx_amd64"; MEX_EXT="mexmaci64" ;;
        esac
        ;;
    Linux)
        LIB_FILE="lib${LIB_NAME}.so"
        PLATFORM="linux_amd64"
        MEX_EXT="mexa64"
        ;;
    MINGW*|MSYS*|CYGWIN*)
        LIB_FILE="${LIB_NAME}.dll"
        PLATFORM="win_amd64"
        MEX_EXT="mexw64"
        ;;
    *)
        echo "Unsupported system: $(uname -s)" >&2
        exit 1
        ;;
esac

# CMakeLists.txt copies everything it built into this directory below the build tree
RELEASE_LIB_DIR="$NATIVE_BUILD_DIR/release/${LIB_NAME}/${LIB_NAME}/lib/$PLATFORM"

# Sets MATLABROOT if MATLAB can be found, otherwise leaves it empty. cmake only adds the
# MEX target when the variable is present in the environment, and the C, C++ and Python
# suites do not need it.
find_matlabroot() {
    if [ -n "${MATLABROOT:-}" ]; then
        return
    fi

    if ! command -v matlab >/dev/null 2>&1; then
        return
    fi

    # The matlab on PATH is usually <matlabroot>/bin/matlab, possibly through a symlink
    local exe
    exe="$(command -v matlab)"
    if command -v readlink >/dev/null 2>&1; then
        # -f is not portable to older macOS, so ignore a failure and keep the original
        exe="$(readlink -f "$exe" 2>/dev/null || echo "$exe")"
    fi

    local candidate
    candidate="$(cd "$(dirname "$exe")/.." 2>/dev/null && pwd)"
    if [ -n "$candidate" ] && [ -d "$candidate/extern/include" ]; then
        MATLABROOT="$candidate"
        return
    fi

    # Fall back to asking MATLAB itself. Correct but slow, so it is the last resort.
    candidate="$(matlab -batch "disp(matlabroot)" 2>/dev/null | tr -d '\r' | tail -n 1)"
    if [ -n "$candidate" ] && [ -d "$candidate/extern/include" ]; then
        MATLABROOT="$candidate"
    fi
}

# The Maven wrapper is not checked in, so fall back to whatever mvn is on PATH
MVN="mvn"

require_built() {
    if [ ! -f "$IMAGE_DIR/$LIB_FILE" ]; then
        echo "Shared library not found at $IMAGE_DIR/$LIB_FILE" >&2
        echo "Run scripts/build-native.sh first." >&2
        exit 1
    fi
}

step() {
    echo
    echo "==> $*"
}
