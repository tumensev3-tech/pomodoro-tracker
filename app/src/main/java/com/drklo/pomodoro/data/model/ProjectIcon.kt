package com.drklo.pomodoro.data.model

/**
 * Stable semantic icon keys stored with a project.
 *
 * UI rendering is deliberately separate from the stored key so the artwork can change later
 * without migrating user data.
 */
enum class ProjectIcon {
    NONE,
    COMPUTER,
    BOOK,
    STUDY,
    COOKING,
    HOME,
    CAR,
    TOOLS,
    FITNESS,
    WALK,
    MUSIC,
    SHOPPING,
    COFFEE,
    CREATIVE,
    PHONE,
    MEETING,
    WRITING,
    EMAIL,
    DOCUMENTS,
    MONEY,
    FAMILY,
    CHILDREN,
    CLEANING,
    LAUNDRY,
    DISHES,
    GARDEN,
    FOOD,
    HEALTH,
    MEDICINE,
    SLEEP,
    GAME,
    TV,
    PHOTO,
    TRAVEL,
    PET,
    BICYCLE,
    PLANTS;

    companion object {
        fun fromName(value: String?): ProjectIcon =
            entries.firstOrNull { it.name == value } ?: NONE
    }
}
