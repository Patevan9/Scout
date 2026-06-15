cmake_minimum_required(VERSION 3.22.1)
project("scout_llama" LANGUAGES CXX)

set(JNILIB ${CMAKE_SOURCE_DIR}/../jniLibs/arm64-v8a)

add_library(scout_llama SHARED scout_llama_jni.cpp)

target_include_directories(scout_llama PRIVATE ${CMAKE_SOURCE_DIR})

target_compile_options(scout_llama PRIVATE -std=c++17 -O2)

target_link_libraries(
        scout_llama
        -L${JNILIB}
        -lllama
        -lllama-common
        -lggml
        -lggml-base
        -lggml-cpu-android_armv8.2_2
        android
        log
)