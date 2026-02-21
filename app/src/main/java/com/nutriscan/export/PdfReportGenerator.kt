package com.nutriscan.export

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import com.nutriscan.data.repository.WeeklyReportData
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.max

/**
 * Generates a multi-section PDF report using Android's native
 * [PdfDocument] + [Canvas] API.
 *
 * Sections:
 * 1. Header (title, date range, user profile)
 * 2. Net-calorie 7-day chart + deviation %
 * 3. Macro summary bars
 * 4. Activity stats grid
 * 5. Water intake chart
 * 6. Achievements / streaks
 */
object PdfReportGenerator {

    // Page dimensions (A4 at 72 dpi)
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 40f
    private const val CONTENT_W = PAGE_W - 2 * MARGIN

    // Colours
    private val COLOR_PRIMARY = Color.parseColor("#1B5E20")
    private val COLOR_ACCENT = Color.parseColor("#4CAF50")
    private val COLOR_BURNED = Color.parseColor("#FF7043")
    private val COLOR_EATEN = Color.parseColor("#66BB6A")
    private val COLOR_NET = Color.parseColor("#1E88E5")
    private val COLOR_PROTEIN = Color.parseColor("#4CAF50")
    private val COLOR_CARBS = Color.parseColor("#FF9800")
    private val COLOR_FAT = Color.parseColor("#F44336")
    private val COLOR_WATER = Color.parseColor("#2196F3")
    private val COLOR_GREY_BG = Color.parseColor("#F5F5F5")
    private val COLOR_LIGHT_BORDER = Color.parseColor("#E0E0E0")
    private val COLOR_TEXT = Color.parseColor("#212121")
    private val COLOR_TEXT_SEC = Color.parseColor("#757575")

    fun generate(context: Context, data: WeeklyReportData): File {
        val document = PdfDocument()

        // ---- PAGE 1 ----
        val pageInfo1 = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create()
        val page1 = document.startPage(pageInfo1)
        var y = drawPage1(page1.canvas, data)
        document.finishPage(page1)

        // ---- PAGE 2 (if needed) ----
        val pageInfo2 = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 2).create()
        val page2 = document.startPage(pageInfo2)
        drawPage2(page2.canvas, data)
        document.finishPage(page2)

        // Save to cache
        val dir = File(context.cacheDir, "reports")
        dir.mkdirs()
        val file = File(dir, "NutriScan_Weekly_Report.pdf")
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        return file
    }

    // ===================== PAGE 1 =====================

    private fun drawPage1(c: Canvas, d: WeeklyReportData): Float {
        var y = MARGIN

        y = drawHeader(c, d, y)
        y += 10f
        y = drawSectionTitle(c, "📊  Net Calorie Results", y)
        y = drawCalorieChart(c, d, y)
        y += 10f
        y = drawCalorieSummaryRow(c, d, y)
        y += 16f
        y = drawSectionTitle(c, "🥩  Macro Breakdown", y)
        y = drawMacroSection(c, d, y)

        return y
    }

    // ===================== PAGE 2 =====================

    private fun drawPage2(c: Canvas, d: WeeklyReportData) {
        var y = MARGIN

        y = drawSectionTitle(c, "🚶  Activity Summary", y)
        y = drawActivitySection(c, d, y)
        y += 16f
        y = drawSectionTitle(c, "💧  Water Intake", y)
        y = drawWaterChart(c, d, y)
        y += 16f
        y = drawSectionTitle(c, "🏆  Achievements", y)
        y = drawAchievements(c, d, y)
        y += 20f
        drawFooter(c, d)
    }

    // ===================== HEADER =====================

    private fun drawHeader(c: Canvas, d: WeeklyReportData, startY: Float): Float {
        var y = startY

        // Green header bar
        val headerPaint = Paint().apply { color = COLOR_PRIMARY; style = Paint.Style.FILL }
        c.drawRoundRect(MARGIN, y, PAGE_W - MARGIN, y + 90f, 12f, 12f, headerPaint)

        // Title
        val titlePaint = textPaint(24f, Color.WHITE, true)
        c.drawText("NutriScan Weekly Report", MARGIN + 16f, y + 35f, titlePaint)

        // Date range
        val subPaint = textPaint(12f, Color.parseColor("#C8E6C9"), false)
        c.drawText("${d.startDate}  →  ${d.endDate}", MARGIN + 16f, y + 55f, subPaint)

        // User info
        val gender = if (d.isFemale) "Female" else "Male"
        val profileText = "${d.userWeightKg} kg  •  ${d.userHeightCm} cm  •  Age ${d.userAge}  •  $gender"
        c.drawText(profileText, MARGIN + 16f, y + 75f, subPaint)

        y += 100f
        return y
    }

    // ===================== CALORIE CHART =====================

    private fun drawCalorieChart(c: Canvas, d: WeeklyReportData, startY: Float): Float {
        val data = d.dailyNet
        if (data.isEmpty()) {
            val p = textPaint(12f, COLOR_TEXT_SEC, false)
            c.drawText("No calorie data available for this week.", MARGIN, startY + 20f, p)
            return startY + 40f
        }

        val chartH = 160f
        val chartTop = startY + 10f
        val chartBot = chartTop + chartH
        val barAreaW = CONTENT_W
        val barGroupW = barAreaW / data.size
        val barW = barGroupW * 0.32f
        val gap = barGroupW * 0.04f

        // Background
        val bgPaint = Paint().apply { color = COLOR_GREY_BG; style = Paint.Style.FILL }
        c.drawRoundRect(MARGIN, chartTop - 5f, PAGE_W - MARGIN, chartBot + 30f, 8f, 8f, bgPaint)

        // Find max value for scaling
        val maxVal = max(
            data.maxOf { maxOf(it.eatenKcal, it.burnedKcal, abs(it.netKcal)) },
            d.targetCalories
        ).coerceAtLeast(1)

        // Target line
        val targetY = chartBot - (d.targetCalories.toFloat() / maxVal) * chartH
        val targetPaint = Paint().apply {
            color = Color.parseColor("#9E9E9E"); strokeWidth = 1.5f
            style = Paint.Style.STROKE
            pathEffect = DashPathEffect(floatArrayOf(6f, 4f), 0f)
        }
        c.drawLine(MARGIN, targetY, PAGE_W - MARGIN, targetY, targetPaint)
        val targetLabel = textPaint(8f, COLOR_TEXT_SEC, false)
        c.drawText("Target ${d.targetCalories}", PAGE_W - MARGIN - 60f, targetY - 3f, targetLabel)

        // Bars
        val eatenPaint = Paint().apply { color = COLOR_EATEN; style = Paint.Style.FILL }
        val burnedPaint = Paint().apply { color = COLOR_BURNED; style = Paint.Style.FILL }
        val netPaint = Paint().apply { color = COLOR_NET; style = Paint.Style.FILL }
        val dayLabel = textPaint(8f, COLOR_TEXT_SEC, false).apply { textAlign = Paint.Align.CENTER }

        for ((i, day) in data.withIndex()) {
            val x = MARGIN + i * barGroupW

            // Eaten bar
            val eH = (day.eatenKcal.toFloat() / maxVal) * chartH
            c.drawRoundRect(x + gap, chartBot - eH, x + gap + barW, chartBot, 3f, 3f, eatenPaint)

            // Burned bar
            val bH = (day.burnedKcal.toFloat() / maxVal) * chartH
            c.drawRoundRect(x + gap + barW + 2f, chartBot - bH, x + gap + barW * 2 + 2f, chartBot, 3f, 3f, burnedPaint)

            // Net dot
            val nH = (abs(day.netKcal).toFloat() / maxVal) * chartH
            val dotY = chartBot - nH
            val dotPaint = Paint().apply {
                color = if (day.netKcal >= 0) COLOR_NET else COLOR_BURNED
                style = Paint.Style.FILL
            }
            c.drawCircle(x + gap + barW + 1f, dotY.coerceIn(chartTop, chartBot), 4f, dotPaint)

            // Day label (Mon, Tue, ...)
            val label = day.day.takeLast(5) // "MM-DD"
            c.drawText(label, x + barGroupW / 2, chartBot + 14f, dayLabel)
        }

        // Legend
        val ly = chartBot + 24f
        drawLegendDot(c, MARGIN, ly, COLOR_EATEN, "Eaten")
        drawLegendDot(c, MARGIN + 70f, ly, COLOR_BURNED, "Burned")
        drawLegendDot(c, MARGIN + 145f, ly, COLOR_NET, "Net")

        return chartBot + 38f
    }

    private fun drawCalorieSummaryRow(c: Canvas, d: WeeklyReportData, startY: Float): Float {
        val y = startY
        val boxW = CONTENT_W / 3f

        // Avg Net
        drawStatBox(c, MARGIN, y, boxW - 4f, "Avg Net", "${d.avgNetCalories} kcal", COLOR_NET)
        // Target
        drawStatBox(c, MARGIN + boxW, y, boxW - 4f, "Daily Target", "${d.targetCalories} kcal", COLOR_ACCENT)
        // Deviation
        val devLabel = if (d.calorieDeviationPct >= 0) "+${"%.1f".format(d.calorieDeviationPct)}%"
            else "${"%.1f".format(d.calorieDeviationPct)}%"
        val devColor = if (abs(d.calorieDeviationPct) <= 10) COLOR_ACCENT else COLOR_BURNED
        drawStatBox(c, MARGIN + boxW * 2, y, boxW - 4f, "Deviation", devLabel, devColor)

        return y + 58f
    }

    // ===================== MACROS =====================

    private fun drawMacroSection(c: Canvas, d: WeeklyReportData, startY: Float): Float {
        var y = startY + 6f
        val m = d.avgMacros
        val total = m.protein + m.carbs + m.fat
        if (total <= 0f) {
            c.drawText("No macro data.", MARGIN, y + 14f, textPaint(11f, COLOR_TEXT_SEC, false))
            return y + 30f
        }

        // Stacked horizontal bar
        val barH = 22f
        val bgP = Paint().apply { color = COLOR_GREY_BG; style = Paint.Style.FILL }
        c.drawRoundRect(MARGIN, y, PAGE_W - MARGIN, y + barH, 6f, 6f, bgP)

        val pW = (m.protein / total) * CONTENT_W
        val cW = (m.carbs / total) * CONTENT_W
        val fW = (m.fat / total) * CONTENT_W

        val pP = Paint().apply { color = COLOR_PROTEIN; style = Paint.Style.FILL }
        val cP = Paint().apply { color = COLOR_CARBS; style = Paint.Style.FILL }
        val fP = Paint().apply { color = COLOR_FAT; style = Paint.Style.FILL }

        c.drawRoundRect(MARGIN, y, MARGIN + pW, y + barH, 6f, 6f, pP)
        c.drawRect(MARGIN + pW, y, MARGIN + pW + cW, y + barH, cP)
        c.drawRoundRect(MARGIN + pW + cW, y, MARGIN + pW + cW + fW, y + barH, 6f, 6f, fP)

        y += barH + 10f

        // Labels
        val lp = textPaint(10f, COLOR_TEXT, false)
        c.drawText("Protein: ${"%.0f".format(m.protein)}g  (${"%.0f".format(m.protein / total * 100)}%)", MARGIN, y + 10f, lp.apply { color = COLOR_PROTEIN })
        c.drawText("Carbs: ${"%.0f".format(m.carbs)}g  (${"%.0f".format(m.carbs / total * 100)}%)", MARGIN + 170f, y + 10f, lp.apply { color = COLOR_CARBS })
        c.drawText("Fat: ${"%.0f".format(m.fat)}g  (${"%.0f".format(m.fat / total * 100)}%)", MARGIN + 340f, y + 10f, lp.apply { color = COLOR_FAT })

        return y + 24f
    }

    // ===================== ACTIVITY =====================

    private fun drawActivitySection(c: Canvas, d: WeeklyReportData, startY: Float): Float {
        val y = startY + 4f
        val boxW = CONTENT_W / 3f

        drawStatBox(c, MARGIN, y, boxW - 4f, "Total Steps", String.format("%,d", d.totalSteps), COLOR_ACCENT)
        drawStatBox(c, MARGIN + boxW, y, boxW - 4f, "Distance", "${"%.2f".format(d.totalDistanceKm)} km", COLOR_PRIMARY)
        drawStatBox(c, MARGIN + boxW * 2, y, boxW - 4f, "Daily Avg", String.format("%,d steps", d.avgDailySteps), COLOR_NET)

        return y + 58f
    }

    // ===================== WATER =====================

    private fun drawWaterChart(c: Canvas, d: WeeklyReportData, startY: Float): Float {
        val water = d.dailyWater
        if (water.isEmpty()) {
            c.drawText("No water data.", MARGIN, startY + 14f, textPaint(11f, COLOR_TEXT_SEC, false))
            return startY + 30f
        }

        val chartH = 110f
        val chartTop = startY + 6f
        val chartBot = chartTop + chartH
        val barGroupW = CONTENT_W / water.size
        val barW = barGroupW * 0.6f

        val bgP = Paint().apply { color = COLOR_GREY_BG; style = Paint.Style.FILL }
        c.drawRoundRect(MARGIN, chartTop - 4f, PAGE_W - MARGIN, chartBot + 26f, 8f, 8f, bgP)

        val maxVal = max(water.maxOf { it.totalMl }, d.waterGoalMl).coerceAtLeast(1)

        // Goal line
        val goalY = chartBot - (d.waterGoalMl.toFloat() / maxVal) * chartH
        val goalPaint = Paint().apply {
            color = Color.parseColor("#1565C0"); strokeWidth = 1.5f
            style = Paint.Style.STROKE
            pathEffect = DashPathEffect(floatArrayOf(6f, 4f), 0f)
        }
        c.drawLine(MARGIN, goalY, PAGE_W - MARGIN, goalY, goalPaint)
        c.drawText("Goal ${d.waterGoalMl}ml", PAGE_W - MARGIN - 70f, goalY - 3f, textPaint(8f, COLOR_TEXT_SEC, false))

        val barPaint = Paint().apply { color = COLOR_WATER; style = Paint.Style.FILL }
        val dayLabel = textPaint(8f, COLOR_TEXT_SEC, false).apply { textAlign = Paint.Align.CENTER }

        for ((i, day) in water.withIndex()) {
            val x = MARGIN + i * barGroupW + (barGroupW - barW) / 2
            val h = (day.totalMl.toFloat() / maxVal) * chartH
            c.drawRoundRect(x, chartBot - h, x + barW, chartBot, 3f, 3f, barPaint)
            c.drawText(day.day.takeLast(5), MARGIN + i * barGroupW + barGroupW / 2, chartBot + 14f, dayLabel)
        }

        // Avg label
        c.drawText("Weekly avg: ${d.avgWaterMl} ml", MARGIN, chartBot + 24f, textPaint(9f, COLOR_TEXT_SEC, false))

        return chartBot + 32f
    }

    // ===================== ACHIEVEMENTS =====================

    private fun drawAchievements(c: Canvas, d: WeeklyReportData, startY: Float): Float {
        var y = startY + 4f

        // Streaks
        for (streak in d.streaks) {
            val label = "${streak.emoji}  ${streak.label}: ${streak.currentStreak}-day streak (best: ${streak.bestStreak})"
            c.drawText(label, MARGIN, y + 12f, textPaint(11f, COLOR_TEXT, false))
            y += 20f
        }

        y += 6f

        // Badges
        if (d.earnedBadges.isNotEmpty()) {
            c.drawText("Earned Badges:", MARGIN, y + 12f, textPaint(11f, COLOR_TEXT, true))
            y += 18f
            for (badge in d.earnedBadges) {
                c.drawText("${badge.emoji}  ${badge.title} — ${badge.description}", MARGIN + 10f, y + 12f, textPaint(10f, COLOR_ACCENT, false))
                y += 18f
            }
        } else {
            c.drawText("No badges earned yet — keep going! 💪", MARGIN, y + 12f, textPaint(10f, COLOR_TEXT_SEC, false))
            y += 18f
        }

        return y
    }

    // ===================== FOOTER =====================

    private fun drawFooter(c: Canvas, d: WeeklyReportData) {
        val y = PAGE_H - MARGIN
        val p = textPaint(8f, COLOR_TEXT_SEC, false).apply { textAlign = Paint.Align.CENTER }
        c.drawText("Generated by NutriScan  •  ${d.endDate}", PAGE_W / 2f, y, p)
    }

    // ===================== HELPERS =====================

    private fun drawSectionTitle(c: Canvas, title: String, y: Float): Float {
        val p = textPaint(14f, COLOR_PRIMARY, true)
        c.drawText(title, MARGIN, y + 16f, p)
        // Underline
        val lp = Paint().apply { color = COLOR_LIGHT_BORDER; strokeWidth = 1f }
        c.drawLine(MARGIN, y + 22f, PAGE_W - MARGIN, y + 22f, lp)
        return y + 28f
    }

    private fun drawStatBox(c: Canvas, x: Float, y: Float, w: Float, label: String, value: String, color: Int) {
        val bgP = Paint().apply { this.color = COLOR_GREY_BG; style = Paint.Style.FILL }
        c.drawRoundRect(x, y, x + w, y + 50f, 8f, 8f, bgP)
        val borderP = Paint().apply { this.color = COLOR_LIGHT_BORDER; style = Paint.Style.STROKE; strokeWidth = 1f }
        c.drawRoundRect(x, y, x + w, y + 50f, 8f, 8f, borderP)

        c.drawText(label, x + 8f, y + 16f, textPaint(9f, COLOR_TEXT_SEC, false))
        c.drawText(value, x + 8f, y + 38f, textPaint(14f, color, true))
    }

    private fun drawLegendDot(c: Canvas, x: Float, y: Float, color: Int, label: String) {
        val dotP = Paint().apply { this.color = color; style = Paint.Style.FILL }
        c.drawCircle(x + 5f, y, 4f, dotP)
        c.drawText(label, x + 14f, y + 4f, textPaint(9f, COLOR_TEXT, false))
    }

    private fun textPaint(size: Float, color: Int, bold: Boolean): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
    }
}
