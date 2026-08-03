# Version file for find_package version checks against the packaged version.
# The same major version is considered compatible.
set(PACKAGE_VERSION "<libVersion>")

if("${PACKAGE_FIND_VERSION}" STREQUAL "")
    set(PACKAGE_VERSION_COMPATIBLE TRUE)
elseif(PACKAGE_FIND_VERSION VERSION_GREATER PACKAGE_VERSION)
    set(PACKAGE_VERSION_COMPATIBLE FALSE)
else()
    string(REGEX MATCH "^[0-9]+" _<libName>_major "${PACKAGE_VERSION}")
    if(PACKAGE_FIND_VERSION_MAJOR EQUAL _<libName>_major)
        set(PACKAGE_VERSION_COMPATIBLE TRUE)
    else()
        set(PACKAGE_VERSION_COMPATIBLE FALSE)
    endif()
endif()

if(PACKAGE_FIND_VERSION VERSION_EQUAL PACKAGE_VERSION)
    set(PACKAGE_VERSION_EXACT TRUE)
endif()
