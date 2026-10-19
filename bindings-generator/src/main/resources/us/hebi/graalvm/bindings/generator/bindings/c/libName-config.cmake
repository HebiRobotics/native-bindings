# find_package configuration for the prebuilt static package. Defines the
# imported target <libName>::static carrying the whole link contract: include
# directories, the merged archive, system libraries and the linker options a
# consumer's final link needs.
#
#   find_package(<libName> REQUIRED PATHS path/to/package/cmake)
#   target_link_libraries(app PRIVATE <libName>::static)

if(TARGET <libName>::static)
    return()
endif()

get_filename_component(_<libName>_root "${CMAKE_CURRENT_LIST_DIR}/.." ABSOLUTE)

# Same platform naming as the release build. Keyed off the target system so
# toolchain files select the right prebuilt directory when cross-compiling.
if(CMAKE_SYSTEM_NAME STREQUAL "Linux")
    set(_<libName>_os "linux")
elseif(CMAKE_SYSTEM_NAME STREQUAL "Darwin")
    set(_<libName>_os "osx")
elseif(CMAKE_SYSTEM_NAME STREQUAL "Windows")
    set(_<libName>_os "win")
endif()
if(CMAKE_SYSTEM_PROCESSOR STREQUAL "x86_64" OR CMAKE_SYSTEM_PROCESSOR STREQUAL "AMD64")
    set(_<libName>_arch "amd64")
elseif(CMAKE_SYSTEM_PROCESSOR STREQUAL "arm64" OR CMAKE_SYSTEM_PROCESSOR STREQUAL "aarch64")
    set(_<libName>_arch "arm64")
endif()
set(_<libName>_platform "${_<libName>_os}_${_<libName>_arch}")

set(_<libName>_libdir "${_<libName>_root}/lib/${_<libName>_platform}")
if(NOT IS_DIRECTORY "${_<libName>_libdir}")
    file(GLOB _<libName>_available RELATIVE "${_<libName>_root}/lib" "${_<libName>_root}/lib/*")
    message(FATAL_ERROR "<libName>: no prebuilt static library for platform "
            "'${_<libName>_platform}' (${CMAKE_SYSTEM_NAME} on ${CMAKE_SYSTEM_PROCESSOR}). "
            "Packaged platforms: ${_<libName>_available}")
endif()

set(_<libName>_archive
        "${_<libName>_libdir}/${CMAKE_STATIC_LIBRARY_PREFIX}<libName>_static${CMAKE_STATIC_LIBRARY_SUFFIX}")
if(NOT EXISTS "${_<libName>_archive}")
    message(FATAL_ERROR "<libName>: expected archive not found: ${_<libName>_archive}")
endif()

# System libraries the archive needs, one per line in link order
file(STRINGS "${_<libName>_libdir}/syslibs.txt" _<libName>_syslibs)

add_library(<libName>::static STATIC IMPORTED)

# The archive contains the compiled C++ shim, so the consumer's final link has
# to include the C++ runtime even for a pure C program.
set_target_properties(<libName>::static PROPERTIES
        IMPORTED_LOCATION "${_<libName>_archive}"
        IMPORTED_LINK_INTERFACE_LANGUAGES "CXX"
        INTERFACE_INCLUDE_DIRECTORIES "${_<libName>_root}/include"
)
set_property(TARGET <libName>::static PROPERTY INTERFACE_LINK_LIBRARIES ${_<libName>_syslibs})

# Options the consumer's final link needs. Deliberately minimal, everything
# else in the build's GRAALVM_OPTS only matters when linking the shared library.
if(MSVC)
    # The archive was built against the DLL C runtime, so the static one gets excluded
    # and the DLL one linked in its place. Both lines are needed: a target that happens
    # to reference nothing in the archive would otherwise end up with no C runtime.
    set_property(TARGET <libName>::static PROPERTY INTERFACE_LINK_OPTIONS "/NODEFAULTLIB:LIBCMT")
    set_property(TARGET <libName>::static APPEND PROPERTY INTERFACE_LINK_LIBRARIES msvcrt)
elseif(CMAKE_SYSTEM_NAME STREQUAL "Linux")
    # The Graal image objects carry no .note.GNU-stack section
    set_property(TARGET <libName>::static PROPERTY INTERFACE_LINK_OPTIONS "-Wl,-z,noexecstack")
elseif(APPLE)
    # JavaFX defines jStringToNSString in both glass and prism_mtl, and ld only
    # downgrades that duplicate to a warning when dead stripping
    set_property(TARGET <libName>::static PROPERTY INTERFACE_LINK_OPTIONS "-Wl,-dead_strip")
endif()

set(<libName>_FOUND TRUE)
