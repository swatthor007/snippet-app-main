package com.sockjaw.clipsy.ui.theme

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * Patterned vibrations that the one-shot HapticFeedbackType values can't express. Played as
 * touch feedback, so they follow the phone's touch-vibration setting where Android allows it.
 */
object Haptics {
    private const val TAG = "Haptics"

    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)

    private fun play(context: Context, effect: VibrationEffect) {
        val v = vibrator(context)?.takeIf { it.hasVibrator() } ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).build())
            }
        }
    }

    /**
     * The share swoosh: soft ticks that come faster and firmer as the preview speeds up, then one
     * solid thud as it lands in the Share button. Timed for the 340 ms flight. ". . . . . ▬"
     */
    fun swoosh(context: Context) {
        val v = vibrator(context) ?: return
        // Gaps shrink as the flight accelerates; the thud lands at ~330 ms.
        val tickDelays = intArrayOf(0, 70, 55, 45, 38)
        val tickScales = floatArrayOf(0.2f, 0.28f, 0.36f, 0.45f, 0.55f)
        if (Build.VERSION.SDK_INT >= 30) {
            // LOW_TICK and THUD arrived in Android 12; Android 11 uses TICK and CLICK.
            val tick = if (Build.VERSION.SDK_INT >= 31 && v.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_LOW_TICK)) VibrationEffect.Composition.PRIMITIVE_LOW_TICK
            else VibrationEffect.Composition.PRIMITIVE_TICK
            val land = if (Build.VERSION.SDK_INT >= 31 && v.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_THUD)) VibrationEffect.Composition.PRIMITIVE_THUD
            else VibrationEffect.Composition.PRIMITIVE_CLICK
            if (v.areAllPrimitivesSupported(tick, land)) {
                val c = VibrationEffect.startComposition()
                tickDelays.forEachIndexed { i, d -> c.addPrimitive(tick, tickScales[i], d) }
                c.addPrimitive(land, 1f, 122)
                Log.d(TAG, "swoosh: primitives (tick=$tick land=$land)")
                play(context, c.compose())
                return
            }
        }
        if (v.hasAmplitudeControl()) {
            // Waveform fallback: pauses then short pulses, ending with one long, strong pulse. Linear
            // motors (OnePlus 7T) need ~15 ms and a firm amplitude to register a pulse at all, so
            // the ticks are longer and stronger than they would be on a primitive-capable motor.
            Log.d(TAG, "swoosh: waveform")
            val timings = longArrayOf(0, 16, 54, 16, 39, 17, 28, 18, 20, 20, 100, 45)
            val amps = intArrayOf(0, 110, 0, 130, 0, 150, 0, 175, 0, 200, 0, 255)
            play(context, VibrationEffect.createWaveform(timings, amps, -1))
        } else if (Build.VERSION.SDK_INT >= 29) {
            Log.d(TAG, "swoosh: heavy click only")
            // No fine control: keep just the landing so it never buzzes crudely.
            play(context, VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
        }
    }

    /** A light tick as the shared preview pops back into place. */
    fun pop(context: Context) {
        val v = vibrator(context) ?: return
        if (Build.VERSION.SDK_INT >= 30 && v.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE)) {
            play(context, VibrationEffect.startComposition().addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.35f).compose())
        } else if (Build.VERSION.SDK_INT >= 29) {
            play(context, VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        }
    }
}
