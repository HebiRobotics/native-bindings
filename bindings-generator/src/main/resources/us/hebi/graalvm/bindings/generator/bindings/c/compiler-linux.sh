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

# zlib arrives as -lz, which leaves every consumer of the merged archive needing the
# system zlib installed. Minimal containers routinely do not have it, so merge a static
# one whenever a usable one exists. Usable means position independent, because the
# archive ends up inside a shared library.
# ${BINDINGS_ZLIB_STATIC} overrides the path, so CI can build a valid -fPIC one. It only
# arrives here if native-image was told to pass it on, via -EBINDINGS_ZLIB_STATIC.
zlib_is_usable() {
    [ -f "$1" ] && gcc -shared -o /dev/null -Wl,--whole-archive "$1" -Wl,--no-whole-archive 2>/dev/null
}

if [ -n "${BINDINGS_ZLIB_STATIC}" ]
then
    ZLIB_STATIC="${BINDINGS_ZLIB_STATIC}"
    USE_STATIC_ZLIB=1
    if ! zlib_is_usable "${ZLIB_STATIC}"
    then
        ERROR="BINDINGS_ZLIB_STATIC=${ZLIB_STATIC} is missing or not position independent"
        echo "* ERROR: ${ERROR}" >> ${LOG_FILE}
        echo "${ERROR}" >&2
        exit 1
    fi
    echo "* Static zlib from BINDINGS_ZLIB_STATIC: ${ZLIB_STATIC}" >> ${LOG_FILE}
else
    # Github action runners:
    # > /usr/lib/aarch64-linux-gnu/libz.a (Ubuntu 22.04 on Arm) -> valid -fPIC
    # > /usr/lib/x86_64-linux-gnu/libz.a (ubuntu 22.04 and 24.04) -> invalid
    ARCH=$(uname -m)
    ZLIB_STATIC="/usr/lib/${ARCH}-linux-gnu/libz.a"
    USE_STATIC_ZLIB=0
    if zlib_is_usable "${ZLIB_STATIC}"
    then
        USE_STATIC_ZLIB=1
        echo "* Static zlib is usable: ${ZLIB_STATIC}" >> ${LOG_FILE}
    else
        echo "* No position independent zlib at ${ZLIB_STATIC}, leaving -lz to the consumer" >> ${LOG_FILE}
    fi
fi

# Do the original call, but replace dynamic libs with static versions
GCC_ARGS=""
for arg in "$@"
do
    if [ "$arg" = "-lz" ] && [ "${USE_STATIC_ZLIB}" = "1" ]
    then
        echo "* Replacing -lz with static ${ZLIB_STATIC}" >> ${LOG_FILE}
        GCC_ARGS+=" ${ZLIB_STATIC}"
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
            # Liberica NIK ships libjavafx_iio.a as a bare ELF object. ld links it regardless
            # of the name, but the stage 2 merge (ar -M addlib) only accepts real archives,
            # so anything without the archive magic gets wrapped into one on the way.
            if [ "$(head -c 7 "$arg")" = "!<arch>" ]; then
                echo "* Copied static lib: $arg"  >> ${LOG_FILE}
                cp $arg ${STATIC_OUTPUT_PATH}
            else
                echo "* Wrapped object into static lib: $arg"  >> ${LOG_FILE}
                OBJ_NAME="${BASE_NAME%.a}.o"
                cp $arg "${STATIC_OUTPUT_PATH}/${OBJ_NAME}"
                (cd ${STATIC_OUTPUT_PATH} && ar -rcs "${BASE_NAME}" "${OBJ_NAME}" && rm "${OBJ_NAME}")
            fi
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
