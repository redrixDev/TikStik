# SPDX-FileCopyrightText: 2026 redrixDev
# SPDX-License-Identifier: GPL-3.0-or-later

cmake_minimum_required(VERSION 3.22.1)
project(tikstik LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
foreach(tool ANIM_UTILS CWEBP DWEBP GIF2WEBP IMG2WEBP VWEBP WEBPINFO WEBPMUX EXTRAS WEBP_JS FUZZTEST)
  set(WEBP_BUILD_${tool} OFF CACHE BOOL "" FORCE)
endforeach()
set(WEBP_BUILD_LIBWEBPMUX ON CACHE BOOL "" FORCE)
# Without -O2 in debug, encoding an animation takes minutes.
set(CMAKE_C_FLAGS_DEBUG "-O2 -g")
set(CMAKE_CXX_FLAGS_DEBUG "-O2 -g")

# libwebp 1.6.0 (BSD-3-Clause) and giflib 6.1.3 (MIT), unmodified: see vendor/*/COPYING.
add_subdirectory(vendor/libwebp-1.6.0 webp)
add_library(gif STATIC
    vendor/giflib-6.1.3/dgif_lib.c
    vendor/giflib-6.1.3/gifalloc.c
    vendor/giflib-6.1.3/gif_err.c
    vendor/giflib-6.1.3/gif_hash.c
    vendor/giflib-6.1.3/openbsd-reallocarray.c)
target_include_directories(gif PUBLIC vendor/giflib-6.1.3)

add_library(tikcodec SHARED codec.cpp)
target_compile_options(tikcodec PRIVATE -Wall -Wextra)
target_link_libraries(tikcodec webp webpdemux libwebpmux gif)
# Only the JNI entry points are exported: libwebp and giflib stay hidden and the linker drops what is unused.
set(TIKCODEC_MAP ${CMAKE_CURRENT_SOURCE_DIR}/libtikcodec.map.txt)
target_link_options(tikcodec PRIVATE "-Wl,-z,max-page-size=16384" "-Wl,--version-script,${TIKCODEC_MAP}" "-Wl,--no-undefined-version")
set_target_properties(tikcodec PROPERTIES LINK_DEPENDS ${TIKCODEC_MAP})
