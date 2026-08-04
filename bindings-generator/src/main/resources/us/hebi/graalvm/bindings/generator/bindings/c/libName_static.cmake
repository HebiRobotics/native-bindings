# GraalVM libraries
include( ${CMAKE_CURRENT_LIST_DIR}/libs.cmake ) # defines GRAALVM_LIBS
include( ${CMAKE_CURRENT_LIST_DIR}/opts.cmake ) # defines GRAALVM_OPTS

# Liberica's JavaFX static libs require the release CRT. Set the variable beforehand to
# opt out, e.g. for an image that does not link any of them.
if(MSVC AND NOT DEFINED CMAKE_MSVC_RUNTIME_LIBRARY)
    set(CMAKE_MSVC_RUNTIME_LIBRARY "MultiThreadedDLL")
endif()

# Create STATIC library target. Users need to pick headers from an appropriate bindings dir
add_library(<libName>_static STATIC ${CMAKE_CURRENT_LIST_DIR}/<libName>_c.cpp)
target_include_directories(<libName>_static PRIVATE ${CMAKE_CURRENT_LIST_DIR})
target_compile_features(<libName>_static PUBLIC cxx_std_11)
target_link_libraries(<libName>_static PRIVATE ${GRAALVM_LIBS})

# ELF only allows position independent objects inside a shared library, and consumers
# link this archive into theirs. The GraalVM members already are, the shim is the one
# that has to be told. Without it the consumer's link fails on a TPOFF32 relocation
# against the thread local in the isolate helper.
#
# The archive gets shipped too, so on MSVC its objects have to carry their own debug
# info. The default /Zi records the path of a PDB in this build tree, which a consumer
# linking the archive does not have and reports as LNK4099. The property needs CMP0141,
# which the generated CMakeLists.txt gets from its cmake_minimum_required.
set_target_properties(<libName>_static PROPERTIES
        POSITION_INDEPENDENT_CODE ON
        MSVC_DEBUG_INFORMATION_FORMAT "$\<$\<CONFIG:Debug,RelWithDebInfo>:Embedded>")

# Platform specific additions
if(APPLE)
    enable_language(OBJC)
    target_sources(<libName>_static PRIVATE ${CMAKE_CURRENT_LIST_DIR}/<libName>_objc.m)
    target_link_libraries(<libName>_static PRIVATE <appleLibs>)
endif()