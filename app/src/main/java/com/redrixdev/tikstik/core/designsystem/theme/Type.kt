// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.redrixdev.tikstik.R

private fun jakarta(weight: Int) = Font(
    R.font.plus_jakarta_sans,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Jakarta = FontFamily(jakarta(400), jakarta(500), jakarta(600), jakarta(700), jakarta(800))

// No ligatures: Jakarta's "fi" swallows the dot of the i ("Configúralo").
private fun style(size: Int, line: Int, weight: Int, tracking: Double = 0.0) = TextStyle(
    fontFamily = Jakarta,
    fontWeight = FontWeight(weight),
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.em,
    fontFeatureSettings = "liga 0",
)

val TikStikTypography = Typography(
    displayLarge = style(48, 54, 800, -0.02),
    displayMedium = style(40, 46, 800, -0.02),
    displaySmall = style(34, 40, 800, -0.015),
    headlineLarge = style(30, 36, 800, -0.01),
    headlineMedium = style(26, 32, 800, -0.01),
    headlineSmall = style(22, 28, 700, -0.005),
    titleLarge = style(20, 26, 700),
    titleMedium = style(16, 22, 700),
    titleSmall = style(14, 20, 700),
    bodyLarge = style(16, 24, 500),
    bodyMedium = style(14, 21, 500),
    bodySmall = style(12, 17, 500),
    labelLarge = style(15, 20, 700),
    labelMedium = style(13, 18, 600),
    labelSmall = style(11, 14, 600, 0.02),
)
