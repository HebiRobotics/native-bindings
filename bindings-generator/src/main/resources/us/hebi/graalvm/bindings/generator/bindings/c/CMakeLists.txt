# 3.25 for CMP0141: /Z7 on Windows. Only stage 2 builds this file, so no minimum imposed on users.
cmake_minimum_required(VERSION 3.25) 
project(<projectName> VERSION <libVersion>)

# Find current platform
if (CMAKE_SYSTEM_NAME STREQUAL "Linux")
    set(CMAKE_PLATFORM_PREFIX "linux")
elseif (CMAKE_SYSTEM_NAME STREQUAL "Darwin")
    set(CMAKE_PLATFORM_PREFIX "osx")
elseif (CMAKE_SYSTEM_NAME STREQUAL "Windows")
    set(CMAKE_PLATFORM_PREFIX "win")
endif ()

if (CMAKE_SYSTEM_PROCESSOR STREQUAL "x86_64" OR CMAKE_SYSTEM_PROCESSOR STREQUAL "AMD64")
    set(CMAKE_PLATFORM_SUFFIX "amd64") # For 64-bit
elseif (CMAKE_SYSTEM_PROCESSOR STREQUAL "arm64" OR CMAKE_SYSTEM_PROCESSOR STREQUAL "aarch64")
    set(CMAKE_PLATFORM_SUFFIX "arm64") # For ARM64
endif ()

set(CMAKE_PLATFORM "${CMAKE_PLATFORM_PREFIX}_${CMAKE_PLATFORM_SUFFIX}")
message(STATUS "Building for platform: ${CMAKE_PLATFORM}")

###############################################################################
# MERGED STATIC ARCHIVE
###############################################################################
# Load static lib and GraalVM definitions
include(${CMAKE_CURRENT_LIST_DIR}/<libName>_static.cmake)

# GRAALVM_LIBS mixes archive files with system libraries. The archives get
# merged with the compiled shim into a single static library, which is what
# the static package ships and what the shared library below links. The group
# markers only matter for the loose list, ld resolves references between
# members of a single archive to fixpoint. Archives marked -force_load keep
# their whole-archive semantics only when linked as separate files, so they
# stay on the link line.
set(GRAALVM_ARCHIVES)
set(GRAALVM_SYSLIBS)
foreach (_item IN LISTS GRAALVM_LIBS)
    if (_item MATCHES "^-Wl,--(start|end)-group$")
        continue()
    elseif (_item MATCHES "force_load")
        list(APPEND GRAALVM_SYSLIBS ${_item})
    elseif (EXISTS ${_item})
        list(APPEND GRAALVM_ARCHIVES ${_item})
    else ()
        list(APPEND GRAALVM_SYSLIBS ${_item})
    endif ()
endforeach ()
list(REMOVE_DUPLICATES GRAALVM_ARCHIVES)
list(REMOVE_DUPLICATES GRAALVM_SYSLIBS)

set(MERGED_ARCHIVE_DIR ${CMAKE_CURRENT_BINARY_DIR}/static)
set(MERGED_ARCHIVE ${MERGED_ARCHIVE_DIR}/${CMAKE_STATIC_LIBRARY_PREFIX}<libName>_static${CMAKE_STATIC_LIBRARY_SUFFIX})
file(MAKE_DIRECTORY ${MERGED_ARCHIVE_DIR})

if (MSVC)
    set(_lib_tool ${CMAKE_AR})
    if (NOT _lib_tool)
        set(_lib_tool lib.exe)
    endif ()
    add_custom_command(OUTPUT ${MERGED_ARCHIVE}
            COMMAND ${_lib_tool} /NOLOGO "/OUT:${MERGED_ARCHIVE}" "$\<TARGET_FILE:<libName>_static>" ${GRAALVM_ARCHIVES}
            DEPENDS <libName>_static ${GRAALVM_ARCHIVES}
            COMMENT "Merging static archives into ${MERGED_ARCHIVE}"
            VERBATIM)
elseif (APPLE)
    add_custom_command(OUTPUT ${MERGED_ARCHIVE}
            COMMAND libtool -static -no_warning_for_no_symbols -o ${MERGED_ARCHIVE} "$\<TARGET_FILE:<libName>_static>" ${GRAALVM_ARCHIVES}
            DEPENDS <libName>_static ${GRAALVM_ARCHIVES}
            COMMENT "Merging static archives into ${MERGED_ARCHIVE}"
            VERBATIM)
else ()
    # binutils ar merges archives only through an MRI script
    set(MERGE_SCRIPT ${MERGED_ARCHIVE_DIR}/merge.mri)
    set(_mri "create ${MERGED_ARCHIVE}\naddlib $\<TARGET_FILE:<libName>_static>\n")
    foreach (_archive IN LISTS GRAALVM_ARCHIVES)
        string(APPEND _mri "addlib ${_archive}\n")
    endforeach ()
    string(APPEND _mri "save\nend\n")
    file(GENERATE OUTPUT ${MERGE_SCRIPT} CONTENT "${_mri}")
    add_custom_command(OUTPUT ${MERGED_ARCHIVE}
            COMMAND sh -c "${CMAKE_AR} -M \< ${MERGE_SCRIPT}"
            DEPENDS <libName>_static ${GRAALVM_ARCHIVES} ${MERGE_SCRIPT}
            COMMENT "Merging static archives into ${MERGED_ARCHIVE}"
            VERBATIM)
endif ()
add_custom_target(<libName>_merged DEPENDS ${MERGED_ARCHIVE})

###############################################################################
# SHARED LIBRARY TARGET
###############################################################################

# Create SHARED library (external release). It links the merged archive rather
# than the loose GRAALVM_LIBS, so every build verifies the artifact the static
# package ships. The shim compiles as this target's own source: its object
# provides the exported wrappers and pulls the image member out of the archive,
# and the archive's own shim member is never pulled because nothing unresolved
# points at it.
add_library(<libName> SHARED ${CMAKE_CURRENT_LIST_DIR}/<libName>_c.cpp)
target_include_directories(<libName> PRIVATE ${CMAKE_CURRENT_LIST_DIR})
target_compile_features(<libName> PUBLIC cxx_std_11)
target_link_libraries(<libName> PRIVATE ${MERGED_ARCHIVE} ${GRAALVM_SYSLIBS})
target_link_options(<libName> PRIVATE ${GRAALVM_OPTS})
add_dependencies(<libName> <libName>_merged)

# Platform specific additions
if (APPLE)
    enable_language(OBJC)
    target_sources(<libName> PRIVATE ${CMAKE_CURRENT_LIST_DIR}/<libName>_objc.m)
    target_link_libraries(<libName> PRIVATE "")
endif ()

if (CMAKE_SYSTEM_NAME STREQUAL "Linux")
    # force a non-executable stack on Linux due to issues with newer Python::dlopen
    target_link_options(<libName> PRIVATE "-Wl,-z,noexecstack")
endif ()

# Properties
set_target_properties(<libName> PROPERTIES
        CXX_VISIBILITY_PRESET hidden
        VISIBILITY_INLINES_HIDDEN YES
        MACOSX_RPATH ON
)

###############################################################################
# BUILD RELEASE PACKAGE
###############################################################################
option(BUILD_RELEASE_PACKAGE "Build release package with bindings and ZIP archive" ON)
if (BUILD_RELEASE_PACKAGE)

    # Post process the shared library and move files to an adequate directory structure
    set(BINDINGS_DIR ${CMAKE_CURRENT_LIST_DIR}/bindings)
    set(RELEASE_DIR ${CMAKE_CURRENT_BINARY_DIR}/release/<libName>/<libName>)
    set(RELEASE_LIB_DIR ${RELEASE_DIR}/lib/${CMAKE_PLATFORM}/)
    add_custom_command(TARGET <libName> POST_BUILD
            COMMAND ${CMAKE_COMMAND} -E rm -rf ${RELEASE_DIR}/..
            COMMAND ${CMAKE_COMMAND} -E make_directory ${RELEASE_LIB_DIR}

            # Copy the language bindings
            COMMAND ${CMAKE_COMMAND} -E copy_directory ${BINDINGS_DIR} ${RELEASE_DIR}

            # Copy the shared library (note: automatically picks the correct extension)
            COMMAND ${CMAKE_COMMAND} -E copy "$\<TARGET_FILE:<libName>>" ${RELEASE_LIB_DIR}
    )

    if (WIN32) # windows - add .lib file
        add_custom_command(TARGET <libName> POST_BUILD
                COMMAND ${CMAKE_COMMAND} -E copy "$\<TARGET_FILE_DIR:<libName>>/<libName>.lib" ${RELEASE_LIB_DIR}
        )
    endif ()

    ###############################################################################
    # Build MEX Target via CMake
    ###############################################################################
    # Needs no MATLAB installation: the minimal bundled mex-sdk covers the default case.

    # Create a MEX target
    include(${CMAKE_CURRENT_LIST_DIR}/mex.cmake)
    add_mex_target(<libName>_native ${BINDINGS_DIR}/+<matlab_namespace>/private/<matlab_namespace>_native.cpp)

    # Make sure that the MEX library gets built beforehand
    add_dependencies(<libName> <libName>_native)

    # Copy the MEX file to the lib dir
    add_custom_command(TARGET <libName> POST_BUILD
            COMMAND ${CMAKE_COMMAND} -E copy "$\<TARGET_FILE:<libName>_native>" ${RELEASE_LIB_DIR}
    )

    ###############################################################################
    # POST-PROCESSING
    ###############################################################################
    # hide unwanted symbols (TODO: not generated yet. Is it necessary?)
    if (CMAKE_CXX_COMPILER_ID MATCHES "GNU|Clang")
        #set_target_properties(<libName> PROPERTIES COMPILE_FLAGS
        #        "-Wl,--version-script=${GRAALVM_OUTPUT_DIR}/cpp/exports -fvisibility=hidden -fvisibility-inlines-hidden")
    endif ()

    if (UNIX AND NOT APPLE) # unix - strip symbols
        add_custom_command(TARGET <libName> POST_BUILD
                COMMAND strip --strip-all --discard-all -x ${RELEASE_LIB_DIR}/lib<libName>.so
        )
    endif ()

    if (APPLE) # macOS - set rpath
        add_custom_command(TARGET <libName> POST_BUILD
                COMMAND install_name_tool -id @rpath/lib<libName>.dylib ${RELEASE_LIB_DIR}/lib<libName>.dylib
                COMMAND strip -x ${RELEASE_LIB_DIR}/lib<libName>.dylib
        )
    endif ()

    ###############################################################################
    # FINALIZATION
    ###############################################################################
    # Build a .zip release for each platform
    set(ZIP_DIR ${RELEASE_DIR}/../..)
    set(RELEASE_ZIP_NAME "<libName>-${PROJECT_VERSION}-${CMAKE_PLATFORM}.zip")
    add_custom_command(TARGET <libName> POST_BUILD
            COMMAND ${CMAKE_COMMAND} -E remove ${ZIP_DIR}/${RELEASE_ZIP_NAME}
            COMMAND ${CMAKE_COMMAND} -E tar cvf "${ZIP_DIR}/${RELEASE_ZIP_NAME}" --format=zip -- .
            WORKING_DIRECTORY "${RELEASE_DIR}/.."
    )

endif ()

###############################################################################
# BUILD STATIC PACKAGE
###############################################################################
# A self-contained package for consumers that link the library into their own
# binary: the merged archive, the user headers, the shim sources and a
# find_package configuration. Mirrors the release package above.
option(BUILD_STATIC_PACKAGE "Build prebuilt static library package and ZIP archive" ON)
if (BUILD_STATIC_PACKAGE)

    # System libraries the merged archive needs, one per line in link order.
    # Consumers that do not use CMake read these instead of the config file.
    list(JOIN GRAALVM_SYSLIBS "\n" _syslibs_content)
    file(WRITE ${MERGED_ARCHIVE_DIR}/syslibs.txt "${_syslibs_content}\n")
    foreach (_item IN LISTS GRAALVM_SYSLIBS)
        if (_item MATCHES "force_load")
            message(WARNING "The static package cannot ship ${_item}, "
                    "whole-archive semantics are lost outside this build tree.")
        endif ()
    endforeach ()

    # Record what the package was built from
    set(MANIFEST_FILE ${MERGED_ARCHIVE_DIR}/manifest.json)
    set(_manifest_archives ${CMAKE_STATIC_LIBRARY_PREFIX}<libName>_static${CMAKE_STATIC_LIBRARY_SUFFIX})
    foreach (_archive IN LISTS GRAALVM_ARCHIVES)
        get_filename_component(_name ${_archive} NAME)
        list(APPEND _manifest_archives ${_name})
    endforeach ()
    list(JOIN _manifest_archives "\", \"" _manifest_archives)
    list(JOIN GRAALVM_SYSLIBS "\", \"" _manifest_syslibs)
    if (MSVC)
        set(_manifest_runtime "\n  \"msvc_runtime\": \"${CMAKE_MSVC_RUNTIME_LIBRARY}\",")
    else ()
        set(_manifest_runtime "")
    endif ()
    file(WRITE ${MANIFEST_FILE} "{
  \"name\": \"<libName>\",
  \"version\": \"${PROJECT_VERSION}\",
  \"platform\": \"${CMAKE_PLATFORM}\",
  \"toolchain\": \"${CMAKE_CXX_COMPILER_ID} ${CMAKE_CXX_COMPILER_VERSION}\",${_manifest_runtime}
  \"archives\": [\"${_manifest_archives}\"],
  \"syslibs\": [\"${_manifest_syslibs}\"]
}
")

    # Stage the package tree
    set(STATIC_RELEASE_DIR ${CMAKE_CURRENT_BINARY_DIR}/release-static/<libName>/<libName>)
    set(STATIC_RELEASE_LIB_DIR ${STATIC_RELEASE_DIR}/lib/${CMAKE_PLATFORM}/)
    add_custom_command(TARGET <libName> POST_BUILD
            COMMAND ${CMAKE_COMMAND} -E rm -rf ${STATIC_RELEASE_DIR}/..
            COMMAND ${CMAKE_COMMAND} -E make_directory ${STATIC_RELEASE_LIB_DIR} ${STATIC_RELEASE_DIR}/include ${STATIC_RELEASE_DIR}/src ${STATIC_RELEASE_DIR}/cmake

            # The undecorated user headers, without <libName>_dynamic.hpp: the
            # runtime loader only applies to the shared library
            COMMAND ${CMAKE_COMMAND} -E copy ${CMAKE_CURRENT_LIST_DIR}/bindings/include/<libName>_c.h ${CMAKE_CURRENT_LIST_DIR}/bindings/include/<libName>.hpp ${STATIC_RELEASE_DIR}/include

            # The merged archive plus its link metadata
            COMMAND ${CMAKE_COMMAND} -E copy ${MERGED_ARCHIVE} ${MERGED_ARCHIVE_DIR}/syslibs.txt ${MANIFEST_FILE} ${STATIC_RELEASE_LIB_DIR}

            # Shim sources for consumers that want to compile the shim themselves
            COMMAND ${CMAKE_COMMAND} -E copy ${CMAKE_CURRENT_LIST_DIR}/<libName>_c.cpp ${CMAKE_CURRENT_LIST_DIR}/<libName>_c.h ${CMAKE_CURRENT_LIST_DIR}/<libName>_objc.m ${STATIC_RELEASE_DIR}/src

            # find_package entry point
            COMMAND ${CMAKE_COMMAND} -E copy ${CMAKE_CURRENT_LIST_DIR}/<libName>-config.cmake ${CMAKE_CURRENT_LIST_DIR}/<libName>-config-version.cmake ${STATIC_RELEASE_DIR}/cmake
    )

    # Build a .zip release for each platform
    set(STATIC_ZIP_DIR ${STATIC_RELEASE_DIR}/../..)
    set(STATIC_ZIP_NAME "<libName>-${PROJECT_VERSION}-${CMAKE_PLATFORM}-static.zip")
    add_custom_command(TARGET <libName> POST_BUILD
            COMMAND ${CMAKE_COMMAND} -E remove ${STATIC_ZIP_DIR}/${STATIC_ZIP_NAME}
            COMMAND ${CMAKE_COMMAND} -E tar cvf "${STATIC_ZIP_DIR}/${STATIC_ZIP_NAME}" --format=zip -- .
            WORKING_DIRECTORY "${STATIC_RELEASE_DIR}/.."
    )

endif ()

###############################################################################
# UTILITY METHODS TO SIMPLIFY TESTING
###############################################################################
function(target_add_<libName>_static target_name)
    include(${CMAKE_CURRENT_FUNCTION_LIST_DIR}/opts.cmake) # defines GRAALVM_OPTS
    target_include_directories(${target_name} PRIVATE ${CMAKE_CURRENT_FUNCTION_LIST_DIR}/bindings/include)
    target_link_libraries(${target_name} PRIVATE <libName>_static)
    target_link_options(${target_name} PRIVATE ${GRAALVM_OPTS})
    if(MSVC AND NOT DEFINED CMAKE_MSVC_RUNTIME_LIBRARY)
        # Match the static libs. Silently ignored unless the caller declares 3.15 or newer.
        set_property(TARGET ${target_name} PROPERTY MSVC_RUNTIME_LIBRARY "MultiThreadedDLL")
    endif()
endfunction()

function(target_add_<libName>_shared target_name)
    target_include_directories(${target_name} PRIVATE ${CMAKE_CURRENT_FUNCTION_LIST_DIR}/bindings/include)
    target_link_libraries(${target_name} PRIVATE <libName>)
    add_copy_<libName>(${target_name})
endfunction()

function(add_copy_<libName> target_name)
    add_custom_command(TARGET ${target_name} POST_BUILD
            COMMAND ${CMAKE_COMMAND} -E copy_if_different
            "$\<TARGET_FILE:<libName>>"
            $\<TARGET_FILE_DIR:${target_name}>)
endfunction()