#!/bin/bash
# This script intercepts compiler arguments from graalvm native shared images and can replace
# select dynamically linked libraries with statically linked ones. The script was tested with
# GraalVM jdk22.0.1 on Ubuntu and may need to be modified for other versions.
#
# Use with --native-compiler-path=${pathToThisScript}.sh

# Determine the project name and output path based on the output .so argument
for arg in "$@"; do
  if [[ "$arg" == *.so ]]; then
    OUTPUT_PATH=$(dirname "$arg")
    LIB_NAME=$(basename "${arg%.so}")
    break
  fi
done

# Do a simple forward for any calls that are used to compile individual C files or other libs
if [[ -z $LIB_NAME || $LIB_NAME == 'libjava' || $LIB_NAME == 'libjvm' ]]; then
    gcc $*
    exit 0
fi

# Setup log path and log file
LOG_PATH="${OUTPUT_PATH}/reports"
LOG_FILE="${LOG_PATH}/compiler_commands.txt"
mkdir -p ${LOG_PATH}

echo "=====================================================" > ${LOG_FILE}
echo "Working directory: ${PWD}" >> ${LOG_FILE}
echo "Output path: ${OUTPUT_PATH}" >> ${LOG_FILE}
echo "Library name: ${LIB_NAME}" >> ${LOG_FILE}

echo "=====================================================" >> ${LOG_FILE}
echo "                    ORIGINAL ARGS                    " >> ${LOG_FILE}
echo "=====================================================" >> ${LOG_FILE}
echo "$*" >> ${LOG_FILE}

echo "=====================================================" >> ${LOG_FILE}
echo "           SHARED LIBRARY WITH STATIC LIBS           " >> ${LOG_FILE}
echo "=====================================================" >> ${LOG_FILE}
# On Linux the original shared image gets created by dynamically linking
# to some libraries that we'd prefer to be statically linked, so we manually
# modify the arguments.

# Path to the system library files, e.g.,
# found on Github actions runners:
# > /usr/lib/x86_64-linux-gnu/libz.a (ubuntu 22.04 and 24.04)
# > /usr/lib/aarch64-linux-gnu/libz.a (Ubuntu 22.04 on Arm)
ARCH=$(uname -m)
STATIC_LIBS_PATH="/usr/lib/${ARCH}-linux-gnu"

# Do the original call, but replace dynamic libs with static versions
# Only use static libz on arm64 as x86_64 was compiled without fPIC.
GCC_ARGS=""
for arg in "$@"
do
    if [ "$arg" = "-lz" ] && [ "$ARCH" = "aarch64" ]
    then
        echo "* Replacing -lz with static ${STATIC_LIBS_PATH}/libz.a" >> ${LOG_FILE}
        GCC_ARGS+=" ${STATIC_LIBS_PATH}/libz.a"
    else
        GCC_ARGS+=" $arg"
    fi
done

echo "gcc $GCC_ARGS" >> ${LOG_FILE}
gcc $GCC_ARGS

echo "=====================================================" >> ${LOG_FILE}
echo "                   STATIC LIBRARY                    " >> ${LOG_FILE}
echo "=====================================================" >> ${LOG_FILE}
# We generate the static library in a separate directory that includes all
# dependencies and a matching cmake file.
STATIC_OUTPUT_PATH="${OUTPUT_PATH}/static"
CMAKE_LIBS="${STATIC_OUTPUT_PATH}/libs.cmake"
mkdir -p ${STATIC_OUTPUT_PATH}

# Empty extra compiler options
echo "set(GRAALVM_OPTS)" > "${STATIC_OUTPUT_PATH}/opts.cmake"

# Create an archive of the original obj file via "ar -rcs output.a input.o"
AR_ARGS="-rcs ${STATIC_OUTPUT_PATH}/${LIB_NAME}.a ${LIB_NAME}.o"
echo "ar $AR_ARGS" >> ${LOG_FILE}
ar $AR_ARGS

# Collect the required static and OS dependencies. The linker command line repeats
# the GraalVM archives, so each one is recorded only the first time it is seen.
SEEN_LIBS=" "
ARCHIVE_LINES=""
SYSLIB_LINES=""
for arg in $GCC_ARGS
do
    # copy each used archive into the static output dir
    if [[ $arg =~ .*\.(a)$ ]]; then
        BASE_NAME=$(basename "${arg}")
        if [[ "$SEEN_LIBS" != *" ${BASE_NAME} "* ]]; then
            SEEN_LIBS+="${BASE_NAME} "
            echo "* Copied static lib: $arg"  >> ${LOG_FILE}
            cp $arg ${STATIC_OUTPUT_PATH}
            ARCHIVE_LINES+="    \${CMAKE_CURRENT_LIST_DIR}/${BASE_NAME}  # ${arg}"$'\n'
        else
            echo "* Skipped duplicate: $arg" >> ${LOG_FILE}
        fi

    # add system dependencies (start with -l in the input arguments)
    elif [[ $arg == -l* ]]; then
        echo "* Added os lib: $arg" >> ${LOG_FILE}
        SYSLIB_LINES+="    ${arg#-l}"$'\n'
    fi
done

# The deduplicated archives depend on each other in a circle, which a single-pass
# linker only resolves inside a group. The merged archive from stage 2 does not
# need one, ld resolves references between members of one archive to fixpoint.
{
    echo "set(GRAALVM_LIBS"
    echo "    -Wl,--start-group"
    echo "    \${CMAKE_CURRENT_LIST_DIR}/${LIB_NAME}.a"
    printf '%s' "${ARCHIVE_LINES}"
    echo "    -Wl,--end-group"
    printf '%s' "${SYSLIB_LINES}"
    echo ")"
} > ${CMAKE_LIBS}
