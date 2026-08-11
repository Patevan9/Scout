package com.example.scoutface.brain

/**
 * Minimal, engine/Android-independent snapshot of the fields of
 * android.speech.tts.Voice that matter for choosing Scout's default voice.
 * A plain data class rather than the real Voice type, so this logic is
 * unit-testable without Robolectric/a real Android runtime -- same pattern
 * as every other pure class in this package.
 */
data class VoiceCandidate(
    val name: String,
    val languageEn: Boolean,
    val countryUs: Boolean,
    val quality: Int,
    val networkRequired: Boolean,
    val notInstalled: Boolean
)

/**
 * Chooses Scout's default TTS voice from whatever voices the currently
 * bound TTS engine actually reports.
 *
 * Deliberately engine-agnostic: this never assumes or is told which engine
 * is bound, and it doesn't need to be. Android's public API cannot prove
 * that (TextToSpeech.getCurrentEngine() exists at runtime but is annotated
 * @UnsupportedAppUsage -- excluded from the public SDK a real build
 * compiles against), so the caller (MainActivity.applyPreferredVoice())
 * never passes engine identity in here and never claims it in its own
 * diagnostic logging either -- only the resulting voice's own name (read
 * back from the public TextToSpeech.getVoice() after selection) is ever
 * reported as ground truth.
 *
 * Selection order:
 *  1. The first name in [preferredNames] that is present among the
 *     offline, installed, en-US candidates -- verified by a human actually
 *     listening to the voice, never inferred from a name string alone.
 *  2. If none of those names are present, the highest-quality offline
 *     en-US candidate (ties broken by name, for determinism across runs).
 *  3. If no offline en-US candidate exists at all, null -- caller leaves
 *     whatever voice the engine already defaulted to in place, exactly
 *     like Scout's original (pre-this-feature) behavior for that case.
 *
 * A network-required or not-yet-installed voice is never chosen, at any
 * step -- not even if it's the preferred name -- since Scout must keep
 * working offline.
 */
object ScoutVoiceSelector {

    fun choose(voices: List<VoiceCandidate>, preferredNames: List<String>): String? {
        val offlineUs = voices.filter {
            it.languageEn && it.countryUs && !it.networkRequired && !it.notInstalled
        }

        val preferredMatch = preferredNames.firstNotNullOfOrNull { name ->
            offlineUs.find { it.name == name }?.name
        }
        if (preferredMatch != null) return preferredMatch

        return offlineUs
            .sortedWith(compareByDescending<VoiceCandidate> { it.quality }.thenBy { it.name })
            .firstOrNull()
            ?.name
    }
}
