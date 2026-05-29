#include <jni.h>
#include <atomic>
#include <cmath>
#include <memory>
#include <vector>
#include <android/log.h>
#include <oboe/Oboe.h>

#define LOG_TAG "MetronomeEngine"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

namespace {
    JavaVM*   gJvm           = nullptr;
    jclass    gEngineClass   = nullptr;
    jmethodID gOnBeatMethod  = nullptr;
    jmethodID gOnSwapMethod  = nullptr;

    constexpr int BPM_MIN = 40;
    constexpr int BPM_MAX = 300;
    constexpr int BEATS_MIN = 2;
    constexpr int BEATS_MAX = 6;
    constexpr int BARS_MIN = 1;
    constexpr int BARS_MAX = 8;

    constexpr int VISUAL_PENDING_CAP = 16;
}

class MetronomeEngine : public oboe::AudioStreamDataCallback,
                         public oboe::AudioStreamErrorCallback {
public:
    MetronomeEngine() = default;
    ~MetronomeEngine() override { stop(); }

    bool start(int bpm, int beatsPerBar, int barsPerPhrase, int subdivision) {
        if (mIsPlaying.load()) return true;

        // v0.9.2 cold-boot fix: apply the caller's preset snapshot to the atomics
        // BEFORE opening/starting the stream. On a cold boot sEngine doesn't exist
        // until nativeStart() runs, so any setBpm/setBeatsPerBar calls the Kotlin
        // layer made earlier were no-ops (sEngine == nullptr) and the engine would
        // otherwise start with C++ defaults (e.g. 4/4 instead of a persisted 3/4).
        // Setting here guarantees the very first audio callback counts the right
        // meter. recomputeSamplesPerBeat() runs again below once the real sample
        // rate is known.
        setBpm(bpm);
        setBeatsPerBar(beatsPerBar);
        setBarsPerPhrase(barsPerPhrase);
        setSubdivision(subdivision);

        oboe::AudioStreamBuilder builder;
        builder.setDirection(oboe::Direction::Output)
               ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
               ->setSharingMode(oboe::SharingMode::Exclusive)
               ->setFormat(oboe::AudioFormat::Float)
               ->setChannelCount(oboe::ChannelCount::Mono)
               ->setSampleRate(48000)
               ->setSampleRateConversionQuality(oboe::SampleRateConversionQuality::Medium)
               ->setDataCallback(this)
               ->setErrorCallback(this);

        oboe::Result result = builder.openStream(mStream);
        if (result != oboe::Result::OK) {
            LOGE("openStream failed: %s", oboe::convertToText(result));
            return false;
        }

        mSampleRate = mStream->getSampleRate();
        mChannelCount = mStream->getChannelCount();
        generateClickBuffer();
        recomputeSamplesPerBeat();
        recomputeVisualDelaySamples();

        mTotalSamples   = 0;
        mNextBeatSample = 0;
        mCurrentBeat    = 0;
        mCurrentBar     = 0;
        mNextSubIndex   = 0;
        mClickPosition  = -1;
        mVisualHead     = 0;
        mVisualTail     = 0;

        mHasPending.store(false);
        mPendingEndBar.store(-1);

        result = mStream->requestStart();
        if (result != oboe::Result::OK) {
            LOGE("requestStart failed: %s", oboe::convertToText(result));
            mStream->close();
            mStream.reset();
            return false;
        }

        mIsPlaying.store(true);
        LOGI("Stream started: sampleRate=%d channels=%d", mSampleRate, mChannelCount);
        return true;
    }

    void stop() {
        if (!mIsPlaying.exchange(false)) return;
        mHasPending.store(false);
        mPendingEndBar.store(-1);
        if (mStream) {
            mStream->requestStop();
            mStream->close();
            mStream.reset();
        }
    }

    bool isPlaying() const { return mIsPlaying.load(); }

    void setBpm(int bpm) {
        bpm = clampInt(bpm, BPM_MIN, BPM_MAX);
        mBpm.store(bpm);
        recomputeSamplesPerBeat();
    }

    void setBeatsPerBar(int beats) {
        beats = clampInt(beats, BEATS_MIN, BEATS_MAX);
        mBeatsPerBar.store(beats);
    }

    void setBarsPerPhrase(int bars) {
        bars = clampInt(bars, BARS_MIN, BARS_MAX);
        mBarsPerPhrase.store(bars);
    }

    void setSubdivision(int sub) {
        if (sub != 1 && sub != 2) sub = 1;
        mSubdivision.store(sub);
    }

    void setVisualOffsetMs(int ms) {
        if (ms < 0)   ms = 0;
        if (ms > 250) ms = 250;
        mVisualOffsetMs.store(ms);
        recomputeVisualDelaySamples();
    }

    void queuePreset(int bpm, int beatsPerBar, int barsPerPhrase, int subdivision, int endBar = -1) {
        mPendingBpm.store(clampInt(bpm, BPM_MIN, BPM_MAX));
        mPendingBeats.store(clampInt(beatsPerBar, BEATS_MIN, BEATS_MAX));
        mPendingBars.store(clampInt(barsPerPhrase, BARS_MIN, BARS_MAX));
        mPendingSub.store((subdivision == 2) ? 2 : 1);
        mPendingEndBar.store(endBar);
        mHasPending.store(true);
    }

    oboe::DataCallbackResult onAudioReady(
            oboe::AudioStream* audioStream,
            void* audioData,
            int32_t numFrames) override {

        auto* out = static_cast<float*>(audioData);
        const int channels = audioStream->getChannelCount();
        const int totalSamples = numFrames * channels;

        for (int i = 0; i < totalSamples; ++i) out[i] = 0.0f;

        int64_t samplesPerBeat = mSamplesPerBeat.load();
        if (samplesPerBeat <= 0) return oboe::DataCallbackResult::Continue;

        const int clickLen = (int) mClickBuffer.size();

        for (int frame = 0; frame < numFrames; ++frame) {
            // Drain any visual events whose fire-sample has arrived.
            while (mVisualHead != mVisualTail &&
                   mVisualBuf[mVisualHead].fireAtSample <= mTotalSamples) {
                const auto& ev = mVisualBuf[mVisualHead];
                notifyBeat(ev.bar, ev.beat, ev.subIndex);
                if (ev.swapped) notifySwap(ev.bpm, ev.beats, ev.bars, ev.sub);
                mVisualHead = (mVisualHead + 1) % VISUAL_PENDING_CAP;
            }

            if (mTotalSamples >= mNextBeatSample) {
                onTickTriggered();
            }

            float sample = 0.0f;
            if (mClickPosition >= 0 && mClickPosition < clickLen) {
                sample = mClickBuffer[mClickPosition] * mClickGain;
                mClickPosition++;
                if (mClickPosition >= clickLen) mClickPosition = -1;
            }

            for (int ch = 0; ch < channels; ++ch) {
                out[frame * channels + ch] = sample;
            }

            mTotalSamples++;
        }

        return oboe::DataCallbackResult::Continue;
    }

    void onErrorAfterClose(oboe::AudioStream*, oboe::Result error) override {
        LOGE("Stream error: %s", oboe::convertToText(error));
        mIsPlaying.store(false);
    }

private:
    struct VisualEvent {
        int64_t fireAtSample;
        int bar;
        int beat;
        int subIndex;
        bool swapped;
        int bpm;
        int beats;
        int bars;
        int sub;
    };

    static int clampInt(int v, int lo, int hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }

    std::shared_ptr<oboe::AudioStream> mStream;
    int mSampleRate   = 48000;
    int mChannelCount = 1;

    std::atomic<int>     mBpm{120};
    std::atomic<int>     mBeatsPerBar{4};
    std::atomic<int>     mBarsPerPhrase{4};
    std::atomic<int>     mSubdivision{1};
    std::atomic<int>     mVisualOffsetMs{0};
    std::atomic<int64_t> mSamplesPerBeat{24000};
    std::atomic<int64_t> mVisualDelaySamples{0};
    std::atomic<bool>    mIsPlaying{false};

    std::atomic<bool> mHasPending{false};
    std::atomic<int>  mPendingBpm{120};
    std::atomic<int>  mPendingBeats{4};
    std::atomic<int>  mPendingBars{4};
    std::atomic<int>  mPendingSub{1};
    std::atomic<int>  mPendingEndBar{-1};

    // Audio-thread-only state
    int64_t mTotalSamples   = 0;
    int64_t mNextBeatSample = 0;
    int     mCurrentBeat    = 0;
    int     mCurrentBar     = 0;
    int     mNextSubIndex   = 0;
    int     mClickPosition  = -1;
    float   mClickGain      = 1.0f;

    // SPSC ring drained on the audio thread itself (producer == consumer == audio thread)
    VisualEvent mVisualBuf[VISUAL_PENDING_CAP];
    int         mVisualHead = 0;
    int         mVisualTail = 0;

    std::vector<float> mClickBuffer;

    void recomputeSamplesPerBeat() {
        int bpm = mBpm.load();
        if (bpm <= 0) bpm = 120;
        int64_t spb = (int64_t) mSampleRate * 60 / bpm;
        mSamplesPerBeat.store(spb);
    }

    void recomputeVisualDelaySamples() {
        int ms = mVisualOffsetMs.load();
        mVisualDelaySamples.store((int64_t) mSampleRate * ms / 1000);
    }

    void generateClickBuffer() {
        const int duration = (mSampleRate * 10) / 1000;
        const int fade     = (mSampleRate * 1)  / 1000;
        const float freq   = 800.0f;
        const float twoPiF = 2.0f * (float) M_PI * freq;

        mClickBuffer.resize(duration);
        for (int i = 0; i < duration; ++i) {
            float t   = (float) i / (float) mSampleRate;
            float env = 1.0f;
            if (i < fade) env = (float) i / (float) fade;
            else if (i > duration - fade) env = (float) (duration - i) / (float) fade;
            mClickBuffer[i] = env * sinf(twoPiF * t) * 0.7f;
        }
    }

    void pushVisual(const VisualEvent& ev) {
        int next = (mVisualTail + 1) % VISUAL_PENDING_CAP;
        if (next == mVisualHead) {
            // Buffer full — drop oldest to keep newest visible
            mVisualHead = (mVisualHead + 1) % VISUAL_PENDING_CAP;
        }
        mVisualBuf[mVisualTail] = ev;
        mVisualTail = next;
    }

    void onTickTriggered() {
        const int subdivision = mSubdivision.load();
        const int beatsPerBar = mBeatsPerBar.load();
        const int barsPerPhrase = mBarsPerPhrase.load();
        const int64_t samplesPerBeat = mSamplesPerBeat.load();

        // Sub-beat tick (subdivision == 2 and we're on subIndex 1) is quieter.
        mClickPosition = 0;
        mClickGain = (mNextSubIndex == 0) ? 1.0f : 0.5f;

        const int reportedBar  = mCurrentBar;
        const int reportedBeat = mCurrentBeat;
        const int reportedSub  = mNextSubIndex;

        VisualEvent ev{};
        ev.fireAtSample = mTotalSamples + mVisualDelaySamples.load();
        ev.bar = reportedBar;
        ev.beat = reportedBeat;
        ev.subIndex = reportedSub;
        ev.swapped = false;

        // Advance to the next tick.
        if (subdivision == 2 && mNextSubIndex == 0) {
            mNextSubIndex = 1;
            mNextBeatSample += samplesPerBeat / 2;
        } else {
            mNextSubIndex = 0;
            // Use full samplesPerBeat regardless of whether we came from sub=1 or sub=0
            // (when subdivision==2, we're returning to the next downbeat half a beat later;
            // when subdivision==1, full beat).
            mNextBeatSample += (subdivision == 2) ? (samplesPerBeat - samplesPerBeat / 2)
                                                  : samplesPerBeat;

            // Advance beat/bar/phrase counters now that we've completed a full beat.
            mCurrentBeat++;
            if (mCurrentBeat >= beatsPerBar) {
                const int endBar = mPendingEndBar.load();
                const bool canSwap = mHasPending.load() &&
                    (endBar < 0 || mCurrentBar == endBar);
                if (canSwap) {
                    mHasPending.store(false);
                    const int newBpm   = mPendingBpm.load();
                    const int newBeats = mPendingBeats.load();
                    const int newBars  = mPendingBars.load();
                    const int newSub   = mPendingSub.load();
                    mBpm.store(newBpm);
                    mBeatsPerBar.store(newBeats);
                    mBarsPerPhrase.store(newBars);
                    mSubdivision.store(newSub);
                    recomputeSamplesPerBeat();
                    mCurrentBeat = 0;
                    mCurrentBar  = 0;
                    mNextSubIndex = 0;
                    ev.swapped = true;
                    ev.bpm = newBpm;
                    ev.beats = newBeats;
                    ev.bars = newBars;
                    ev.sub = newSub;
                } else {
                    mCurrentBeat = 0;
                    mCurrentBar  = (mCurrentBar + 1) % barsPerPhrase;
                }
            }
        }

        pushVisual(ev);
    }

    void notifyBeat(int bar, int beat, int subIndex) {
        if (!gJvm || !gEngineClass || !gOnBeatMethod) return;
        JNIEnv* env = nullptr;
        jint res = gJvm->GetEnv((void**) &env, JNI_VERSION_1_6);
        if (res == JNI_EDETACHED) {
            if (gJvm->AttachCurrentThread(&env, nullptr) != JNI_OK) return;
        } else if (res != JNI_OK || !env) {
            return;
        }
        env->CallStaticVoidMethod(gEngineClass, gOnBeatMethod,
                                  (jint) bar, (jint) beat, (jint) subIndex);
        if (env->ExceptionCheck()) env->ExceptionClear();
    }

    void notifySwap(int bpm, int beats, int bars, int sub) {
        if (!gJvm || !gEngineClass || !gOnSwapMethod) return;
        JNIEnv* env = nullptr;
        jint res = gJvm->GetEnv((void**) &env, JNI_VERSION_1_6);
        if (res == JNI_EDETACHED) {
            if (gJvm->AttachCurrentThread(&env, nullptr) != JNI_OK) return;
        } else if (res != JNI_OK || !env) {
            return;
        }
        env->CallStaticVoidMethod(gEngineClass, gOnSwapMethod,
                                  (jint) bpm, (jint) beats, (jint) bars, (jint) sub);
        if (env->ExceptionCheck()) env->ExceptionClear();
    }
};

static std::unique_ptr<MetronomeEngine> sEngine;

extern "C" {

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
    gJvm = vm;
    JNIEnv* env = nullptr;
    if (vm->GetEnv((void**) &env, JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;
    jclass cls = env->FindClass("com/velvet/metronome/audio/MetronomeEngine");
    if (!cls) return JNI_ERR;
    gEngineClass  = (jclass) env->NewGlobalRef(cls);
    gOnBeatMethod = env->GetStaticMethodID(gEngineClass, "onBeat", "(III)V");
    if (!gOnBeatMethod) return JNI_ERR;
    gOnSwapMethod = env->GetStaticMethodID(gEngineClass, "onPresetSwapped", "(IIII)V");
    if (!gOnSwapMethod) return JNI_ERR;
    return JNI_VERSION_1_6;
}

JNIEXPORT jboolean JNICALL
Java_com_velvet_metronome_audio_MetronomeEngine_nativeStart(
        JNIEnv*, jobject, jint bpm, jint beats, jint bars, jint sub) {
    if (!sEngine) sEngine = std::make_unique<MetronomeEngine>();
    return sEngine->start(bpm, beats, bars, sub) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_velvet_metronome_audio_MetronomeEngine_nativeStop(JNIEnv*, jobject) {
    if (sEngine) sEngine->stop();
}

JNIEXPORT void JNICALL
Java_com_velvet_metronome_audio_MetronomeEngine_nativeSetBpm(JNIEnv*, jobject, jint bpm) {
    if (sEngine) sEngine->setBpm(bpm);
}

JNIEXPORT void JNICALL
Java_com_velvet_metronome_audio_MetronomeEngine_nativeSetBeatsPerBar(JNIEnv*, jobject, jint beats) {
    if (sEngine) sEngine->setBeatsPerBar(beats);
}

JNIEXPORT void JNICALL
Java_com_velvet_metronome_audio_MetronomeEngine_nativeSetBarsPerPhrase(JNIEnv*, jobject, jint bars) {
    if (sEngine) sEngine->setBarsPerPhrase(bars);
}

JNIEXPORT void JNICALL
Java_com_velvet_metronome_audio_MetronomeEngine_nativeSetSubdivision(JNIEnv*, jobject, jint sub) {
    if (sEngine) sEngine->setSubdivision(sub);
}

JNIEXPORT void JNICALL
Java_com_velvet_metronome_audio_MetronomeEngine_nativeSetVisualOffsetMs(JNIEnv*, jobject, jint ms) {
    if (sEngine) sEngine->setVisualOffsetMs(ms);
}

JNIEXPORT void JNICALL
Java_com_velvet_metronome_audio_MetronomeEngine_nativeQueuePreset(
        JNIEnv*, jobject, jint bpm, jint beats, jint bars, jint sub, jint endBar) {
    if (sEngine) sEngine->queuePreset(bpm, beats, bars, sub, endBar);
}

JNIEXPORT jboolean JNICALL
Java_com_velvet_metronome_audio_MetronomeEngine_nativeIsPlaying(JNIEnv*, jobject) {
    return (sEngine && sEngine->isPlaying()) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_velvet_metronome_audio_MetronomeEngine_nativeGetVersion(JNIEnv* env, jobject) {
    return env->NewStringUTF("MetronomeEngine v2.0 — subdivision + visual offset");
}

} // extern "C"
