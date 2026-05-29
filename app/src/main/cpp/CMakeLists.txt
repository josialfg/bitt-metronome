cmake_minimum_required(VERSION 3.22.1)
project("metronome")

find_package(oboe REQUIRED CONFIG)

add_library(
    metronome
    SHARED
    metronome_engine.cpp
)

target_link_libraries(
    metronome
    oboe::oboe
    android
    log
)

# Align ELF segments to 16 KB pages so the .so loads on devices that require
# 16 KB page size (Android 15+, some Pixel 8/9 builds). Without this the system
# may flag the app as "not compatible with 16 KB page sizes".
target_link_options(metronome PRIVATE "-Wl,-z,max-page-size=16384")
