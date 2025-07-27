package com.di.fitric.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Custom Typography for the Fitness Assistant app, prioritizing legibility and size.
 */
val AppTypography = Typography(
    // Used for the main timer display ("00:00")
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 100.sp, // Made even larger for prominence
        lineHeight = 108.sp,
        letterSpacing = 0.sp
    ),
    // Used for secondary stats like "Distance: 1.23 km"
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    // Used for the text inside large buttons like "START" and "STOP"
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.5.sp
    )
)