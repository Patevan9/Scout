package com.example.scoutface

import android.content.Context
import android.graphics.*
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import com.example.scoutface.brain.ScoutExpressionLayer
import com.example.scoutface.brain.ScoutExpressionPriority
import com.example.scoutface.brain.ScoutSpeechRangeMouth
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class ScoutFaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // ======================================================
    //  PUBLIC API  (called by MainActivity)
    //
    //  All setters are UI-thread-safe.
    //  Background-thread callers are marshaled via post{}.
    //  Booleans skip redraw if value unchanged.
    //  Floats skip redraw if delta < 0.01f.
    // ======================================================

    private var vSpeaking    = false
    private var vListening   = false
    private var vThinking    = false
    private var vDownloading = false
    private var vLookTargetX = 0f
    private var vLookTargetY = 0f
    private var vMicLevel    = 0f
    private var vSpeechLevel = 0f
    private var vBatteryPct  = 100  // 0–100
    private var vVergence    = 0f   // 0..1 — 0=neutral, 1=full near-focus

    fun setSpeaking(s: Boolean) = setOnUiThread {
        if (vSpeaking != s) {
            vSpeaking = s
            if (s) {
                // Speaking Mouth v1: each new speaking dispatch starts fresh
                // in synthetic-fallback mode -- "has a real onRangeStart()
                // event ever been seen" must be scoped per dispatch, never
                // carried over from whatever the previous utterance's TTS
                // engine did or didn't support. See speechRangePulse()'s own
                // doc comment for the full reasoning. speechRangeEstablished
                // is deliberately sticky once true (PR #80 review
                // correction) -- only this per-dispatch reset, or the
                // symmetric one in the !vSpeaking branch of updateLife()/
                // resetFace(), ever clears it back to false.
                speechRangeImpulse      = 0f
                speechRangeEstablished  = false
            }
            requestActiveFrame()
        }
    }
    fun setListening(s: Boolean) = setOnUiThread {
        if (vListening != s) { vListening = s; requestActiveFrame() }
    }
    fun setThinking(s: Boolean) = setOnUiThread {
        if (vThinking != s) { vThinking = s; requestActiveFrame() }
    }
    fun setDownloading(s: Boolean) = setOnUiThread {
        if (vDownloading != s) { vDownloading = s; requestActiveFrame() }
    }
    fun setGaze(x: Float, y: Float) = setOnUiThread {
        val nx = x.coerceIn(-80f, 80f)
        val ny = y.coerceIn(-55f, 55f)
        if (abs(vLookTargetX - nx) > 0.5f || abs(vLookTargetY - ny) > 0.5f) {
            vLookTargetX = nx
            vLookTargetY = ny
            requestActiveFrame()
        }
    }
    fun setMicLevel(v: Float) = setOnUiThread {
        val nv = v.coerceIn(0f, 1f)
        if (abs(vMicLevel - nv) > 0.01f) { vMicLevel = nv; requestActiveFrame() }
    }
    fun setSpeechLevel(v: Float) = setOnUiThread {
        val nv = v.coerceIn(0f, 1f)
        if (abs(vSpeechLevel - nv) > 0.01f) { vSpeechLevel = nv; requestActiveFrame() }
    }

    /**
     * Speaking Mouth v1 -- one real speech-timed impulse, reported by
     * MainActivity's TTS `onRangeStart()` callback for the dispatch that
     * currently owns Scout's global speaking state (dispatch-scoped
     * ownership is already resolved by the caller before this is ever
     * invoked -- see `ScoutSpeechDispatchGuard.ownsGlobalSpeakingState()` at
     * that call site; this method has no dispatch-id awareness of its own,
     * exactly like `setSpeaking()` itself never has).
     *
     * Deliberately NOT a reuse of `setSpeechLevel()`/`vSpeechLevel`: that
     * existing path models a continuously-HELD level (a caller pushes the
     * current value; nothing about it decays on its own), which is the
     * right shape for a real amplitude stream but the wrong shape for a
     * fire-and-forget per-word event -- there is no natural "back to zero"
     * for a level that's only ever set, never re-driven between words. This
     * method instead follows the exact same fire-and-forget pulse idiom this
     * class already uses for noticePresence()/pleasedBeat()/uncertainBeat():
     * the caller reports WHEN, this class -- and ScoutSpeechRangeMouth's pure
     * helper functions -- own HOW it rises and decays, entirely in
     * updateLife(). setSpeechLevel()/vSpeechLevel/speechSmooth are completely
     * untouched by this feature and remain exactly as dormant as before.
     *
     * [rangeChars] is the onRangeStart(start, end, ...) span's character
     * length -- a simple, deterministic timing/size heuristic (see
     * ScoutSpeechRangeMouth.impulseMagnitude()), never a claim of real audio
     * amplitude, which onRangeStart() does not provide. Safe to call as
     * often as real range events arrive; each call boosts (never additively
     * stacks past the bounded ceiling) the current impulse.
     *
     * PR #80 review correction: the FIRST call for a given speaking dispatch
     * also latches speechRangeEstablished to true, STICKY for the rest of
     * that dispatch -- see ScoutSpeechRangeMouth.isRangeDriven()'s own doc
     * comment for why this is deliberately not time-windowed. A long gap
     * before the next real event no longer falls back to the synthetic
     * animation; the separately-decaying speechRangeImpulse simply settles
     * toward 0 (mouth closes) until the next event arrives. See
     * updateLife()'s own branch selection for exactly how that's used, and
     * setSpeaking()/resetFace() for where this state is fully cleared back
     * to false at a genuine dispatch boundary.
     */
    fun speechRangePulse(rangeChars: Int) = setOnUiThread {
        val magnitude = ScoutSpeechRangeMouth.impulseMagnitude(
            rangeChars, SPEECH_RANGE_PULSE_MIN, SPEECH_RANGE_PULSE_MAX, SPEECH_RANGE_CHARS_NORMALIZER
        )
        speechRangeImpulse = max(speechRangeImpulse, magnitude)
        speechRangeEstablished = true
        requestActiveFrame()
    }
    fun setBatteryLevel(pct: Int) = setOnUiThread {
        val np = pct.coerceIn(0, 100)
        if (vBatteryPct != np) { vBatteryPct = np; requestActiveFrame() }
    }
    /**
     * Vergence — how hard Scout is "focusing."
     * 0 = neutral. 1 = full near-focus (irises pull maximally inward).
     * Thinking state also pushes vergence automatically inside updateLife().
     */
    fun setVergence(v: Float) = setOnUiThread {
        val nv = v.coerceIn(0f, 1f)
        if (abs(vVergence - nv) > 0.01f) { vVergence = nv; requestActiveFrame() }
    }

    /**
     * Silent Arrival Acknowledgment v1. A single deliberate brow lift --
     * "I noticed you" -- distinct from the ambient random brow micro-drift
     * (browMicroY/browMicroTarget below) so a random idle twitch can never
     * be mistaken for, or blended into, a deliberate acknowledgment. Callers
     * decide WHEN this fires (once per genuine arrival, never from ambient
     * animation); this method only owns HOW it looks. Safe to call as often
     * as a caller likes -- each call simply (re)starts the hold window, so a
     * caller with its own one-shot latch (as MainActivity's is designed)
     * never needs to worry about this side.
     *
     * Existing gaze tracking (setGaze()) remains entirely responsible for
     * looking toward whoever arrived; this only adds the small expressive
     * accent on top of that, exactly like blinkBrowRelax already does for a
     * blink -- same instant-rise/smooth-decay idiom, just a different
     * trigger, magnitude, and decay time so the two read as clearly
     * different events.
     */
    fun noticePresence() = setOnUiThread {
        noticePulseUntilMs = System.currentTimeMillis() + NOTICE_PULSE_HOLD_MS
        requestActiveFrame()
    }

    /**
     * Emotional Face v1 -- ATTENTIVE. Sustained, not a pulse: mirrors
     * setListening()'s exact shape (a plain boolean the caller keeps
     * up-to-date every frame, smoothed on this side so it rises/falls
     * gently rather than snapping). Callers pass the existing sustained
     * direct-address signal (directAddressStreakStartMs/
     * DIRECT_ADDRESS_SUSTAIN_MS) -- no new detector or debounce lives here;
     * this method only owns how ATTENTIVE looks once the caller has already
     * decided the person has been genuinely, continuously facing Scout long
     * enough. Whether it's actually visible this frame is decided by
     * ScoutExpressionPriority.resolveBrowOwner() in updateLife() -- thinking
     * and listening (which already claim the brow) take priority, and so do
     * PLEASED/UNCERTAIN and PR #73's own arrival pulse, per the approved
     * ownership order.
     */
    fun setAttentive(active: Boolean) = setOnUiThread {
        if (vAttentive != active) { vAttentive = active; requestActiveFrame() }
    }

    /**
     * Emotional Face v1 -- PLEASED / WARM. A single deliberate expressive
     * beat -- same rise/hold/decay idiom as noticePresence(), a distinct
     * pulse and magnitude so the two never blend into each other (see
     * ScoutExpressionPriority for how ownership between them is decided
     * when both are somehow active at once). Callers decide WHEN (see
     * MainActivity's PRAISE/AFFECTION/THANKS/successful-teaching call
     * sites); this method only owns HOW it looks. Safe to call as often as
     * a caller likes -- each call simply (re)starts the hold window.
     *
     * Round 2 fix: also arms the mouth's own, separately-timed release (see
     * pleasedMouthArmed's doc comment) -- the brow pulse above starts aging
     * immediately as before, but the mouth-corner shape must not, since
     * speaking (which owns the mouth) doesn't actually begin until well
     * after this call returns.
     */
    fun pleasedBeat() = setOnUiThread {
        val now = System.currentTimeMillis()
        pleasedPulseUntilMs = now + PLEASED_HOLD_MS
        pleasedMouthArmed = true
        pleasedMouthSawSpeaking = false
        pleasedMouthArmedAtMs = now
        requestActiveFrame()
    }

    /**
     * Emotional Face v1 -- UNCERTAIN. Same fire-and-forget pulse shape as
     * pleasedBeat()/noticePresence(). Callers decide WHEN (see
     * MainActivity's DONT_KNOW / unrecognized-teaching / TinyLlama-null-
     * fallback call sites); this method only owns HOW it looks -- "I didn't
     * quite get that," never sadness, fear, or distress. Never changes what
     * text is spoken or how a reply is routed; purely an accompanying
     * facial beat.
     *
     * Round 2 fix: same mouth-arming addition as pleasedBeat() above.
     */
    fun uncertainBeat() = setOnUiThread {
        val now = System.currentTimeMillis()
        uncertainPulseUntilMs = now + UNCERTAIN_HOLD_MS
        uncertainMouthArmed = true
        uncertainMouthSawSpeaking = false
        uncertainMouthArmedAtMs = now
        requestActiveFrame()
    }

    /**
     * Full recovery reset.
     * Call when: STT/TTS crashes, app resumes from background, camera lost/regained.
     * Battery level intentionally NOT reset — it is system state, not animation state.
     */
    fun resetFace() = setOnUiThread {
        vSpeaking    = false
        vListening   = false
        vThinking    = false
        vDownloading = false
        vLookTargetX = 0f
        vLookTargetY = 0f
        vMicLevel    = 0f
        vSpeechLevel = 0f
        vVergence    = 0f

        micSmooth    = 0f
        speechSmooth = 0f

        mouthOpen   = 0f
        speechPhase = 0f
        wavePhase   = 0f

        // Speaking Mouth v1
        speechRangeImpulse     = 0f
        speechRangeEstablished = false

        lookX  = 0f; lookY  = 0f
        lookVX = 0f; lookVY = 0f
        thinkGazeY        = 0f
        thinkGazeX        = 0f
        thinkGlanceActive = false
        thinkGlanceSideX  = 0f
        nextThinkGlanceAt = 0L
        thinkEndedAt      = 0L
        vergenceSmooth = 0f

        // FIX 1: reset listening bias smoother
        listeningBiasSmooth = 0f

        lowerLidSmooth = 0f
        lowerLidExprL  = 0f
        lowerLidExprR  = 0f
        thinkLidSmooth = 0f

        blinking      = false
        blinkPhaseL   = 0f; blinkPhaseR   = 0f
        blinkLagPhase = 0f; blinkLead     = -1
        blinkSpeed    = 1.0f; blinkMaxPhase = 2.0f
        blinkL        = 0f; blinkR        = 0f
        nextBlinkAt   = 0L

        browMicroY      = 0f
        browMicroTarget = 0f
        nextBrowMicroAt = 0L

        noticePulse      = 0f
        noticePulseUntilMs = 0L

        // Emotional Face v1
        vAttentive          = false
        attentiveSmooth     = 0f
        pleasedPulse        = 0f
        pleasedPulseUntilMs = 0L
        uncertainPulse        = 0f
        uncertainPulseUntilMs = 0L
        browExpressionOwner  = ScoutExpressionLayer.NONE
        mouthExpressionOwner = ScoutExpressionLayer.NONE

        // Emotional Face v1 round 2 fix -- deferred mouth release state
        pleasedMouthArmed       = false
        pleasedMouthSawSpeaking = false
        pleasedMouthArmedAtMs   = 0L
        pleasedMouthUntilMs     = 0L
        pleasedMouthIntensity   = 0f
        uncertainMouthArmed       = false
        uncertainMouthSawSpeaking = false
        uncertainMouthArmedAtMs   = 0L
        uncertainMouthUntilMs     = 0L
        uncertainMouthIntensity   = 0f

        saccadeX = 0f; saccadeY = 0f
        saccadeTargetX = 0f; saccadeTargetY = 0f
        saccadeDecayMs = 800f; nextSaccadeAt = 0L

        idleDriftX = 0f; idleDriftY = 0f
        idleDriftTargetX = 0f; idleDriftTargetY = 0f
        nextDriftChangeAt = 0L

        focusBreathPhase = 0f

        microTremorX = 0f; microTremorY = 0f
        microTremorTargetX = 0f; microTremorTargetY = 0f
        nextMicroTremorAt = 0L
        pendingDoubleBlink = false

        faceIdleDriftX = 0f; faceIdleDriftY = 0f
        faceIdleDriftTargetX = 0f; faceIdleDriftTargetY = 0f
        nextFaceDriftAt = 0L
        faceGazeDriftX = 0f; faceGazeDriftY = 0f
        blinkDipY = 0f; blinkBrowRelax = 0f

        // FIX 3: reset wave gate
        waveActive = false

        lastNow  = 0L
        idleMode = false
        removeCallbacks(tickRunnable)
        requestActiveFrame()
    }

    // ======================================================
    //  THREAD MARSHALING HELPER
    // ======================================================
    private inline fun setOnUiThread(crossinline block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block()
        else post { block() }
    }

    // ======================================================
    //  VIRTUAL CANVAS  — face designed at 1920x1080
    // ======================================================
    private val VW = 1920f
    private val VH = 1080f

    // ======================================================
    //  ANIMATION STATE
    // ======================================================
    private var lastNow      = 0L
    private var micSmooth    = 0f
    private var speechSmooth = 0f

    private var lookX  = 0f
    private var lookY  = 0f
    private var lookVX = 0f
    private var lookVY = 0f

    private var blinking      = false
    private var blinkPhaseL   = 0f
    private var blinkPhaseR   = 0f
    private var blinkLagPhase = 0f
    private var blinkLead     = -1
    private var blinkSpeed    = 1.0f
    private var blinkMaxPhase = 2.0f
    private var blinkL        = 0f
    private var blinkR        = 0f
    private var nextBlinkAt   = 0L

    // Very subtle at rest
    private var lidDroopL = 0.06f + Random.nextFloat() * 0.02f
    private var lidDroopR = 0.06f + Random.nextFloat() * 0.02f
    private val lidTauL   = 1800f + Random.nextFloat() * 600f
    private val lidTauR   = 1800f + Random.nextFloat() * 600f
    private val lidTiredL = 0.54f + Random.nextFloat() * 0.08f
    private val lidTiredR = 0.54f + Random.nextFloat() * 0.08f

    private var lowerLidSmooth  = 0f   // speech/breath base lower lid, both eyes
    private var lowerLidExprL   = 0f   // expression layer, left eye (px, upward)
    private var lowerLidExprR   = 0f   // expression layer, right eye (px, upward)
    private var thinkLidSmooth  = 0f   // fast-responding right-eye droop during thinking

    private var vergenceSmooth    = 0f
    private val VERGENCE_MAX_BIAS = 18f

    // FIX 1: smooth the listening inward-bias so irises don't snap when
    // vListening flips. Replaces the old binary `if (vListening) 10f else 0f`.
    // Tau 160ms — fast enough to feel responsive, slow enough to kill the twitch.
    private var listeningBiasSmooth = 0f

    private var thinkGazeY       = 0f
    private var thinkGazeX       = 0f   // X component of thinking glance
    private var thinkGlanceActive = false
    private var thinkGlanceSideX  = 0f
    private var nextThinkGlanceAt = 0L
    private var thinkEndedAt      = 0L  // timestamp thinking stopped; drives reconnect snap

    private var browMicroY      = 0f
    private var browMicroTarget = 0f
    private var nextBrowMicroAt = 0L

    // Silent Arrival Acknowledgment v1. noticePulse is the current brow-lift
    // amount this pulse contributes, kept fully separate from browMicroY
    // above; noticePulseUntilMs is the wall-clock time the current pulse's
    // hold phase ends. 0L means no pulse has ever been requested (or the
    // last one has fully finished decaying and self-cleared) -- see
    // updateLife() for the rise/hold/decay curve. This pulse's own timing
    // and magnitude are completely unchanged by Emotional Face v1 below --
    // only WHERE its resulting value gets used in drawBrow() changed, from
    // "always subtracted directly" to "the NOTICE candidate in
    // ScoutExpressionPriority's ownership resolution," so it can no longer
    // silently stack with a higher-priority PLEASED/UNCERTAIN beat.
    private var noticePulse       = 0f
    private var noticePulseUntilMs = 0L
    private val NOTICE_PULSE_LIFT_PX = 9f   // between ambient browMicroY's ±4f range and the 20f sustained listening lift
    private val NOTICE_PULSE_HOLD_MS = 500L
    private val NOTICE_PULSE_RISE_TAU_MS = 220f
    private val NOTICE_PULSE_DECAY_TAU_MS = 480f

    // Emotional Face v1 -- ATTENTIVE, PLEASED, UNCERTAIN. Each pulse
    // (pleased/uncertain) follows the exact same rise/hold/decay idiom as
    // noticePulse above, at its own magnitude/timing so the three read as
    // clearly different events; ATTENTIVE is sustained (driven by a plain
    // boolean, smoothed like listeningBiasSmooth) rather than a pulse, since
    // it's meant to persist for as long as the caller reports genuine
    // sustained direct address, not fire once and decay.
    //
    // Amplitudes are deliberately chosen in the visually-readable
    // neighborhood the investigation identified (roughly the existing
    // LISTENING/THINKING 20-38px range), not PR #73's own deliberately
    // conservative 9px pilot value -- see each constant's own comment for
    // the reasoning. browExpressionOwner/mouthExpressionOwner are computed
    // once per frame in updateLife() via ScoutExpressionPriority and read
    // by drawBrow()/drawMouth() -- the single place that decides which one
    // (if any) of these candidates, plus NOTICE above, actually renders.
    private var vAttentive      = false
    private var attentiveSmooth = 0f
    // Expression Visibility v2: bumped from the original 16px pilot value.
    // ATTENTIVE now typically renders WHILE listening's own existing +20px
    // baseline lift is already applied (see drawBrow()'s listeningLift,
    // unconditional on vListening and structurally independent of the
    // expression-priority layer) -- since ATTENTIVE can now actually own
    // the brow during listening (Expression Visibility v2's core fix, see
    // ScoutExpressionPriority), its own amplitude is set to match
    // listening's own 20px so the combined "attentive while listening"
    // total (~40px) reads as a clearly distinguishable EXTRA lift above
    // ordinary listening, in the same neighborhood as THINKING's 38px,
    // without exceeding it into more-dramatic-than-thinking territory.
    private val ATTENTIVE_BROW_LIFT_PX = 20f
    private val ATTENTIVE_TAU_MS       = 260f
    // Expression Visibility v2: both brows' OUTER end lifts a bit further
    // than a flat/rigid lift alone -- a gentle "eyebrows raised in
    // interest" shape (symmetric, unlike THINKING's single-sided arch),
    // giving ATTENTIVE its own readable silhouette rather than relying on
    // vertical amplitude alone, per the investigation's "brow renderer
    // already supports angle/arch" finding. See drawBrow() for exactly how
    // this composes with the base lift.
    private val ATTENTIVE_OUTER_ARCH_PX = 6f
    // Expression Visibility v2: bumped from the original 0.035 pilot value
    // -- still restrained (well short of a full eye-widen), but now a
    // clearly perceptible "more open/alert" cue rather than a near-
    // imperceptible one, coordinated with the brow shape above rather than
    // relied on alone. Expressed as a fraction subtracted from drawEye()'s
    // existing 0..1 lid closure amount (b), not a new geometry capability.
    private val ATTENTIVE_EYE_OPEN_MAX = 0.09f

    private var pleasedPulse        = 0f
    private var pleasedPulseUntilMs = 0L
    // A deliberate positive beat -- stronger than ATTENTIVE since it's a
    // one-off event, not a sustained cue, and comfortably inside the
    // investigation's 15-30px readable range rather than near-ambient.
    private val PLEASED_BROW_LIFT_PX     = 22f
    // Expression Visibility v2: hold extended from the original 700ms --
    // the investigation found the full rise+hold+decay arc (previously
    // ~1.15s even when not suppressed) was short enough to miss even when
    // correctly rendering. Rise/decay taus are unchanged -- only the hold
    // plateau is longer, so the expression still arrives and leaves at the
    // same brisk pace, it just stays fully visible longer in between. This
    // same constant also extends pleasedMouthUntilMs's own hold window
    // below (single source of truth, no separate mouth timing edit needed).
    private val PLEASED_HOLD_MS          = 1100L
    private val PLEASED_RISE_TAU_MS      = 150f
    private val PLEASED_DECAY_TAU_MS     = 450f
    // Expression Visibility v2: both brows arch gently upward in the middle
    // (a soft convex bow, via the curve's own midY control point) on top of
    // the vertical lift -- a warmer, rounder shape distinct from a flat
    // lift or THINKING's own single-sided angular arch, per the "use brow
    // geometry already proven capable of arch" requirement. See drawBrow().
    private val PLEASED_ARCH_PX          = 10f
    // Expression Visibility v2: a small, symmetric, deterministic "eyes
    // crinkle warmly" cue -- both lower lids rise slightly while PLEASED
    // owns the brow, reusing the existing lowerLidExprL/R mechanism
    // (already independently per-eye, already wired into drawEye() via
    // drawFace()'s lowerL/lowerR) rather than adding new geometry. See
    // updateLife()'s exprTargetL/R computation.
    private val PLEASED_LOWER_LID_PX     = 5f
    // Mouth-corner lift (both corners) when PLEASED owns the mouth -- a real
    // smile-shape change, several times the ~2-6px swing the resting mouth
    // shows across every existing state today.
    private val PLEASED_MOUTH_CORNER_PX  = 12f
    // Round 2 fix: the mouth's own independent release state -- deliberately
    // separate from pleasedPulse above, which keeps driving the brow on its
    // original immediate timing, completely unaffected by any of this. See
    // ScoutExpressionPriority.shouldReleaseDeferredMouthExpression()'s doc
    // comment for the full reasoning. pleasedMouthIntensity (0..1) is what
    // drawMouth() actually reads; it only starts rising once the mouth is
    // released, using the same PLEASED_RISE_TAU_MS/PLEASED_DECAY_TAU_MS
    // shape as the brow pulse for a matching feel, just on its own clock.
    private var pleasedMouthArmed         = false
    private var pleasedMouthSawSpeaking   = false
    private var pleasedMouthArmedAtMs     = 0L
    private var pleasedMouthUntilMs       = 0L
    private var pleasedMouthIntensity     = 0f

    private var uncertainPulse        = 0f
    private var uncertainPulseUntilMs = 0L
    // Asymmetric -- only the LEFT eyebrow (side < 0) lifts by this amount;
    // the right eyebrow gets none of it. Deliberately the opposite side and
    // a plain height change with no arc/tilt at all, unlike THINKING's own
    // right-eyebrow arch (thinkTilt/thinkInward/thinkInnerLift below), so
    // the two read as clearly different shapes, not just different amounts.
    // Roughly half of THINKING's 38px, comfortably above LISTENING's 20px
    // floor for restraint while staying unmistakably visible -- a single
    // raised eyebrow is a common, readable "hmm, not sure" cue on its own.
    private val UNCERTAIN_BROW_PRIMARY_PX = 18f
    // Expression Visibility v2: hold extended from the original 550ms, same
    // reasoning as PLEASED_HOLD_MS above -- rise/decay taus unchanged. Also
    // extends uncertainMouthUntilMs's own hold window below.
    private val UNCERTAIN_HOLD_MS         = 900L
    private val UNCERTAIN_RISE_TAU_MS     = 180f
    private val UNCERTAIN_DECAY_TAU_MS    = 380f
    // Expression Visibility v2: the raised (left) brow's OUTER end tilts up
    // further than its inner end -- an inquisitive "cocked eyebrow" shape,
    // not just a flat single-brow lift. Deliberately the opposite emphasis
    // from THINKING's own inner-end arch (thinkTilt/thinkInnerLift), so the
    // two read as clearly different gestures rather than the same shape at
    // a different amount. See drawBrow().
    private val UNCERTAIN_OUTER_TILT_PX = 8f
    // Expression Visibility v2: a small, deterministic, ONE-SIDED lid
    // narrowing on the eye opposite the raised brow (right) -- a restrained
    // "quizzical" cue (one brow up, the other eye narrows slightly), reusing
    // the existing lowerLidExprR mechanism, already independent per-eye.
    // Deliberately smaller than PLEASED's symmetric lower-lid cue and
    // one-sided only, so the two never read as the same eye behavior.
    private val UNCERTAIN_LID_NARROW_PX = 5f
    // Mouth corners flatten (move toward/slightly past the default resting
    // height, never past the center dip point -- see drawMouth()'s own
    // comment) rather than dropping into anything frown-shaped. Asymmetric
    // like the brow: the left corner (same side as the raised eyebrow)
    // moves more than the right, reinforcing one coherent "off-kilter,
    // didn't quite follow" read rather than a symmetric, sadder-looking dip.
    private val UNCERTAIN_MOUTH_PRIMARY_PX   = 10f
    private val UNCERTAIN_MOUTH_SECONDARY_PX = 2f
    // Round 2 fix: same independent mouth-release state as PLEASED above.
    private var uncertainMouthArmed       = false
    private var uncertainMouthSawSpeaking = false
    private var uncertainMouthArmedAtMs   = 0L
    private var uncertainMouthUntilMs     = 0L
    private var uncertainMouthIntensity   = 0f

    // Round 2 fix: shared safety-timeout for both mouth arms above -- see
    // ScoutExpressionPriority.shouldReleaseDeferredMouthExpression()'s doc
    // comment. Comfortably longer than MainActivity's own natural-pause
    // pre-dispatch delay (220-650ms max), so it never fires for a normal
    // dispatch; only guards the unanticipated case speech never starts.
    private val MOUTH_EXPRESSION_ARM_TIMEOUT_MS = 2000L

    private var browExpressionOwner: ScoutExpressionLayer  = ScoutExpressionLayer.NONE
    private var mouthExpressionOwner: ScoutExpressionLayer = ScoutExpressionLayer.NONE

    private var speechPhase = 0f
    private var mouthOpen   = 0f
    private var wavePhase   = 0f

    // Speaking Mouth v1 -- real speech-timed mouth impulses, driven by
    // MainActivity's TTS onRangeStart() callback via speechRangePulse()
    // above. speechRangeImpulse is the current decaying impulse magnitude
    // (0..1, decayed every frame in updateLife() regardless of whether a new
    // event arrived that frame -- see SPEECH_RANGE_DECAY_TAU_MS); it is
    // never additively stacked, only boosted up to its own ceiling by each
    // new event.
    //
    // speechRangeEstablished (PR #80 review correction) is a STICKY,
    // dispatch-scoped flag, deliberately NOT time-windowed: once a real
    // onRangeStart() event has been seen for the CURRENT speaking dispatch
    // it latches true and stays true for the rest of that same dispatch, no
    // matter how long a gap opens up before the next event -- an ordinary
    // spoken pause must read as a pause (speechRangeImpulse decays toward 0,
    // closing the mouth) rather than silently falling back to the unrelated
    // synthetic animation mid-utterance. It resets to false only at a
    // genuine dispatch boundary: setSpeaking(true) (a NEW dispatch starting)
    // or here/updateLife()'s !vSpeaking branch (this dispatch ending, by any
    // of natural completion/engine error/user interruption) -- so a TTS
    // engine that never calls onRangeStart at all for a given dispatch
    // simply never sets this true, and that dispatch stays on the existing
    // synthetic animation for its entire duration, exactly as before. See
    // ScoutSpeechRangeMouth.isRangeDriven()'s own doc comment for the full
    // reasoning.
    private var speechRangeImpulse     = 0f
    private var speechRangeEstablished = false
    // Character-length-to-magnitude mapping range -- deliberately similar in
    // overall scale to the existing synthetic fallback's own 0f..0.42f
    // target ceiling (see updateLife()'s speaking else-branch below), so the
    // two paths don't produce a visually jarring difference in how open the
    // mouth generally looks -- this PR is a timing fix, not a mouth-shape
    // redesign. SPEECH_RANGE_CHARS_NORMALIZER (~8 chars, roughly one short
    // word) is the range length at which the mapping saturates to its max.
    private val SPEECH_RANGE_PULSE_MIN       = 0.22f
    private val SPEECH_RANGE_PULSE_MAX       = 0.55f
    private val SPEECH_RANGE_CHARS_NORMALIZER = 8f
    // Continuous per-frame decay tau for speechRangeImpulse -- fast enough
    // that consecutive words/ranges visibly separate (typical spoken word
    // duration is a few hundred ms at Scout's configured speech rate) rather
    // than staying held open, producing a smooth fade between events instead
    // of the old fixed-period cycling. This is the ONLY mechanism that
    // returns the mouth toward rest between events now -- there is
    // deliberately no separate "gave up waiting, fall back" timer alongside
    // it (see speechRangeEstablished's own doc comment above for why).
    private val SPEECH_RANGE_DECAY_TAU_MS = 160f
    // How quickly the RENDERED mouthOpen chases speechRangeImpulse's target
    // -- deliberately close to the existing branches' own 38-42ms taus so
    // the three speaking-mouth paths (range-driven, dormant real-level,
    // synthetic fallback) all feel like the same mouth, just driven
    // differently.
    private val SPEECH_RANGE_MOUTH_TAU_MS = 45f

    private var breathPhase      = Random.nextFloat() * (2f * Math.PI).toFloat()
    private val BREATH_PERIOD_MS = 4200f
    private val BREATH_AMPLITUDE = 5f

    // Focus breathing — tiny iris oscillation only when Scout is locked onto a face,
    // saccade is quiet, and not mid-blink. Gives a living "held gaze" feel.
    // Amplitude is intentionally tiny (0.65f virtual px). Period ~3200ms.
    private var focusBreathPhase      = Random.nextFloat() * (2f * Math.PI).toFloat()
    private val FOCUS_BREATH_PERIOD_MS = 3200f
    private val FOCUS_BREATH_AMPLITUDE = 0.65f

    // Micro-tremor: sub-pixel constant random iris drift — eyes never completely still
    private var microTremorX       = 0f
    private var microTremorY       = 0f
    private var microTremorTargetX = 0f
    private var microTremorTargetY = 0f
    private var nextMicroTremorAt  = 0L

    // Double-blink: when set, nextBlinkAt fires quickly for a follow-up blink
    private var pendingDoubleBlink = false

    // Whole-face gentle drift — every feature moves together as one unit (the "alive" feel)
    private var faceIdleDriftX       = 0f
    private var faceIdleDriftY       = 0f
    private var faceIdleDriftTargetX = 0f
    private var faceIdleDriftTargetY = 0f
    private var nextFaceDriftAt      = 0L

    // Gaze-driven face drift — whole face slowly follows the gaze direction.
    // Eyes arrive at target first (spring physics); face lags ~900ms behind.
    // Creates a "head turning to look" feel instead of just eyeballs rolling.
    private var faceGazeDriftX = 0f
    private var faceGazeDriftY = 0f

    // Secondary blink motion: face dips + brows relax when a blink fires
    private var blinkDipY      = 0f
    private var blinkBrowRelax = 0f

    private var saccadeX       = 0f
    private var saccadeY       = 0f
    private var saccadeTargetX = 0f
    private var saccadeTargetY = 0f
    private var nextSaccadeAt  = 0L
    private var saccadeDecayMs = 800f

    private var idleDriftX        = 0f
    private var idleDriftY        = 0f
    private var idleDriftTargetX  = 0f
    private var idleDriftTargetY  = 0f
    private var nextDriftChangeAt = 0L

    // FIX 3: hysteresis gate for the audio wave.
    // Wave turns ON  when amplitude crosses WAVE_ON_THRESHOLD  (0.30).
    // Wave turns OFF when amplitude drops below WAVE_OFF_THRESHOLD (0.12).
    // Prevents noise flickering near the threshold from toggling the bars on/off.
    private var waveActive         = false
    private val WAVE_ON_THRESHOLD  = 0.18f
    private val WAVE_OFF_THRESHOLD = 0.06f

    private var idleMode   = false
    private val idleTickMs = 120L

    // ======================================================
    //  COLORS
    // ======================================================
    private val C_FACE_BG     = Color.parseColor("#1E2B38")
    private val C_SOCKET      = Color.parseColor("#38505f")
    private val C_EYE_WHITE   = Color.parseColor("#eeecea")
    private val C_IRIS_CENTER = Color.parseColor("#b8d0f0")
    private val C_IRIS_MID    = Color.parseColor("#2060b8")
    private val C_IRIS_OUTER  = Color.parseColor("#0a2a6e")
    private val C_IRIS_LIMBUS = Color.parseColor("#060c20")
    private val C_PUPIL       = Color.parseColor("#060408")
    private val C_BROW        = Color.parseColor("#9BBEFF")
    private val C_MOUTH_LINE  = Color.parseColor("#9BBEFF")
    private val C_MOUTH_DARK  = Color.parseColor("#1A2430")
    private val C_TONGUE      = Color.parseColor("#cc2a18")
    private val C_LID_BG      = Color.parseColor("#1E2B38")

    // ======================================================
    //  PAINTS — allocated once, never inside draw calls
    // ======================================================
    private val pBgFull = Paint().apply {
        color = C_FACE_BG
        style = Paint.Style.FILL
    }
    private val pSocket = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = C_SOCKET
        style = Paint.Style.FILL
    }
    private val pSocketShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = C_SOCKET
        style = Paint.Style.FILL
        maskFilter = BlurMaskFilter(22f, BlurMaskFilter.Blur.NORMAL)
    }
    private val pSocketRim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(55, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 20f
        maskFilter = BlurMaskFilter(16f, BlurMaskFilter.Blur.NORMAL)
    }
    private val pBrowRidgeShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val pWhite = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = C_EYE_WHITE
        style = Paint.Style.FILL
    }
    private val pScleraSheen = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pUpperShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = C_SOCKET
        style = Paint.Style.STROKE
        strokeWidth = 7f
    }
    private val pTopArc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 12, 14, 18)
        style = Paint.Style.STROKE
        strokeWidth = 11f
        strokeCap = Paint.Cap.ROUND
    }
    private val pLid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = C_LID_BG
        style = Paint.Style.FILL
    }
    private val irisBasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pSpoke        = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pLimbus       = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pPupil        = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = C_PUPIL
        style = Paint.Style.FILL
    }
    private val pPupilDepth = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pHighlight  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val pBrow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = C_BROW
        style = Paint.Style.STROKE
        strokeWidth = 20f
        strokeCap = Paint.Cap.ROUND
    }
    private val pMouthFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = C_MOUTH_DARK
        style = Paint.Style.FILL
    }
    private val pMouthLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = C_MOUTH_LINE
        style = Paint.Style.STROKE
        strokeWidth = 10f
        strokeCap = Paint.Cap.ROUND
    }
    private val pMouthRim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(60, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 18f
        maskFilter = BlurMaskFilter(14f, BlurMaskFilter.Blur.NORMAL)
    }
    private val pTongue = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = C_TONGUE
        style = Paint.Style.FILL
    }
    private val pTHighlight = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pTGroove = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(70, 80, 10, 10)
        style = Paint.Style.STROKE
        strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND
    }
    private val pTShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pDot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        maskFilter = BlurMaskFilter(3f, BlurMaskFilter.Blur.SOLID)
    }
    private val pWaveGlowDiamond = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00FFD0")
        style = Paint.Style.FILL
        alpha = 70
        maskFilter = BlurMaskFilter(14f, BlurMaskFilter.Blur.NORMAL)
    }
    private val pWaveCore = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    // FIX 4: paint for the idle listening dots (3 teal pulses when quiet)
    private val pIdleDot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00FFD0")
        style = Paint.Style.FILL
    }

    // 3-D depth: gradient lid, lash-line shadow, lower-lid rim light, socket AO
    private val pLidGrad = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pLashLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND
        maskFilter = BlurMaskFilter(3f, BlurMaskFilter.Blur.NORMAL)
    }
    private val pLowerLidRim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
        strokeCap = Paint.Cap.ROUND
    }
    private val pSocketAO = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    // ======================================================
    //  REUSABLE GEOMETRY — allocated once, never inside draw
    // ======================================================
    private val tmpRect         = RectF()
    private val tmpPath         = Path()
    private val wavePath        = Path()
    private val tmpLeftSocket   = RectF()
    private val tmpRightSocket  = RectF()
    private val tmpLeftEye      = RectF()
    private val tmpRightEye     = RectF()
    private val tmpLidRect      = RectF()
    private val tmpTopArcRect   = RectF()
    private val tmpMouthRect    = RectF()
    private val tmpTongueRect   = RectF()
    private val tmpParallaxRect = RectF()

    private class EyeGeom {
        val socketRect = RectF()
        val eyeRect    = RectF()
        var cx         = 0f
        var cy         = 0f
    }
    private val leftGeom  = EyeGeom()
    private val rightGeom = EyeGeom()

    // ======================================================
    //  onDraw
    // ======================================================
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val now  = System.currentTimeMillis()
        val dtMs = if (lastNow == 0L) 16f else (now - lastNow).coerceIn(1L, 40L).toFloat()
        lastNow  = now

        updateLife(dtMs, now)

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), pBgFull)

        val sx = width  / VW
        val sy = height / VH
        val s  = min(sx, sy)
        val ox = (width  - VW * s) * 0.5f
        val oy = (height - VH * s) * 0.5f

        canvas.save()
        canvas.translate(ox, oy)
        canvas.scale(s, s)

        drawFace(canvas, now)

        canvas.restore()
        scheduleNextFrame(now)
    }

    // ======================================================
    //  DRAW FACE
    // ======================================================
    private fun drawFace(c: Canvas, now: Long) {
        val breathOffset  = sin(breathPhase) * BREATH_AMPLITUDE
        val breathOffsetX = sin(breathPhase * 0.618f + 1.1f) * 1.5f  // gentle X sway on a different phase
        val faceCy = VH * 0.46f + breathOffset  + faceIdleDriftY + blinkDipY + faceGazeDriftY
        val faceCx = VW * 0.50f + breathOffsetX + faceIdleDriftX + faceGazeDriftX

        val eyeW      = 510f
        val eyeH      = 394f
        val eyeSep    = 340f
        val socketPad = 24f

        val leftCx  = faceCx - eyeSep
        val rightCx = faceCx + eyeSep

        tmpLeftSocket.set(
            leftCx - eyeW * 0.5f - socketPad,
            faceCy - eyeH * 0.5f - socketPad * 1.3f,
            leftCx + eyeW * 0.5f + socketPad,
            faceCy + eyeH * 0.5f + socketPad * 0.5f
        )
        tmpRightSocket.set(
            rightCx - eyeW * 0.5f - socketPad,
            faceCy - eyeH * 0.5f - socketPad * 1.3f,
            rightCx + eyeW * 0.5f + socketPad,
            faceCy + eyeH * 0.5f + socketPad * 0.5f
        )
        tmpLeftEye.set(
            leftCx - eyeW * 0.5f,
            faceCy - eyeH * 0.5f,
            leftCx + eyeW * 0.5f,
            faceCy + eyeH * 0.5f
        )
        tmpRightEye.set(
            rightCx - eyeW * 0.5f,
            faceCy - eyeH * 0.5f,
            rightCx + eyeW * 0.5f,
            faceCy + eyeH * 0.5f
        )

        leftGeom.socketRect.set(tmpLeftSocket)
        leftGeom.eyeRect.set(tmpLeftEye)
        leftGeom.cx = leftCx
        leftGeom.cy = faceCy

        rightGeom.socketRect.set(tmpRightSocket)
        rightGeom.eyeRect.set(tmpRightEye)
        rightGeom.cx = rightCx
        rightGeom.cy = faceCy

        // Per-eye lower lid: base (speech/breath) + expression layer + blink rise
        val lowerL = (lowerLidSmooth + lowerLidExprL + blinkL * 6f).coerceIn(0f, 20f)
        val lowerR = (lowerLidSmooth + lowerLidExprR + blinkR * 6f).coerceIn(0f, 20f)
        // Emotional Face v1 -- ATTENTIVE's "more open" eye cue, symmetric
        // like its brow lift. Zero whenever ATTENTIVE doesn't own the brow
        // layer this frame (any higher-priority expression, or THINKING,
        // forces browExpressionOwner away from ATTENTIVE, which zeroes this
        // too -- no separate suppression check needed here). Expression
        // Visibility v2: LISTENING no longer forces this away -- ATTENTIVE
        // (and this eye cue with it) may now render while Scout is
        // listening, which is the state it exists to describe.
        val attentiveEyeOpen = if (browExpressionOwner == ScoutExpressionLayer.ATTENTIVE) {
            attentiveSmooth * ATTENTIVE_EYE_OPEN_MAX
        } else 0f
        drawEye(c, leftGeom,  blinkL, lidDroopL,                lowerL, now, attentiveEyeOpen)
        drawEye(c, rightGeom, blinkR, lidDroopR + thinkLidSmooth, lowerR, now, attentiveEyeOpen)
        drawBrow(c, leftGeom, side = -1)
        drawBrow(c, rightGeom, side = 1)
        drawMouth(c, faceCx, faceCy + 240f)
        drawStatusDot(c, now)
    }

    // ======================================================
    //  EYE
    // ======================================================
    private fun drawEye(c: Canvas, eye: EyeGeom, blinkAmount: Float,
                        lidDroop: Float, lowerLid: Float, now: Long,
                        eyeOpenBoost: Float = 0f) {

        val pxSocket = lookX * 0.03f
        val pySocket = lookY * 0.03f
        val pxWhite  = lookX * 0.07f
        val pyWhite  = lookY * 0.07f

        tmpParallaxRect.set(
            eye.socketRect.left + pxSocket,
            eye.socketRect.top + pySocket,
            eye.socketRect.right + pxSocket,
            eye.socketRect.bottom + pySocket
        )
        c.drawOval(tmpParallaxRect, pSocketShadow)
        c.drawOval(tmpParallaxRect, pSocket)

        pBrowRidgeShadow.shader = LinearGradient(
            tmpParallaxRect.centerX(),
            tmpParallaxRect.top,
            tmpParallaxRect.centerX(),
            tmpParallaxRect.top + tmpParallaxRect.height() * 0.45f,
            intArrayOf(Color.argb(90, 5, 8, 15), Color.TRANSPARENT),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawOval(tmpParallaxRect, pBrowRidgeShadow)
        pBrowRidgeShadow.shader = null

        c.drawOval(tmpParallaxRect, pSocketRim)

        tmpParallaxRect.set(
            eye.eyeRect.left + pxWhite,
            eye.eyeRect.top + pyWhite,
            eye.eyeRect.right + pxWhite,
            eye.eyeRect.bottom + pyWhite
        )

        c.save()
        tmpPath.reset()
        tmpPath.addOval(tmpParallaxRect, Path.Direction.CW)
        c.clipPath(tmpPath)

        c.drawOval(tmpParallaxRect, pWhite)

        pScleraSheen.shader = RadialGradient(
            tmpParallaxRect.centerX(),
            tmpParallaxRect.top + tmpParallaxRect.height() * 0.18f,
            tmpParallaxRect.width() * 0.55f,
            intArrayOf(Color.argb(55, 255, 255, 255), Color.TRANSPARENT),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawOval(tmpParallaxRect, pScleraSheen)
        pScleraSheen.shader = null

        // Ambient occlusion ring: darkens sclera perimeter → socket-wall shadow, eye looks recessed
        pSocketAO.shader = RadialGradient(
            tmpParallaxRect.centerX(),
            tmpParallaxRect.centerY() - tmpParallaxRect.height() * 0.06f,
            tmpParallaxRect.width() * 0.56f,
            intArrayOf(Color.TRANSPARENT, Color.argb(95, 3, 6, 14)),
            floatArrayOf(0.40f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawOval(tmpParallaxRect, pSocketAO)
        pSocketAO.shader = null

        val irisR  = min(eye.eyeRect.width(), eye.eyeRect.height()) * 0.34f
        val isLeft = eye.cx < VW * 0.5f
        val sign   = if (isLeft) 1f else -1f

        val vergenceAdd = vergenceSmooth * VERGENCE_MAX_BIAS
// Tiny inward pull only. This prevents the "cross-eyed" center pull from stealing left travel.
        val inwardBias = sign * (2f + listeningBiasSmooth * 0.18f + vergenceAdd * 0.18f)

// Focus breathing — tiny sine applied only when Scout is locked on a face,
// saccade has settled, and eyes are not mid-blink.
        val saccadeQuiet = abs(saccadeX) < 0.35f && abs(saccadeY) < 0.35f
        val lockedOn = (abs(vLookTargetX) > 1f || abs(vLookTargetY) > 1f) &&
                !blinking && saccadeQuiet
        val focusBreath = if (lockedOn) {
            sin(focusBreathPhase.toDouble()).toFloat() * FOCUS_BREATH_AMPLITUDE
        } else 0f

// Wider, more obvious travel.
// Slightly wider X travel, a little more visible Y travel.
        val irisCx = eye.cx + inwardBias + lookX * 1.10f + saccadeX + focusBreath + microTremorX
        val irisCy = eye.cy + lookY * 0.88f + saccadeY + thinkGazeY + focusBreath * 0.35f + microTremorY

        drawIris(c, irisCx, irisCy, irisR, lookX, lookY)

        pUpperShadow.shader = LinearGradient(
            eye.cx,
            tmpParallaxRect.top,
            eye.cx,
            tmpParallaxRect.top + tmpParallaxRect.height() * 0.38f,
            intArrayOf(Color.argb(160, 10, 15, 25), Color.TRANSPARENT),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawOval(tmpParallaxRect, pUpperShadow)
        pUpperShadow.shader = null

        val b = (blinkAmount + lidDroop - eyeOpenBoost).coerceIn(0f, 1f)
        if (b > 0.01f) {
            val lidBottom = tmpParallaxRect.top + tmpParallaxRect.height() * b
            tmpLidRect.set(
                tmpParallaxRect.left - 4f,
                tmpParallaxRect.top - 8f,
                tmpParallaxRect.right + 4f,
                lidBottom
            )
            // Gradient lid: surface-catch-light at top → face-bg → shadow crease at edge
            val gradBot = lidBottom.coerceAtLeast(tmpParallaxRect.top + 2f)
            pLidGrad.shader = LinearGradient(
                eye.cx, tmpParallaxRect.top,
                eye.cx, gradBot,
                intArrayOf(
                    Color.parseColor("#2C3E50"),
                    Color.parseColor("#1E2B38"),
                    Color.argb(255, 10, 16, 24)
                ),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
            c.drawRect(tmpLidRect, pLidGrad)
            pLidGrad.shader = null
            // Dome highlight: radial soft spot near top-center → lid reads as convex/rounded bump
            val lidSpan = (lidBottom - tmpParallaxRect.top).coerceAtLeast(4f)
            val domeAlpha = (b * 58f).toInt().coerceIn(0, 58)
            if (domeAlpha > 6) {
                pLidGrad.shader = RadialGradient(
                    eye.cx,
                    tmpParallaxRect.top + lidSpan * 0.20f,
                    lidSpan * 1.05f,
                    intArrayOf(Color.argb(domeAlpha, 95, 135, 172), Color.TRANSPARENT),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP
                )
                c.drawRect(tmpLidRect, pLidGrad)
                pLidGrad.shader = null
            }
            // Lash-line shadow at the lid's closing edge
            if (b > 0.06f) {
                pLashLine.color = Color.argb((b * 155).toInt().coerceIn(0, 155), 6, 10, 18)
                // During thinking, right eye lash-line angles down toward inner (nose) corner
                val lashAngle = if (!isLeft) thinkLidSmooth * 22f else 0f
                c.drawLine(
                    tmpParallaxRect.left + 16f, lidBottom + lashAngle,
                    tmpParallaxRect.right - 16f, lidBottom,
                    pLashLine
                )
            }
        }

        if (lowerLid > 0.5f) {
            val lowerLidTop = tmpParallaxRect.bottom - lowerLid
            tmpLidRect.set(
                tmpParallaxRect.left - 4f,
                lowerLidTop,
                tmpParallaxRect.right + 4f,
                tmpParallaxRect.bottom + 8f
            )
            c.drawRect(tmpLidRect, pLid)
        }

        // Lower lid rim: subtle cool-light arc — lower lid catching ambient light
        val rimAlpha = ((1f - b) * 48f).toInt().coerceIn(0, 48)
        if (rimAlpha > 4) {
            pLowerLidRim.shader = LinearGradient(
                tmpParallaxRect.left, tmpParallaxRect.bottom - 4f,
                tmpParallaxRect.right, tmpParallaxRect.bottom - 4f,
                intArrayOf(Color.TRANSPARENT, Color.argb(rimAlpha, 185, 215, 255), Color.TRANSPARENT),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            tmpLidRect.set(
                tmpParallaxRect.left,
                tmpParallaxRect.centerY(),
                tmpParallaxRect.right,
                tmpParallaxRect.bottom + 6f
            )
            c.drawArc(tmpLidRect, 0f, 180f, false, pLowerLidRim)
            pLowerLidRim.shader = null
        }

        c.restore()

        c.drawOval(eye.eyeRect, pRing)
        tmpTopArcRect.set(
            eye.eyeRect.left - 6f,
            eye.eyeRect.top - 6f,
            eye.eyeRect.right + 6f,
            eye.eyeRect.bottom + 6f
        )
        c.drawArc(tmpTopArcRect, 200f, 140f, false, pTopArc)
    }

    // ======================================================
    //  IRIS
    // ======================================================
    private fun drawIris(c: Canvas, cx: Float, cy: Float, r: Float,
                         gazeLX: Float, gazeLY: Float) {

        val lightBiasX = -gazeLX * 0.04f
        val lightBiasY = -gazeLY * 0.04f
        irisBasePaint.shader = RadialGradient(
            cx + lightBiasX, cy + lightBiasY, r,
            intArrayOf(C_IRIS_CENTER, C_IRIS_MID, C_IRIS_OUTER, C_IRIS_LIMBUS),
            floatArrayOf(0f, 0.30f, 0.75f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, r, irisBasePaint)
        irisBasePaint.shader = null

        val spokeCount = 28
        for (i in 0 until spokeCount) {
            val startAngle = i * (360f / spokeCount)
            val sweep      = (360f / spokeCount) * 0.58f
            pSpoke.color   = if (i % 2 == 0) {
                Color.argb(55, 180, 210, 255)
            } else {
                Color.argb(30, 10, 30, 80)
            }
            tmpPath.reset()
            tmpPath.moveTo(cx, cy)
            tmpRect.set(cx - r * 0.96f, cy - r * 0.96f, cx + r * 0.96f, cy + r * 0.96f)
            tmpPath.arcTo(tmpRect, startAngle, sweep)
            tmpPath.close()
            c.drawPath(tmpPath, pSpoke)
        }

        pLimbus.shader = RadialGradient(
            cx, cy, r,
            intArrayOf(Color.TRANSPARENT, Color.argb(200, 4, 8, 20)),
            floatArrayOf(0.68f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, r, pLimbus)
        pLimbus.shader = null

        val pupilR = r * 0.44f
        c.drawCircle(cx, cy, pupilR, pPupil)

        pPupilDepth.shader = RadialGradient(
            cx, cy, pupilR,
            intArrayOf(Color.argb(50, 60, 80, 120), Color.TRANSPARENT),
            floatArrayOf(0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, pupilR, pPupilDepth)
        pPupilDepth.shader = null

        val h1R = r * 0.40f
        val h1x = cx - r * 0.22f - gazeLX * 0.06f
        val h1y = cy - r * 0.26f - gazeLY * 0.06f
        pHighlight.shader = RadialGradient(
            h1x, h1y, h1R,
            intArrayOf(
                Color.argb(240, 255, 255, 255),
                Color.argb(100, 255, 255, 255),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.38f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawCircle(h1x, h1y, h1R, pHighlight)

        val h2R = r * 0.15f
        val h2x = cx + r * 0.30f
        val h2y = cy + r * 0.24f
        pHighlight.shader = RadialGradient(
            h2x, h2y, h2R,
            intArrayOf(Color.argb(200, 255, 255, 255), Color.TRANSPARENT),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawCircle(h2x, h2y, h2R, pHighlight)

        pHighlight.shader = null
    }

    // ======================================================
    //  EYEBROW
    // ======================================================
    private fun drawBrow(c: Canvas, eye: EyeGeom, side: Int) {

        val listeningLift = if (vListening) 20f else 0f
        val browAsym = if (side < 0) 30f else 4f
        val thinkingLift = if (vThinking) {
            if (side > 0) 38f + sin(System.currentTimeMillis() / 900.0).toFloat() * 3f else 0f
        } else 0f
        val tiredDrop     = if (vBatteryPct < 20) 8f else 0f

        val speechBrowLift = (speechSmooth * 6f).coerceIn(0f, 6f)

        // Emotional Face v1: exactly one of {UNCERTAIN, PLEASED, NOTICE,
        // ATTENTIVE} contributes here, per browExpressionOwner (resolved
        // once per frame in updateLife() via ScoutExpressionPriority) --
        // never a sum of several at once, which is what let ambient/pulse
        // terms simply stack before. UNCERTAIN is the one asymmetric case
        // (left brow only, side < 0); PLEASED/NOTICE/ATTENTIVE all lift both
        // brows equally, same as PR #73's original symmetric notice pulse.
        val expressionBrowLift = when (browExpressionOwner) {
            ScoutExpressionLayer.UNCERTAIN -> if (side < 0) uncertainPulse else 0f
            ScoutExpressionLayer.PLEASED   -> pleasedPulse
            ScoutExpressionLayer.NOTICE    -> noticePulse
            ScoutExpressionLayer.ATTENTIVE -> attentiveSmooth * ATTENTIVE_BROW_LIFT_PX
            ScoutExpressionLayer.NONE      -> 0f
        }

        // Expression Visibility v2 -- shape contributions beyond a flat
        // vertical lift, each scaled by its own expression's current
        // 0..1 progress (so they rise/decay in lockstep with the lift
        // above, never a separate clock) and zero whenever that expression
        // doesn't currently own the brow. Applied to y1/y2/midY below,
        // alongside (never instead of) the existing THINKING-only
        // thinkInward/thinkInnerLift/thinkTilt shape terms, using the same
        // quadTo() curve those already prove works.
        //
        // ATTENTIVE: both brows' OUTER end lifts a little further than a
        // flat lift alone -- symmetric, applied to whichever endpoint is
        // this side's own outer one (y1 for the left brow, y2 for the
        // right, matching the x1/x2 "outer"/"inner" convention THINKING's
        // own thinkTilt/thinkInnerLift already establish below).
        val attentiveArch = if (browExpressionOwner == ScoutExpressionLayer.ATTENTIVE)
            attentiveSmooth * ATTENTIVE_OUTER_ARCH_PX else 0f

        // PLEASED: both brows arch gently upward in the middle (the curve's
        // own midY control point, not an endpoint) -- a warm, rounded shape,
        // identical contribution on both sides since it's the shared
        // midpoint, not a per-endpoint term.
        val pleasedArch = if (browExpressionOwner == ScoutExpressionLayer.PLEASED)
            (pleasedPulse / PLEASED_BROW_LIFT_PX) * PLEASED_ARCH_PX else 0f

        // UNCERTAIN: only the already-raised left brow's OUTER end (y1)
        // tilts up further -- deliberately zero on the right brow (side > 0)
        // and zero unless UNCERTAIN currently owns the layer, so this can
        // never contribute to a brow that isn't already the raised one.
        val uncertainTilt = if (browExpressionOwner == ScoutExpressionLayer.UNCERTAIN && side < 0)
            (uncertainPulse / UNCERTAIN_BROW_PRIMARY_PX) * UNCERTAIN_OUTER_TILT_PX else 0f

        // Silent Arrival Acknowledgment v1 (now folded into expressionBrowLift
        // above when NOTICE owns the layer): applied symmetrically to both
        // brows (unlike thinking's deliberately asymmetric curious arch) --
        // a clean, even lift reads as "noticed/interested," not "curious" or
        // "surprised." Subtracted the same way listeningLift/thinkingLift
        // are, since this coordinate system is Y-down (raising a brow means
        // decreasing browCy).
        val browCy = eye.socketRect.top - 38f - listeningLift - thinkingLift + tiredDrop +
                browMicroY + blinkBrowRelax - speechBrowLift - expressionBrowLift

        val bw   = eye.eyeRect.width() * 0.40f
        val tilt = 22f
        val x1   = eye.cx - bw
        val x2   = eye.cx + bw

        // Curious look: inner edges raise slightly, outer edges relax → questioning arch
        val thinkInward = if (vThinking) {
            if (side < 0) 2f else -2f
        } else 0f
        val thinkInnerLift = if (vThinking && side < 0) 6f else 0f

        // thinkTilt < 0 on right brow: inner end (x1) goes UP → curious arch
        val thinkTilt = if (vThinking && side > 0) -18f else 0f

        val y1: Float
        val y2: Float
        if (side < 0) {
            // y1 is this (left) brow's OUTER end -- attentiveArch and
            // uncertainTilt both apply here (Expression Visibility v2).
            y1 = browCy + tilt * 0.5f - attentiveArch - uncertainTilt
            y2 = browCy - tilt * 0.5f - thinkInnerLift  // inner edge of left brow rises when curious
        } else {
            y1 = browCy - tilt * 0.5f + thinkTilt  // inner end of right brow rises (thinkTilt = -10f)
            // y2 is this (right) brow's OUTER end -- attentiveArch applies
            // here to match the left brow's own outer-end contribution
            // above, keeping ATTENTIVE's arch symmetric across both brows.
            y2 = browCy + tilt * 0.5f - attentiveArch
        }

        val midX = eye.cx + thinkInward
        // pleasedArch bows the curve's shared control point further up,
        // independent of side -- applies identically whichever brow this
        // call is drawing.
        val midY = (y1 + y2) * 0.5f - 28f - pleasedArch

        tmpPath.reset()
        tmpPath.moveTo(x1, y1)
        tmpPath.quadTo(midX, midY, x2, y2)
// subtle shadow under brow (depth)
        pBrowRidgeShadow.shader = LinearGradient(
            eye.cx,
            browCy,
            eye.cx,
            browCy + 28f,
            intArrayOf(Color.argb(120, 20, 35, 48), Color.TRANSPARENT),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawPath(tmpPath, pBrowRidgeShadow)
        pBrowRidgeShadow.shader = null

// actual brow line
        c.drawPath(tmpPath, pBrow)
    }

    // ======================================================
    //  MOUTH
    // ======================================================
    private fun drawMouth(c: Canvas, cx: Float, cy: Float) {
        val open = mouthOpen.coerceIn(0f, 1f)

        if (open > 0.06f) {
            val mw = 92f + 110f * open
            val mh = 44f + 85f * open
            tmpMouthRect.set(cx - mw, cy - mh * 0.45f, cx + mw, cy + mh * 0.65f)

            c.drawOval(tmpMouthRect, pMouthRim)
            c.drawOval(tmpMouthRect, pMouthFill)

            if (open > 0.28f) {
                val tongueW = mw * 0.68f
                val tt      = cy + mh * 0.10f
                val tb      = cy + mh * 0.55f
                tmpTongueRect.set(cx - tongueW, tt, cx + tongueW, tb + mh * 0.08f)

                c.drawOval(tmpTongueRect, pTongue)

                pTHighlight.shader = RadialGradient(
                    cx, tt + (tb - tt) * 0.25f, tongueW * 0.6f,
                    intArrayOf(Color.argb(140, 255, 160, 140), Color.TRANSPARENT),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP
                )
                c.drawOval(tmpTongueRect, pTHighlight)
                c.drawLine(cx, tt + 6f, cx, tb - 6f, pTGroove)

                pTShadow.shader = RadialGradient(
                    cx, tt + (tb - tt) * 0.5f, tongueW,
                    intArrayOf(Color.TRANSPARENT, Color.argb(90, 60, 0, 0)),
                    floatArrayOf(0.55f, 1f),
                    Shader.TileMode.CLAMP
                )
                c.drawOval(tmpTongueRect, pTShadow)
            }

            pMouthLine.strokeWidth = 14f
            c.drawOval(tmpMouthRect, pMouthLine)

            pTHighlight.shader = null
            pTShadow.shader    = null

        } else {
            val mw    = 80f
            val ctrY  = if (vThinking) cy + 11f else cy + 18f
            val ctrXo = mw * 0.44f
            // Emotional Face v1: PLEASED/UNCERTAIN corner targets, owned
            // exclusively per mouthExpressionOwner (resolved once per frame
            // in updateLife() -- already NONE whenever speaking/thinking/
            // listening holds the mouth, so the existing vThinking-aware
            // fallback below is untouched in every case it used to apply).
            // Both are a real, deliberately larger corner-height swing than
            // the ~2-6px difference the existing thinking/default states
            // show -- see PLEASED_MOUTH_CORNER_PX/UNCERTAIN_MOUTH_*_PX's own
            // comments for the reasoning. Scaled by each expression's own
            // independent pleasedMouthIntensity/uncertainMouthIntensity
            // (0..1, round 2 fix) rather than the brow pulse directly -- see
            // that field's doc comment for why the two must not share a
            // clock.
            val corYL: Float
            val corYR: Float
            when (mouthExpressionOwner) {
                ScoutExpressionLayer.PLEASED -> {
                    val lift = pleasedMouthIntensity * PLEASED_MOUTH_CORNER_PX
                    corYL = cy + 2f - lift
                    corYR = cy + 2f - lift
                }
                ScoutExpressionLayer.UNCERTAIN -> {
                    corYL = cy + 2f + UNCERTAIN_MOUTH_PRIMARY_PX * uncertainMouthIntensity
                    corYR = cy + 2f + UNCERTAIN_MOUTH_SECONDARY_PX * uncertainMouthIntensity
                }
                else -> {
                    corYL = if (vThinking) cy - 2f else cy + 2f   // both corners rise → warm smile
                    corYR = if (vThinking) cy - 4f else cy + 2f   // right corner slightly higher
                }
            }
            tmpPath.reset()
            tmpPath.moveTo(cx - mw, corYL)
            tmpPath.quadTo(cx - ctrXo, ctrY, cx, ctrY * 0.72f + cy * 0.28f)
            tmpPath.quadTo(cx + ctrXo, ctrY, cx + mw, corYR)
            pMouthLine.strokeWidth = 10f
            c.drawPath(tmpPath, pMouthLine)
        }
    }

    // ======================================================
    //  STATUS DOT + AUDIO WAVE DIAMONDS
    // ======================================================
    private fun drawStatusDot(c: Canvas, now: Long) {
        val x = 34f
        val y = 34f

        val pulse    = ((sin((now % 6000L) / 6000f * (2f * Math.PI).toFloat()) + 1f) * 0.5f)
        val dotAlpha = when {
            vDownloading || vSpeaking || vListening || vThinking -> 255
            else -> (110 + pulse * 90).roundToInt()
        }
        val dotR = when {
            vDownloading -> 11f
            vSpeaking    -> 10f
            else         -> 8.5f
        }
        pDot.alpha = dotAlpha
        c.drawCircle(x, y, dotR, pDot)

        if (vListening || vSpeaking) {
            val baseX     = x + 30f
            val baseY     = y
            val amplitude = if (vSpeaking) speechSmooth else micSmooth

            if (waveActive) {
                // ── Active signal: draw wave bars ────────────────────────────
                // FIX 3 continued: calmer bars.
                // Max height 68px → 52px. Sine contribution 28px → 18px.
                // Bars stay small and readable rather than spiking on minor noise.
                val bars    = 22
                val spacing = 12f
                wavePhase  += 0.08f

                for (i in 0 until bars) {
                    val centreWeight = 1f - abs(i - bars * 0.5f) / (bars * 0.5f)
                    val sine = (sin((wavePhase + i * 0.45f).toDouble()) * 0.5 + 0.5).toFloat()
                    val hh   = ((6f + sine * 18f + amplitude * 30f) *
                            (0.5f + centreWeight * 0.5f)).coerceIn(4f, 52f)

                    val cxBar = baseX + i * spacing
                    val top   = baseY - hh
                    val bot   = baseY + hh
                    val hw    = (hh * 0.22f).coerceIn(2.5f, 7f)

                    wavePath.reset()
                    wavePath.moveTo(cxBar, top)
                    wavePath.lineTo(cxBar + hw, baseY)
                    wavePath.lineTo(cxBar, bot)
                    wavePath.lineTo(cxBar - hw, baseY)
                    wavePath.close()

                    c.drawPath(wavePath, pWaveGlowDiamond)

                    pWaveCore.shader = LinearGradient(
                        cxBar, top, cxBar, bot,
                        intArrayOf(
                            Color.argb(180, 180, 255, 240),
                            Color.argb(255, 0, 255, 200),
                            Color.argb(255, 0, 220, 160),
                            Color.argb(180, 180, 255, 240)
                        ),
                        floatArrayOf(0f, 0.35f, 0.65f, 1f),
                        Shader.TileMode.CLAMP
                    )
                    c.drawPath(wavePath, pWaveCore)
                    pWaveCore.shader = null
                }

            } else if (vListening) {
                // FIX 4: idle listening indicator — 3 teal dots that pulse in sequence.
                // Shown whenever vListening is true but the mic is quiet.
                // Each dot is offset by 1/3 of the cycle so they chase left-to-right.
                val dotSpacing  = 18f
                val dotRadius   = 5f
                val phasePeriod = 1800f   // ms for one full pulse cycle
                for (i in 0 until 3) {
                    val phaseOffset = i * (phasePeriod / 3f)
                    val t           = ((now + phaseOffset) % phasePeriod.toLong()) / phasePeriod
                    // Sine mapped 0..1 → alpha range 90..255
                    val brightness  = (sin(t * 2.0 * Math.PI).toFloat() * 0.5f + 0.5f)
                    val alpha       = (90 + brightness * 165).roundToInt().coerceIn(0, 255)
                    val dotCx       = baseX + i * dotSpacing + dotRadius
                    pIdleDot.alpha  = alpha
                    c.drawCircle(dotCx, baseY, dotRadius, pIdleDot)
                }
            }
        }
    }

    // ======================================================
    //  LIFE UPDATE — runs every frame, zero allocations
    // ======================================================
    private fun updateLife(dtMs: Float, now: Long) {

        // FIX 3: asymmetric mic smoothing.
        // Slow attack (tau 220ms) — quiet noise spikes don't rush up to the on-threshold.
        // Fast decay  (tau 90ms)  — wave drops quickly when room goes quiet again.
        val micTarget = vMicLevel
        val micTau    = if (micTarget > micSmooth) 220f else 90f
        micSmooth    += (micTarget - micSmooth) * smoothAlpha(dtMs, micTau)

        speechSmooth += (vSpeechLevel - speechSmooth) * smoothAlpha(dtMs, 55f)

        // FIX 3: hysteresis gate.
        val amplitude = if (vSpeaking) speechSmooth else micSmooth
        if (!waveActive && amplitude > WAVE_ON_THRESHOLD)  waveActive = true
        if ( waveActive && amplitude < WAVE_OFF_THRESHOLD) waveActive = false

        // FIX 1: smooth the listening inward-bias.
        // Target is 10f while vListening, 0f otherwise. Tau 160ms.
        val listeningBiasTarget = if (vListening) 10f else 0f
        listeningBiasSmooth += (listeningBiasTarget - listeningBiasSmooth) *
                smoothAlpha(dtMs, 160f)

        breathPhase += dtMs / BREATH_PERIOD_MS * (2f * Math.PI).toFloat()
        if (breathPhase > (2f * Math.PI).toFloat()) {
            breathPhase -= (2f * Math.PI).toFloat()
        }

        // Focus breathing phase — always advances; gating happens in drawEye.
        focusBreathPhase += dtMs / FOCUS_BREATH_PERIOD_MS * (2f * Math.PI).toFloat()
        if (focusBreathPhase > (2f * Math.PI).toFloat()) {
            focusBreathPhase -= (2f * Math.PI).toFloat()
        }

        val targetL = if (vBatteryPct < 20) lidTiredL else 0.07f
        val targetR = if (vBatteryPct < 20) lidTiredR else 0.07f
        lidDroopL += (targetL - lidDroopL) * smoothAlpha(dtMs, lidTauL)
        lidDroopR += (targetR - lidDroopR) * smoothAlpha(dtMs, lidTauR)
        // Thinking lid: right-eye hint of concentration (7% — whisper, not focal point)
        val thinkLidTarget = if (vThinking) 0.07f else 0f
        thinkLidSmooth += (thinkLidTarget - thinkLidSmooth) * smoothAlpha(dtMs, 350f)

        val smileLift = (mouthOpen * 14f).coerceIn(0f, 10f)
        val lowerLidTarget = when {
            vThinking  -> smileLift
            vListening -> maxOf(8f, smileLift)
            else       -> smileLift
        }
        lowerLidSmooth += (lowerLidTarget - lowerLidSmooth) * smoothAlpha(dtMs, 200f)

        val vergenceTarget = if (vThinking) maxOf(vVergence, 0.3f) else vVergence
        vergenceSmooth += (vergenceTarget - vergenceSmooth) * smoothAlpha(dtMs, 280f)

        if (vThinking) {
            thinkEndedAt = 0L
            if (nextThinkGlanceAt == 0L) {
                thinkGlanceActive = true
                // One side chosen per glance episode, not continuously varied within
                // it -- previously hardcoded to always the same side (left), with no
                // documented reason found in history. Magnitude range unchanged.
                val thinkGlanceSide = if (Random.nextBoolean()) 1f else -1f
                thinkGlanceSideX  = thinkGlanceSide * (35f + Random.nextFloat() * 15f)
                nextThinkGlanceAt = 1L
            }
        } else {
            if (thinkGlanceActive) thinkEndedAt = now  // record moment thinking stopped
            thinkGlanceActive = false; nextThinkGlanceAt = 0L; thinkGlanceSideX = 0f
        }
        // Micro-drift: slow organic wandering around the upper-left-or-right anchor while thinking
        val thinkMicroX = if (thinkGlanceActive) sin(now / 1400.0).toFloat() * 6f else 0f
        val thinkMicroY = if (thinkGlanceActive) cos(now / 1100.0).toFloat() * 4f else 0f
        val thinkGazeTargetY = if (thinkGlanceActive) -20f + thinkMicroY else 0f
        val thinkGazeTargetX = if (thinkGlanceActive) thinkGlanceSideX + thinkMicroX else 0f
        // Reconnect snap: fast tau for ~400ms after thinking ends so eyes return to user visibly
        val reconnecting = !vThinking && thinkEndedAt > 0L && (now - thinkEndedAt) < 400L
        val thinkGazeTau = if (reconnecting) 80f else 350f
        thinkGazeY += (thinkGazeTargetY - thinkGazeY) * smoothAlpha(dtMs, thinkGazeTau)
        thinkGazeX += (thinkGazeTargetX - thinkGazeX) * smoothAlpha(dtMs, thinkGazeTau)

        if (!vListening && !vThinking) {
            if (now >= nextBrowMicroAt) {
                browMicroTarget = (Random.nextFloat() * 2f - 1f) * 4f
                nextBrowMicroAt = now + Random.nextLong(3000, 8000)
            }
            browMicroY += (browMicroTarget - browMicroY) * smoothAlpha(dtMs, 1200f)
        } else {
            browMicroY += (0f - browMicroY) * smoothAlpha(dtMs, 300f)
        }

        // Silent Arrival Acknowledgment v1. Rise/hold/decay: target holds at
        // the full lift until noticePulseUntilMs, then drops to 0 -- rising
        // faster (220ms tau) than it decays (480ms tau) so it reads as a
        // deliberate, considered raise-and-settle rather than a startled
        // snap or a slow ambient drift. noticePulseUntilMs == 0L (no pulse
        // ever requested, or fully finished) behaves identically to "already
        // past the hold window" -- target is 0f either way.
        val noticePulseTarget = if (now < noticePulseUntilMs) NOTICE_PULSE_LIFT_PX else 0f
        val noticePulseTau = if (noticePulseTarget > noticePulse) NOTICE_PULSE_RISE_TAU_MS else NOTICE_PULSE_DECAY_TAU_MS
        noticePulse += (noticePulseTarget - noticePulse) * smoothAlpha(dtMs, noticePulseTau)

        // Emotional Face v1 -- PLEASED/UNCERTAIN pulses. Same rise/hold/decay
        // shape as noticePulse above, each at its own magnitude/timing (see
        // the constants' own doc comments) so the three read as distinct
        // events rather than variations on one animation.
        val pleasedPulseTarget = if (now < pleasedPulseUntilMs) PLEASED_BROW_LIFT_PX else 0f
        val pleasedPulseTau = if (pleasedPulseTarget > pleasedPulse) PLEASED_RISE_TAU_MS else PLEASED_DECAY_TAU_MS
        pleasedPulse += (pleasedPulseTarget - pleasedPulse) * smoothAlpha(dtMs, pleasedPulseTau)

        val uncertainPulseTarget = if (now < uncertainPulseUntilMs) UNCERTAIN_BROW_PRIMARY_PX else 0f
        val uncertainPulseTau = if (uncertainPulseTarget > uncertainPulse) UNCERTAIN_RISE_TAU_MS else UNCERTAIN_DECAY_TAU_MS
        uncertainPulse += (uncertainPulseTarget - uncertainPulse) * smoothAlpha(dtMs, uncertainPulseTau)

        // ATTENTIVE is sustained, not a pulse -- a single tau smooths both
        // the rise (when the caller reports genuine sustained direct
        // address) and the fall (when it stops), same idiom as
        // listeningBiasSmooth above.
        val attentiveTarget = if (vAttentive) 1f else 0f
        attentiveSmooth += (attentiveTarget - attentiveSmooth) * smoothAlpha(dtMs, ATTENTIVE_TAU_MS)

        // Round 2 fix: PLEASED/UNCERTAIN's mouth release, deferred until
        // speaking has genuinely finished (not merely "isn't true right
        // now," which is equally true during MainActivity's own pre-
        // dispatch delay before speaking has even started) -- see
        // ScoutExpressionPriority.shouldReleaseDeferredMouthExpression()'s
        // doc comment. The brow pulses above are completely unaffected --
        // pleasedPulse/uncertainPulse keep aging on their original,
        // immediate clock regardless of any of this.
        if (pleasedMouthArmed && vSpeaking) pleasedMouthSawSpeaking = true
        if (ScoutExpressionPriority.shouldReleaseDeferredMouthExpression(
                armed = pleasedMouthArmed,
                isSpeaking = vSpeaking,
                sawSpeakingWhileArmed = pleasedMouthSawSpeaking,
                armedForMs = now - pleasedMouthArmedAtMs,
                armTimeoutMs = MOUTH_EXPRESSION_ARM_TIMEOUT_MS
            )) {
            pleasedMouthUntilMs = now + PLEASED_HOLD_MS
            pleasedMouthArmed = false
            pleasedMouthSawSpeaking = false
        }
        val pleasedMouthTarget = if (now < pleasedMouthUntilMs) 1f else 0f
        val pleasedMouthTau = if (pleasedMouthTarget > pleasedMouthIntensity) PLEASED_RISE_TAU_MS else PLEASED_DECAY_TAU_MS
        pleasedMouthIntensity += (pleasedMouthTarget - pleasedMouthIntensity) * smoothAlpha(dtMs, pleasedMouthTau)

        if (uncertainMouthArmed && vSpeaking) uncertainMouthSawSpeaking = true
        if (ScoutExpressionPriority.shouldReleaseDeferredMouthExpression(
                armed = uncertainMouthArmed,
                isSpeaking = vSpeaking,
                sawSpeakingWhileArmed = uncertainMouthSawSpeaking,
                armedForMs = now - uncertainMouthArmedAtMs,
                armTimeoutMs = MOUTH_EXPRESSION_ARM_TIMEOUT_MS
            )) {
            uncertainMouthUntilMs = now + UNCERTAIN_HOLD_MS
            uncertainMouthArmed = false
            uncertainMouthSawSpeaking = false
        }
        val uncertainMouthTarget = if (now < uncertainMouthUntilMs) 1f else 0f
        val uncertainMouthTau = if (uncertainMouthTarget > uncertainMouthIntensity) UNCERTAIN_RISE_TAU_MS else UNCERTAIN_DECAY_TAU_MS
        uncertainMouthIntensity += (uncertainMouthTarget - uncertainMouthIntensity) * smoothAlpha(dtMs, uncertainMouthTau)

        // Expression ownership: resolved once per frame, read by
        // drawBrow()/drawMouth() below. "Active" for each pulse-based
        // candidate is its own current magnitude clearing a small epsilon --
        // once a pulse decays past that point, ownership falls through to
        // the next candidate in priority order on its own, with no separate
        // expiry bookkeeping needed here. Note the brow and mouth resolvers
        // deliberately read different magnitudes for the same expression --
        // the brow pulse (immediate) for resolveBrowOwner(), the mouth's own
        // independently-released intensity for resolveMouthOwner() -- see
        // the round 2 fix above.
        browExpressionOwner = ScoutExpressionPriority.resolveBrowOwner(
            isThinking = vThinking,
            isListening = vListening,
            uncertainActive = uncertainPulse > 0.5f,
            pleasedActive = pleasedPulse > 0.5f,
            noticeActive = noticePulse > 0.5f,
            attentiveActive = vAttentive
        )
        mouthExpressionOwner = ScoutExpressionPriority.resolveMouthOwner(
            isSpeaking = vSpeaking,
            isThinking = vThinking,
            isListening = vListening,
            uncertainActive = uncertainMouthIntensity > 0.02f,
            pleasedActive = pleasedMouthIntensity > 0.02f
        )

        // Per-eye expression lower lids: emotions tighten/relax each cheek
        // independently. Expression Visibility v2: PLEASED's symmetric
        // "eyes crinkle warmly" cue and UNCERTAIN's one-sided (right eye
        // only) narrowing are added here, gated on browExpressionOwner
        // (resolved just above) and scaled by each pulse's own current 0..1
        // progress -- computed here, after ownership resolution, rather
        // than earlier in this function, specifically so it can reference
        // browExpressionOwner directly instead of duplicating
        // ScoutExpressionPriority's own ownership logic a second time.
        val pleasedLidContribution = if (browExpressionOwner == ScoutExpressionLayer.PLEASED)
            (pleasedPulse / PLEASED_BROW_LIFT_PX) * PLEASED_LOWER_LID_PX else 0f
        val uncertainLidContribution = if (browExpressionOwner == ScoutExpressionLayer.UNCERTAIN)
            (uncertainPulse / UNCERTAIN_BROW_PRIMARY_PX) * UNCERTAIN_LID_NARROW_PX else 0f

        val exprTargetL = when {
            vThinking  -> 1f   // barely perceptible sympathetic tighten
            vListening -> 1f
            else       -> 0f
        } + pleasedLidContribution
        val exprTargetR = when {
            vThinking  -> 2f   // subtle tighten — brow is the hero, this is support
            vListening -> 1f
            else       -> 0f
        } + pleasedLidContribution + uncertainLidContribution
        lowerLidExprL += (exprTargetL - lowerLidExprL) * smoothAlpha(dtMs, 250f)
        lowerLidExprR += (exprTargetR - lowerLidExprR) * smoothAlpha(dtMs, 250f)

        val hasExternalGaze = abs(vLookTargetX) > 1f || abs(vLookTargetY) > 1f
        if (!hasExternalGaze) {
            if (now >= nextDriftChangeAt) {
                idleDriftTargetX  = (Random.nextFloat() * 2f - 1f) * 5f
                idleDriftTargetY  = (Random.nextFloat() * 2f - 1f) * 3f
                nextDriftChangeAt = now + Random.nextLong(2500, 5500)
            }
            idleDriftX += (idleDriftTargetX - idleDriftX) * smoothAlpha(dtMs, 2200f)
            idleDriftY += (idleDriftTargetY - idleDriftY) * smoothAlpha(dtMs, 2200f)
        } else {
            idleDriftX += (0f - idleDriftX) * smoothAlpha(dtMs, 500f)
            idleDriftY += (0f - idleDriftY) * smoothAlpha(dtMs, 500f)
        }

        if (now >= nextSaccadeAt) {
            // Saccade X range reduced 1.5f → 1.0f — combined with inward bias,
            // ±1.5 was enough to push the far eye noticeably nose-ward.
            saccadeTargetX = (Random.nextFloat() * 2f - 1f) * 1.0f
            saccadeTargetY = (Random.nextFloat() * 2f - 1f) * 1.0f
            saccadeDecayMs = Random.nextFloat() * 800f + 600f
            nextSaccadeAt  = now + Random.nextLong(4000, 9000)
        }
        val saccadeTau = if (vThinking) 600f else 80f
        saccadeX       += (saccadeTargetX - saccadeX) * smoothAlpha(dtMs, saccadeTau)
        saccadeY       += (saccadeTargetY - saccadeY) * smoothAlpha(dtMs, saccadeTau)
        saccadeTargetX += (0f - saccadeTargetX) * smoothAlpha(dtMs, saccadeDecayMs)
        saccadeTargetY += (0f - saccadeTargetY) * smoothAlpha(dtMs, saccadeDecayMs)

        // Micro-tremor: tiny constant random iris drift — active when Scout is engaged
        val isEngaged = vListening || vSpeaking || vThinking || hasExternalGaze
        if (isEngaged) {
            if (now >= nextMicroTremorAt) {
                microTremorTargetX = (Random.nextFloat() * 2f - 1f) * 0.9f
                microTremorTargetY = (Random.nextFloat() * 2f - 1f) * 0.55f
                nextMicroTremorAt  = now + Random.nextLong(500, 1600)
            }
        } else {
            microTremorTargetX = 0f
            microTremorTargetY = 0f
        }
        microTremorX += (microTremorTargetX - microTremorX) * smoothAlpha(dtMs, 380f)
        microTremorY += (microTremorTargetY - microTremorY) * smoothAlpha(dtMs, 380f)

        // Whole-face idle drift: slow wandering of the entire face position as one unit
        if (now >= nextFaceDriftAt) {
            faceIdleDriftTargetX = (Random.nextFloat() * 2f - 1f) * 3f
            faceIdleDriftTargetY = (Random.nextFloat() * 2f - 1f) * 2f
            nextFaceDriftAt      = now + Random.nextLong(3500, 7000)
        }
        faceIdleDriftX += (faceIdleDriftTargetX - faceIdleDriftX) * smoothAlpha(dtMs, 2800f)
        faceIdleDriftY += (faceIdleDriftTargetY - faceIdleDriftY) * smoothAlpha(dtMs, 2800f)

        // Gaze-driven face drift: face slowly follows where the eyes are pointing.
        // Thinking uses 220ms tau so the face catches up within the short glance window.
        // Face-tracking of a real person uses 900ms — natural "head turns to look" lag.
        // Amplitude tune (conservative first pass): 0.32/0.26 → 0.60/0.38. At
        // lookX/Y's max (±80/±55), the old coefficients capped whole-face travel
        // at ~26/14 virtual px on a 1920x1080 canvas -- under 1.5% of screen
        // width, effectively unreadable on a real device even though the eyes
        // (which travel at lookX*1.10 in drawEye()) were clearly moving. Raising
        // just this coefficient is safe: sockets sit with ~340px of margin to the
        // canvas edge at neutral, so even the new max travel (~48/21px) leaves a
        // wide margin, and every other feature keyed off faceCx/faceCy (both
        // sockets, both brows, the mouth) rides along automatically since they
        // already derive from it -- no other animation logic changed.
        val gazeDriftTau = if (vThinking) 220f else 900f
        faceGazeDriftX += (lookX * 0.60f - faceGazeDriftX) * smoothAlpha(dtMs, gazeDriftTau)
        faceGazeDriftY += (lookY * 0.38f - faceGazeDriftY) * smoothAlpha(dtMs, gazeDriftTau)

        // FIX 2: spring tuned for snappier iris motion.
        // springK 0.24 → 0.28 — faster acceleration toward gaze target.
        // dampK   0.83 → 0.80 — slightly less resistance, livelier feel.
        // Travel multiplier in drawEye() is 0.55 → 0.68 for wider left/right range.
        // Slightly calmer to reduce tiny twitching while keeping visible motion.
        val springK  = 0.34f
        val dampK    = 0.78f
        val dtScale  = dtMs / 16f

        val thinkingScanX = if (vThinking) {

            0f

        } else {

            0f

        }

        val targetX = if (vDownloading || vSpeaking) {
            0f
        } else {
            if (vThinking) thinkGazeX + idleDriftX * 0.3f else vLookTargetX + idleDriftX + thinkingScanX
        }

        val targetY = if (vDownloading || vSpeaking) {
            0f
        } else {
            if (vThinking) thinkGazeY + idleDriftY * 0.5f else vLookTargetY + idleDriftY
        }

        if (vThinking || vDownloading || vSpeaking) {
            lookVX = 0f
            lookVY = 0f
            lookX += (targetX - lookX) * 0.08f
            lookY += (targetY - lookY) * 0.08f
        } else {
            // Semi-implicit Euler: damping divided out rather than subtracted — stable at any
            // frame time. Explicit Euler oscillates (velocity sign-flips) when dampK*dtScale > 1,
            // which happens on any frame slower than ~20ms. A32 routinely exceeds that.
            val ax  = (targetX - lookX) * springK * dtScale
            val ay  = (targetY - lookY) * springK * dtScale
            val div = 1f + dampK * dtScale
            lookVX  = (lookVX + ax) / div
            lookVY  = (lookVY + ay) / div
            lookX  += lookVX * dtScale
            lookY  += lookVY * dtScale
        }

        lookX = lookX.coerceIn(-80f, 80f)
        lookY = lookY.coerceIn(-55f, 55f)

        if (nextBlinkAt == 0L) nextBlinkAt = now + Random.nextLong(2800, 6500)
        if (now >= nextBlinkAt && !blinking) {
            val isFollowUp = pendingDoubleBlink
            pendingDoubleBlink = false

            blinking      = true
            blinkPhaseL   = 0f
            blinkPhaseR   = 0f
            blinkLead     = if (Random.nextBoolean()) -1 else 1
            blinkLagPhase = 0.12f + Random.nextFloat() * 0.16f
            blinkSpeed    = when {
                isFollowUp       -> 1.25f + Random.nextFloat() * 0.30f  // follow-up blinks are snappier
                vBatteryPct < 20 -> 0.55f + Random.nextFloat() * 0.20f
                else             -> 0.85f + Random.nextFloat() * 0.40f
            }
            blinkMaxPhase  = if (!isFollowUp && Random.nextFloat() < 0.18f) 1.35f else 2.0f
            blinkDipY      = if (isFollowUp) 3f else 6f   // follow-up blinks have a shallower dip
            blinkBrowRelax = 5f

            if (isFollowUp) {
                // Resume normal interval after the second blink of a double
                nextBlinkAt = now + if (vBatteryPct < 20) {
                    Random.nextLong(8000, 15000)
                } else {
                    Random.nextLong(3200, 7800)
                }
            } else {
                // 21% chance to schedule a quick follow-up double blink
                if (Random.nextFloat() < 0.21f && vBatteryPct >= 20) {
                    pendingDoubleBlink = true
                    nextBlinkAt = now + Random.nextLong(130, 270)
                } else {
                    nextBlinkAt = now + if (vBatteryPct < 20) {
                        Random.nextLong(8000, 15000)
                    } else {
                        Random.nextLong(3200, 7800)
                    }
                }
            }
        }

        if (blinking) {
            val step = (dtMs / 160f) * blinkSpeed
            if (blinkLead < 0) {
                blinkPhaseL += step
                if (blinkPhaseL >= blinkLagPhase) blinkPhaseR += step
            } else {
                blinkPhaseR += step
                if (blinkPhaseR >= blinkLagPhase) blinkPhaseL += step
            }

            fun blinkCurve(phase: Float): Float {
                val t = phase.coerceIn(0f, blinkMaxPhase)
                val n = t / blinkMaxPhase
                return if (n <= 0.5f) easeInOut(n * 2f)
                else 1f - easeInOut((n - 0.5f) * 2f)
            }

            blinkL = blinkCurve(blinkPhaseL).coerceIn(0f, 1f)
            blinkR = blinkCurve(blinkPhaseR).coerceIn(0f, 1f)

            if (blinkPhaseL >= blinkMaxPhase && blinkPhaseR >= blinkMaxPhase) {
                blinking = false
                blinkL *= 0.18f
                blinkR *= 0.18f
            }
        } else {
            blinkL *= 0.76f
            blinkR *= 0.76f
        }

        // Secondary blink motion decays back to rest after each blink
        blinkDipY      += (0f - blinkDipY)      * smoothAlpha(dtMs, 160f)
        blinkBrowRelax += (0f - blinkBrowRelax) * smoothAlpha(dtMs, 200f)

        if (vSpeaking) {
            speechPhase += dtMs / 85f

            // Speaking Mouth v1: decay the range-timed impulse every frame,
            // unconditionally, regardless of whether a new onRangeStart()
            // event arrived this frame -- this is what produces a smooth
            // fade between events instead of the old fixed-period cycling.
            // Never touches speechPhase/speechSmooth/mouthOpen directly;
            // only feeds the branch selection and target below.
            speechRangeImpulse += (0f - speechRangeImpulse) *
                    smoothAlpha(dtMs, SPEECH_RANGE_DECAY_TAU_MS)

            if (ScoutSpeechRangeMouth.isRangeDriven(speechRangeEstablished)) {
                // Real speech-timed path: at least one onRangeStart() event
                // has been seen for the current dispatch -- this path now
                // owns the mouth exclusively for the REST of that dispatch
                // (see the else-if/else below, both permanently skipped once
                // this is true), matching "once usable range callbacks are
                // established for a dispatch, the speech-timed path should
                // own the mouth for the remainder of that dispatch." A
                // pause between callbacks is handled entirely by
                // speechRangeImpulse's own continuous decay above -- never
                // by falling out of this branch.
                val target = speechRangeImpulse.coerceIn(0f, 1f)
                mouthOpen += (target - mouthOpen) * smoothAlpha(dtMs, SPEECH_RANGE_MOUTH_TAU_MS)
            } else if (speechSmooth > 0.05f) {
                // Unchanged, pre-existing branch -- dead in production today
                // since nothing calls setSpeechLevel() (see that method's own
                // doc comment for why this feature deliberately does not
                // reuse it), left exactly as it was.
                val jitter = Random.nextFloat() * 0.14f - 0.07f
                val target = (speechSmooth * 0.85f + jitter + 0.10f).coerceIn(0f, 1f)
                mouthOpen += (target - mouthOpen) * smoothAlpha(dtMs, 38f)
            } else {
                // Unchanged, pre-existing synthetic fallback -- still exactly
                // what runs whenever the current dispatch has not (yet, or
                // ever) produced a usable onRangeStart() event. Required
                // fail-safe: real-device onRangeStart() reliability on
                // Samsung TTS is unproven, so Scout must never go motionless
                // if no events arrive.
                val wave   = (sin(speechPhase.toDouble()) * 0.5 + 0.5).toFloat()
                val burst  = (sin(speechPhase.toDouble() * 2.618) * 0.5 + 0.5).toFloat()
                val jitter = Random.nextFloat() * 0.08f
                val target = (wave * 0.20f + burst * 0.11f + jitter + 0.05f).coerceIn(0f, 0.42f)
                mouthOpen += (target - mouthOpen) * smoothAlpha(dtMs, 42f)
            }
        } else {
            speechPhase  = 0f
            speechSmooth = 0f
            // Speaking Mouth v1: unified with every other kind of speech
            // completion via ScoutFaceView.setSpeaking(false), which
            // MainActivity's finishSpeechDispatch() already calls for
            // NATURAL/ENGINE_ERROR/USER_INTERRUPTED alike -- so tap-to-
            // interrupt, a natural finish, and an engine error all return
            // this state to rest through this exact same line, with no
            // special-casing needed here.
            speechRangeImpulse     = 0f
            speechRangeEstablished = false
            mouthOpen   += (0f - mouthOpen) * smoothAlpha(dtMs, 120f)
        }

        mouthOpen = mouthOpen.coerceIn(0f, 1f)
        blinkL    = blinkL.coerceIn(0f, 1f)
        blinkR    = blinkR.coerceIn(0f, 1f)
    }

    private fun smoothAlpha(dtMs: Float, tauMs: Float): Float =
        (1f - exp(-dtMs / tauMs)).coerceIn(0f, 1f)

    private fun easeInOut(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    // ======================================================
    //  SCHEDULING
    // ======================================================
    private val tickRunnable = Runnable { postInvalidateOnAnimation() }

    private fun requestActiveFrame() {
        idleMode = false
        removeCallbacks(tickRunnable)
        postInvalidateOnAnimation()
    }

    private fun scheduleNextFrame(now: Long) {
        val movingGaze     = abs(vLookTargetX - lookX) > 0.6f || abs(vLookTargetY - lookY) > 0.6f
        val movingSpring   = abs(lookVX) > 0.1f || abs(lookVY) > 0.1f
        val movingSaccade  = abs(saccadeX) > 0.3f || abs(saccadeY) > 0.3f
        val movingBrow     = abs(browMicroY - browMicroTarget) > 0.3f
        val movingVergence = abs(
            vergenceSmooth - (if (vThinking) maxOf(vVergence, 0.3f) else vVergence)
        ) > 0.01f
        val movingMouth    = abs(mouthOpen) > 0.01f

        val smileLift = (mouthOpen * 14f).coerceIn(0f, 10f)
        val lowerLidTarget = when {
            vThinking  -> smileLift
            vListening -> maxOf(8f, smileLift)
            else       -> smileLift
        }
        val movingLowerLid   = abs(lowerLidSmooth - lowerLidTarget) > 0.3f ||
                               lowerLidExprL > 0.1f || lowerLidExprR > 0.1f
        // Keep ticking while listening bias is transitioning
        val movingListenBias = abs(listeningBiasSmooth - (if (vListening) 10f else 0f)) > 0.1f
        // Keep ticking while locked on so focus breathing renders each frame
        val isLockedOn       = abs(vLookTargetX) > 1f || abs(vLookTargetY) > 1f

        val movingTremor    = (abs(microTremorX) + abs(microTremorY)) > 0.05f
        val movingGazeDrift = (abs(faceGazeDriftX) + abs(faceGazeDriftY)) > 0.08f
        val movingThinkLid  = thinkLidSmooth > 0.004f
        // Silent Arrival Acknowledgment v1: keep ticking through the whole
        // rise/hold/decay, not just while noticePulse itself is above the
        // epsilon -- otherwise the hold phase (noticePulse already at its
        // flat peak, nothing "moving") could let idle mode kick in and delay
        // the eventual decay tick.
        val movingNotice = noticePulse > 0.05f || now < noticePulseUntilMs

        // Emotional Face v1: same "keep ticking through the whole rise/hold/
        // decay" reasoning as movingNotice above, for the two new pulses;
        // ATTENTIVE keeps ticking while its smoothed value hasn't yet
        // settled at its current target (mirrors movingListenBias's shape).
        val movingPleased   = pleasedPulse > 0.05f || now < pleasedPulseUntilMs
        val movingUncertain = uncertainPulse > 0.05f || now < uncertainPulseUntilMs
        val movingAttentive = abs(attentiveSmooth - (if (vAttentive) 1f else 0f)) > 0.01f
        // Round 2 fix: keep ticking through the whole armed-and-waiting
        // window too (not just once the mouth's own curve is actually
        // moving) -- otherwise idle mode could in principle delay noticing
        // the exact frame speaking ends and the mouth should release.
        val movingPleasedMouth = pleasedMouthArmed || pleasedMouthIntensity > 0.05f || now < pleasedMouthUntilMs
        val movingUncertainMouth = uncertainMouthArmed || uncertainMouthIntensity > 0.05f || now < uncertainMouthUntilMs

        val active = vSpeaking || vListening || vThinking || vDownloading ||
                blinking || movingGaze || movingSpring || movingSaccade ||
                movingBrow || movingVergence || movingMouth ||
                movingLowerLid || movingListenBias || isLockedOn ||
                micSmooth > 0.04f || movingTremor || movingGazeDrift || movingThinkLid ||
                movingNotice || movingPleased || movingUncertain || movingAttentive ||
                movingPleasedMouth || movingUncertainMouth

        if (active) {
            idleMode = false
            removeCallbacks(tickRunnable)
            postInvalidateOnAnimation()
        } else {
            if (!idleMode) {
                idleMode = true
                removeCallbacks(tickRunnable)
            }
            val untilBlink = (nextBlinkAt - now).coerceAtLeast(0L)
            val delay = if (untilBlink in 1..15000) max(16L, untilBlink) else idleTickMs
            postDelayed(tickRunnable, delay)
        }
    }
}