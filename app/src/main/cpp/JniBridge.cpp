#include "NativeAudioEngine.h"

#include <jni.h>

#include <memory>

using pulse::BufferMode;
using pulse::FilterType;
using pulse::MeterSnapshot;
using pulse::NativeAudioEngine;

namespace {

NativeAudioEngine* engineFromHandle(jlong handle) {
    return reinterpret_cast<NativeAudioEngine*>(handle);
}

jfloatArray toFloatArray(JNIEnv* env, const float* values, jsize size) {
    jfloatArray array = env->NewFloatArray(size);
    if (array != nullptr) {
        env->SetFloatArrayRegion(array, 0, size, values);
    }
    return array;
}

} // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_pulsedeck_app_audio_NativeAudioEngineBridge_nativeGetEngineVersion(JNIEnv* env, jclass) {
    return env->NewStringUTF("pulse_native_audio/1.1 oboe");
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_pulsedeck_app_audio_NativeAudioEngineBridge_nativeCreateEngine(JNIEnv*, jclass) {
    return reinterpret_cast<jlong>(new NativeAudioEngine());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_pulsedeck_app_audio_NativeAudioEngineBridge_nativeStart(JNIEnv*, jclass, jlong handle) {
    auto* engine = engineFromHandle(handle);
    return engine != nullptr && engine->start() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_pulsedeck_app_audio_NativeAudioEngineBridge_nativeStop(JNIEnv*, jclass, jlong handle) {
    auto* engine = engineFromHandle(handle);
    if (engine != nullptr) engine->stop();
}

extern "C" JNIEXPORT void JNICALL
Java_com_pulsedeck_app_audio_NativeAudioEngineBridge_nativeRelease(JNIEnv*, jclass, jlong handle) {
    auto* engine = engineFromHandle(handle);
    if (engine != nullptr) {
        engine->release();
        delete engine;
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_pulsedeck_app_audio_NativeAudioEngineBridge_nativeGetStreamInfo(JNIEnv* env, jclass, jlong handle) {
    auto* engine = engineFromHandle(handle);
    const std::string info = engine != nullptr ? engine->streamInfo() : "native unavailable";
    return env->NewStringUTF(info.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_pulsedeck_app_audio_NativeAudioEngineBridge_nativeGetGraphDescription(JNIEnv* env, jclass, jlong handle) {
    auto* engine = engineFromHandle(handle);
    const std::string info = engine != nullptr ? engine->graphDescription() : "native unavailable";
    return env->NewStringUTF(info.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_pulsedeck_app_audio_NativeAudioEngineBridge_nativeSetBufferMode(JNIEnv*, jclass, jlong handle, jint mode) {
    auto* engine = engineFromHandle(handle);
    if (engine != nullptr) engine->setBufferMode(static_cast<BufferMode>(mode));
}

extern "C" JNIEXPORT void JNICALL
Java_com_pulsedeck_app_audio_NativeAudioEngineBridge_nativeSetEnabled(JNIEnv*, jclass, jlong handle, jboolean enabled) {
    auto* engine = engineFromHandle(handle);
    if (engine != nullptr) engine->setEnabled(enabled == JNI_TRUE);
}

extern "C" JNIEXPORT void JNICALL
Java_com_pulsedeck_app_audio_NativeAudioEngineBridge_nativeSetBypass(JNIEnv*, jclass, jlong handle, jboolean bypass) {
    auto* engine = engineFromHandle(handle);
    if (engine != nullptr) engine->setBypass(bypass == JNI_TRUE);
}

extern "C" JNIEXPORT void JNICALL
Java_com_pulsedeck_app_audio_NativeAudioEngineBridge_nativeSetMasterGain(JNIEnv*, jclass, jlong handle, jfloat value) {
    auto* engine = engineFromHandle(handle);
    if (engine != nullptr) engine->setMasterGain(value);
}

extern "C" JNIEXPORT void JNICALL
Java_com_pulsedeck_app_audio_NativeAudioEngineBridge_nativeSetSource(JNIEnv*, jclass, jlong handle, jfloat frequencyHz, jfloat gain) {
    auto* engine = engineFromHandle(handle);
    if (engine != nullptr) engine->setSource(frequencyHz, gain);
}

extern "C" JNIEXPORT void JNICALL
Java_com_pulsedeck_app_audio_NativeAudioEngineBridge_nativeSetEqBand(JNIEnv*, jclass, jlong handle, jint index, jfloat frequencyHz, jfloat gainDb, jfloat q, jint type, jboolean enabled) {
    auto* engine = engineFromHandle(handle);
    if (engine != nullptr) engine->setEqBand(index, frequencyHz, gainDb, q, static_cast<FilterType>(type), enabled == JNI_TRUE);
}

extern "C" JNIEXPORT void JNICALL
Java_com_pulsedeck_app_audio_NativeAudioEngineBridge_nativeSetStereo(JNIEnv*, jclass, jlong handle, jfloat balance, jboolean mono, jfloat stereoWidth, jfloat crossfeed) {
    auto* engine = engineFromHandle(handle);
    if (engine != nullptr) engine->setStereo(balance, mono == JNI_TRUE, stereoWidth, crossfeed);
}

extern "C" JNIEXPORT void JNICALL
Java_com_pulsedeck_app_audio_NativeAudioEngineBridge_nativeSetLimiter(JNIEnv*, jclass, jlong handle, jboolean enabled, jfloat ceilingDb, jfloat releaseMs, jfloat strength) {
    auto* engine = engineFromHandle(handle);
    if (engine != nullptr) engine->setLimiter(enabled == JNI_TRUE, ceilingDb, releaseMs, strength);
}

extern "C" JNIEXPORT void JNICALL
Java_com_pulsedeck_app_audio_NativeAudioEngineBridge_nativeSetCompressor(JNIEnv*, jclass, jlong handle, jboolean enabled, jfloat thresholdDb, jfloat ratio, jfloat attackMs, jfloat releaseMs, jfloat makeupDb, jfloat mix) {
    auto* engine = engineFromHandle(handle);
    if (engine != nullptr) engine->setCompressor(enabled == JNI_TRUE, thresholdDb, ratio, attackMs, releaseMs, makeupDb, mix);
}

extern "C" JNIEXPORT void JNICALL
Java_com_pulsedeck_app_audio_NativeAudioEngineBridge_nativeSetGate(JNIEnv*, jclass, jlong handle, jboolean enabled, jfloat thresholdDb, jfloat attackMs, jfloat holdMs, jfloat releaseMs) {
    auto* engine = engineFromHandle(handle);
    if (engine != nullptr) engine->setGate(enabled == JNI_TRUE, thresholdDb, attackMs, holdMs, releaseMs);
}

extern "C" JNIEXPORT void JNICALL
Java_com_pulsedeck_app_audio_NativeAudioEngineBridge_nativeSetReverb(JNIEnv*, jclass, jlong handle, jboolean enabled, jfloat mix, jfloat size, jfloat preDelayMs, jfloat damp, jfloat decay) {
    auto* engine = engineFromHandle(handle);
    if (engine != nullptr) engine->setReverb(enabled == JNI_TRUE, mix, size, preDelayMs, damp, decay);
}

extern "C" JNIEXPORT void JNICALL
Java_com_pulsedeck_app_audio_NativeAudioEngineBridge_nativeSetDelay(JNIEnv*, jclass, jlong handle, jboolean enabled, jfloat timeMs, jfloat feedback, jfloat mix) {
    auto* engine = engineFromHandle(handle);
    if (engine != nullptr) engine->setDelay(enabled == JNI_TRUE, timeMs, feedback, mix);
}

extern "C" JNIEXPORT void JNICALL
Java_com_pulsedeck_app_audio_NativeAudioEngineBridge_nativeSetModulation(JNIEnv*, jclass, jlong handle, jboolean chorusEnabled, jboolean flangerEnabled, jboolean phaserEnabled, jfloat rateHz, jfloat depth, jfloat feedback, jfloat mix) {
    auto* engine = engineFromHandle(handle);
    if (engine != nullptr) engine->setModulation(chorusEnabled == JNI_TRUE, flangerEnabled == JNI_TRUE, phaserEnabled == JNI_TRUE, rateHz, depth, feedback, mix);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_pulsedeck_app_audio_NativeAudioEngineBridge_nativeProcessPcm16(JNIEnv* env, jclass, jlong handle, jobject inputBuffer, jobject outputBuffer, jint bytes, jint sampleRate, jint channels) {
    auto* engine = engineFromHandle(handle);
    auto* input = static_cast<int16_t*>(env->GetDirectBufferAddress(inputBuffer));
    auto* output = static_cast<int16_t*>(env->GetDirectBufferAddress(outputBuffer));
    if (engine == nullptr || input == nullptr || output == nullptr) return 0;
    return engine->processPcm16(input, output, bytes, sampleRate, channels);
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_pulsedeck_app_audio_NativeAudioEngineBridge_nativeGetLatestMeters(JNIEnv* env, jclass, jlong handle) {
    auto* engine = engineFromHandle(handle);
    const MeterSnapshot meters = engine != nullptr ? engine->meters() : MeterSnapshot{};
    const float values[4] = {meters.peakL, meters.peakR, meters.rmsL, meters.rmsR};
    return toFloatArray(env, values, 4);
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_pulsedeck_app_audio_NativeAudioEngineBridge_nativeGetLatestSpectrum(JNIEnv* env, jclass, jlong handle) {
    auto* engine = engineFromHandle(handle);
    const auto spectrum = engine != nullptr ? engine->spectrumSnapshot() : std::array<float, pulse::kSpectrumBins>{};
    return toFloatArray(env, spectrum.data(), static_cast<jsize>(spectrum.size()));
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_pulsedeck_app_audio_NativeAudioEngineBridge_nativeExportPostGraphWav(JNIEnv* env, jclass, jlong handle, jstring path, jint durationMs, jint sampleRate, jint channels) {
    auto* engine = engineFromHandle(handle);
    if (engine == nullptr || path == nullptr) return JNI_FALSE;
    const char* chars = env->GetStringUTFChars(path, nullptr);
    const bool ok = engine->exportWav(chars, durationMs, sampleRate, channels);
    env->ReleaseStringUTFChars(path, chars);
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_pulsedeck_app_audio_NativeAudioEngineBridge_nativeRunSelfTest(JNIEnv*, jclass, jlong handle) {
    auto* engine = engineFromHandle(handle);
    return engine != nullptr && engine->selfTest() ? JNI_TRUE : JNI_FALSE;
}
