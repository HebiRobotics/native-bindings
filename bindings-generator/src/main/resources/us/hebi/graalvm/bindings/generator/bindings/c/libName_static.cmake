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

# Platform specific additions
if(APPLE)
    enable_language(OBJC)
    target_sources(<libName>_static PRIVATE ${CMAKE_CURRENT_LIST_DIR}/<libName>_objc.m)
    target_link_libraries(<libName>_static PRIVATE <appleLibs>)
endif()