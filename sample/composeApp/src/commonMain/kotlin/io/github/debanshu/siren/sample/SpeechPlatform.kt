package io.github.debanshu.siren.sample

import io.github.debanshu.siren.audio.PcmAudio
import io.github.debanshu.siren.speech.SpeechEngine

/** The demo adds host audio and permissions to a replaceable inference engine. */
interface SpeechPlatform : SpeechEngine {
    suspend fun requestMicrophonePermission(): Boolean
    suspend fun startRecording()
    suspend fun stopRecording(): PcmAudio
    suspend fun play(audio: PcmAudio)
    suspend fun stop()
}
