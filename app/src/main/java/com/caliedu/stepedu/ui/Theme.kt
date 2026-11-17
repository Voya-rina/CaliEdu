package com.caliedu.stepedu.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Ink {
    val Bg = Color(0xFFF6F5F2)
    val Card = Color(0xFFFFFFFF)
    val Text = Color(0xFF1B1B18)
    val Sub = Color(0xFF8A8880)
    val Line = Color(0xFFE6E4DE)
    val Accent = Color(0xFF2E6B5E)
    val AccentSoft = Color(0xFFE9F0ED)
    val Wrong = Color(0xFFAD3E2D)
    val WrongSoft = Color(0xFFF8EBE7)
    val Code = Color(0xFFF1F0EB)
    val Mono = Color(0xFF3A3A33)
    val Amber = Color(0xFF9A6B12)
}

private val scheme = lightColorScheme(
    primary = Ink.Accent,
    onPrimary = Color.White,
    background = Ink.Bg,
    onBackground = Ink.Text,
    surface = Ink.Card,
    onSurface = Ink.Text,
    surfaceVariant = Ink.Code,
    onSurfaceVariant = Ink.Sub,
    outline = Ink.Line,
)

@Composable
fun StepEduTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
}
