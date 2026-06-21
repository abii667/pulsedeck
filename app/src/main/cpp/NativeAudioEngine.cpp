#include "NativeAudioEngine.h"

#include <algorithm>
#include <cmath>
#include <cstdio>
#include <fstream>
#include <sstream>
#include <vector>

namespace pulse {
namespace {

constexpr float kPi = 3.14159265358979323846f;
constexpr float kTwoPi = 2.0f * kPi;
constexpr float kMinFrequency = 20.0f;
constexpr float kMaxFrequency = 20000.0f;
constexpr float kInvInt16 = 1.0f / 32768.0f;

float clampFloat(float value, float minValue, float maxValue) {
    return std::max(minValue, std::min(maxValue, value));
}

float dbToLinear(float db) {
    return std::pow(10.0f, db / 20.0f);
}

float linearToDb(float value) {
    return 20.0f * std::log10(std::max(value, 0.000001f));
}

float onePoleCoefficient(float ms, float sampleRate) {
    const float safeMs = std::max(ms, 0.1f);
    return 1.0f - std::exp(-1.0f / (0.001f * safeMs * sampleRate));
}

void writeLittleEndian16(std::ofstream& output, int16_t value) {
    output.put(static_cast<char>(value & 0xff));
    output.put(static_cast<char>((value >> 8) & 0xff));
}

void writeLittleEndian32(std::ofstream& output, int32_t value) {
    output.put(static_cast<char>(value & 0xff));
    output.put(static_cast<char>((value >> 8) & 0xff));
    output.put(static_cast<char>((value >> 16) & 0xff));
    output.put(static_cast<char>((value >> 24) & 0xff));
}

BiquadCoefficients makeBiquadCoefficients(FilterType type, float sampleRate, float frequencyHz, float q, float gainDb) {
    const float safeRate = std::max(sampleRate, 8000.0f);
    const float freq = clampFloat(frequencyHz, kMinFrequency, std::min(kMaxFrequency, safeRate * 0.45f));
    const float safeQ = clampFloat(q, 0.1f, 18.0f);
    const float omega = kTwoPi * freq / safeRate;
    const float sinW = std::sin(omega);
    const float cosW = std::cos(omega);
    const float alpha = sinW / (2.0f * safeQ);
    const float a = std::pow(10.0f, gainDb / 40.0f);

    float b0 = 1.0f;
    float b1 = 0.0f;
    float b2 = 0.0f;
    float a0 = 1.0f;
    float a1 = 0.0f;
    float a2 = 0.0f;

    switch (type) {
        case FilterType::LowShelf: {
            const float beta = std::sqrt(a) / safeQ;
            b0 = a * ((a + 1.0f) - (a - 1.0f) * cosW + beta * sinW);
            b1 = 2.0f * a * ((a - 1.0f) - (a + 1.0f) * cosW);
            b2 = a * ((a + 1.0f) - (a - 1.0f) * cosW - beta * sinW);
            a0 = (a + 1.0f) + (a - 1.0f) * cosW + beta * sinW;
            a1 = -2.0f * ((a - 1.0f) + (a + 1.0f) * cosW);
            a2 = (a + 1.0f) + (a - 1.0f) * cosW - beta * sinW;
            break;
        }
        case FilterType::HighShelf: {
            const float beta = std::sqrt(a) / safeQ;
            b0 = a * ((a + 1.0f) + (a - 1.0f) * cosW + beta * sinW);
            b1 = -2.0f * a * ((a - 1.0f) + (a + 1.0f) * cosW);
            b2 = a * ((a + 1.0f) + (a - 1.0f) * cosW - beta * sinW);
            a0 = (a + 1.0f) - (a - 1.0f) * cosW + beta * sinW;
            a1 = 2.0f * ((a - 1.0f) - (a + 1.0f) * cosW);
            a2 = (a + 1.0f) - (a - 1.0f) * cosW - beta * sinW;
            break;
        }
        case FilterType::LowPass:
            b0 = (1.0f - cosW) * 0.5f;
            b1 = 1.0f - cosW;
            b2 = (1.0f - cosW) * 0.5f;
            a0 = 1.0f + alpha;
            a1 = -2.0f * cosW;
            a2 = 1.0f - alpha;
            break;
        case FilterType::HighPass:
            b0 = (1.0f + cosW) * 0.5f;
            b1 = -(1.0f + cosW);
            b2 = (1.0f + cosW) * 0.5f;
            a0 = 1.0f + alpha;
            a1 = -2.0f * cosW;
            a2 = 1.0f - alpha;
            break;
        case FilterType::Notch:
            b0 = 1.0f;
            b1 = -2.0f * cosW;
            b2 = 1.0f;
            a0 = 1.0f + alpha;
            a1 = -2.0f * cosW;
            a2 = 1.0f - alpha;
            break;
        case FilterType::Peak:
        default:
            b0 = 1.0f + alpha * a;
            b1 = -2.0f * cosW;
            b2 = 1.0f - alpha * a;
            a0 = 1.0f + alpha / a;
            a1 = -2.0f * cosW;
            a2 = 1.0f - alpha / a;
            break;
    }

    const float invA0 = 1.0f / a0;
    return BiquadCoefficients{
        b0 * invA0,
        b1 * invA0,
        b2 * invA0,
        a1 * invA0,
        a2 * invA0,
    };
}

} // namespace

void AtomicBiquadCoefficients::store(const BiquadCoefficients& coefficients) {
    b0.store(coefficients.b0, std::memory_order_relaxed);
    b1.store(coefficients.b1, std::memory_order_relaxed);
    b2.store(coefficients.b2, std::memory_order_relaxed);
    a1.store(coefficients.a1, std::memory_order_relaxed);
    a2.store(coefficients.a2, std::memory_order_relaxed);
}

BiquadCoefficients AtomicBiquadCoefficients::load() const {
    return BiquadCoefficients{
        b0.load(std::memory_order_relaxed),
        b1.load(std::memory_order_relaxed),
        b2.load(std::memory_order_relaxed),
        a1.load(std::memory_order_relaxed),
        a2.load(std::memory_order_relaxed),
    };
}

void SmoothedValue::reset(float value) {
    current_ = value;
}

float SmoothedValue::next(float target, float coefficient) {
    current_ += (target - current_) * clampFloat(coefficient, 0.0001f, 1.0f);
    return current_;
}

void Biquad::reset() {
    z1_ = {};
    z2_ = {};
}

void Biquad::setBypass() {
    setCoefficients(BiquadCoefficients{});
}

void Biquad::setCoefficients(const BiquadCoefficients& coefficients) {
    b0_ = coefficients.b0;
    b1_ = coefficients.b1;
    b2_ = coefficients.b2;
    a1_ = coefficients.a1;
    a2_ = coefficients.a2;
}

void Biquad::set(FilterType type, float sampleRate, float frequencyHz, float q, float gainDb) {
    setCoefficients(makeBiquadCoefficients(type, sampleRate, frequencyHz, q, gainDb));
}

float Biquad::process(float input, int channel) {
    const int ch = channel & 1;
    const float output = b0_ * input + z1_[ch];
    z1_[ch] = b1_ * input - a1_ * output + z2_[ch];
    z2_[ch] = b2_ * input - a2_ * output;
    return output;
}

NativeAudioEngine::NativeAudioEngine() {
    const std::array<float, kMaxEqBands> defaults{
        31.0f, 62.0f, 125.0f, 250.0f, 500.0f, 1000.0f, 2000.0f,
        4000.0f, 8000.0f, 16000.0f, 80.0f, 650.0f, 2500.0f, 10000.0f,
    };
    for (int i = 0; i < kMaxEqBands; ++i) {
        eqBandEnabled_[i].store(true);
        eqBandFrequency_[i].store(defaults[i]);
        eqBandGain_[i].store(0.0f);
        eqBandQ_[i].store(1.0f);
        eqBandType_[i].store(static_cast<int32_t>(FilterType::Peak));
        eqBandCoefficients_[i].store(BiquadCoefficients{});
        eqBands_[i].setBypass();
    }
    for (auto& sample : spectrumRing_) {
        sample.store(0.0f);
    }
    masterGainSmoother_.reset(1.0f);
    sourceGainSmoother_.reset(0.05f);
    sourceFrequencySmoother_.reset(440.0f);
    prepare(sampleRate_.load(), channelCount_.load());
}

NativeAudioEngine::~NativeAudioEngine() {
    release();
}

bool NativeAudioEngine::start() {
    if (released_.load()) return false;
    if (running_.load()) return true;

    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)
        ->setUsage(oboe::Usage::Media)
        ->setContentType(oboe::ContentType::Music)
        ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setSharingMode(oboe::SharingMode::Exclusive)
        ->setFormat(oboe::AudioFormat::Float)
        ->setChannelCount(oboe::ChannelCount::Stereo)
        ->setSampleRate(48000)
        ->setDataCallback(this)
        ->setErrorCallback(this);

    std::shared_ptr<oboe::AudioStream> opened;
    auto result = builder.openStream(opened);
    if (result != oboe::Result::OK) {
        builder.setSharingMode(oboe::SharingMode::Shared);
        result = builder.openStream(opened);
    }
    if (result != oboe::Result::OK || opened == nullptr) {
        return false;
    }

    stream_ = opened;
    prepare(stream_->getSampleRate(), stream_->getChannelCount());
    framesPerBurst_.store(stream_->getFramesPerBurst());
    tuneBuffer();

    const auto startResult = stream_->requestStart();
    if (startResult != oboe::Result::OK) {
        closeStream();
        return false;
    }

    running_.store(true);
    return true;
}

void NativeAudioEngine::stop() {
    running_.store(false);
    if (stream_ != nullptr) {
        stream_->requestStop();
    }
}

void NativeAudioEngine::release() {
    if (released_.exchange(true)) return;
    running_.store(false);
    closeStream();
}

void NativeAudioEngine::setBufferMode(BufferMode mode) {
    bufferMode_.store(static_cast<int32_t>(mode));
    tuneBuffer();
}

void NativeAudioEngine::setEnabled(bool enabled) {
    enabled_.store(enabled);
}

void NativeAudioEngine::setBypass(bool bypass) {
    bypass_.store(bypass);
}

void NativeAudioEngine::setMasterGain(float value) {
    masterGain_.store(clampFloat(value, 0.0f, 1.5f));
}

void NativeAudioEngine::setSource(float frequencyHz, float gain) {
    sourceFrequencyHz_.store(clampFloat(frequencyHz, 20.0f, 4000.0f));
    sourceGain_.store(clampFloat(gain, 0.0f, 0.4f));
}

void NativeAudioEngine::setEqBand(int32_t index, float frequencyHz, float gainDb, float q, FilterType type, bool enabled) {
    if (index < 0 || index >= kMaxEqBands) return;
    eqBandEnabled_[index].store(enabled);
    eqBandFrequency_[index].store(clampFloat(frequencyHz, kMinFrequency, kMaxFrequency));
    eqBandGain_[index].store(clampFloat(gainDb, -18.0f, 18.0f));
    eqBandQ_[index].store(clampFloat(q, 0.1f, 18.0f));
    eqBandType_[index].store(static_cast<int32_t>(type));
    publishEqBandCoefficients(index);
}

void NativeAudioEngine::setStereo(float balance, bool mono, float stereoWidth, float crossfeed) {
    stereoBalance_.store(clampFloat(balance, -1.0f, 1.0f));
    monoEnabled_.store(mono);
    stereoWidth_.store(clampFloat(stereoWidth, 0.0f, 1.0f));
    crossfeed_.store(clampFloat(crossfeed, 0.0f, 1.0f));
}

void NativeAudioEngine::setLimiter(bool enabled, float ceilingDb, float releaseMs, float strength) {
    limiterEnabled_.store(enabled);
    limiterCeilingDb_.store(clampFloat(ceilingDb, -12.0f, 0.0f));
    limiterReleaseMs_.store(clampFloat(releaseMs, 10.0f, 1000.0f));
    limiterStrength_.store(clampFloat(strength, 0.0f, 1.0f));
}

void NativeAudioEngine::setCompressor(bool enabled, float thresholdDb, float ratio, float attackMs, float releaseMs, float makeupDb, float mix) {
    compressorEnabled_.store(enabled);
    compressorThresholdDb_.store(clampFloat(thresholdDb, -60.0f, 0.0f));
    compressorRatio_.store(clampFloat(ratio, 1.0f, 20.0f));
    compressorAttackMs_.store(clampFloat(attackMs, 0.1f, 200.0f));
    compressorReleaseMs_.store(clampFloat(releaseMs, 5.0f, 1500.0f));
    compressorMakeupDb_.store(clampFloat(makeupDb, -12.0f, 12.0f));
    compressorMix_.store(clampFloat(mix, 0.0f, 1.0f));
}

void NativeAudioEngine::setGate(bool enabled, float thresholdDb, float attackMs, float holdMs, float releaseMs) {
    gateEnabled_.store(enabled);
    gateThresholdDb_.store(clampFloat(thresholdDb, -80.0f, 0.0f));
    gateAttackMs_.store(clampFloat(attackMs, 0.1f, 200.0f));
    gateHoldMs_.store(clampFloat(holdMs, 0.0f, 1000.0f));
    gateReleaseMs_.store(clampFloat(releaseMs, 5.0f, 1500.0f));
}

void NativeAudioEngine::setReverb(bool enabled, float mix, float size, float preDelayMs, float damp, float decay) {
    reverbEnabled_.store(enabled);
    reverbMix_.store(clampFloat(mix, 0.0f, 1.0f));
    reverbSize_.store(clampFloat(size, 0.0f, 1.0f));
    reverbPreDelayMs_.store(clampFloat(preDelayMs, 0.0f, 120.0f));
    reverbDamp_.store(clampFloat(damp, 0.0f, 1.0f));
    reverbDecay_.store(clampFloat(decay, 0.2f, 6.0f));
}

void NativeAudioEngine::setDelay(bool enabled, float timeMs, float feedback, float mix) {
    delayEnabled_.store(enabled);
    delayTimeMs_.store(clampFloat(timeMs, 1.0f, 1800.0f));
    delayFeedback_.store(clampFloat(feedback, 0.0f, 0.92f));
    delayMix_.store(clampFloat(mix, 0.0f, 1.0f));
}

void NativeAudioEngine::setModulation(bool chorusEnabled, bool flangerEnabled, bool phaserEnabled, float rateHz, float depth, float feedback, float mix) {
    chorusEnabled_.store(chorusEnabled);
    flangerEnabled_.store(flangerEnabled);
    phaserEnabled_.store(phaserEnabled);
    modulationRateHz_.store(clampFloat(rateHz, 0.02f, 8.0f));
    modulationDepth_.store(clampFloat(depth, 0.0f, 1.0f));
    modulationFeedback_.store(clampFloat(feedback, 0.0f, 0.9f));
    modulationMix_.store(clampFloat(mix, 0.0f, 1.0f));
}

int32_t NativeAudioEngine::processPcm16(const int16_t* input, int16_t* output, int32_t bytes, int32_t sampleRate, int32_t channels) {
    if (input == nullptr || output == nullptr || bytes <= 0 || channels <= 0) return 0;
    const int32_t frames = bytes / static_cast<int32_t>(sizeof(int16_t)) / channels;
    if (frames <= 0) return 0;
    prepare(sampleRate, channels);

    constexpr int32_t kChunkFrames = 512;
    std::array<float, kChunkFrames * 2> scratch{};
    int32_t writtenFrames = 0;
    while (writtenFrames < frames) {
        const int32_t todo = std::min(kChunkFrames, frames - writtenFrames);
        render(scratch.data(), todo, channels, input + writtenFrames * channels);
        for (int32_t frame = 0; frame < todo; ++frame) {
            for (int32_t ch = 0; ch < channels; ++ch) {
                const float sample = clampFloat(scratch[frame * channels + ch], -1.0f, 1.0f);
                output[(writtenFrames + frame) * channels + ch] = static_cast<int16_t>(std::lrint(sample * 32767.0f));
            }
        }
        writtenFrames += todo;
    }
    return frames * channels * static_cast<int32_t>(sizeof(int16_t));
}

bool NativeAudioEngine::exportWav(const char* path, int32_t durationMs, int32_t sampleRate, int32_t channels) {
    if (path == nullptr || durationMs <= 0) return false;
    const int32_t safeRate = std::max(8000, sampleRate);
    const int32_t safeChannels = std::max(1, std::min(2, channels));
    const int32_t frames = static_cast<int32_t>((static_cast<int64_t>(safeRate) * durationMs) / 1000);
    const int32_t dataBytes = frames * safeChannels * static_cast<int32_t>(sizeof(int16_t));

    std::ofstream output(path, std::ios::binary | std::ios::trunc);
    if (!output.good()) return false;

    output.write("RIFF", 4);
    writeLittleEndian32(output, 36 + dataBytes);
    output.write("WAVEfmt ", 8);
    writeLittleEndian32(output, 16);
    writeLittleEndian16(output, 1);
    writeLittleEndian16(output, static_cast<int16_t>(safeChannels));
    writeLittleEndian32(output, safeRate);
    writeLittleEndian32(output, safeRate * safeChannels * static_cast<int32_t>(sizeof(int16_t)));
    writeLittleEndian16(output, static_cast<int16_t>(safeChannels * static_cast<int32_t>(sizeof(int16_t))));
    writeLittleEndian16(output, 16);
    output.write("data", 4);
    writeLittleEndian32(output, dataBytes);

    prepare(safeRate, safeChannels);
    constexpr int32_t kChunkFrames = 512;
    std::array<float, kChunkFrames * 2> scratch{};
    int32_t remaining = frames;
    while (remaining > 0) {
        const int32_t todo = std::min(kChunkFrames, remaining);
        render(scratch.data(), todo, safeChannels, nullptr);
        for (int32_t i = 0; i < todo * safeChannels; ++i) {
            const float sample = clampFloat(scratch[i], -1.0f, 1.0f);
            writeLittleEndian16(output, static_cast<int16_t>(std::lrint(sample * 32767.0f)));
        }
        remaining -= todo;
    }
    return output.good();
}

MeterSnapshot NativeAudioEngine::meters() const {
    return MeterSnapshot{
        peakL_.load(),
        peakR_.load(),
        rmsL_.load(),
        rmsR_.load(),
    };
}

std::array<float, kSpectrumBins> NativeAudioEngine::spectrumSnapshot() const {
    std::array<float, kSpectrumBins> bins{};
    const int32_t writeIndex = spectrumWriteIndex_.load();
    for (int bin = 0; bin < kSpectrumBins; ++bin) {
        float real = 0.0f;
        float imag = 0.0f;
        for (int n = 0; n < kSpectrumRingSize; ++n) {
            const int index = (writeIndex + n) & (kSpectrumRingSize - 1);
            const float sample = spectrumRing_[index].load();
            const float window = 0.5f - 0.5f * std::cos(kTwoPi * static_cast<float>(n) / static_cast<float>(kSpectrumRingSize - 1));
            const float angle = kTwoPi * static_cast<float>(bin + 1) * static_cast<float>(n) / static_cast<float>(kSpectrumRingSize);
            real += sample * window * std::cos(angle);
            imag -= sample * window * std::sin(angle);
        }
        bins[bin] = clampFloat(std::sqrt(real * real + imag * imag) / 128.0f, 0.0f, 1.0f);
    }
    return bins;
}

std::string NativeAudioEngine::streamInfo() const {
    std::ostringstream out;
    out << "sampleRate=" << sampleRate_.load()
        << ";channels=" << channelCount_.load()
        << ";framesPerBurst=" << framesPerBurst_.load()
        << ";bufferSizeFrames=" << bufferSizeFrames_.load()
        << ";xRunCount=" << xRunCount_.load()
        << ";running=" << (running_.load() ? "true" : "false");
    if (stream_ != nullptr) {
        out << ";sharingMode=" << oboe::convertToText(stream_->getSharingMode())
            << ";performanceMode=" << oboe::convertToText(stream_->getPerformanceMode())
            << ";format=" << oboe::convertToText(stream_->getFormat());
    }
    return out.str();
}

std::string NativeAudioEngine::graphDescription() const {
    std::ostringstream out;
    out << "Source -> gain -> EQ(" << kMaxEqBands << ") -> gate/compressor -> delay/modulation -> reverb -> limiter -> stereo matrix -> meters";
    return out.str();
}

bool NativeAudioEngine::selfTest() {
    constexpr int32_t frames = 256;
    std::array<float, frames * 2> data{};
    prepare(48000, 2);
    render(data.data(), frames, 2, nullptr);
    float peak = 0.0f;
    for (float sample : data) {
        peak = std::max(peak, std::abs(sample));
    }
    return peak > 0.0f && peak < 1.0f;
}

oboe::DataCallbackResult NativeAudioEngine::onAudioReady(oboe::AudioStream* stream, void* audioData, int32_t numFrames) {
    if (released_.load() || audioData == nullptr) {
        return oboe::DataCallbackResult::Stop;
    }
    if (stream != nullptr) {
        const auto xRuns = stream->getXRunCount();
        if (xRuns) {
            xRunCount_.store(xRuns.value());
        }
    }
    render(static_cast<float*>(audioData), numFrames, channelCount_.load(), nullptr);
    return oboe::DataCallbackResult::Continue;
}

void NativeAudioEngine::onErrorAfterClose(oboe::AudioStream*, oboe::Result) {
    running_.store(false);
}

void NativeAudioEngine::prepare(int32_t sampleRate, int32_t channels) {
    const int32_t safeRate = std::max(8000, sampleRate);
    const int32_t safeChannels = std::max(1, std::min(2, channels));
    const bool changed = sampleRate_.load() != safeRate || channelCount_.load() != safeChannels;
    sampleRate_.store(safeRate);
    channelCount_.store(safeChannels);
    if (changed) {
        phase_ = 0.0f;
        delayWriteIndex_ = 0;
        modWriteIndex_ = 0;
        reverbWriteIndex_ = 0;
        for (auto& band : eqBands_) {
            band.reset();
        }
        for (int i = 0; i < kMaxEqBands; ++i) {
            publishEqBandCoefficients(i);
        }
    }
}

void NativeAudioEngine::publishEqBandCoefficients(int32_t index) {
    if (index < 0 || index >= kMaxEqBands) return;
    if (!eqBandEnabled_[index].load(std::memory_order_relaxed)) {
        eqBandCoefficients_[index].store(BiquadCoefficients{});
        return;
    }
    eqBandCoefficients_[index].store(makeBiquadCoefficients(
        static_cast<FilterType>(eqBandType_[index].load(std::memory_order_relaxed)),
        static_cast<float>(sampleRate_.load(std::memory_order_relaxed)),
        eqBandFrequency_[index].load(std::memory_order_relaxed),
        eqBandQ_[index].load(std::memory_order_relaxed),
        eqBandGain_[index].load(std::memory_order_relaxed)));
}

void NativeAudioEngine::refreshEqBands() {
    for (int i = 0; i < kMaxEqBands; ++i) {
        eqBands_[i].setCoefficients(eqBandCoefficients_[i].load());
    }
}

void NativeAudioEngine::render(float* out, int32_t frames, int32_t channels, const int16_t* input) {
    const int32_t safeChannels = std::max(1, std::min(2, channels));
    const bool active = enabled_.load() && !bypass_.load();
    const float smoothCoefficient = onePoleCoefficient(8.0f, static_cast<float>(sampleRate_.load()));
    refreshEqBands();
    for (int32_t frame = 0; frame < frames; ++frame) {
        const float gain = masterGainSmoother_.next(masterGain_.load(), smoothCoefficient);
        float monoSource = 0.0f;
        std::array<float, 2> frameSamples{};
        for (int32_t ch = 0; ch < safeChannels; ++ch) {
            float sample = 0.0f;
            if (input != nullptr) {
                sample = static_cast<float>(input[frame * safeChannels + ch]) * kInvInt16;
            } else if (ch == 0) {
                monoSource = oscillatorSample();
                sample = monoSource;
            } else {
                sample = monoSource;
            }

            if (active) {
                sample *= gain;
                for (int i = 0; i < kMaxEqBands; ++i) {
                    if (eqBandEnabled_[i].load()) {
                        sample = eqBands_[i].process(sample, ch);
                    }
                }
                sample = processDynamics(sample, ch);
                sample = processDelayAndModulation(sample, ch);
                sample = processReverb(sample, ch);
                sample = softLimit(sample);
            } else if (input == nullptr) {
                sample = 0.0f;
            }
            frameSamples[ch] = clampFloat(sample, -1.0f, 1.0f);
        }
        applyStereoMatrix(frameSamples.data(), safeChannels);
        for (int32_t ch = 0; ch < safeChannels; ++ch) {
            out[frame * safeChannels + ch] = clampFloat(frameSamples[ch], -1.0f, 1.0f);
        }
    }
    updateMeters(out, frames, safeChannels);
}

void NativeAudioEngine::applyStereoMatrix(float* samples, int32_t channels) {
    if (samples == nullptr || channels < 2) return;
    float left = samples[0];
    float right = samples[1];

    if (monoEnabled_.load()) {
        const float mid = (left + right) * 0.5f;
        left = mid;
        right = mid;
    } else {
        const float width = 1.0f + 0.75f * stereoWidth_.load();
        const float mid = (left + right) * 0.5f;
        const float side = (left - right) * 0.5f * width;
        left = mid + side;
        right = mid - side;

        const float crossfeed = crossfeed_.load() * 0.45f;
        if (crossfeed > 0.0f) {
            const float fedLeft = left * (1.0f - crossfeed) + right * crossfeed;
            const float fedRight = right * (1.0f - crossfeed) + left * crossfeed;
            left = fedLeft;
            right = fedRight;
        }
    }

    const float balance = stereoBalance_.load();
    if (balance > 0.0f) {
        left *= std::cos(balance * kPi * 0.5f);
    } else if (balance < 0.0f) {
        right *= std::cos(-balance * kPi * 0.5f);
    }

    samples[0] = left;
    samples[1] = right;
}

void NativeAudioEngine::updateMeters(const float* data, int32_t frames, int32_t channels) {
    float peakL = 0.0f;
    float peakR = 0.0f;
    float sumL = 0.0f;
    float sumR = 0.0f;
    int32_t writeIndex = spectrumWriteIndex_.load();
    for (int32_t frame = 0; frame < frames; ++frame) {
        const float left = data[frame * channels];
        const float right = channels > 1 ? data[frame * channels + 1] : left;
        peakL = std::max(peakL, std::abs(left));
        peakR = std::max(peakR, std::abs(right));
        sumL += left * left;
        sumR += right * right;
        const float mono = (left + right) * 0.5f;
        spectrumRing_[writeIndex & (kSpectrumRingSize - 1)].store(mono);
        ++writeIndex;
    }
    spectrumWriteIndex_.store(writeIndex & (kSpectrumRingSize - 1));
    peakL_.store(peakL);
    peakR_.store(peakR);
    rmsL_.store(std::sqrt(sumL / std::max(1, frames)));
    rmsR_.store(std::sqrt(sumR / std::max(1, frames)));
}

float NativeAudioEngine::processDynamics(float sample, int channel) {
    const int ch = channel & 1;
    float processed = sample;
    if (gateEnabled_.load()) {
        const float threshold = dbToLinear(gateThresholdDb_.load());
        const float attack = onePoleCoefficient(gateAttackMs_.load(), static_cast<float>(sampleRate_.load()));
        const float release = onePoleCoefficient(gateReleaseMs_.load(), static_cast<float>(sampleRate_.load()));
        const float envelope = std::abs(processed);
        const float coeff = envelope > gateEnvelope_[ch] ? attack : release;
        gateEnvelope_[ch] += (envelope - gateEnvelope_[ch]) * coeff;
        if (gateEnvelope_[ch] > threshold) {
            gateHoldFrames_[ch] = static_cast<int32_t>(gateHoldMs_.load() * 0.001f * sampleRate_.load());
        } else if (gateHoldFrames_[ch] > 0) {
            --gateHoldFrames_[ch];
        }
        if (gateHoldFrames_[ch] <= 0 && gateEnvelope_[ch] < threshold) {
            const float open = clampFloat(gateEnvelope_[ch] / std::max(threshold, 0.00001f), 0.0f, 1.0f);
            processed *= open;
        }
    }

    if (compressorEnabled_.load() && compressorMix_.load() > 0.0f) {
        const float attack = onePoleCoefficient(compressorAttackMs_.load(), static_cast<float>(sampleRate_.load()));
        const float release = onePoleCoefficient(compressorReleaseMs_.load(), static_cast<float>(sampleRate_.load()));
        const float envelope = std::abs(processed);
        const float coeff = envelope > compressorEnvelope_[ch] ? attack : release;
        compressorEnvelope_[ch] += (envelope - compressorEnvelope_[ch]) * coeff;
        const float levelDb = linearToDb(compressorEnvelope_[ch]);
        const float threshold = compressorThresholdDb_.load();
        const float ratio = compressorRatio_.load();
        float gainDb = 0.0f;
        if (levelDb > threshold) {
            gainDb = threshold + (levelDb - threshold) / ratio - levelDb;
        }
        const float compressed = processed * dbToLinear(gainDb + compressorMakeupDb_.load());
        const float mix = compressorMix_.load();
        processed = processed * (1.0f - mix) + compressed * mix;
    }
    return processed;
}

float NativeAudioEngine::processDelayAndModulation(float sample, int channel) {
    const int ch = channel & 1;
    float output = sample;
    const int32_t channels = 2;
    const int32_t maxFrames = kMaxDelayFrames;

    if (delayEnabled_.load() && delayMix_.load() > 0.0f) {
        const int32_t delayFrames = std::max(1, std::min(maxFrames - 1, static_cast<int32_t>(delayTimeMs_.load() * 0.001f * sampleRate_.load())));
        const int32_t readFrame = (delayWriteIndex_ + maxFrames - delayFrames) % maxFrames;
        const int32_t readIndex = readFrame * channels + ch;
        const float delayed = delayBuffer_[readIndex];
        const float feedback = delayFeedback_.load();
        delayBuffer_[delayWriteIndex_ * channels + ch] = clampFloat(sample + delayed * feedback, -1.2f, 1.2f);
        const float mix = delayMix_.load();
        output = output * (1.0f - mix) + delayed * mix;
    } else {
        delayBuffer_[delayWriteIndex_ * channels + ch] = sample;
    }

    if ((chorusEnabled_.load() || flangerEnabled_.load()) && modulationMix_.load() > 0.0f) {
        const float lfo = 0.5f + 0.5f * std::sin(modulationPhase_ + (ch == 0 ? 0.0f : kPi * 0.5f));
        const bool flanger = flangerEnabled_.load();
        const float baseMs = flanger ? 2.0f : 18.0f;
        const float depthMs = (flanger ? 6.0f : 24.0f) * modulationDepth_.load();
        const int32_t delayFrames = std::max(1, std::min(maxFrames - 1, static_cast<int32_t>((baseMs + depthMs * lfo) * 0.001f * sampleRate_.load())));
        const int32_t readFrame = (modWriteIndex_ + maxFrames - delayFrames) % maxFrames;
        const float modulated = modBuffer_[readFrame * channels + ch];
        modBuffer_[modWriteIndex_ * channels + ch] = clampFloat(output + modulated * modulationFeedback_.load(), -1.2f, 1.2f);
        const float mix = modulationMix_.load();
        output = output * (1.0f - mix) + modulated * mix;
    } else {
        modBuffer_[modWriteIndex_ * channels + ch] = output;
    }

    if (phaserEnabled_.load() && modulationMix_.load() > 0.0f) {
        const float depth = modulationDepth_.load();
        const float feedback = modulationFeedback_.load();
        const float coefficient = 0.25f + 0.72f * (0.5f + 0.5f * std::sin(modulationPhase_)) * depth;
        const float allpass = -coefficient * output + phaserState_[ch];
        phaserState_[ch] = output + coefficient * allpass + allpass * feedback;
        const float mix = modulationMix_.load();
        output = output * (1.0f - mix) + allpass * mix;
    }

    if (ch == channelCount_.load() - 1 || channelCount_.load() == 1) {
        delayWriteIndex_ = (delayWriteIndex_ + 1) % maxFrames;
        modWriteIndex_ = (modWriteIndex_ + 1) % maxFrames;
        modulationPhase_ += kTwoPi * modulationRateHz_.load() / static_cast<float>(sampleRate_.load());
        if (modulationPhase_ > kTwoPi) modulationPhase_ -= kTwoPi;
    }
    return output;
}

float NativeAudioEngine::processReverb(float sample, int channel) {
    if (!reverbEnabled_.load() || reverbMix_.load() <= 0.0f) return sample;
    const int ch = channel & 1;
    const int32_t channels = 2;
    const int32_t maxFrames = kMaxDelayFrames;
    const float size = reverbSize_.load();
    const float preDelayMs = reverbPreDelayMs_.load();
    const float damp = reverbDamp_.load();
    const float decay = clampFloat(reverbDecay_.load() / 6.0f, 0.05f, 0.98f);
    const int32_t delayFrames = std::max(64, std::min(maxFrames - 1, static_cast<int32_t>((preDelayMs + 45.0f + 155.0f * size) * 0.001f * sampleRate_.load())));
    const int32_t readFrame = (reverbWriteIndex_ + maxFrames - delayFrames) % maxFrames;
    const int32_t index = readFrame * channels + ch;
    const float wet = reverbBuffer_[index];
    const float filtered = wet * (1.0f - damp) + sample * damp;
    reverbBuffer_[reverbWriteIndex_ * channels + ch] = clampFloat(sample + filtered * decay, -1.2f, 1.2f);
    const float mix = reverbMix_.load();
    const float output = sample * (1.0f - mix) + wet * mix;
    if (ch == channelCount_.load() - 1 || channelCount_.load() == 1) {
        reverbWriteIndex_ = (reverbWriteIndex_ + 1) % maxFrames;
    }
    return output;
}

float NativeAudioEngine::softLimit(float sample) {
    if (!limiterEnabled_.load()) return sample;
    const float ceiling = dbToLinear(limiterCeilingDb_.load());
    const float strength = limiterStrength_.load();
    const float shaped = sample / (1.0f + std::abs(sample) * strength);
    return clampFloat(shaped, -ceiling, ceiling);
}

float NativeAudioEngine::oscillatorSample() {
    const float frequency = sourceFrequencySmoother_.next(sourceFrequencyHz_.load(), 0.002f);
    const float gain = sourceGainSmoother_.next(sourceGain_.load(), 0.002f);
    const float sample = std::sin(phase_) * gain;
    phase_ += kTwoPi * frequency / static_cast<float>(sampleRate_.load());
    if (phase_ > kTwoPi) phase_ -= kTwoPi;
    return sample;
}

void NativeAudioEngine::tuneBuffer() {
    if (stream_ == nullptr) return;
    const int32_t burst = std::max(1, stream_->getFramesPerBurst());
    const auto mode = static_cast<BufferMode>(bufferMode_.load());
    const int multiplier = mode == BufferMode::LowLatency ? 2 : (mode == BufferMode::Stable ? 4 : 3);
    const int32_t target = burst * multiplier;
    stream_->setBufferSizeInFrames(target);
    bufferSizeFrames_.store(stream_->getBufferSizeInFrames());
}

void NativeAudioEngine::closeStream() {
    if (stream_ != nullptr) {
        stream_->close();
        stream_.reset();
    }
}

} // namespace pulse
