# Vendored RNNoise baseline

Source: https://github.com/xiph/rnnoise
Tag: v0.1 (upstream calls it the original 2017 demo)
Commit: cdf196b1e9de2f8ff1003328ebf9a4316477429d

This is a pinned, older baseline, not the current RNNoise model. It was selected because its model is distributed directly as C source in the licensed source tree, allowing an offline, reproducible Android build without a runtime model downloader. Upgrading to a newer model requires separate compatibility/license and audio-quality evaluation.

The 18 source/header/licence files listed in SHA256SUMS are byte-for-byte copies of this commit. They include the compiled-in model in src/rnn_data.c. Original copyright notices are retained. COPYING is also bundled in the APK as res/raw/rnnoise_license.txt and available from More settings.

The local CMake/JNI wrapper is outside this third_party directory. RNNoise uses 480-sample frames at 48 kHz on PCM16 amplitude scale. This app denoises one mono capture stream before left/right processing, with no undelayed wet/dry mixing. Its frame assembler adds one frame of delay on top of the upstream algorithm's buffering. Device speed, total delay, voice quality and suitability for hearing loss remain unverified until device/real-speech tests.

