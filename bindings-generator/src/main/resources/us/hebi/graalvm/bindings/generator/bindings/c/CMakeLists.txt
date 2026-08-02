cmake_minimum_required(VERSION 3.17)
project(<projectName> VERSION <libVersion>)

###############################################################################
# SHARED LIBRARY TARGET
###############################################################################
# Load static lib and GraalVM definitions
include(${CMAKE_CURRENT_LIST_DIR}/<libName>_static.cmake)

# Create SHARED library (external release)
add_library(<libName> SHARED ${CMAKE_CURRENT_LIST_DIR}/<libName>_c.cpp)
target_include_directories(<libName> PRIVATE ${CMAKE_CURRENT_LIST_DIR})
target_compile_features(<libName> PUBLIC cxx_std_11)
target_link_libraries(<libName> PRIVATE ${GRAALVM_LIBS})
target_link_options(<libName> PRIVATE ${GRAALVM_OPTS})

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
    # Optional: Build MEX Target via CMake
    ###############################################################################
    if (DEFINED ENV{MATLABROOT})
        # Create a MEX target
        include(${CMAKE_CURRENT_LIST_DIR}/mex.cmake)
        add_mex_target(<libName>_native ${BINDINGS_DIR}/+<matlab_namespace>/private/<matlab_namespace>_native.cpp)

        # Make sure that the MEX library gets built beforehand
        add_dependencies(<libName> <libName>_native)

        # Copy the MEX file to the lib dir
        add_custom_command(TARGET <libName> POST_BUILD
                COMMAND ${CMAKE_COMMAND} -E copy "$\<TARGET_FILE:<libName>_native>" ${RELEASE_LIB_DIR}
        )
    endif ()

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