package com.pranshuparmar.inkmayhem.android

import android.os.Build
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import com.pranshuparmar.inkmayhem.input.PenButtons
import com.pranshuparmar.inkmayhem.input.PenPhase
import com.pranshuparmar.inkmayhem.input.PenQueue
import com.pranshuparmar.inkmayhem.input.ToolType
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Converts Android MotionEvents (touch and hover) into [com.pranshuparmar.inkmayhem.input.PenSample]s
 * on the UI thread and pushes them, including every historical sample, onto the lock-free [queue].
 * Nothing here allocates per event.
 */
class AndroidPenInput(private val queue: PenQueue, private val view: View) {

    init {
        // Ask for every pointer sample (touch and hover) as it arrives instead of once per vsync.
        // The request travels up the view parents to the window, so wait until the view is attached.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.post { view.requestUnbufferedDispatch(InputDevice.SOURCE_CLASS_POINTER) }
        }
    }

    /** Touch stream: pen, eraser end and fingers. Always consumed so libGDX never sees raw touches. */
    fun onTouch(ev: MotionEvent) {
        val action = ev.actionMasked
        // Per-gesture request, valid during dispatch on every API level.
        if (action == MotionEvent.ACTION_DOWN) view.requestUnbufferedDispatch(ev)
        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> emitPointer(ev, ev.actionIndex, PenPhase.DOWN)
            MotionEvent.ACTION_MOVE -> emitMoves(ev, PenPhase.MOVE)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> emitPointer(ev, ev.actionIndex, PenPhase.UP)
            MotionEvent.ACTION_CANCEL -> for (i in 0 until ev.pointerCount) emitPointer(ev, i, PenPhase.CANCEL)
        }
    }

    /** Generic motion stream: hover enter/move/exit. Returns true when the event was a pen hover. */
    fun onGenericMotion(ev: MotionEvent): Boolean {
        if (!ev.isFromSource(InputDevice.SOURCE_CLASS_POINTER)) return false
        return when (ev.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> {
                emitMoves(ev, PenPhase.HOVER)
                true
            }
            MotionEvent.ACTION_HOVER_EXIT -> {
                emitPointer(ev, 0, PenPhase.HOVER_EXIT)
                true
            }
            else -> false
        }
    }

    private fun emitMoves(ev: MotionEvent, phase: PenPhase) {
        val history = ev.historySize
        for (i in 0 until ev.pointerCount) {
            val tool = toolOf(ev.getToolType(i))
            val buttons = buttonsOf(ev.buttonState)
            val id = ev.getPointerId(i)
            for (h in 0 until history) {
                tilt(ev.getHistoricalAxisValue(MotionEvent.AXIS_TILT, i, h), ev.getHistoricalAxisValue(MotionEvent.AXIS_ORIENTATION, i, h))
                queue.offer(
                    ev.getHistoricalX(i, h), ev.getHistoricalY(i, h), ev.getHistoricalPressure(i, h), tiltX, tiltY,
                    buttons, phase, tool, id, ev.historicalTimeNanosCompat(h), isHistorical = true,
                )
            }
            tilt(ev.getAxisValue(MotionEvent.AXIS_TILT, i), ev.getAxisValue(MotionEvent.AXIS_ORIENTATION, i))
            queue.offer(
                ev.getX(i), ev.getY(i), ev.getPressure(i), tiltX, tiltY, buttons, phase, tool, id,
                ev.eventTimeNanosCompat(),
            )
        }
    }

    private fun emitPointer(ev: MotionEvent, i: Int, phase: PenPhase) {
        tilt(ev.getAxisValue(MotionEvent.AXIS_TILT, i), ev.getAxisValue(MotionEvent.AXIS_ORIENTATION, i))
        queue.offer(
            ev.getX(i), ev.getY(i), ev.getPressure(i), tiltX, tiltY, buttonsOf(ev.buttonState), phase,
            toolOf(ev.getToolType(i)), ev.getPointerId(i), ev.eventTimeNanosCompat(),
        )
    }

    private var tiltX = 0f
    private var tiltY = 0f

    /** AXIS_TILT is the angle from vertical, AXIS_ORIENTATION its direction (0 = pointing up the screen). */
    private fun tilt(tilt: Float, orientation: Float) {
        val t = tan(tilt.toDouble().coerceIn(0.0, 1.5))
        tiltX = Math.toDegrees(atan(t * sin(orientation.toDouble()))).toFloat()
        tiltY = Math.toDegrees(atan(-t * cos(orientation.toDouble()))).toFloat()
    }

    // Same precision for head and historical samples, so sample times never step backwards.
    private fun MotionEvent.eventTimeNanosCompat(): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) eventTimeNanos else eventTime * 1_000_000L

    private fun MotionEvent.historicalTimeNanosCompat(h: Int): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) getHistoricalEventTimeNanos(h) else getHistoricalEventTime(h) * 1_000_000L

    private fun toolOf(type: Int): ToolType = when (type) {
        MotionEvent.TOOL_TYPE_STYLUS -> ToolType.STYLUS
        MotionEvent.TOOL_TYPE_ERASER -> ToolType.ERASER
        MotionEvent.TOOL_TYPE_FINGER -> ToolType.FINGER
        MotionEvent.TOOL_TYPE_MOUSE -> ToolType.MOUSE
        else -> ToolType.UNKNOWN
    }

    private fun buttonsOf(state: Int): Int {
        var b = 0
        // Samsung reports the S Pen side button as BUTTON_STYLUS_PRIMARY on current firmware and as
        // BUTTON_SECONDARY on some older builds; both mean "side button held".
        if (state and (MotionEvent.BUTTON_STYLUS_PRIMARY or MotionEvent.BUTTON_SECONDARY) != 0) b = b or PenButtons.SIDE
        return b
    }
}
