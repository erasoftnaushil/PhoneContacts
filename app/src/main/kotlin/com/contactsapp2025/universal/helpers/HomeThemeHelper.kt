package com.contactsapp2025.universal.helpers

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.util.TypedValue
import com.contactsapp2025.universal.extensions.config

data class HomePalette(
    val isDark: Boolean,
    val pageBg: Int,
    val searchBg: Int,
    val searchHint: Int,
    val primaryBlue: Int,
    val fabAddBg: Int,
    val fabAddIcon: Int,
    val chipUnselectedBg: Int,
    val chipUnselectedText: Int,
    val sectionHeaderText: Int,
    val cardBg: Int,
    val itemTitleText: Int,
    val itemSubtitleText: Int,
    val itemDivider: Int,
    val callBtnBg: Int,
    val callBtnIcon: Int,
    val chevronTint: Int,
    val bottomNavBg: Int,
    val bottomNavPill: Int,
    val bottomNavActive: Int,
    val bottomNavUnselected: Int
)

object HomeThemeHelper {

    val AVATAR_COLORS = intArrayOf(
        Color.parseColor("#F45BA0"),
        Color.parseColor("#36B9E9"),
        Color.parseColor("#A855F7"),
        Color.parseColor("#EB6351"),
        Color.parseColor("#4CAF6A"),
        Color.parseColor("#F58B32"),
        Color.parseColor("#F4C430")
    )

    fun isDarkTheme(context: Context): Boolean {
        return when (context.config.theme) {
            Settings.THEME_DARK -> true
            Settings.THEME_LIGHT -> false
            else -> {
                val nightMask = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                nightMask == Configuration.UI_MODE_NIGHT_YES
            }
        }
    }

    fun getPalette(context: Context): HomePalette {
        val dark = isDarkTheme(context)
        return if (dark) {
            HomePalette(
                isDark = true,
                pageBg = Color.parseColor("#12131A"),
                searchBg = Color.parseColor("#222533"),
                searchHint = Color.parseColor("#94A3B8"),
                primaryBlue = Color.parseColor("#3871E0"),
                fabAddBg = Color.parseColor("#2D4A7A"),
                fabAddIcon = Color.parseColor("#F8FAFC"),
                chipUnselectedBg = Color.parseColor("#222533"),
                chipUnselectedText = Color.parseColor("#E2E8F0"),
                sectionHeaderText = Color.parseColor("#F8FAFC"),
                cardBg = Color.parseColor("#1C1F2B"),
                itemTitleText = Color.parseColor("#F8FAFC"),
                itemSubtitleText = Color.parseColor("#94A3B8"),
                itemDivider = Color.parseColor("#2A2E3F"),
                callBtnBg = Color.parseColor("#173324"),
                callBtnIcon = Color.parseColor("#F8FAFC"),
                chevronTint = Color.parseColor("#64748B"),
                bottomNavBg = Color.parseColor("#1A1C26"),
                bottomNavPill = Color.parseColor("#2D4A7A"),
                bottomNavActive = Color.parseColor("#F8FAFC"),
                bottomNavUnselected = Color.parseColor("#94A3B8")
            )
        } else {
            HomePalette(
                isDark = false,
                pageBg = Color.parseColor("#F5F7FB"),
                searchBg = Color.parseColor("#DCE6F4"),
                searchHint = Color.parseColor("#5A6578"),
                primaryBlue = Color.parseColor("#3871E0"),
                fabAddBg = Color.parseColor("#9AC7F7"),
                fabAddIcon = Color.parseColor("#111827"),
                chipUnselectedBg = Color.parseColor("#DCE6F4"),
                chipUnselectedText = Color.parseColor("#2D3648"),
                sectionHeaderText = Color.parseColor("#111827"),
                cardBg = Color.parseColor("#FFFFFF"),
                itemTitleText = Color.parseColor("#111827"),
                itemSubtitleText = Color.parseColor("#5A6578"),
                itemDivider = Color.parseColor("#E5EAF2"),
                callBtnBg = Color.parseColor("#E8F8EE"),
                callBtnIcon = Color.parseColor("#1E293B"),
                chevronTint = Color.parseColor("#8E98A8"),
                bottomNavBg = Color.parseColor("#DCE6F4"),
                bottomNavPill = Color.parseColor("#9AC7F7"),
                bottomNavActive = Color.parseColor("#111827"),
                bottomNavUnselected = Color.parseColor("#1E293B")
            )
        }
    }

    fun dpToPx(context: Context, dp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        )
    }

    fun createChipBackground(context: Context, color: Int): Drawable {
        val radius = dpToPx(context, 20f)
        val bgShape = GradientDrawable().apply {
            this.shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(color)
        }
        val rippleColor = ColorStateList.valueOf(Color.parseColor("#22000000"))
        return RippleDrawable(rippleColor, bgShape, null)
    }

    fun createCardItemBackground(
        context: Context,
        cardColor: Int,
        isDark: Boolean,
        isFirst: Boolean,
        isLast: Boolean
    ): Drawable {
        val r = dpToPx(context, 16f)
        val radii = when {
            isFirst && isLast -> floatArrayOf(r, r, r, r, r, r, r, r)
            isFirst -> floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            isLast -> floatArrayOf(0f, 0f, 0f, 0f, r, r, r, r)
            else -> floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        }
        val bgShape = GradientDrawable().apply {
            this.shape = GradientDrawable.RECTANGLE
            cornerRadii = radii
            setColor(cardColor)
        }
        val mask = GradientDrawable().apply {
            this.shape = GradientDrawable.RECTANGLE
            cornerRadii = radii
            setColor(Color.WHITE)
        }
        val rippleColor = ColorStateList.valueOf(
            if (isDark) Color.parseColor("#26FFFFFF") else Color.parseColor("#14000000")
        )
        return RippleDrawable(rippleColor, bgShape, mask)
    }

    fun createFlatItemRipple(isDark: Boolean): Drawable {
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.WHITE)
        }
        val rippleColor = ColorStateList.valueOf(
            if (isDark) Color.parseColor("#22FFFFFF") else Color.parseColor("#12000000")
        )
        return RippleDrawable(rippleColor, ColorDrawable(Color.TRANSPARENT), mask)
    }

    fun createOvalBackground(color: Int): Drawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
    }

    fun createRoundedSquareBackground(context: Context, color: Int, radiusDp: Float = 16f): Drawable {
        val r = dpToPx(context, radiusDp)
        val bgShape = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = r
            setColor(color)
        }
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = r
            setColor(Color.WHITE)
        }
        val rippleColor = ColorStateList.valueOf(Color.parseColor("#22000000"))
        return RippleDrawable(rippleColor, bgShape, mask)
    }

    fun createPopupCardBackground(context: Context, cardColor: Int, strokeColor: Int): Drawable {
        val r = dpToPx(context, 16f)
        val strokeW = dpToPx(context, 1f).toInt().coerceAtLeast(1)
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = r
            setColor(cardColor)
            setStroke(strokeW, strokeColor)
        }
    }

    fun createFastscrollerPillBackground(context: Context, cardColor: Int, strokeColor: Int): Drawable {
        val r = dpToPx(context, 12f)
        val strokeW = dpToPx(context, 0.5f).toInt().coerceAtLeast(1)
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = r
            setColor(cardColor)
            setStroke(strokeW, strokeColor)
        }
    }

    fun createBottomNavBarBackground(context: Context, bgColor: Int, dividerColor: Int): Drawable {
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(bgColor)
        }
        val topLine = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(dividerColor)
        }
        val layer = LayerDrawable(arrayOf(bg, topLine))
        val dividerHeight = dpToPx(context, 1f).toInt().coerceAtLeast(1)
        layer.setLayerHeight(1, dividerHeight)
        layer.setLayerGravity(1, android.view.Gravity.TOP)
        return layer
    }

    fun createBottomNavItemBackground(context: Context, pillColor: Int): Drawable {
        val radius = dpToPx(context, 18f)
        val insetH = dpToPx(context, 6f).toInt()
        val insetV = dpToPx(context, 5f).toInt()
        val pillShape = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(pillColor)
        }
        val insetDrawable = object : InsetDrawable(pillShape, insetH, insetV, insetH, insetV) {
            override fun getPadding(padding: android.graphics.Rect): Boolean {
                padding.set(0, 0, 0, 0)
                return false
            }
        }
        return object : StateListDrawable() {
            override fun getPadding(padding: android.graphics.Rect): Boolean {
                padding.set(0, 0, 0, 0)
                return false
            }
        }.apply {
            addState(intArrayOf(android.R.attr.state_checked), insetDrawable)
            addState(intArrayOf(android.R.attr.state_selected), insetDrawable)
            addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
        }
    }

    fun createSearchPillBackground(context: Context, searchBgColor: Int): Drawable {
        val r = dpToPx(context, 24f)
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = r
            setColor(searchBgColor)
        }
    }

    fun createDialogCardBackground(context: Context, isDark: Boolean): Drawable {
        val r = dpToPx(context, 22f)
        val bgColor = if (isDark) Color.parseColor("#1C1F2B") else Color.parseColor("#EEF3FA")
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = r
            setColor(bgColor)
        }
    }

    fun createDialogInputBackground(context: Context, isDark: Boolean): Drawable {
        val r = dpToPx(context, 12f)
        val bgColor = if (isDark) Color.parseColor("#25293A") else Color.parseColor("#FFFFFF")
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = r
            setColor(bgColor)
        }
    }

    fun createGroupAvatarDrawable(context: Context, index: Int): Drawable {
        val bgColor = AVATAR_COLORS[Math.abs(index) % AVATAR_COLORS.size]
        val circle = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(bgColor)
        }
        val peopleIcon = androidx.core.content.ContextCompat.getDrawable(
            context,
            org.fossify.commons.R.drawable.ic_people_vector
        )?.mutate()
        peopleIcon?.setTint(Color.WHITE)
        val inset = dpToPx(context, 10f).toInt()
        val layer = LayerDrawable(arrayOf(circle, peopleIcon ?: ColorDrawable(Color.TRANSPARENT)))
        layer.setLayerInset(1, inset, inset, inset, inset)
        return layer
    }

    fun createContactAvatarBitmap(context: Context, name: String, index: Int, sizeDp: Float = 44f): android.graphics.Bitmap {
        val trimmed = name.trim()
        val firstNormalizedChar = trimmed.firstOrNull()?.toString()
            ?.let { java.text.Normalizer.normalize(it, java.text.Normalizer.Form.NFD).replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "") }
            ?.firstOrNull()
        val isLetter = firstNormalizedChar != null && firstNormalizedChar.isLetter()
        val letter = if (isLetter) firstNormalizedChar!!.uppercaseChar().toString() else ""
        val size = dpToPx(context, sizeDp).toInt().coerceAtLeast(44)
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)

        val bgColor = AVATAR_COLORS[Math.abs(index) % AVATAR_COLORS.size]
        val circlePaint = android.graphics.Paint().apply {
            color = bgColor
            isAntiAlias = true
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, circlePaint)

        if (isLetter) {
            val wantedTextSize = size * 0.43f
            val textPaint = android.graphics.Paint().apply {
                color = Color.WHITE
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
                textSize = wantedTextSize
                style = android.graphics.Paint.Style.FILL
                isFakeBoldText = true
            }
            val xPos = canvas.width / 2f
            val yPos = canvas.height / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(letter, xPos, yPos, textPaint)
        } else {
            val personDrawable = androidx.core.content.ContextCompat.getDrawable(
                context,
                org.fossify.commons.R.drawable.ic_person_vector
            )?.mutate()
            if (personDrawable != null) {
                personDrawable.setTint(Color.WHITE)
                val iconPadding = (size * 0.24f).toInt()
                personDrawable.setBounds(iconPadding, iconPadding, size - iconPadding, size - iconPadding)
                personDrawable.draw(canvas)
            }
        }
        return bitmap
    }
}
