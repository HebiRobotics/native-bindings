# Consumer test for the staged static library package.
#
# This verifies that the static package produced by stage 2 (see item 8 in
# ../../../.claude/plans.md) can be consumed through plain find_package, with
# bindings_tests::static carrying its own include dirs, system libs and link
# options. It has to link and run with no shared library present at all, which
# is the property the whole package exists to provide.
#
# Run after `scripts/build-native.bat` or `scripts/build-native.sh` has staged
# the package:
#
#   cmake -S src/test/static -B target/ctest-static
#   cmake --build target/ctest-static
#   ctest --test-dir target/ctest-static --output-on-failure
#
# The package location can be overridden with -DBINDINGS_STATIC_PACKAGE_DIR=...
# if the build output lives somewhere else.

cmake_minimum_required(VERSION 3.19)
project(BindingsTestsStaticConsumer C CXX)

enable_testing()

# bindings-tests module root, three levels up from src/test/static
get_filename_component(MODULE_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../.." ABSOLUTE)

# Stage two output
set(BINDINGS_STATIC_PACKAGE_DIR
    "${MODULE_ROOT}/target/native-build/release-static/bindings_tests/bindings_tests"
    CACHE PATH "Directory holding the staged static package")

if(NOT EXISTS "${BINDINGS_STATIC_PACKAGE_DIR}/cmake/bindings_tests-config.cmake")
  message(FATAL_ERROR
      "bindings_tests-config.cmake not found in ${BINDINGS_STATIC_PACKAGE_DIR}/cmake. "
      "Run scripts/build-native.bat or scripts/build-native.sh first, which runs "
      "both the Maven build and the release CMake build.")
endif()

find_package(bindings_tests REQUIRED PATHS "${BINDINGS_STATIC_PACKAGE_DIR}/cmake" NO_DEFAULT_PATH)

# Proves that a pure C translation unit links against the imported target, so
# no C++ property is set on this executable.
add_executable(test_static_c test_static_c.c)
target_link_libraries(test_static_c PRIVATE bindings_tests::static)

if(MSVC)
  # The test sources contain UTF-8 string literals
  target_compile_options(test_static_c PRIVATE /utf-8)
endif()

add_executable(test_static_cpp test_static_cpp.cpp)
target_link_libraries(test_static_cpp PRIVATE bindings_tests::static)
set_target_properties(test_static_cpp PROPERTIES
    CXX_STANDARD 17
    CXX_STANDARD_REQUIRED ON)

add_test(NAME static_consumer_c COMMAND test_static_c)
add_test(NAME static_consumer_cpp COMMAND test_static_cpp)
