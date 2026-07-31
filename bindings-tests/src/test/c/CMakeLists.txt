# Runtime test for the raw C ABI.
#
# Run after `mvn verify -Pnative` has produced the shared library and the
# generated bindings:
#
#   cmake -S src/test/c -B target/ctest-c
#   cmake --build target/ctest-c
#   ctest --test-dir target/ctest-c --output-on-failure
#
# Both locations can be overridden with -DBINDINGS_IMAGE_DIR=... and
# -DBINDINGS_INCLUDE_DIR=... if the build output lives somewhere else.

cmake_minimum_required(VERSION 3.14)
project(BindingsTestsC C)

set(CMAKE_C_STANDARD 99)
set(CMAKE_C_STANDARD_REQUIRED ON)

enable_testing()

# bindings-tests module root, three levels up from src/test/c
get_filename_component(MODULE_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../.." ABSOLUTE)

# Same platform naming the release CMakeLists uses
if(CMAKE_SYSTEM_NAME STREQUAL "Linux")
  set(_platform_prefix "linux")
elseif(CMAKE_SYSTEM_NAME STREQUAL "Darwin")
  set(_platform_prefix "osx")
elseif(CMAKE_SYSTEM_NAME STREQUAL "Windows")
  set(_platform_prefix "win")
endif()
if(CMAKE_SYSTEM_PROCESSOR STREQUAL "x86_64" OR CMAKE_SYSTEM_PROCESSOR STREQUAL "AMD64")
  set(_platform_suffix "amd64")
elseif(CMAKE_SYSTEM_PROCESSOR STREQUAL "arm64" OR CMAKE_SYSTEM_PROCESSOR STREQUAL "aarch64")
  set(_platform_suffix "arm64")
endif()
set(BINDINGS_PLATFORM "${_platform_prefix}_${_platform_suffix}")

# Stage two output
set(BINDINGS_IMAGE_DIR
    "${MODULE_ROOT}/target/native-build/release/bindings_tests/bindings_tests/lib/${BINDINGS_PLATFORM}"
    CACHE PATH "Directory holding the built shared library")
set(BINDINGS_INCLUDE_DIR "${MODULE_ROOT}/target/image/static/bindings/include"
    CACHE PATH "Directory holding the generated C header")

if(NOT EXISTS "${BINDINGS_INCLUDE_DIR}/bindings_tests_c.h")
  message(FATAL_ERROR
      "bindings_tests_c.h not found in ${BINDINGS_INCLUDE_DIR}. "
      "Run `mvn verify -Pnative` first.")
endif()

find_library(BINDINGS_TESTS_LIBRARY
             NAMES bindings_tests
             PATHS "${BINDINGS_IMAGE_DIR}"
             NO_DEFAULT_PATH)
if(NOT BINDINGS_TESTS_LIBRARY)
  message(FATAL_ERROR
      "Shared library not found in ${BINDINGS_IMAGE_DIR}. "
      "Run scripts/build-native.bat or scripts/build-native.sh first, which runs "
      "both the Maven build and the release CMake build.")
endif()

add_executable(test_bindings_c test_bindings.c)
target_include_directories(test_bindings_c PRIVATE "${BINDINGS_INCLUDE_DIR}")
target_link_libraries(test_bindings_c PRIVATE "${BINDINGS_TESTS_LIBRARY}")

if(MSVC)
  # The test sources contain UTF-8 string literals
  target_compile_options(test_bindings_c PRIVATE /utf-8)
endif()

# Find the shared library at runtime without installing it
set_target_properties(test_bindings_c PROPERTIES
    BUILD_RPATH "${BINDINGS_IMAGE_DIR}"
    INSTALL_RPATH "${BINDINGS_IMAGE_DIR}")

if(WIN32)
  # Windows has no rpath, so put a copy of the dll next to the executable
  add_custom_command(TARGET test_bindings_c POST_BUILD
                     COMMAND ${CMAKE_COMMAND} -E copy_if_different
                     "${BINDINGS_IMAGE_DIR}/bindings_tests.dll"
                     $<TARGET_FILE_DIR:test_bindings_c>)
endif()

add_test(NAME c_bindings COMMAND test_bindings_c)
