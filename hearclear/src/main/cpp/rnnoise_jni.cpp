#include <jni.h>
#include <cstdint>
#include <cstdlib>
#include <cmath>
#include <algorithm>
extern "C" {
#include "rnnoise.h"
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_dipak_hearclear_RnNoiseNative_create(JNIEnv *, jobject) {
    auto *state = static_cast<DenoiseState *>(std::malloc(rnnoise_get_size()));
    if (!state) return 0;
    if (rnnoise_init(state) != 0) { std::free(state); return 0; }
    return static_cast<jlong>(reinterpret_cast<intptr_t>(state));
}
extern "C" JNIEXPORT void JNICALL
Java_com_dipak_hearclear_RnNoiseNative_process(JNIEnv *env, jobject, jlong handle, jshortArray input, jshortArray output) {
    if (!handle || !input || !output || env->GetArrayLength(input) != 480 || env->GetArrayLength(output) != 480) {
        jclass error = env->FindClass("java/lang/IllegalArgumentException");
        env->ThrowNew(error, "RNNoise needs a live state and two 480-sample frames");
        return;
    }
    jshort pcm[480];
    float frame[480];
    env->GetShortArrayRegion(input, 0, 480, pcm);
    if (env->ExceptionCheck()) return;
    for (int i = 0; i < 480; ++i) frame[i] = static_cast<float>(pcm[i]); // RNNoise expects PCM16 scale.
    rnnoise_process_frame(reinterpret_cast<DenoiseState *>(static_cast<intptr_t>(handle)), frame, frame);
    for (int i = 0; i < 480; ++i) {
        float sample = std::isfinite(frame[i]) ? frame[i] : 0.f;
        pcm[i] = static_cast<jshort>(std::lrint(std::clamp(sample, -32768.f, 32767.f)));
    }
    env->SetShortArrayRegion(output, 0, 480, pcm);
}
extern "C" JNIEXPORT void JNICALL
Java_com_dipak_hearclear_RnNoiseNative_destroy(JNIEnv *, jobject, jlong handle) {
    if (handle) rnnoise_destroy(reinterpret_cast<DenoiseState *>(static_cast<intptr_t>(handle)));
}

