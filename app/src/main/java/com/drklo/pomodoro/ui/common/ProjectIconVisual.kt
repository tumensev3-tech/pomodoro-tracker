package com.drklo.pomodoro.ui.common

import androidx.annotation.StringRes
import com.drklo.pomodoro.R
import com.drklo.pomodoro.data.model.ProjectIcon

private val SYMBOLS = mapOf(
    ProjectIcon.NONE to "—",
    ProjectIcon.COMPUTER to "💻",
    ProjectIcon.BOOK to "📚",
    ProjectIcon.STUDY to "🎓",
    ProjectIcon.COOKING to "🍳",
    ProjectIcon.HOME to "🏠",
    ProjectIcon.CAR to "🚗",
    ProjectIcon.TOOLS to "🔧",
    ProjectIcon.FITNESS to "🏃",
    ProjectIcon.WALK to "🚶",
    ProjectIcon.MUSIC to "🎵",
    ProjectIcon.SHOPPING to "🛒",
    ProjectIcon.COFFEE to "☕",
    ProjectIcon.CREATIVE to "🎨"
)

private val LABELS = mapOf(
    ProjectIcon.NONE to R.string.project_icon_none,
    ProjectIcon.COMPUTER to R.string.project_icon_computer,
    ProjectIcon.BOOK to R.string.project_icon_book,
    ProjectIcon.STUDY to R.string.project_icon_study,
    ProjectIcon.COOKING to R.string.project_icon_cooking,
    ProjectIcon.HOME to R.string.project_icon_home,
    ProjectIcon.CAR to R.string.project_icon_car,
    ProjectIcon.TOOLS to R.string.project_icon_tools,
    ProjectIcon.FITNESS to R.string.project_icon_fitness,
    ProjectIcon.WALK to R.string.project_icon_walk,
    ProjectIcon.MUSIC to R.string.project_icon_music,
    ProjectIcon.SHOPPING to R.string.project_icon_shopping,
    ProjectIcon.COFFEE to R.string.project_icon_coffee,
    ProjectIcon.CREATIVE to R.string.project_icon_creative
)

fun ProjectIcon.symbol(): String = SYMBOLS.getValue(this)

@StringRes
fun ProjectIcon.labelRes(): Int = LABELS.getValue(this)
