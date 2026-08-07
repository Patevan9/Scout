package com.example.scoutface.brain

import java.util.Locale

/**
 * Single source of truth for the locale Android's SpeechRecognizer is
 * configured to listen in.
 *
 * MainActivity's buildRecognizerIntent() sets RecognizerIntent.EXTRA_LANGUAGE
 * from RECOGNITION_LOCALE below, and handleLanguageIntent() answers "what
 * language are we speaking" from the same constant via spokenLanguageName().
 * There is deliberately only one place this locale is set -- if the
 * recognition language ever changes, Scout's spoken answer changes with it
 * automatically instead of silently going stale.
 *
 * Does NOT change speech-recognition behavior -- RECOGNITION_LOCALE holds the
 * same Locale.US value the recognizer intent already used before this file
 * existed; this only gives that value one named home instead of a repeated
 * literal.
 */
object ScoutSpeechLanguage {

    val RECOGNITION_LOCALE: Locale = Locale.US

    /** A natural spoken language name, e.g. "English". */
    fun spokenLanguageName(): String = RECOGNITION_LOCALE.getDisplayLanguage(Locale.US)
}
