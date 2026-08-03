# Determine Platform and Extension
if (WIN32)
    set(MEX_EXT ".mexw64") # x64 and emulated ARM
elseif (APPLE)
    # Allow cross compilation
    set(OSX_TARGET_ARCH ${CMAKE_SYSTEM_PROCESSOR})
    if (NOT "${CMAKE_OSX_ARCHITECTURES}" STREQUAL "")
        set(OSX_TARGET_ARCH ${CMAKE_OSX_ARCHITECTURES})
    endif ()
    if (OSX_TARGET_ARCH MATCHES "arm64|aarch64")
        set(MEX_EXT ".mexmaca64") # Apple Silicon (maca)
    else ()
        set(MEX_EXT ".mexmaci64") # Intel Mac (maci)
    endif ()
else ()
    set(MEX_EXT ".mexa64")  # a64 for all 64-bit Linux
endif ()

# Helper function for creating MEX files
function(add_mex_target TARGET_NAME)

    # Building MEX only needs the headers and, on Windows, the import libraries.
    # MATLABROOT selects a real installation, otherwise the bundled clean-room
    # mex-sdk covers the API subset the generated gateway uses.
    if(DEFINED ENV{MATLABROOT})
        message(STATUS "Using matlabroot: $ENV{MATLABROOT}")
        set(SDK_ROOT "$ENV{MATLABROOT}/extern")
        set(SDK_INCLUDE_DIR "${SDK_ROOT}/include")
        set(USE_BUNDLED_MEX_SDK OFF)
    else()
        message(STATUS "MATLABROOT is not set, using the bundled MEX SDK")
        set(SDK_INCLUDE_DIR "${CMAKE_CURRENT_LIST_DIR}/mex-sdk")
        set(USE_BUNDLED_MEX_SDK ON)
    endif()

    # Define the module (MEX is a loadable module)
    add_library(${TARGET_NAME} MODULE ${ARGN})
    target_include_directories(${TARGET_NAME} PRIVATE "${SDK_INCLUDE_DIR}")

    # Set the correct extension
    set_target_properties(${TARGET_NAME} PROPERTIES
            PREFIX ""
            SUFFIX "${MEX_EXT}"
            OUTPUT_NAME "${TARGET_NAME}"
            C_VISIBILITY_PRESET hidden
            CXX_VISIBILITY_PRESET hidden
            VISIBILITY_INLINES_HIDDEN ON
    )

    # MATLAB compatibility definitions
    target_compile_definitions(${TARGET_NAME} PRIVATE
            MATLAB_MEX_FILE # Mandatory MEX identifier
            MX_COMPAT_64 # 64-bit MATLAB
            MATLAB_DEFAULT_RELEASE=R2018a # Interleaved API
    )

    # Compiler options
    if(MSVC)
        # MSVC compiler options. Shipped MEX files are always release-built, so the
        # optimizer flags only apply outside Debug configurations: cl rejects /RTC1
        # together with /O2 ("D8016"), which would break IDE profiles that default
        # to Debug before they compile anything.
        target_compile_options(${TARGET_NAME} PRIVATE
                /MT # statically link vcruntime
                $<$<NOT:$<CONFIG:Debug>>:/O2> # maximize speed
                $<$<NOT:$<CONFIG:Debug>>:/DNDEBUG> # strip asserts
        )
        target_link_options(${TARGET_NAME} PRIVATE "/EXPORT:mexFunction") # export manually due to mex.h breaking macro
        set(WIN_SUBDIR "microsoft")
    else ()
        # GNU / Clang compiler options
        target_compile_options(${TARGET_NAME} PRIVATE
                -O3 # Aggressive optimizations
                -DNDEBUG # strip asserts
        )
        set(WIN_SUBDIR "mingw64")
    endif()

    # Platform-specific Linking
    if(WIN32 AND USE_BUNDLED_MEX_SDK)
        # Windows MUST link against import libs, so build them from the bundled def
        # files. The def files only carry export names, which is all an import
        # library contains.
        set(_mex_sdk_lib_dir "${CMAKE_CURRENT_BINARY_DIR}/mex-sdk")
        file(MAKE_DIRECTORY "${_mex_sdk_lib_dir}")
        if(MSVC)
            set(_lib_tool ${CMAKE_AR})
            if (NOT _lib_tool)
                set(_lib_tool lib.exe)
            endif ()
            foreach(_name libmex libmx)
                execute_process(
                        COMMAND ${_lib_tool} /NOLOGO /MACHINE:X64
                                "/DEF:${SDK_INCLUDE_DIR}/${_name}.def"
                                "/OUT:${_mex_sdk_lib_dir}/${_name}.lib"
                        RESULT_VARIABLE _lib_result)
                if(NOT _lib_result EQUAL 0)
                    message(FATAL_ERROR "Failed to build ${_name}.lib from ${_name}.def")
                endif()
            endforeach()
        else()
            # MinGW ships dlltool with binutils
            foreach(_name libmex libmx)
                execute_process(
                        COMMAND dlltool -d "${SDK_INCLUDE_DIR}/${_name}.def"
                                -l "${_mex_sdk_lib_dir}/${_name}.lib"
                        RESULT_VARIABLE _lib_result)
                if(NOT _lib_result EQUAL 0)
                    message(FATAL_ERROR "Failed to build ${_name}.lib from ${_name}.def")
                endif()
            endforeach()
        endif()
        target_link_libraries(${TARGET_NAME} PRIVATE
                "${_mex_sdk_lib_dir}/libmex.lib"
                "${_mex_sdk_lib_dir}/libmx.lib"
        )
    elseif(WIN32)
        # Windows MUST link against the import libs
        target_link_libraries(${TARGET_NAME} PRIVATE
                "${SDK_ROOT}/lib/win64/${WIN_SUBDIR}/libmex.lib"
                "${SDK_ROOT}/lib/win64/${WIN_SUBDIR}/libmx.lib"
        )
    elseif(APPLE)
        # macOS: Resolve symbols at runtime from the MATLAB process
        target_link_options(${TARGET_NAME} PRIVATE "-undefined" "dynamic_lookup")
    else()
        # Linux: Symbols are resolved by MATLAB's process global scope
    endif()

endfunction()