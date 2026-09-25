package com.drklo.pomodoro.ui.common

import androidx.annotation.StringRes
import com.drklo.pomodoro.R
import com.drklo.pomodoro.data.model.ProjectIcon

fun ProjectIcon.symbol(): String = when (this) {
    ProjectIcon.NONE -> "—"
    ProjectIcon.COMPUTER -> "💻"
    ProjectIcon.BOOK -> "📚"
    ProjectIcon.STUDY -> "🎓"
    ProjectIcon.COOKING -> "🍳"
    ProjectIcon.HOME -> "🏠"
    ProjectIcon.CAR -> "🚗"
    ProjectIcon.TOOLS -> "🔧"
    ProjectIcon.FITNESS -> "🏃"
    ProjectIcon.WALK -> "🚶"
    ProjectIcon.MUSIC -> "🎵"
    ProjectIcon.SHOPPING -> "🛒"
    ProjectIcon.COFFEE -> "☕"
    ProjectIcon.CREATIVE -> "🎨"
}

@StringRes
fun ProjectIcon.labelRes(): Int = when (this) {
    ProjectIcon.NONE -> R.string.project_icon_none
    ProjectIcon.COMPUTER -> R.string.project_icon_computer
    ProjectIcon.BOOK -> R.string.project_icon_book
    ProjectIcon.STUDY -> R.string.project_icon_study
    ProjectIcon.COOKING -> R.string.project_icon_cooking
    ProjectIcon.HOME -> R.string.project_icon_home
    ProjectIcon.CAR -> R.string.project_icon_car
    ProjectIcon.TOOLS -> R.string.project_icon_tools
    ProjectIcon.FITNESS -> R.string.project_icon_fitness
    ProjectIcon.WALK -> R.string.project_icon_walk
    ProjectIcon.MUSIC -> R.string.project_icon_music
    ProjectIcon.SHOPPING -> R.string.project_icon_shopping
    ProjectIcon.COFFEE -> R.string.project_icon_coffee
    ProjectIcon.CREATIVE -> R.string.project_icon_creative
}
