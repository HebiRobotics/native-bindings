# find_package configuration for the prebuilt static package. Defines the
# imported target bindings_tests::static carrying the whole link contract: include
# directories, the merged archive, system libraries and the linker options a
# consumer's final link needs.
#
#   find_package(bindings_tests REQUIRED PATHS path/to/package/cmake)
#   target_link_libraries(app PRIVATE bindings_tests::static)

if(TARGET bindings_tests::static)
    return()
endif()

get_filename_component(_bindings_tests_root "${CMAKE_CURRENT_LIST_DIR}/.." ABSOLUTE)

# Same platform naming as the release build. Keyed off the target system so
# toolchain files select the right prebuilt directory when cross-compiling.
if(CMAKE_SYSTEM_NAME STREQUAL "Linux")
    set(_bindings_tests_os "linux")
elseif(CMAKE_SYSTEM_NAME STREQUAL "Darwin")
    set(_bindings_tests_os "osx")
elseif(CMAKE_SYSTEM_NAME STREQUAL "Windows")
    set(_bindings_tests_os "win")
endif()
if(CMAKE_SYSTEM_PROCESSOR STREQUAL "x86_64" OR CMAKE_SYSTEM_PROCESSOR STREQUAL "AMD64")
    set(_bindings_tests_arch "amd64")
elseif(CMAKE_SYSTEM_PROCESSOR STREQUAL "arm64" OR CMAKE_SYSTEM_PROCESSOR STREQUAL "aarch64")
    set(_bindings_tests_arch "arm64")
endif()
set(_bindings_tests_platform "${_bindings_tests_os}_${_bindings_tests_arch}")

set(_bindings_tests_libdir "${_bindings_tests_root}/lib/${_bindings_tests_platform}")
if(NOT IS_DIRECTORY "${_bindings_tests_libdir}")
    file(GLOB _bindings_tests_available RELATIVE "${_bindings_tests_root}/lib" "${_bindings_tests_root}/lib/*")
    message(FATAL_ERROR "bindings_tests: no prebuilt static library for platform "
            "'${_bindings_tests_platform}' (${CMAKE_SYSTEM_NAME} on ${CMAKE_SYSTEM_PROCESSOR}). "
            "Packaged platforms: ${_bindings_tests_available}")
endif()

set(_bindings_tests_archive
        "${_bindings_tests_libdir}/${CMAKE_STATIC_LIBRARY_PREFIX}bindings_tests_static${CMAKE_STATIC_LIBRARY_SUFFIX}")
if(NOT EXISTS "${_bindings_tests_archive}")
    message(FATAL_ERROR "bindings_tests: expected archive not found: ${_bindings_tests_archive}")
endif()

# System libraries the archive needs, one per line in link order
file(STRINGS "${_bindings_tests_libdir}/syslibs.txt" _bindings_tests_syslibs)

add_library(bindings_tests::static STATIC IMPORTED)

# The archive contains the compiled C++ shim, so the consumer's final link has
# to include the C++ runtime even for a pure C program.
set_target_properties(bindings_tests::static PROPERTIES
        IMPORTED_LOCATION "${_bindings_tests_archive}"
        IMPORTED_LINK_INTERFACE_LANGUAGES "CXX"
        INTERFACE_INCLUDE_DIRECTORIES "${_bindings_tests_root}/include"
)
set_property(TARGET bindings_tests::static PROPERTY INTERFACE_LINK_LIBRARIES ${_bindings_tests_syslibs})

# Options the consumer's final link needs. Deliberately minimal, everything
# else in the build's GRAALVM_OPTS only matters when linking the shared library.
if(MSVC)
    set_property(TARGET bindings_tests::static PROPERTY INTERFACE_LINK_OPTIONS "/NODEFAULTLIB:LIBCMT")
elseif(CMAKE_SYSTEM_NAME STREQUAL "Linux")
    # The Graal image objects carry no .note.GNU-stack section
    set_property(TARGET bindings_tests::static PROPERTY INTERFACE_LINK_OPTIONS "-Wl,-z,noexecstack")
endif()

set(bindings_tests_FOUND TRUE)
