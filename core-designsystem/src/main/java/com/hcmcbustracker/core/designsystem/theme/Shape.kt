package com.hcmcbustracker.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes

/**
 * Standard Material 3 Shape hierarchy configured with Apple HIG corner smoothing curves.
 */
val HcmcShapes = Shapes(
    extraSmall = RoundedCornerShape(TransitDimens.RadiusXs),
    small = RoundedCornerShape(TransitDimens.RadiusSm),
    medium = RoundedCornerShape(TransitDimens.RadiusMd),
    large = RoundedCornerShape(TransitDimens.RadiusCard),
    extraLarge = RoundedCornerShape(TransitDimens.RadiusSheet)
)

val PillShape = RoundedCornerShape(TransitDimens.RadiusPill)
val CardShape = RoundedCornerShape(TransitDimens.RadiusCard)
val BottomSheetShape = RoundedCornerShape(
    topStart = TransitDimens.RadiusSheet,
    topEnd = TransitDimens.RadiusSheet,
    bottomStart = TransitDimens.RadiusNone,
    bottomEnd = TransitDimens.RadiusNone
)
val BadgeShape = RoundedCornerShape(TransitDimens.RadiusSm)
