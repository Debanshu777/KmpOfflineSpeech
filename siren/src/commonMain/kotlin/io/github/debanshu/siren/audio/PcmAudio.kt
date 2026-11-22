package io.github.debanshu.siren.audio

/** Caller-owned mono float samples and their actual sample rate. */
public data class PcmAudio(val samples: FloatArray, val sampleRate: Int) {
    init {
        require(sampleRate > 0) { "Sample rate must be positive" }
        require(samples.isNotEmpty()) { "No audio was captured" }
    }
}
