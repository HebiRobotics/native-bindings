# Runtime test for the generated RAII C++ wrapper.
#
# Run after `mvn verify -Pnative` has produced the shared library and the
# generated bindings:
#
#   cmake -S src/test/cpp -B target/ctest-cpp
#   cmake --build target/ctest-cpp
#   ctest --test-dir target/ctest-cpp --output-on-failure
#
# Both locations can be overridden with -DBINDINGS_IMAGE_DIR=... and
# -DBINDINGS_INCLUDE_DIR=... if the build output lives somewhere else.

cmake_minimum_required(VERSION 3.14)
project(BindingsTestsCpp CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

enable_testing()

# bindings-tests module root, three levels up from src/test/cpp
get_filename_component(MODULE_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../.." ABSOLUTE)

set(BINDINGS_IMAGE_DIR "${MODULE_ROOT}/target/image"
    CACHE PATH "Directory holding the built shared library")
set(BINDINGS_INCLUDE_DIR "${MODULE_ROOT}/target/image/static/bindings/include"
    CACHE PATH "Directory holding the generated C++ header")

if(NOT EXISTS "${BINDINGS_INCLUDE_DIR}/bindings_tests.hpp")
  message(FATAL_ERROR
      "bindings_tests.hpp not found in ${BINDINGS_INCLUDE_DIR}. "
      "Run `mvn verify -Pnative` first.")
endif()

find_library(BINDINGS_TESTS_LIBRARY
             NAMES bindings_tests
             PATHS "${BINDINGS_IMAGE_DIR}"
             NO_DEFAULT_PATH)
if(NOT BINDINGS_TESTS_LIBRARY)
  message(FATAL_ERROR
      "Shared library not found in ${BINDINGS_IMAGE_DIR}. "
      "Run `mvn verify -Pnative` first.")
endif()

add_executable(test_bindings_cpp test_bindings.cpp)
target_include_directories(test_bindings_cpp PRIVATE "${BINDINGS_INCLUDE_DIR}")
target_link_libraries(test_bindings_cpp PRIVATE "${BINDINGS_TESTS_LIBRARY}")

if(MSVC)
  # The test sources contain UTF-8 string literals
  target_compile_options(test_bindings_cpp PRIVATE /utf-8)
endif()

# Find the shared library at runtime without installing it
set_target_properties(test_bindings_cpp PROPERTIES
    BUILD_RPATH "${BINDINGS_IMAGE_DIR}"
    INSTALL_RPATH "${BINDINGS_IMAGE_DIR}")

if(WIN32)
  # Windows has no rpath, so put a copy of the dll next to the executable
  add_custom_command(TARGET test_bindings_cpp POST_BUILD
                     COMMAND ${CMAKE_COMMAND} -E copy_if_different
                     "${BINDINGS_IMAGE_DIR}/bindings_tests.dll"
                     $<TARGET_FILE_DIR:test_bindings_cpp>)
endif()

add_test(NAME cpp_bindings COMMAND test_bindings_cpp)
