package app.teatimer.model

/**
 * Background colours for the widget, chosen in the app. [WALLPAPER] follows the phone's
 * wallpaper colours (Material You); the others are fixed, each with a text colour that reads well on it.
 * Colours are ARGB.
 */
enum class WidgetBackground(val label: String, val background: Long?, val text: Long?) {
    WALLPAPER("Wallpaper", null, null),
    TEA_GREEN("Tea green", 0xFF4E6E3A, 0xFFFFF8E7),
    OOLONG("Oolong", 0xFF8A5A2B, 0xFFFFF4E6),
    BLACK_TEA("Black tea", 0xFF3B2420, 0xFFF5E9E2),
    MATCHA("Matcha", 0xFFDDE8C9, 0xFF22301A),
    PORCELAIN("Porcelain", 0xFFF7F5F0, 0xFF1E1E1E),
    NIGHT("Night", 0xFF121212, 0xFFEDEDED),
}
