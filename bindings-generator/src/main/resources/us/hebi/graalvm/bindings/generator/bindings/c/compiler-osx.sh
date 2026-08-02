#!/bin/bash
# This script intercepts compiler arguments from graalvm native shared images and can replace
# select dynamically linked libraries with statically linked ones. The script was tested with
# GraalVM jdk22.0.1 on macOS and may need to be modified for other versions.
#
# Use with --native-compiler-path=${pathToThisScript}

# Determine the project name and output path based on the output .dylib argument
for arg in "$@"; do
  if [[ "$arg" == *.dylib ]]; then
    OUTPUT_PATH=$(dirname "$arg")
    LIB_NAME=$(basename "${arg%.dylib}")
    break
  fi
done

# Do a simple forward for any calls that are used to compile individual C files
if [[ -z $LIB_NAME ]]; then
    cc $*
    exit 0
fi

# Create a debug log in $output/logs
LOG_PATH="${OUTPUT_PATH}/reports"
LOG_FILE="${LOG_PATH}/compiler_commands.txt"
mkdir -p $LOG_PATH

echo "=====================================================" > ${LOG_FILE}
echo "Working directory: ${PWD}" >> ${LOG_FILE}
echo "Output path: ${OUTPUT_PATH}" >> ${LOG_FILE}
echo "Library name: ${LIB_NAME}" >> ${LOG_FILE}

echo "=====================================================" >> ${LOG_FILE}
echo "                  SHARED LIBRARY                     " >> ${LOG_FILE}
echo "=====================================================" >> ${LOG_FILE}
# Build the original shared library as intended by native-image
CC_ARGS=$*
echo "cc $CC_ARGS" >> ${LOG_FILE}
cc $CC_ARGS

echo "=====================================================" >> ${LOG_FILE}
echo "                   STATIC LIBRARY                    " >> ${LOG_FILE}
echo "=====================================================" >> ${LOG_FILE}
# We generate the static library in a separate directory that includes all
# dependencies and a matching cmake file.
STATIC_OUTPUT_PATH="${OUTPUT_PATH}/static"
CMAKE_LIBS="${STATIC_OUTPUT_PATH}/libs.cmake"
mkdir -p ${STATIC_OUTPUT_PATH}
echo "set(GRAALVM_LIBS" > ${CMAKE_LIBS}

# Empty extra compiler options
echo "set(GRAALVM_OPTS)" > "${STATIC_OUTPUT_PATH}/opts.cmake"

# Older versions of GraalVM do not set Mach-O platform metadata and cause
# warnings like 'ld: warning: no platform load command found in ...'
# This retroactively sets the metadata before archiving.
OBJ_FILE="${LIB_NAME}.o"
if [[ -f "$OBJ_FILE" ]]; then
    # Ignore if the metadata already exists
    CHECK=$(otool -l "$OBJ_FILE" | grep -E "LC_BUILD_VERSION|LC_VERSION_MIN_MACOSX")
    if [ -z "$CHECK" ]; then
        # We don't know the real version, but as of 2026, macOS 13.0 should be a
        # safe minimum deployment target
        echo "Injecting missing macOS platform metadata into $OBJ_FILE" >> ${LOG_FILE}
        vtool -set-build-version macos 13.0.0 13.0.0 -output "${OBJ_FILE}.fixed" "$OBJ_FILE"
        mv "${OBJ_FILE}.fixed" "$OBJ_FILE"
    fi
fi

# Create an archive of the original obj file via "ar -rcs output.a input.o"
AR_ARGS="-rcs ${STATIC_OUTPUT_PATH}/${LIB_NAME}.a ${LIB_NAME}.o"
echo "ar $AR_ARGS" >> ${LOG_FILE}
ar $AR_ARGS
echo "    \${CMAKE_CURRENT_LIST_DIR}/${LIB_NAME}.a" >> ${CMAKE_LIBS}

# Add the required static and OS dependencies
# TODO: how should "-framework,Foundation" be added?
for arg in $CC_ARGS
do
    # handle specific libraries specified with -Wl,-force_load (e.g. libglass.a)
    prefix=""
    if [[ $arg =~ ^-Wl,-force_load,.*\.(a)$ ]]; then
        arg=$(echo "$arg" | sed 's/^-Wl,-force_load,//')
        prefix="-Wl,-force_load,"
    fi

    # copy each used archive into the static output dir
    if [[ $arg =~ .*\.(a)$ ]]; then
        echo "* Copied static lib: $arg"  >> ${LOG_FILE}
        cp $arg ${STATIC_OUTPUT_PATH}
        echo "    ${prefix}\${CMAKE_CURRENT_LIST_DIR}/$(basename "${arg}")  # ${arg}" >> ${CMAKE_LIBS}

    # add system dependencies (start with -l in the input arguments)
    elif [[ $arg == -l* ]]; then
        echo "* Added os lib: $arg" >> ${LOG_FILE}
        echo "    ${arg#-l}" >> ${CMAKE_LIBS}

    # add system frameworks
    elif [[ $arg == *-Wl,-framework,* ]]; then
          FRAMEWORK=$(echo "$arg" | sed -E 's/-Wl,-framework,//')
          echo "* Added framework: ${FRAMEWORK}" >> ${LOG_FILE}
          echo "    ${arg}" >> ${CMAKE_LIBS}

    fi
done

# finish cmake file
echo ")" >> ${CMAKE_LIBS}