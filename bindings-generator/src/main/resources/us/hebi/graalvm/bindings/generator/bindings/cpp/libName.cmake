# Used to download the binaries - this should not be used directly.
cmake_minimum_required(VERSION 3.12)

set(<projectName>_VERSION "<libVersion>")
set(<projectName>_BUILD_NUMBER "<buildNumber>")

# ======== Determine target platform ========
if(CMAKE_SYSTEM_NAME STREQUAL "Linux")
  set(<projectName>_OS "linux")
  set(<projectName>_LIB_NAME "lib<libName>.so")
  set(<projectName>_LINK_NAME "lib<libName>.so")
  if(DEFINED ENV{XDG_CACHE_HOME})
    set(<projectName>_CACHE_HOME "$ENV{XDG_CACHE_HOME}")
  else()
    set(<projectName>_CACHE_HOME "$ENV{HOME}/.cache")
  endif()
elseif(CMAKE_SYSTEM_NAME STREQUAL "Darwin")
  set(<projectName>_OS "osx")
  set(<projectName>_LIB_NAME "lib<libName>.dylib")
  set(<projectName>_LINK_NAME "lib<libName>.dylib")
  set(<projectName>_CACHE_HOME "$ENV{HOME}/Library/Caches")
elseif(CMAKE_SYSTEM_NAME STREQUAL "Windows")
  set(<projectName>_OS "win")
  set(<projectName>_LIB_NAME "<libName>.dll")
  set(<projectName>_LINK_NAME "<libName>.lib")
  set(<projectName>_CACHE_HOME "$ENV{LOCALAPPDATA}")
endif()

if(CMAKE_SYSTEM_PROCESSOR STREQUAL "x86_64" OR CMAKE_SYSTEM_PROCESSOR STREQUAL "AMD64")
  set(<projectName>_ARCH "amd64") # For 64-bit
elseif(CMAKE_SYSTEM_PROCESSOR STREQUAL "arm64" OR CMAKE_SYSTEM_PROCESSOR STREQUAL "aarch64")
  set(<projectName>_ARCH "arm64") # For ARM64
endif()

set(<projectName>_PLATFORM "${<projectName>_OS}_${<projectName>_ARCH}")

# ======== Variables to be used by including projects ========
# Prioritize locally bundled lib binaries
set(<projectName>_INCLUDE_DIR "${CMAKE_CURRENT_LIST_DIR}/include/")
set(<projectName>_LIB_FILE "${CMAKE_CURRENT_LIST_DIR}/lib/${<projectName>_PLATFORM}/${<projectName>_LIB_NAME}")
set(<projectName>_LINK_FILE "${CMAKE_CURRENT_LIST_DIR}/lib/${<projectName>_PLATFORM}/${<projectName>_LINK_NAME}")

# Fall back to shared downloaded version in local cache
if(NOT EXISTS "${<projectName>_LIB_FILE}")

  set(<projectName>_DOWNLOAD_DIR "${<projectName>_CACHE_HOME}/<libName>/${<projectName>_VERSION}-build${<projectName>_BUILD_NUMBER}")
  set(<projectName>_LIB_FILE "${<projectName>_DOWNLOAD_DIR}/<libName>/lib/${<projectName>_PLATFORM}/${<projectName>_LIB_NAME}")
  set(<projectName>_LINK_FILE "${<projectName>_DOWNLOAD_DIR}/<libName>/lib/${<projectName>_PLATFORM}/${<projectName>_LINK_NAME}")

  # Include directory fallback, so we could bundle the cmake file without any headers
  if(NOT EXISTS "${<projectName>_INCLUDE_DIR}")
    set(<projectName>_INCLUDE_DIR "${<projectName>_DOWNLOAD_DIR}/<libName>/include/")
  endif()

endif()

function(add_copy_<libName> target_name)
  add_custom_command(TARGET ${target_name} POST_BUILD
                     COMMAND ${CMAKE_COMMAND} -E copy_if_different
                     "${<projectName>_LIB_FILE}"
                     $\<TARGET_FILE_DIR:${target_name}>)
endfunction()

function(target_add_<libName> target_name)
  target_include_directories(${target_name} PRIVATE ${<projectName>_INCLUDE_DIR})
  target_link_libraries(${target_name} PRIVATE ${<projectName>_LINK_FILE})
  add_copy_<libName>(${target_name})
endfunction()
<if(downloadUrl)>

# ======== Download binaries on-demand ========
option(DOWNLOAD_<projectName> "Download binaries for the local system if none are available" ON)
if(DOWNLOAD_<projectName> AND NOT EXISTS "${<projectName>_LIB_FILE}")

  # Build URL
  set(<projectName>_DL_NAME "<libName>-${<projectName>_VERSION}-${<projectName>_PLATFORM}.zip")
  set(<projectName>_DL_URL "<downloadUrl>/${<projectName>_DL_NAME}")

  # Make sure the directory exists
  file(MAKE_DIRECTORY "${<projectName>_DOWNLOAD_DIR}")

  # Download full release
  message(STATUS "Downloading ${<projectName>_DL_URL}")
  get_filename_component(<projectName>_DL_PATH "${<projectName>_DOWNLOAD_DIR}/${<projectName>_DL_NAME}" REALPATH)
  file(DOWNLOAD "${<projectName>_DL_URL}" "${<projectName>_DL_PATH}" SHOW_PROGRESS)

  # Extract to shared cache directory
  message(STATUS "Extracting to ${<projectName>_DOWNLOAD_DIR}")
  file(ARCHIVE_EXTRACT
       INPUT "${<projectName>_DL_PATH}"
       DESTINATION "${<projectName>_DOWNLOAD_DIR}")

  message(STATUS "Removing temporary files")
  file(REMOVE "${<projectName>_DL_PATH}")

endif()
<endif>