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

    # Make sure we have a path to the headers
    if(NOT DEFINED ENV{MATLABROOT})
        message(FATAL_ERROR "Required environment variable: MATLABROOT is not set. Point it to your MATLAB installation or Slim SDK.")
    endif()
    message(STATUS "Using matlabroot: $ENV{MATLABROOT}")
    set(SDK_ROOT "$ENV{MATLABROOT}/extern")

    # Define the module (MEX is a loadable module)
    add_library(${TARGET_NAME} MODULE ${ARGN})
    target_include_directories(${TARGET_NAME} PRIVATE "${SDK_ROOT}/include")

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
        # MSVC compiler options
        target_compile_options(${TARGET_NAME} PRIVATE
                /MT # statically link vcruntime
                /O2 # maximize speed
                /DNDEBUG # strip asserts
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
    if(WIN32)
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