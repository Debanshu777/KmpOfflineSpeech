package io.github.debanshu.siren.sample.desktop

import io.github.debanshu.siren.sample.SampleModels
import io.github.debanshu.siren.speech.createJvmSpeechEngine
import kotlinx.coroutines.runBlocking

/** Actual desktop classpath/runtime verification; intentionally never opens an audio device. */
fun main() = runBlocking {
    val engine = createJvmSpeechEngine(SampleModels.bundledDemo())
    try {
        val audio = engine.synthesize("The quick brown fox jumps over the lazy dog.")
        try {
            val transcript = engine.transcribe(audio)
            val passed = listOf("quick", "brown", "fox").all { it in transcript.lowercase() }
            println("{\"passed\":$passed,\"sampleRate\":${audio.sampleRate},\"samples\":${audio.samples.size}," +
                "\"transcript\":\"${transcript.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")}\"," +
                "\"scope\":\"desktop classpath models and JVM SDK; no microphone or speaker\"}")
            check(passed) { "Desktop native roundtrip did not recognize the expected words" }
        } finally { audio.samples.fill(0f) }
    } finally { engine.close() }
}
