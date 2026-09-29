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
    ProjectIcon.CREATIVE to "🎨",
    ProjectIcon.PHONE to "📱",
    ProjectIcon.MEETING to "👥",
    ProjectIcon.WRITING to "✍️",
    ProjectIcon.EMAIL to "✉️",
    ProjectIcon.DOCUMENTS to "📄",
    ProjectIcon.MONEY to "💰",
    ProjectIcon.FAMILY to "👨‍👩‍👧",
    ProjectIcon.CHILDREN to "🧒",
    ProjectIcon.CLEANING to "🧹",
    ProjectIcon.LAUNDRY to "🧺",
    ProjectIcon.DISHES to "🍽️",
    ProjectIcon.GARDEN to "🌳",
    ProjectIcon.FOOD to "🍽",
    ProjectIcon.HEALTH to "❤️",
    ProjectIcon.MEDICINE to "💊",
    ProjectIcon.SLEEP to "😴",
    ProjectIcon.GAME to "🎮",
    ProjectIcon.TV to "📺",
    ProjectIcon.PHOTO to "📷",
    ProjectIcon.TRAVEL to "✈️",
    ProjectIcon.PET to "🐾",
    ProjectIcon.BICYCLE to "🚲",
    ProjectIcon.PLANTS to "🌱"
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
    ProjectIcon.CREATIVE to R.string.project_icon_creative,
    ProjectIcon.PHONE to R.string.project_icon_phone,
    ProjectIcon.MEETING to R.string.project_icon_meeting,
    ProjectIcon.WRITING to R.string.project_icon_writing,
    ProjectIcon.EMAIL to R.string.project_icon_email,
    ProjectIcon.DOCUMENTS to R.string.project_icon_documents,
    ProjectIcon.MONEY to R.string.project_icon_money,
    ProjectIcon.FAMILY to R.string.project_icon_family,
    ProjectIcon.CHILDREN to R.string.project_icon_children,
    ProjectIcon.CLEANING to R.string.project_icon_cleaning,
    ProjectIcon.LAUNDRY to R.string.project_icon_laundry,
    ProjectIcon.DISHES to R.string.project_icon_dishes,
    ProjectIcon.GARDEN to R.string.project_icon_garden,
    ProjectIcon.FOOD to R.string.project_icon_food,
    ProjectIcon.HEALTH to R.string.project_icon_health,
    ProjectIcon.MEDICINE to R.string.project_icon_medicine,
    ProjectIcon.SLEEP to R.string.project_icon_sleep,
    ProjectIcon.GAME to R.string.project_icon_game,
    ProjectIcon.TV to R.string.project_icon_tv,
    ProjectIcon.PHOTO to R.string.project_icon_photo,
    ProjectIcon.TRAVEL to R.string.project_icon_travel,
    ProjectIcon.PET to R.string.project_icon_pet,
    ProjectIcon.BICYCLE to R.string.project_icon_bicycle,
    ProjectIcon.PLANTS to R.string.project_icon_plants
)

fun ProjectIcon.symbol(): String = SYMBOLS.getValue(this)

@StringRes
fun ProjectIcon.labelRes(): Int = LABELS.getValue(this)
