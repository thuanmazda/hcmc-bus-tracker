package com.hcmcbustracker.feature.map.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.DisplayMetrics
import android.util.LruCache
import com.hcmcbustracker.core.model.GpsFreshnessState
import com.hcmcbustracker.core.model.GpsLatencyCategory
import com.hcmcbustracker.core.model.PositionSource
import com.hcmcbustracker.core.model.VehicleArrivalStatus
import kotlin.math.roundToInt

/**
 * Centralized Phase 12 visual sizing, zoom threshold, and cache limits.
 */
object VehicleMapRenderConfig {
    const val NORMAL_COACH_DP = 38f
    const val SELECTED_COACH_DP = 50f
    const val SELECTED_HALO_DP = 60f
    const val USER_BLUE_DOT_DP = 22f
    const val STOP_CLUSTER_BADGE_DP = 30f

    const val MEDIUM_ZOOM_LABEL_MIN = 13.0f
    const val HIGH_ZOOM_LABEL_MIN = 14.8f
    const val STOP_CLUSTER_MAX_ZOOM = 13
    const val OVERLAP_TAP_RADIUS_DP = 44f

    const val ICON_POOL_MAX_ENTRIES = 32
    const val LABEL_CACHE_MAX_ENTRIES = 64
    const val CLUSTER_CACHE_MAX_ENTRIES = 32

    const val DEFAULT_ICON_SIZE = 1.0f
    const val LOW_ZOOM_ICON_SIZE = 0.95f
    const val HIGH_ZOOM_ICON_SIZE = 1.05f

    const val LABEL_NORMAL_OFFSET_Y_DP = 23f
    const val LABEL_SELECTED_OFFSET_Y_DP = 31f
}

/**
 * Visual freshness grouping for bus coach tone/border without creating a cluttered rainbow palette.
 */
enum class CoachFreshTier {
    ACTIVE,
    DELAYED,
    STALE_OR_HELD
}

/**
 * Small structural corner indicator separating arrival/position nuance beyond color alone.
 */
enum class CoachStatusBadge {
    NONE,
    ARRIVING,
    AT_STOP,
    PASSED,
    STALE_CLOCK,
    UNKNOWN_MARK
}

data class CoachIconSpec(
    val isSelected: Boolean,
    val freshTier: CoachFreshTier,
    val statusBadge: CoachStatusBadge,
    val hasHeading: Boolean,
    val isPredictedOrInterpolated: Boolean
) {
    fun toIconId(): String {
        return "hcmc-coach-${isSelected}-${freshTier.name}-${statusBadge.name}-${hasHeading}-${isPredictedOrInterpolated}"
    }
}

/**
 * Logical dp geometry bounds for a coach icon canvas, shared verbatim by Canvas drawing and unit tests
 * to guarantee zero pixel clipping across all densities.
 */
data class CoachCanvasGeometry(
    val canvasDp: Float,
    val cxDp: Float,
    val cyDp: Float,
    val bodyWidthDp: Float,
    val bodyHeightDp: Float,
    val casingDp: Float,
    val radiusDp: Float,
    val noseTipOffsetDp: Float,
    val noseBaseHalfDp: Float,
    val haloRadiusDp: Float,
    val haloStrokeDp: Float,
    val visualMinXDp: Float,
    val visualMinYDp: Float,
    val visualMaxXDp: Float,
    val visualMaxYDp: Float
) {
    val opaqueWidthDp: Float get() = visualMaxXDp - visualMinXDp
    val opaqueHeightDp: Float get() = visualMaxYDp - visualMinYDp
    val isStrictlyInsideCanvas: Boolean
        get() = visualMinXDp > 0f && visualMinYDp > 0f && visualMaxXDp < canvasDp && visualMaxYDp < canvasDp
}

enum class LabelZoomTier {
    SHORT,
    FULL
}

data class LabelIconSpec(
    val text: String,
    val tier: LabelZoomTier,
    val isSelected: Boolean,
    val isStale: Boolean
) {
    fun toIconId(): String {
        return "hcmc-lbl-${tier.name}-${text}-${isSelected}-${isStale}"
    }
}

/**
 * Generates density-accurate MapLibre-compatible Top-Down Bus Coach bitmaps,
 * adaptive plate pills, Google Maps Blue Dot, and Stop Cluster badges.
 *
 * Bytecode-verified against MapLibre Android 11.5.2 `Style.toImage`:
 * MapLibre sets `pixelRatio = bitmap.density / 160.0f` and renders icons at
 * `(bitmapWidthPx / pixelRatio) * SymbolLayer.iconSize` logical dp.
 * Setting `bitmap.density = safeDensityDpi` alongside `px = round(targetDp * scale)`
 * guarantees exact `targetDp` sizing across `mdpi`..`xxxhdpi`.
 */
class VehicleMarkerBitmapFactory(
    densityDpi: Int = DisplayMetrics.DENSITY_MEDIUM
) {
    private val lock = Any()

    val safeDensityDpi: Int = densityDpi.coerceAtLeast(DisplayMetrics.DENSITY_MEDIUM)
    val scaleFactor: Float = safeDensityDpi / 160f

    private val coachPool = object : LruCache<String, Bitmap>(VehicleMapRenderConfig.ICON_POOL_MAX_ENTRIES) {}
    private val labelPool = object : LruCache<String, Bitmap>(VehicleMapRenderConfig.LABEL_CACHE_MAX_ENTRIES) {}
    private val clusterPool = object : LruCache<String, Bitmap>(VehicleMapRenderConfig.CLUSTER_CACHE_MAX_ENTRIES) {}

    private var cachedBlueDot: Bitmap? = null

    fun dpToPx(dp: Float): Int {
        return (dp * scaleFactor).roundToInt().coerceAtLeast(1)
    }

    fun getOrCreateCoachBitmap(spec: CoachIconSpec): Bitmap = synchronized(lock) {
        val key = "${spec.toIconId()}@${safeDensityDpi}"
        coachPool.get(key)?.let { return it }
        val bmp = renderCoachBitmap(spec)
        coachPool.put(key, bmp)
        return bmp
    }

    fun getOrCreateLabelBitmap(spec: LabelIconSpec): Bitmap = synchronized(lock) {
        val key = "${spec.toIconId()}@${safeDensityDpi}"
        labelPool.get(key)?.let { return it }
        val bmp = renderLabelBitmap(spec)
        labelPool.put(key, bmp)
        return bmp
    }

    fun getOrCreateStopClusterBitmap(clusterCount: Int): Bitmap = synchronized(lock) {
        val display = if (clusterCount > 99) "99+" else clusterCount.coerceAtLeast(2).toString()
        val key = "cluster_${display}@${safeDensityDpi}"
        clusterPool.get(key)?.let { return it }
        val bmp = renderStopClusterBitmap(display)
        clusterPool.put(key, bmp)
        return bmp
    }

    fun getOrCreateBlueDotBitmap(): Bitmap = synchronized(lock) {
        cachedBlueDot?.let { return it }
        val targetDp = VehicleMapRenderConfig.USER_BLUE_DOT_DP + 6f
        val sizePx = dpToPx(targetDp)
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).apply {
            density = safeDensityDpi
        }
        val canvas = Canvas(bmp)
        val c = sizePx / 2f

        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(52, 26, 115, 232)
            style = Paint.Style.FILL
        }
        canvas.drawCircle(c, c, dpToPxF(13f), shadowPaint)

        val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(c, c, dpToPxF(10.5f), whitePaint)

        val bluePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1A73E8")
            style = Paint.Style.FILL
        }
        canvas.drawCircle(c, c, dpToPxF(7.8f), bluePaint)

        cachedBlueDot = bmp
        return bmp
    }

    /**
     * Releases pool references cleanly on memory pressure WITHOUT recycling bitmaps
     * while MapLibre's GL thread may still hold GPU upload textures.
     */
    fun trimCachesOnLowMemory() = synchronized(lock) {
        labelPool.evictAll()
        clusterPool.evictAll()
        coachPool.trimToSize(12)
    }

    fun coachPoolSize(): Int = synchronized(lock) { coachPool.size() }
    fun labelPoolSize(): Int = synchronized(lock) { labelPool.size() }

    private fun dpToPxF(dp: Float): Float = dp * scaleFactor

    private fun renderCoachBitmap(spec: CoachIconSpec): Bitmap {
        val geom = computeCoachGeometry(spec)
        val sizePx = dpToPx(geom.canvasDp)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).apply {
            density = safeDensityDpi
        }
        val canvas = Canvas(bitmap)
        val cx = dpToPxF(geom.cxDp)
        val cy = dpToPxF(geom.cyDp)

        // 1. Selected 60dp Amber Halo Ring (outer diameter ~58.8 dp within 56-62 dp)
        if (spec.isSelected) {
            val haloRadius = dpToPxF(geom.haloRadiusDp)
            val haloFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(62, 245, 158, 11)
                style = Paint.Style.FILL
            }
            val haloBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#F59E0B")
                style = Paint.Style.STROKE
                strokeWidth = dpToPxF(geom.haloStrokeDp)
            }
            canvas.drawCircle(cx, cy, haloRadius, haloFill)
            canvas.drawCircle(cx, cy, haloRadius, haloBorder)
        }

        // Coach geometry dimensions derived directly from shared geometry spec
        val halfW = dpToPxF(geom.bodyWidthDp / 2f)
        val halfH = dpToPxF(geom.bodyHeightDp / 2f)
        val casingPx = dpToPxF(geom.casingDp)
        val radiusPx = dpToPxF(geom.radiusDp)

        // Colors: cohesive, high outdoor contrast without visual clutter
        val coachFillColor = when {
            spec.isSelected -> Color.parseColor("#D32F2F")
            spec.freshTier == CoachFreshTier.STALE_OR_HELD -> Color.parseColor("#64748B")
            else -> Color.parseColor("#00796B")
        }
        val windshieldColor = when {
            spec.isSelected -> Color.parseColor("#FECACA")
            spec.freshTier == CoachFreshTier.STALE_OR_HELD -> Color.parseColor("#CBD5E1")
            else -> Color.parseColor("#B2DFDB")
        }
        val roofDetailColor = when {
            spec.isSelected -> Color.parseColor("#7F1D1D")
            spec.freshTier == CoachFreshTier.STALE_OR_HELD -> Color.parseColor("#475569")
            else -> Color.parseColor("#004D40")
        }

        // 2. Drop shadow below coach for contrast against light bright tiles
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(65, 15, 23, 42)
            style = Paint.Style.FILL
        }
        val shadowRect = RectF(
            cx - halfW - casingPx * 0.7f,
            cy - halfH - casingPx * 0.4f + dpToPxF(1.5f),
            cx + halfW + casingPx * 0.7f,
            cy + halfH + casingPx * 0.7f + dpToPxF(1.5f)
        )
        canvas.drawRoundRect(shadowRect, radiusPx + casingPx * 0.4f, radiusPx + casingPx * 0.4f, shadowPaint)

        // 3. Directional Heading Nose Cone (ONLY when hasHeading == true)
        if (spec.hasHeading) {
            val tipY = cy - halfH - dpToPxF(geom.noseTipOffsetDp)
            val baseY = cy - halfH - casingPx * 0.35f
            val baseHalfX = dpToPxF(geom.noseBaseHalfDp)

            val noseCasing = Path().apply {
                moveTo(cx, tipY - casingPx * 0.9f)
                lineTo(cx + baseHalfX + casingPx * 0.8f, baseY + dpToPxF(1f))
                lineTo(cx - baseHalfX - casingPx * 0.8f, baseY + dpToPxF(1f))
                close()
            }
            val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }
            canvas.drawPath(noseCasing, whitePaint)

            val noseInner = Path().apply {
                moveTo(cx, tipY)
                lineTo(cx + baseHalfX, baseY + dpToPxF(1.2f))
                lineTo(cx - baseHalfX, baseY + dpToPxF(1.2f))
                close()
            }
            val nosePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = coachFillColor
                style = Paint.Style.FILL
            }
            canvas.drawPath(noseInner, nosePaint)
        }

        // 4. Outer White Casing + Optional Status Rim (Delayed / Stale / Arriving)
        val outerRect = RectF(
            cx - halfW - casingPx,
            cy - halfH - casingPx,
            cx + halfW + casingPx,
            cy + halfH + casingPx
        )
        val casingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = when {
                !spec.isSelected && spec.statusBadge == CoachStatusBadge.ARRIVING -> Color.parseColor("#F59E0B")
                !spec.isSelected && spec.freshTier == CoachFreshTier.DELAYED -> Color.parseColor("#FDE68A")
                else -> Color.WHITE
            }
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(outerRect, radiusPx + casingPx * 0.65f, radiusPx + casingPx * 0.65f, casingPaint)

        // Inner white separator if using amber rim, or dashed border for STALE/HELD
        if (!spec.isSelected && spec.freshTier == CoachFreshTier.STALE_OR_HELD) {
            val dashedBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#94A3B8")
                style = Paint.Style.STROKE
                strokeWidth = dpToPxF(1.4f)
                pathEffect = DashPathEffect(floatArrayOf(dpToPxF(3f), dpToPxF(2f)), 0f)
            }
            canvas.drawRoundRect(outerRect, radiusPx + casingPx * 0.65f, radiusPx + casingPx * 0.65f, dashedBorderPaint)
        }

        // 5. Bus Coach Roof & Chassis Fill
        val bodyRect = RectF(cx - halfW, cy - halfH, cx + halfW, cy + halfH)
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = coachFillColor
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(bodyRect, radiusPx, radiusPx, fillPaint)

        // 6. Panoramic Front Windshield + Headlights + Roof AC Pod (signals Top-Down Bus clearly)
        val wsMarginX = halfW * 0.22f
        val wsTop = cy - halfH + halfH * 0.16f
        val wsBottom = wsTop + halfH * 0.38f
        val wsRect = RectF(cx - halfW + wsMarginX, wsTop, cx + halfW - wsMarginX, wsBottom)
        val wsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = windshieldColor
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(wsRect, dpToPxF(2.2f), dpToPxF(2.2f), wsPaint)

        // Micro headlights at front corners when heading is valid
        if (spec.hasHeading) {
            val lightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#FEF08A")
                style = Paint.Style.FILL
            }
            val lxOffset = halfW * 0.56f
            val ly = cy - halfH + dpToPxF(2.0f)
            val lr = dpToPxF(if (spec.isSelected) 1.5f else 1.2f)
            canvas.drawCircle(cx - lxOffset, ly, lr, lightPaint)
            canvas.drawCircle(cx + lxOffset, ly, lr, lightPaint)
        }

        // Roof AC module rectangle (mid-rear coach detail)
        val acRect = RectF(
            cx - halfW * 0.48f,
            cy - dpToPxF(1.5f),
            cx + halfW * 0.48f,
            cy + halfH * 0.58f
        )
        val acPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = roofDetailColor
            alpha = 150
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(acRect, dpToPxF(2f), dpToPxF(2f), acPaint)

        // Subtle forward projection cue arc or badge for non-standard status/freshness
        drawCornerStatusBadge(canvas, cx + halfW + dpToPxF(1.0f), cy - halfH + dpToPxF(1.5f), spec)

        return bitmap
    }

    private fun drawCornerStatusBadge(
        canvas: Canvas,
        bx: Float,
        by: Float,
        spec: CoachIconSpec
    ) {
        if (spec.statusBadge == CoachStatusBadge.NONE && spec.hasHeading) return

        val effectiveBadge = when {
            spec.statusBadge != CoachStatusBadge.NONE -> spec.statusBadge
            !spec.hasHeading -> CoachStatusBadge.AT_STOP
            else -> return
        }

        val r = dpToPxF(5.2f)
        val badgeColor = when (effectiveBadge) {
            CoachStatusBadge.ARRIVING -> Color.parseColor("#F59E0B")
            CoachStatusBadge.AT_STOP -> Color.parseColor("#D97706")
            CoachStatusBadge.PASSED -> Color.parseColor("#EA580C")
            CoachStatusBadge.STALE_CLOCK -> Color.parseColor("#475569")
            CoachStatusBadge.UNKNOWN_MARK -> Color.parseColor("#6B7280")
            CoachStatusBadge.NONE -> return
        }

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(bx, by, r + dpToPxF(1.2f), strokePaint)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = badgeColor
            style = Paint.Style.FILL
        }
        canvas.drawCircle(bx, by, r, bgPaint)

        val symbolPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = dpToPxF(1.3f)
            strokeCap = Paint.Cap.ROUND
        }
        when (effectiveBadge) {
            CoachStatusBadge.AT_STOP -> {
                // Pause || bars
                val dx = dpToPxF(1.4f)
                val dy = dpToPxF(2.2f)
                canvas.drawLine(bx - dx, by - dy, bx - dx, by + dy, symbolPaint)
                canvas.drawLine(bx + dx, by - dy, bx + dx, by + dy, symbolPaint)
            }
            CoachStatusBadge.STALE_CLOCK -> {
                // Clock hands
                canvas.drawLine(bx, by - dpToPxF(2.4f), bx, by, symbolPaint)
                canvas.drawLine(bx, by, bx + dpToPxF(2.0f), by + dpToPxF(1.0f), symbolPaint)
            }
            CoachStatusBadge.ARRIVING, CoachStatusBadge.PASSED -> {
                val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    style = Paint.Style.FILL
                }
                canvas.drawCircle(bx, by, dpToPxF(1.8f), dotPaint)
            }
            CoachStatusBadge.UNKNOWN_MARK -> {
                val txtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    textSize = dpToPxF(7.5f)
                    typeface = Typeface.DEFAULT_BOLD
                    textAlign = Paint.Align.CENTER
                }
                val fm = txtPaint.fontMetrics
                val ty = by - (fm.ascent + fm.descent) / 2f
                canvas.drawText("?", bx, ty, txtPaint)
            }
            CoachStatusBadge.NONE -> {}
        }
    }

    private fun renderLabelBitmap(spec: LabelIconSpec): Bitmap {
        val text = spec.text.ifBlank { "BUS" }
        val textDp = when {
            spec.isSelected -> 11.5f
            spec.tier == LabelZoomTier.SHORT -> 9.8f
            else -> 10.6f
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = dpToPxF(textDp)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val measuredTextWidth = maxOf(
            textPaint.measureText(text),
            text.length * dpToPxF(textDp * 0.54f)
        )
        val horizPadPx = dpToPxF(if (spec.isSelected) 9f else 7f)
        val pillWidthPx = (measuredTextWidth + horizPadPx * 2f).coerceAtLeast(dpToPxF(36f))
        val pillHeightDp = when {
            spec.isSelected -> 23f
            spec.tier == LabelZoomTier.SHORT -> 18f
            else -> 20.5f
        }
        val pillHeightPx = dpToPxF(pillHeightDp)
        val marginPx = dpToPxF(3f)

        val widthPx = (pillWidthPx + marginPx * 2f).roundToInt().coerceAtLeast(1)
        val heightPx = (pillHeightPx + marginPx * 2f).roundToInt().coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888).apply {
            density = safeDensityDpi
        }
        val canvas = Canvas(bitmap)
        val rect = RectF(marginPx, marginPx, widthPx - marginPx, heightPx - marginPx)
        val cornerRadius = dpToPxF(6f)

        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(65, 15, 23, 42)
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(
            RectF(rect.left, rect.top + dpToPxF(1f), rect.right, rect.bottom + dpToPxF(1f)),
            cornerRadius,
            cornerRadius,
            shadowPaint
        )

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = when {
                spec.isSelected -> Color.parseColor("#B71C1C")
                spec.isStale -> Color.parseColor("#475569")
                else -> Color.parseColor("#1E293B")
            }
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (spec.isSelected) Color.parseColor("#FBBF24") else Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = dpToPxF(if (spec.isSelected) 2.0f else 1.5f)
        }
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, strokePaint)

        val fm = textPaint.fontMetrics
        val textY = (heightPx / 2f) - (fm.ascent + fm.descent) / 2f
        canvas.drawText(text, widthPx / 2f, textY, textPaint)
        return bitmap
    }

    private fun renderStopClusterBitmap(countText: String): Bitmap {
        val diameterDp = VehicleMapRenderConfig.STOP_CLUSTER_BADGE_DP + 4f
        val sizePx = dpToPx(diameterDp)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).apply {
            density = safeDensityDpi
        }
        val canvas = Canvas(bitmap)
        val c = sizePx / 2f
        val r = dpToPxF(VehicleMapRenderConfig.STOP_CLUSTER_BADGE_DP / 2f)

        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(55, 15, 23, 42)
            style = Paint.Style.FILL
        }
        canvas.drawCircle(c, c + dpToPxF(1.2f), r, shadowPaint)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1D4ED8")
            style = Paint.Style.FILL
        }
        canvas.drawCircle(c, c, r, bgPaint)

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = dpToPxF(2.2f)
        }
        canvas.drawCircle(c, c, r, strokePaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = dpToPxF(11.5f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val fm = textPaint.fontMetrics
        val ty = c - (fm.ascent + fm.descent) / 2f
        canvas.drawText(countText, c, ty, textPaint)
        return bitmap
    }

    companion object {
        fun computeCoachGeometry(spec: CoachIconSpec): CoachCanvasGeometry {
            val canvasDp = if (spec.isSelected) {
                VehicleMapRenderConfig.SELECTED_HALO_DP
            } else {
                VehicleMapRenderConfig.NORMAL_COACH_DP + 6f
            }
            val cxDp = canvasDp / 2f
            val cyDp = canvasDp / 2f
            val bodyWidthDp = if (spec.isSelected) 22.5f else 17.5f
            val bodyHeightDp = if (spec.isSelected) 36.0f else 28.0f
            val casingDp = if (spec.isSelected) 2.8f else 2.2f
            val radiusDp = if (spec.isSelected) 6.5f else 5.2f
            // Normal nose offset tuned from 7.0 -> 4.6 dp (+ 2.2*0.9 casing = 6.58 dp above body)
            // so top vertex sits at +1.42 dp inside the 44 dp canvas (zero clipping) while keeping
            // total opaque height = 36.78 dp (strictly within 36-40 dp).
            val noseTipOffsetDp = if (spec.isSelected) 8.5f else 4.6f
            val noseBaseHalfDp = if (spec.isSelected) 6.8f else 5.2f
            val haloRadiusDp = if (spec.isSelected) VehicleMapRenderConfig.SELECTED_HALO_DP * 0.475f else 0f
            val haloStrokeDp = if (spec.isSelected) 1.8f else 0f

            val halfW = bodyWidthDp / 2f
            val halfH = bodyHeightDp / 2f
            val hasDashed = !spec.isSelected && spec.freshTier == CoachFreshTier.STALE_OR_HELD
            val dashedPad = if (hasDashed) 0.7f else 0f

            val outerLeft = cxDp - halfW - casingDp - dashedPad
            val outerTop = cyDp - halfH - casingDp - dashedPad
            val outerRight = cxDp + halfW + casingDp + dashedPad
            val outerBottom = cyDp + halfH + casingDp + dashedPad

            val shadowBottom = cyDp + halfH + casingDp * 0.7f + 1.5f
            val noseTop = if (spec.hasHeading) {
                cyDp - halfH - noseTipOffsetDp - casingDp * 0.9f
            } else {
                outerTop
            }

            val hasBadge = spec.statusBadge != CoachStatusBadge.NONE || !spec.hasHeading
            val badgeRight = if (hasBadge) (cxDp + halfW + 1.0f) + 6.4f else outerRight
            val badgeTop = if (hasBadge) (cyDp - halfH + 1.5f) - 6.4f else outerTop

            val haloOut = if (spec.isSelected) haloRadiusDp + haloStrokeDp / 2f else 0f
            val haloMinX = if (spec.isSelected) cxDp - haloOut else Float.POSITIVE_INFINITY
            val haloMinY = if (spec.isSelected) cyDp - haloOut else Float.POSITIVE_INFINITY
            val haloMaxX = if (spec.isSelected) cxDp + haloOut else Float.NEGATIVE_INFINITY
            val haloMaxY = if (spec.isSelected) cyDp + haloOut else Float.NEGATIVE_INFINITY

            val minX = minOf(outerLeft, haloMinX)
            val minY = minOf(outerTop, noseTop, badgeTop, haloMinY)
            val maxX = maxOf(outerRight, badgeRight, haloMaxX)
            val maxY = maxOf(outerBottom, shadowBottom, haloMaxY)

            return CoachCanvasGeometry(
                canvasDp = canvasDp,
                cxDp = cxDp,
                cyDp = cyDp,
                bodyWidthDp = bodyWidthDp,
                bodyHeightDp = bodyHeightDp,
                casingDp = casingDp,
                radiusDp = radiusDp,
                noseTipOffsetDp = noseTipOffsetDp,
                noseBaseHalfDp = noseBaseHalfDp,
                haloRadiusDp = haloRadiusDp,
                haloStrokeDp = haloStrokeDp,
                visualMinXDp = minX,
                visualMinYDp = minY,
                visualMaxXDp = maxX,
                visualMaxYDp = maxY
            )
        }

        fun computeBitmapSizePx(targetDp: Float, densityDpi: Int): Int {
            val safeDpi = densityDpi.coerceAtLeast(DisplayMetrics.DENSITY_MEDIUM)
            return (targetDp * (safeDpi / 160f)).roundToInt().coerceAtLeast(1)
        }

        fun computeRenderedDp(bitmapWidthPx: Int, bitmapDensityDpi: Int, iconSize: Float): Float {
            val safeDpi = bitmapDensityDpi.coerceAtLeast(DisplayMetrics.DENSITY_MEDIUM)
            val pixelRatio = safeDpi / 160f
            return (bitmapWidthPx / pixelRatio) * iconSize
        }

        fun mapFreshTier(
            freshnessState: GpsFreshnessState,
            latencyCategory: GpsLatencyCategory,
            positionSource: PositionSource
        ): CoachFreshTier {
            return when {
                positionSource == PositionSource.HELD_AT_LAST_VALID_GPS -> CoachFreshTier.STALE_OR_HELD
                freshnessState == GpsFreshnessState.STALE ||
                    freshnessState == GpsFreshnessState.UNCHANGED ||
                    latencyCategory == GpsLatencyCategory.STALE -> CoachFreshTier.STALE_OR_HELD
                freshnessState == GpsFreshnessState.AGING ||
                    latencyCategory == GpsLatencyCategory.DELAYED ||
                    latencyCategory == GpsLatencyCategory.SLIGHT_DELAY -> CoachFreshTier.DELAYED
                (freshnessState == GpsFreshnessState.UNKNOWN && latencyCategory == GpsLatencyCategory.UNKNOWN) ||
                    positionSource == PositionSource.UNKNOWN -> CoachFreshTier.STALE_OR_HELD
                else -> CoachFreshTier.ACTIVE
            }
        }

        fun mapStatusBadge(
            arrivalStatus: VehicleArrivalStatus,
            freshTier: CoachFreshTier,
            hasHeading: Boolean
        ): CoachStatusBadge {
            return when {
                arrivalStatus == VehicleArrivalStatus.UNKNOWN -> CoachStatusBadge.UNKNOWN_MARK
                freshTier == CoachFreshTier.STALE_OR_HELD -> CoachStatusBadge.STALE_CLOCK
                arrivalStatus == VehicleArrivalStatus.AT_STOP -> CoachStatusBadge.AT_STOP
                arrivalStatus == VehicleArrivalStatus.ARRIVING_AT_STOP -> CoachStatusBadge.ARRIVING
                arrivalStatus == VehicleArrivalStatus.PASSED_STOP -> CoachStatusBadge.PASSED
                !hasHeading -> CoachStatusBadge.AT_STOP
                else -> CoachStatusBadge.NONE
            }
        }

        fun formatShortPlate(formattedPlate: String): String {
            val trimmed = formattedPlate.trim()
            if (trimmed.isBlank()) return "BUS"
            val dashIdx = trimmed.indexOf('-')
            if (dashIdx in 1 until trimmed.lastIndex) {
                return trimmed.substring(dashIdx + 1)
            }
            return if (trimmed.length > 6) trimmed.takeLast(6) else trimmed
        }
    }
}
