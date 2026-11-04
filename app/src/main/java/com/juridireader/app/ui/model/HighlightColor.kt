package com.juridireader.app.ui.model

import androidx.compose.ui.graphics.Color

enum class HighlightColor(val label: String, val composeColor: Color, val hexString: String) {
    YELLOW("Amarelo", Color(0xFFFFF59D), "#FFF59D"),
    GREEN("Verde", Color(0xFFC8E6C9), "#C8E6C9"),
    BLUE("Azul", Color(0xFFBBDEFB), "#BBDEFB"),
    PINK("Rosa", Color(0xFFF8BBD0), "#F8BBD0"),
    NONE("Sem grifo", Color.Transparent, "#00000000");

    companion object {
        fun fromName(name: String?): HighlightColor {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: NONE
        }
    }
}
