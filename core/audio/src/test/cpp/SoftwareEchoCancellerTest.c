#include <jni.h>
#include <math.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

/* Exercise the packaged JNI entrypoints without installing or changing app data. */
typedef struct { jsize size; jshort data[240]; } Array;
static jsize length(JNIEnv *e, jarray a) { return ((Array *)a)->size; }
static void get(JNIEnv *e, jshortArray a, jsize start, jsize n, jshort *p) {
    memcpy(p, ((Array *)a)->data + start, n * sizeof(jshort));
}
static void set(JNIEnv *e, jshortArray a, jsize start, jsize n, const jshort *p) {
    memcpy(((Array *)a)->data + start, p, n * sizeof(jshort));
}
extern jlong Java_github_ponyhuang_gimi_core_audio_SoftwareEchoCanceller_nativeCreate(JNIEnv *, jobject);
extern void Java_github_ponyhuang_gimi_core_audio_SoftwareEchoCanceller_nativeProcess(JNIEnv *, jobject, jlong, jshortArray, jshortArray, jshortArray);
extern void Java_github_ponyhuang_gimi_core_audio_SoftwareEchoCanceller_nativeDestroy(JNIEnv *, jobject, jlong);

static double far_signal(double t) {
    if (t < 0) return 0;
    double value = 0;
    for (int k = 0; k < 32; k++) {
        double frequency = 173 + ((k * 383) % 3109);
        value += 900 * sin(2 * M_PI * frequency * t + k * k * .37);
    }
    return value * (.55 + .45 * sin(2 * M_PI * .73 * t));
}
int main(void) {
    struct JNINativeInterface table = {0};
    table.GetArrayLength = length;
    table.GetShortArrayRegion = get;
    table.SetShortArrayRegion = set;
    JNIEnv env = &table;
    jlong handle = Java_github_ponyhuang_gimi_core_audio_SoftwareEchoCanceller_nativeCreate(&env, NULL);
    if (!handle) return 2;
    Array mic = {.size=160}, ref = {.size=240}, out = {.size=160};
    double echo_in = 0, echo_out = 0, near_energy = 0, doubletalk_error = 0, correlation = 0;
    for (int frame = 0; frame < 1400; frame++) {
        for (int j = 0; j < 240; j++) {
            double t = frame * .01 + j / 24000.0;
            ref.data[j] = (short)far_signal(t - .020);
        }
        for (int j = 0; j < 160; j++) {
            double t = frame * .01 + j / 16000.0;
            double near = frame >= 1100 ? 4500 * sin(2 * M_PI * 997 * t) : 0;
            mic.data[j] = (short)(.55 * far_signal(t - .060) + .15 * far_signal(t - .074) + near);
        }
        Java_github_ponyhuang_gimi_core_audio_SoftwareEchoCanceller_nativeProcess(&env, NULL, handle,
            (jshortArray)&mic, (jshortArray)&ref, (jshortArray)&out);
        for (int j = 0; j < 160; j++) {
            if (frame >= 900 && frame < 1100) {
                echo_in += (double)mic.data[j] * mic.data[j];
                echo_out += (double)out.data[j] * out.data[j];
            }
            /* Speex preprocessing has one frame algorithmic latency. */
            if (frame >= 1200) {
                double t = frame * .01 + j / 16000.0 - .01;
                double near = 4500 * sin(2 * M_PI * 997 * t);
                near_energy += near * near;
                doubletalk_error += pow(out.data[j] - near, 2);
                correlation += out.data[j] * near;
            }
        }
    }
    Java_github_ponyhuang_gimi_core_audio_SoftwareEchoCanceller_nativeDestroy(&env, NULL, handle);
    double erle = 10 * log10(echo_in / fmax(echo_out, 1));
    double gain = correlation / near_energy;
    double error = sqrt(doubletalk_error / near_energy);
    printf("echo_attenuation_db=%.2f doubletalk_near_gain=%.3f doubletalk_relative_error=%.3f\n", erle, gain, error);
    if (erle < 15 || gain < .5 || error > .6) return 3;
    puts("AEC_SYNTHETIC_PASS");
    return 0;
}
