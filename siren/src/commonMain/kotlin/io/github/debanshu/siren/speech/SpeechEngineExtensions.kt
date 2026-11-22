package io.github.debanshu.siren.speech

import io.github.debanshu.siren.audio.PcmAudio
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Transcribe caller-owned mono PCM at its actual recording rate, without an extra wrapper at the call site. */
public suspend fun SpeechEngine.transcribe(samples: FloatArray, sampleRate: Int): String =
    transcribe(PcmAudio(samples, sampleRate))

/**
 * Run a scoped operation and close its engine, including after cancellation.
 * Use lifecycle-owned engines directly when they must remain open across several UI actions.
 * If both the operation and cleanup fail, the operation's error is preserved and cleanup is suppressed.
 */
public suspend fun <T> SpeechEngine.use(block: suspend (SpeechEngine) -> T): T {
    var failure: Throwable? = null
    try {
        return block(this)
    } catch (error: Throwable) {
        failure = error
        throw error
    } finally {
        val operationFailure = failure
        withContext(NonCancellable) {
            if (operationFailure == null) {
                close()
            } else {
                try {
                    close()
                } catch (cleanupFailure: Throwable) {
                    if (cleanupFailure !== operationFailure) operationFailure.addSuppressed(cleanupFailure)
                }
            }
        }
    }
}
