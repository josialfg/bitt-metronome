package com.velvet.metronome.ui.theme

import androidx.compose.ui.graphics.Color

/** v2-rev4: three themes — Beige (default), Dark (charcoal v1-style), Light (inverse of Dark). */
enum class ThemeChoice { BEIGE, DARK, LIGHT }

data class MetronomePalette(
    val background: Color,
    val currentBox: Color,
    val queuedBox: Color,
    val cellOutline: Color,
    val cellFill: Color,
    val tabBrown: Color,
    val lockKnob: Color,
    val lockTrack: Color,
    val playingDot: Color,
    val idleDot: Color,
    val bpmText: Color,
    val nudgeText: Color,
) {
    companion object {
        val Beige = MetronomePalette(
            background = Color(0xFFBFB09A),
            currentBox = Color(0xFF1A1A1A),
            queuedBox = Color(0xFF3B3B3B),
            cellOutline = Color(0xFFE8E8E8),
            cellFill = Color(0xFFE8E8E8),
            tabBrown = Color(0xFF8B6A4A),
            lockKnob = Color(0xFF1A1A1A),
            lockTrack = Color(0xFFCFC2AD),
            playingDot = Color(0xFF2EE040),
            idleDot = Color(0xFF7A2418),
            bpmText = Color(0xFF1A1A1A),
            nudgeText = Color(0xFF1A1A1A),
        )

        val Dark = MetronomePalette(
            background = Color(0xFF0F0F0F),
            currentBox = Color(0xFF1F1F1F),
            queuedBox = Color(0xFF2E2E2E),
            cellOutline = Color(0xFFE8E8E8),
            cellFill = Color(0xFFE8E8E8),
            tabBrown = Color(0xFF8B6A4A),
            lockKnob = Color(0xFFE8E8E8),
            lockTrack = Color(0xFF2A2A2A),
            playingDot = Color(0xFF2EE040),
            idleDot = Color(0xFF7A2418),
            bpmText = Color(0xFFE8E8E8),
            nudgeText = Color(0xFFE8E8E8),
        )

        val Light = MetronomePalette(
            background = Color(0xFFF5F5F5),
            currentBox = Color(0xFF1A1A1A),
            queuedBox = Color(0xFF3B3B3B),
            cellOutline = Color(0xFFE8E8E8),
            cellFill = Color(0xFFE8E8E8),
            tabBrown = Color(0xFF8B6A4A),
            lockKnob = Color(0xFF1A1A1A),
            lockTrack = Color(0xFFE2E2E2),
            playingDot = Color(0xFF2EE040),
            idleDot = Color(0xFF7A2418),
            bpmText = Color(0xFF1A1A1A),
            nudgeText = Color(0xFF1A1A1A),
        )
    }
}

fun ThemeChoice.toPalette(): MetronomePalette = when (this) {
    ThemeChoice.BEIGE -> MetronomePalette.Beige
    ThemeChoice.DARK  -> MetronomePalette.Dark
    ThemeChoice.LIGHT -> MetronomePalette.Light
}

// Back-compat — legacy direct references in code that haven't been migrated to
// the palette read these. Keep them pointing at the Beige defaults so the rev3
// callsites still compile.
val BeigeBackground = MetronomePalette.Beige.background
val CurrentBoxBlack = MetronomePalette.Beige.currentBox
val QueuedBoxGrey   = MetronomePalette.Beige.queuedBox
val CellOutline     = MetronomePalette.Beige.cellOutline
val CellFill        = MetronomePalette.Beige.cellFill
val TabBrown        = MetronomePalette.Beige.tabBrown
val LockKnob        = MetronomePalette.Beige.lockKnob
val LockTrack       = MetronomePalette.Beige.lockTrack
val PlayingDot      = MetronomePalette.Beige.playingDot
val IdleDot         = MetronomePalette.Beige.idleDot
val NudgeText       = MetronomePalette.Beige.nudgeText
val BpmText         = MetronomePalette.Beige.bpmText
