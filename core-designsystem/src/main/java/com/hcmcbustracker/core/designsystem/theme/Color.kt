package com.hcmcbustracker.core.designsystem.theme

import androidx.compose.ui.graphics.Color

// Primary Brand Colors (Transit Green / Teal / Emerald - Saigon Mobilités & Apple HIG inspired)
val GreenPrimary = Color(0xFF00A86B)           // Vibrant Emerald Transit Green
val GreenPrimaryDark = Color(0xFF006642)       // Deep Forest Transit Green
val GreenPrimaryLight = Color(0xFF4DD091)      // Radiant Mint Green

// Secondary & Accent Colors (Apple HIG Palette)
val BlueSecondary = Color(0xFF007AFF)          // Apple System Blue / Electric Transit
val BlueSecondaryLight = Color(0xFF5AC8FA)     // Apple Sky Blue
val BlueSecondaryDark = Color(0xFF0056B3)      // Deep Navy
val OrangeAccent = Color(0xFFFF9500)           // Apple Amber / Warm Accent
val RedApproaching = Color(0xFFFF3B30)         // Apple System Red / Urgent Arrival
val YellowWarning = Color(0xFFFFCC00)          // Apple Caution Yellow
val PurpleMetro = Color(0xFF5856D6)            // Apple Indigo / Metro Connector
val TealEco = Color(0xFF30B0C7)                // Apple Teal / Eco-Bus

// GPS & Transit Realtime Status Semantic Colors
val StatusFresh = Color(0xFF34C759)            // Fresh GPS data (< 60s)
val StatusSlightDelay = Color(0xFFFFCC00)       // Slight delay (60s - 120s)
val StatusDelayed = Color(0xFFFF9500)           // Noticeable delay (> 120s)
val StatusStale = Color(0xFF8E8E93)             // Stale / No signal
val StatusSuccess = Color(0xFF34C759)           // Positive action confirmation

// Neutral Background & Surface Colors (Light Mode - Apple Clean Aesthetic)
val BackgroundLight = Color(0xFFF2F2F7)        // iOS Grouped Background Light
val SurfaceLight = Color(0xFFFFFFFF)           // Pure Elevated White
val SurfaceVariantLight = Color(0xFFE5E5EA)    // Soft System Gray Fill
val TextPrimaryLight = Color(0xFF1C1C1E)       // High-contrast primary label
val TextSecondaryLight = Color(0xFF636366)     // Accessible secondary label
val TextTertiaryLight = Color(0xFF8E8E93)      // Subdued tertiary label
val DividerLight = Color(0xFFE5E5EA)           // Subtle border hairline
val BorderSubtleLight = Color(0x1F000000)      // Subtle 12% hairline border

// Neutral Background & Surface Colors (Dark Mode - OLED Dark Aesthetic)
val BackgroundDark = Color(0xFF000000)         // Pure OLED Black
val SurfaceDark = Color(0xFF1C1C1E)            // Elevated Dark Card
val SurfaceVariantDark = Color(0xFF2C2C2E)     // Elevated Dark Card Variant
val TextPrimaryDark = Color(0xFFFFFFFF)        // High-contrast pure white text
val TextSecondaryDark = Color(0xFF98989D)      // Accessible dark secondary label
val TextTertiaryDark = Color(0xFF636366)       // Subdued dark tertiary label
val DividerDark = Color(0xFF38383A)            // Subtle dark hairline
val BorderSubtleDark = Color(0x2EFFFFFF)       // Subtle 18% light hairline border

// Shimmer Animation Colors
val ShimmerBaseLight = Color(0xFFE5E5EA)
val ShimmerHighlightLight = Color(0xFFF8F8FA)
val ShimmerBaseDark = Color(0xFF2C2C2E)
val ShimmerHighlightDark = Color(0xFF3A3A3C)
