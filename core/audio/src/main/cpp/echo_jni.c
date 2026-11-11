#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <speex/speex_echo.h>
#include <speex/speex_preprocess.h>
#include <speex/speex_resampler.h>

/* All access is serialized by SoftwareEchoCanceller, including destruction. */
typedef struct {
    SpeexEchoState *echo;
    SpeexPreprocessState *preprocess;
    SpeexResamplerState *resampler;
} EchoSession;

static void destroy(EchoSession *session) {
    if (!session) return;
    if (session->preprocess) speex_preprocess_state_destroy(session->preprocess);
    if (session->echo) speex_echo_state_destroy(session->echo);
    if (session->resampler) speex_resampler_destroy(session->resampler);
    free(session);
}

JNIEXPORT jlong JNICALL
Java_github_ponyhuang_gimi_core_audio_SoftwareEchoCanceller_nativeCreate(JNIEnv *env, jobject self) {
    EchoSession *session = calloc(1, sizeof(*session));
    if (!session) return 0;
    int rate = 16000, error = 0, disabled = 0;
    session->echo = speex_echo_state_init(160, 4800);
    session->preprocess = speex_preprocess_state_init(160, rate);
    session->resampler = speex_resampler_init(1, 24000, rate, 5, &error);
    if (!session->echo || !session->preprocess || !session->resampler || error) {
        destroy(session);
        return 0;
    }
    speex_echo_ctl(session->echo, SPEEX_ECHO_SET_SAMPLING_RATE, &rate);
    speex_preprocess_ctl(session->preprocess, SPEEX_PREPROCESS_SET_ECHO_STATE, session->echo);
    /* Keep near-end speech; this is residual echo suppression, not a speech gate. */
    speex_preprocess_ctl(session->preprocess, SPEEX_PREPROCESS_SET_DENOISE, &disabled);
    speex_preprocess_ctl(session->preprocess, SPEEX_PREPROCESS_SET_AGC, &disabled);
    speex_resampler_skip_zeros(session->resampler);
    return (jlong)(intptr_t)session;
}

JNIEXPORT void JNICALL
Java_github_ponyhuang_gimi_core_audio_SoftwareEchoCanceller_nativeProcess(
    JNIEnv *env, jobject self, jlong handle, jshortArray microphone, jshortArray reference, jshortArray output) {
    /* Frame geometry mirrors SoftwareEchoCanceller.FRAME_SAMPLES (240@24k -> 160@16k);
     * keep the two in sync when changing VoiceAudioFormat constants. */
    EchoSession *session = (EchoSession *)(intptr_t)handle;
    if (!session || (*env)->GetArrayLength(env, microphone) != 160 ||
        (*env)->GetArrayLength(env, reference) != 240 || (*env)->GetArrayLength(env, output) != 160) {
        jclass type = (*env)->FindClass(env, "java/lang/IllegalArgumentException");
        if (type) (*env)->ThrowNew(env, type, "Invalid AEC handle or frame size");
        return;
    }
    spx_int16_t mic[160], render[240], downsampled[160] = {0}, clean[160];
    (*env)->GetShortArrayRegion(env, microphone, 0, 160, mic);
    (*env)->GetShortArrayRegion(env, reference, 0, 240, render);
    spx_uint32_t input_size = 240, output_size = 160;
    /* 输出不足 160 个采样时残缺数据会污染回声估计：失败按参数错误上抛，静默劣化不可观测。 */
    if (speex_resampler_process_int(session->resampler, 0, render, &input_size, downsampled, &output_size) != RESAMPLER_ERR_SUCCESS ||
        output_size != 160) {
        jclass type = (*env)->FindClass(env, "java/lang/IllegalArgumentException");
        if (type) (*env)->ThrowNew(env, type, "Echo reference resampling failed");
        return;
    }
    speex_echo_cancellation(session->echo, mic, downsampled, clean);
    speex_preprocess_run(session->preprocess, clean);
    (*env)->SetShortArrayRegion(env, output, 0, 160, clean);
}

JNIEXPORT void JNICALL
Java_github_ponyhuang_gimi_core_audio_SoftwareEchoCanceller_nativeDestroy(JNIEnv *env, jobject self, jlong handle) {
    destroy((EchoSession *)(intptr_t)handle);
}
