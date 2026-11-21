package dev.debanshu.speech

/** Mono float PCM in [-1, 1], with its actual sample rate (never just relabel it). */
data class PcmAudio(val samples: FloatArray, val sampleRate: Int) {
    init {
        require(sampleRate > 0)
        require(samples.isNotEmpty()) { "No audio was captured" }
    }
}

/** One owner; model calls and teardown must never overlap. No service or cloud API. */
interface SpeechPlatform {
    suspend fun requestMicrophonePermission(): Boolean
    suspend fun startRecording()
    suspend fun stopRecording(): PcmAudio
    suspend fun transcribe(audio: PcmAudio): String
    suspend fun synthesize(text: String): PcmAudio
    suspend fun play(audio: PcmAudio)
    suspend fun stop()
    suspend fun close()
}
