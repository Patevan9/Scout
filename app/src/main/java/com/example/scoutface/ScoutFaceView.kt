package com.example.scoutface

import android.content.Context
import android.graphics.*
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
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
        if (vSpeaking != s) { vSpeaking = s; requestActiveFrame() }
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

    private var speechPhase = 0f
    private var mouthOpen   = 0f
    private var wavePhase   = 0f

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
        drawEye(c, leftGeom,  blinkL, lidDroopL,                lowerL, now)
        drawEye(c, rightGeom, blinkR, lidDroopR + thinkLidSmooth, lowerR, now)
        drawBrow(c, leftGeom, side = -1)
        drawBrow(c, rightGeom, side = 1)
        drawMouth(c, faceCx, faceCy + 240f)
        drawStatusDot(c, now)
    }

    // ======================================================
    //  EYE
    // ======================================================
    private fun drawEye(c: Canvas, eye: EyeGeom, blinkAmount: Float,
                        lidDroop: Float, lowerLid: Float, now: Long) {

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

        val b = (blinkAmount + lidDroop).coerceIn(0f, 1f)
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
        val browCy = eye.socketRect.top - 38f - listeningLift - thinkingLift + tiredDrop +
                browMicroY + blinkBrowRelax - speechBrowLift

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
            y1 = browCy + tilt * 0.5f
            y2 = browCy - tilt * 0.5f - thinkInnerLift  // inner edge of left brow rises when curious
        } else {
            y1 = browCy - tilt * 0.5f + thinkTilt  // inner end of right brow rises (thinkTilt = -10f)
            y2 = browCy + tilt * 0.5f
        }

        val midX = eye.cx + thinkInward
        val midY = (y1 + y2) * 0.5f - 28f

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
            val corYL = if (vThinking) cy - 2f else cy + 2f   // both corners rise → warm smile
            val corYR = if (vThinking) cy - 4f else cy + 2f   // right corner slightly higher
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
        // Thinking lid: right-eye concentration narrowing (13% — focused, not sleepy)
        val thinkLidTarget = if (vThinking) 0.13f else 0f
        thinkLidSmooth += (thinkLidTarget - thinkLidSmooth) * smoothAlpha(dtMs, 350f)

        val smileLift = (mouthOpen * 14f).coerceIn(0f, 10f)
        val lowerLidTarget = when {
            vThinking  -> smileLift
            vListening -> maxOf(8f, smileLift)
            else       -> smileLift
        }
        lowerLidSmooth += (lowerLidTarget - lowerLidSmooth) * smoothAlpha(dtMs, 200f)

        // Per-eye expression lower lids: emotions tighten/relax each cheek independently
        val exprTargetL = when {
            vThinking  -> 2f   // slight sympathetic tighten under non-concentrating eye
            vListening -> 1f   // attentive — barely perceptible
            else       -> 0f
        }
        val exprTargetR = when {
            vThinking  -> 5f   // tighten under the concentrating (right) eye
            vListening -> 1f
            else       -> 0f
        }
        lowerLidExprL += (exprTargetL - lowerLidExprL) * smoothAlpha(dtMs, 250f)
        lowerLidExprR += (exprTargetR - lowerLidExprR) * smoothAlpha(dtMs, 250f)

        val vergenceTarget = if (vThinking) maxOf(vVergence, 0.3f) else vVergence
        vergenceSmooth += (vergenceTarget - vergenceSmooth) * smoothAlpha(dtMs, 280f)

        if (vThinking) {
            thinkEndedAt = 0L
            if (nextThinkGlanceAt == 0L) {
                thinkGlanceActive = true
                thinkGlanceSideX  = -(35f + Random.nextFloat() * 15f)  // always left
                nextThinkGlanceAt = 1L
            }
        } else {
            if (thinkGlanceActive) thinkEndedAt = now  // record moment thinking stopped
            thinkGlanceActive = false; nextThinkGlanceAt = 0L; thinkGlanceSideX = 0f
        }
        // Micro-drift: slow organic wandering around the upper-left anchor while thinking
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
        val gazeDriftTau = if (vThinking) 220f else 900f
        faceGazeDriftX += (lookX * 0.32f - faceGazeDriftX) * smoothAlpha(dtMs, gazeDriftTau)
        faceGazeDriftY += (lookY * 0.26f - faceGazeDriftY) * smoothAlpha(dtMs, gazeDriftTau)

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
            lookVX += ((targetX - lookX) * springK - lookVX * dampK) * dtScale
            lookVY += ((targetY - lookY) * springK - lookVY * dampK) * dtScale
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
            if (speechSmooth > 0.05f) {
                val jitter = Random.nextFloat() * 0.14f - 0.07f
                val target = (speechSmooth * 0.85f + jitter + 0.10f).coerceIn(0f, 1f)
                mouthOpen += (target - mouthOpen) * smoothAlpha(dtMs, 38f)
            } else {
                val wave   = (sin(speechPhase.toDouble()) * 0.5 + 0.5).toFloat()
                val burst  = (sin(speechPhase.toDouble() * 2.618) * 0.5 + 0.5).toFloat()
                val jitter = Random.nextFloat() * 0.08f
                val target = (wave * 0.20f + burst * 0.11f + jitter + 0.05f).coerceIn(0f, 0.42f)
                mouthOpen += (target - mouthOpen) * smoothAlpha(dtMs, 42f)
            }
        } else {
            speechPhase  = 0f
            speechSmooth = 0f
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

        val active = vSpeaking || vListening || vThinking || vDownloading ||
                blinking || movingGaze || movingSpring || movingSaccade ||
                movingBrow || movingVergence || movingMouth ||
                movingLowerLid || movingListenBias || isLockedOn ||
                micSmooth > 0.04f || movingTremor || movingGazeDrift || movingThinkLid

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