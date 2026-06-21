#pragma once

#include <array>
#include <atomic>
#include <cstdint>
#include <memory>
#include <string>

#include <oboe/Oboe.h>

namespace pulse {

constexpr int kMaxEqBands = 14;
constexpr int kSpectrumBins = 64;
constexpr int kSpectrumRingSize = 2048;
constexpr int kMaxDelayFrames = 96000;

enum class FilterType : int32_t {
    Peak = 0,
    LowShelf = 1,
    HighShelf = 2,
    LowPass = 3,
    HighPass = 4,
    Notch = 5,
};

enum class BufferMode : int32_t {
    LowLatency = 0,
    Balanced = 1,
    Stable = 2,
};

struct MeterSnapshot {
    float peakL = 0.0f;
    float peakR = 0.0f;
    float rmsL = 0.0f;
    float rmsR = 0.0f;
};

class SmoothedValue {
public:
    void reset(float value);
    float next(float target, float coefficient);

private:
    float current_ = 0.0f;
};

struct BiquadCoefficients {
    float b0 = 1.0f;
    float b1 = 0.0f;
    float b2 = 0.0f;
    float a1 = 0.0f;
    float a2 = 0.0f;
};

struct AtomicBiquadCoefficients {
    std::atomic<float> b0{1.0f};
    std::atomic<float> b1{0.0f};
    std::atomic<float> b2{0.0f};
    std::atomic<float> a1{0.0f};
    std::atomic<float> a2{0.0f};

    void store(const BiquadCoefficients& coefficients);
    BiquadCoefficients load() const;
};

class Biquad {
public:
    void reset();
    void setBypass();
    void setCoefficients(const BiquadCoefficients& coefficients);
    void set(FilterType type, float sampleRate, float frequencyHz, float q, float gainDb);
    float process(float input, int channel);

private:
    float b0_ = 1.0f;
    float b1_ = 0.0f;
    float b2_ = 0.0f;
    float a1_ = 0.0f;
    float a2_ = 0.0f;
    std::array<float, 2> z1_{};
    std::array<float, 2> z2_{};
};

class NativeAudioEngine final : public oboe::AudioStreamDataCallback, public oboe::AudioStreamErrorCallback {
public:
    NativeAudioEngine();
    ~NativeAudioEngine() override;

    bool start();
    void stop();
    void release();

    void setBufferMode(BufferMode mode);
    void setEnabled(bool enabled);
    void setBypass(bool bypass);
    void setMasterGain(float value);
    void setSource(float frequencyHz, float gain);
    void setEqBand(int32_t index, float frequencyHz, float gainDb, float q, FilterType type, bool enabled);
    void setStereo(float balance, bool mono, float stereoWidth, float crossfeed);
    void setLimiter(bool enabled, float ceilingDb, float releaseMs, float strength);
    void setCompressor(bool enabled, float thresholdDb, float ratio, float attackMs, float releaseMs, float makeupDb, float mix);
    void setGate(bool enabled, float thresholdDb, float attackMs, float holdMs, float releaseMs);
    void setReverb(bool enabled, float mix, float size, float preDelayMs, float damp, float decay);
    void setDelay(bool enabled, float timeMs, float feedback, float mix);
    void setModulation(bool chorusEnabled, bool flangerEnabled, bool phaserEnabled, float rateHz, float depth, float feedback, float mix);

    int32_t processPcm16(const int16_t* input, int16_t* output, int32_t bytes, int32_t sampleRate, int32_t channels);
    bool exportWav(const char* path, int32_t durationMs, int32_t sampleRate, int32_t channels);

    MeterSnapshot meters() const;
    std::array<float, kSpectrumBins> spectrumSnapshot() const;
    std::string streamInfo() const;
    std::string graphDescription() const;
    bool selfTest();

    oboe::DataCallbackResult onAudioReady(oboe::AudioStream* stream, void* audioData, int32_t numFrames) override;
    void onErrorAfterClose(oboe::AudioStream* stream, oboe::Result error) override;

private:
    void prepare(int32_t sampleRate, int32_t channels);
    void publishEqBandCoefficients(int32_t index);
    void refreshEqBands();
    void render(float* out, int32_t frames, int32_t channels, const int16_t* input);
    void updateMeters(const float* data, int32_t frames, int32_t channels);
    void applyStereoMatrix(float* samples, int32_t channels);
    float processDynamics(float sample, int channel);
    float processDelayAndModulation(float sample, int channel);
    float processReverb(float sample, int channel);
    float softLimit(float sample);
    float oscillatorSample();
    void tuneBuffer();
    void closeStream();

    std::shared_ptr<oboe::AudioStream> stream_;
    std::atomic<bool> released_{false};
    std::atomic<bool> running_{false};
    std::atomic<bool> enabled_{true};
    std::atomic<bool> bypass_{false};
    std::atomic<int32_t> sampleRate_{48000};
    std::atomic<int32_t> channelCount_{2};
    std::atomic<int32_t> xRunCount_{0};
    std::atomic<int32_t> framesPerBurst_{0};
    std::atomic<int32_t> bufferSizeFrames_{0};
    std::atomic<int32_t> bufferMode_{static_cast<int32_t>(BufferMode::Balanced)};
    std::atomic<float> masterGain_{1.0f};
    std::atomic<float> sourceFrequencyHz_{440.0f};
    std::atomic<float> sourceGain_{0.05f};

    std::array<std::atomic<bool>, kMaxEqBands> eqBandEnabled_{};
    std::array<std::atomic<float>, kMaxEqBands> eqBandFrequency_{};
    std::array<std::atomic<float>, kMaxEqBands> eqBandGain_{};
    std::array<std::atomic<float>, kMaxEqBands> eqBandQ_{};
    std::array<std::atomic<int32_t>, kMaxEqBands> eqBandType_{};
    std::array<AtomicBiquadCoefficients, kMaxEqBands> eqBandCoefficients_{};
    std::array<Biquad, kMaxEqBands> eqBands_{};

    std::atomic<float> stereoBalance_{0.0f};
    std::atomic<bool> monoEnabled_{false};
    std::atomic<float> stereoWidth_{0.0f};
    std::atomic<float> crossfeed_{0.0f};

    std::atomic<bool> limiterEnabled_{true};
    std::atomic<float> limiterCeilingDb_{-1.0f};
    std::atomic<float> limiterReleaseMs_{120.0f};
    std::atomic<float> limiterStrength_{0.55f};

    std::atomic<bool> compressorEnabled_{false};
    std::atomic<float> compressorThresholdDb_{-18.0f};
    std::atomic<float> compressorRatio_{2.0f};
    std::atomic<float> compressorAttackMs_{12.0f};
    std::atomic<float> compressorReleaseMs_{120.0f};
    std::atomic<float> compressorMakeupDb_{0.0f};
    std::atomic<float> compressorMix_{0.0f};

    std::atomic<bool> gateEnabled_{false};
    std::atomic<float> gateThresholdDb_{-54.0f};
    std::atomic<float> gateAttackMs_{5.0f};
    std::atomic<float> gateHoldMs_{60.0f};
    std::atomic<float> gateReleaseMs_{120.0f};

    std::atomic<bool> delayEnabled_{false};
    std::atomic<float> delayTimeMs_{280.0f};
    std::atomic<float> delayFeedback_{0.22f};
    std::atomic<float> delayMix_{0.0f};

    std::atomic<bool> chorusEnabled_{false};
    std::atomic<bool> flangerEnabled_{false};
    std::atomic<bool> phaserEnabled_{false};
    std::atomic<float> modulationRateHz_{0.35f};
    std::atomic<float> modulationDepth_{0.35f};
    std::atomic<float> modulationFeedback_{0.1f};
    std::atomic<float> modulationMix_{0.0f};

    std::atomic<bool> reverbEnabled_{false};
    std::atomic<float> reverbMix_{0.0f};
    std::atomic<float> reverbSize_{0.35f};
    std::atomic<float> reverbPreDelayMs_{18.0f};
    std::atomic<float> reverbDamp_{0.45f};
    std::atomic<float> reverbDecay_{1.2f};

    SmoothedValue masterGainSmoother_;
    SmoothedValue sourceGainSmoother_;
    SmoothedValue sourceFrequencySmoother_;
    std::array<float, 2> compressorEnvelope_{};
    std::array<float, 2> gateEnvelope_{};
    std::array<int32_t, 2> gateHoldFrames_{};
    std::array<float, 2> phaserState_{};
    float phase_ = 0.0f;
    float modulationPhase_ = 0.0f;

    std::array<float, kMaxDelayFrames * 2> delayBuffer_{};
    std::array<float, kMaxDelayFrames * 2> modBuffer_{};
    std::array<float, kMaxDelayFrames * 2> reverbBuffer_{};
    int32_t delayWriteIndex_ = 0;
    int32_t modWriteIndex_ = 0;
    int32_t reverbWriteIndex_ = 0;

    std::atomic<float> peakL_{0.0f};
    std::atomic<float> peakR_{0.0f};
    std::atomic<float> rmsL_{0.0f};
    std::atomic<float> rmsR_{0.0f};
    std::array<std::atomic<float>, kSpectrumRingSize> spectrumRing_{};
    std::atomic<int32_t> spectrumWriteIndex_{0};
};

} // namespace pulse
