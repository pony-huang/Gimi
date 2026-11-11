SpeexDSP 1.2.1 — selected DSP sources and headers from
https://downloads.xiph.org/releases/speex/speexdsp-1.2.1.tar.gz

Archive SHA-256: 8c777343e4a6399569c72abc38a95b24db56882c83dbdb6c6424a5f4aeb54d3d

License: BSD-3-Clause, see COPYING and individual source headers.
The generated speexdsp_config_types.h is supplied locally for Android's stdint types;
../config.h selects portable floating-point DSP and the BSD-licensed smallft FFT.
No downloads take place during the build.

The optional `GIMI_BUILD_AEC_TESTS=ON` CMake target `gimi_echo_test` calls the
same JNI DSP entrypoints with synthetic delayed/multipath echo and simultaneous
near-end speech. It requires at least 15 dB echo attenuation and preserves
near-end gain above 0.5. This is a DSP regression, not an acoustic/device test.
Run it alongside libgimi_echo.so on Android with LD_LIBRARY_PATH pointing at
their directory. The default build excludes this executable.

Vendored source changes: trailing blank lines and two mixed indentation lines
were normalized to pass the repository whitespace checks; DSP code is unchanged.
