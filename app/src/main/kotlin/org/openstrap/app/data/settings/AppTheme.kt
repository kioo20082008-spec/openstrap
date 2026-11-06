package org.openstrap.app.data.settings

/**
 * The app's colour theme, picked on the You page. Stored by [key] rather than by name, so renaming an entry in code
 * never resets anyone's choice. Navy is the original look and the default.
 */
enum class AppTheme(val key: String) {
    Navy("navy"),
    Oled("oled"),
    Cream("cream"),
    Dusk("dusk"),
    ;

    companion object {
        val Default = Navy

        /** The theme stored as [key]; nothing stored, or a key this version doesn't know (a newer one's), is [Default]. */
        fun fromKey(key: String?): AppTheme = entries.firstOrNull { it.key == key } ?: Default
    }
}
