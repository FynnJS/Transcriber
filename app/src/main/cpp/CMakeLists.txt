cmake_minimum_required(VERSION 3.22.1)
project(transcriber_whisper C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# The app is debugged from Kotlin; inference must not run unoptimized, which is
# roughly 40x slower. Keep debug symbols but optimize whisper.cpp and ggml.
set(CMAKE_C_FLAGS_DEBUG "-O3 -g")
set(CMAKE_CXX_FLAGS_DEBUG "-O3 -g")

set(WHISPER_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/whisper.cpp")
if(NOT EXISTS "${WHISPER_DIR}/include/whisper.h")
    message(FATAL_ERROR "whisper.cpp submodule missing at ${WHISPER_DIR}. Run: git submodule update --init --recursive")
endif()

# Link whisper.cpp into the JNI library. arm64 switches to shared ggml libraries so the
# CPU backend can be picked at runtime (see below); x86_64 stays static.
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_IS_DEV OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL OFF CACHE BOOL "" FORCE)
set(GGML_CPU_ALL_VARIANTS OFF CACHE BOOL "" FORCE)

# GGML_NATIVE is off because the build is cross-compiled, which leaves ggml on its
# scalar baseline. For the x86_64 test ABI (emulator) every supported CPU has AVX2,
# so enable the vector kernels explicitly.
if(ANDROID_ABI STREQUAL "x86_64")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -mavx -mavx2 -mfma -mf16c")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -mavx -mavx2 -mfma -mf16c")
endif()

# arm64 phones cover several instruction set levels. A single -march=armv8.2 build with
# dotprod/fp16 would be faster on current devices but crashes on older armv8.0 ones, which
# minSdk 26 still includes. ggml therefore builds one CPU backend per level and the loader
# picks the best one the device supports. That needs dynamically loaded backends, which in
# turn need shared ggml libraries.
if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(BUILD_SHARED_LIBS ON CACHE BOOL "" FORCE)
    set(GGML_BACKEND_DL ON CACHE BOOL "" FORCE)
    set(GGML_CPU_ALL_VARIANTS ON CACHE BOOL "" FORCE)
endif()

add_subdirectory("${WHISPER_DIR}" "${CMAKE_BINARY_DIR}/whisper.cpp")

add_library(transcriber_whisper SHARED whisper_jni.cpp)

if(GGML_BACKEND_DL)
    target_compile_definitions(transcriber_whisper PRIVATE TRANSCRIBER_GGML_BACKEND_DL=1)
endif()

target_include_directories(transcriber_whisper PRIVATE
    "${WHISPER_DIR}/include"
    "${WHISPER_DIR}/ggml/include"
)

target_compile_options(transcriber_whisper PRIVATE
    -O3
    -ffunction-sections
    -fdata-sections
    -fvisibility=hidden
    -fvisibility-inlines-hidden
)

target_link_options(transcriber_whisper PRIVATE -Wl,--gc-sections)

target_link_libraries(transcriber_whisper
    whisper
    log
    android
    dl
)
