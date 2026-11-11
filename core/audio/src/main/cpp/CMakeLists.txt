cmake_minimum_required(VERSION 3.22.1)
project(gimi_echo C)

add_library(gimi_echo SHARED
    echo_jni.c
    speexdsp/libspeexdsp/mdf.c
    speexdsp/libspeexdsp/preprocess.c
    speexdsp/libspeexdsp/fftwrap.c
    speexdsp/libspeexdsp/smallft.c
    speexdsp/libspeexdsp/filterbank.c
    speexdsp/libspeexdsp/resample.c
)
target_include_directories(gimi_echo PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    speexdsp/include
    speexdsp/include/speex
    speexdsp/libspeexdsp
)
target_compile_definitions(gimi_echo PRIVATE HAVE_CONFIG_H)
target_compile_options(gimi_echo PRIVATE -fvisibility=hidden)
target_link_libraries(gimi_echo PRIVATE m)

# 按需在设备上验证实际 JNI/DSP，日常 APK 不包含测试程序。
option(GIMI_BUILD_AEC_TESTS "Build the native synthetic echo/double-talk regression" OFF)
if(GIMI_BUILD_AEC_TESTS)
    add_executable(gimi_echo_test ../../test/cpp/SoftwareEchoCancellerTest.c)
    target_link_libraries(gimi_echo_test PRIVATE gimi_echo m)
endif()
