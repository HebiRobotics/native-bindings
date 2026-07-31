# Used to download the binaries - this should not be used directly.
cmake_minimum_required(VERSION 3.12)

set(BINDINGS_TESTS_VERSION "1.2.3")
set(BINDINGS_TESTS_BUILD_NUMBER "42")

# ======== Determine target platform ========
if(CMAKE_SYSTEM_NAME STREQUAL "Linux")
  set(BINDINGS_TESTS_OS "linux")
  set(BINDINGS_TESTS_LIB_NAME "libbindings_tests.so")
  set(BINDINGS_TESTS_LINK_NAME "libbindings_tests.so")
  if(DEFINED ENV{XDG_CACHE_HOME})
    set(BINDINGS_TESTS_CACHE_HOME "$ENV{XDG_CACHE_HOME}")
  else()
    set(BINDINGS_TESTS_CACHE_HOME "$ENV{HOME}/.cache")
  endif()
elseif(CMAKE_SYSTEM_NAME STREQUAL "Darwin")
  set(BINDINGS_TESTS_OS "osx")
  set(BINDINGS_TESTS_LIB_NAME "libbindings_tests.dylib")
  set(BINDINGS_TESTS_LINK_NAME "libbindings_tests.dylib")
  set(BINDINGS_TESTS_CACHE_HOME "$ENV{HOME}/Library/Caches")
elseif(CMAKE_SYSTEM_NAME STREQUAL "Windows")
  set(BINDINGS_TESTS_OS "win")
  set(BINDINGS_TESTS_LIB_NAME "bindings_tests.dll")
  set(BINDINGS_TESTS_LINK_NAME "bindings_tests.lib")
  set(BINDINGS_TESTS_CACHE_HOME "$ENV{LOCALAPPDATA}")
endif()

if(CMAKE_SYSTEM_PROCESSOR STREQUAL "x86_64" OR CMAKE_SYSTEM_PROCESSOR STREQUAL "AMD64")
  set(BINDINGS_TESTS_ARCH "amd64") # For 64-bit
elseif(CMAKE_SYSTEM_PROCESSOR STREQUAL "arm64" OR CMAKE_SYSTEM_PROCESSOR STREQUAL "aarch64")
  set(BINDINGS_TESTS_ARCH "arm64") # For ARM64
endif()

set(BINDINGS_TESTS_PLATFORM "${BINDINGS_TESTS_OS}_${BINDINGS_TESTS_ARCH}")

# ======== Variables to be used by including projects ========
# Prioritize locally bundled lib binaries
set(BINDINGS_TESTS_INCLUDE_DIR "${CMAKE_CURRENT_LIST_DIR}/include/")
set(BINDINGS_TESTS_LIB_FILE "${CMAKE_CURRENT_LIST_DIR}/lib/${BINDINGS_TESTS_PLATFORM}/${BINDINGS_TESTS_LIB_NAME}")
set(BINDINGS_TESTS_LINK_FILE "${CMAKE_CURRENT_LIST_DIR}/lib/${BINDINGS_TESTS_PLATFORM}/${BINDINGS_TESTS_LINK_NAME}")

# Fall back to shared downloaded version in local cache
if(NOT EXISTS "${BINDINGS_TESTS_LIB_FILE}")

  set(BINDINGS_TESTS_DOWNLOAD_DIR "${BINDINGS_TESTS_CACHE_HOME}/bindings_tests/${BINDINGS_TESTS_VERSION}-build${BINDINGS_TESTS_BUILD_NUMBER}")
  set(BINDINGS_TESTS_LIB_FILE "${BINDINGS_TESTS_DOWNLOAD_DIR}/bindings_tests/lib/${BINDINGS_TESTS_PLATFORM}/${BINDINGS_TESTS_LIB_NAME}")
  set(BINDINGS_TESTS_LINK_FILE "${BINDINGS_TESTS_DOWNLOAD_DIR}/bindings_tests/lib/${BINDINGS_TESTS_PLATFORM}/${BINDINGS_TESTS_LINK_NAME}")

  # Include directory fallback, so we could bundle the cmake file without any headers
  if(NOT EXISTS "${BINDINGS_TESTS_INCLUDE_DIR}")
    set(BINDINGS_TESTS_INCLUDE_DIR "${BINDINGS_TESTS_DOWNLOAD_DIR}/bindings_tests/include/")
  endif()

endif()

function(add_copy_bindings_tests target_name)
  add_custom_command(TARGET ${target_name} POST_BUILD
                     COMMAND ${CMAKE_COMMAND} -E copy_if_different
                     "${BINDINGS_TESTS_LIB_FILE}"
                     $<TARGET_FILE_DIR:${target_name}>)
endfunction()

function(target_add_bindings_tests target_name)
  target_include_directories(${target_name} PRIVATE ${BINDINGS_TESTS_INCLUDE_DIR})
  target_link_libraries(${target_name} PRIVATE ${BINDINGS_TESTS_LINK_FILE})
  add_copy_bindings_tests(${target_name})
endfunction()

# ======== Download binaries on-demand ========
option(DOWNLOAD_BINDINGS_TESTS "Download binaries for the local system if none are available" ON)
if(DOWNLOAD_BINDINGS_TESTS AND NOT EXISTS "${BINDINGS_TESTS_LIB_FILE}")

  # Build URL
  set(BINDINGS_TESTS_DL_NAME "bindings_tests-${BINDINGS_TESTS_VERSION}-${BINDINGS_TESTS_PLATFORM}.zip")
  set(BINDINGS_TESTS_DL_URL "https://files.hebi.us/download/bindings_tests/snapshot/${BINDINGS_TESTS_BUILD_NUMBER}/${BINDINGS_TESTS_DL_NAME}")

  # Make sure the directory exists
  file(MAKE_DIRECTORY "${BINDINGS_TESTS_DOWNLOAD_DIR}")

  # Download full release
  message(STATUS "Downloading ${BINDINGS_TESTS_DL_URL}")
  get_filename_component(BINDINGS_TESTS_DL_PATH "${BINDINGS_TESTS_DOWNLOAD_DIR}/${BINDINGS_TESTS_DL_NAME}" REALPATH)
  file(DOWNLOAD "${BINDINGS_TESTS_DL_URL}" "${BINDINGS_TESTS_DL_PATH}" SHOW_PROGRESS)

  # Extract to shared cache directory
  message(STATUS "Extracting to ${BINDINGS_TESTS_DOWNLOAD_DIR}")
  file(ARCHIVE_EXTRACT
       INPUT "${BINDINGS_TESTS_DL_PATH}"
       DESTINATION "${BINDINGS_TESTS_DOWNLOAD_DIR}")

  message(STATUS "Removing temporary files")
  file(REMOVE "${BINDINGS_TESTS_DL_PATH}")

endif()
