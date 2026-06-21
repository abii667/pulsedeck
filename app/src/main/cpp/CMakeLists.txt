cmake_minimum_required(VERSION 3.22.1)
project(pulse_native_audio)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(oboe REQUIRED CONFIG)

add_library(pulse_native_audio SHARED
    NativeAudioEngine.cpp
    JniBridge.cpp
)

target_include_directories(pulse_native_audio PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

target_link_options(pulse_native_audio PRIVATE LINKER:-z,max-page-size=16384)

target_link_libraries(pulse_native_audio
    oboe::oboe
    android
    log
)
