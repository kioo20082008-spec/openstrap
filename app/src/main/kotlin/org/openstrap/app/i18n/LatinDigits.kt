package org.openstrap.app.i18n

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

/** Arabic keeps Western digits (1, 2, 3), as the redesign does: every Arabic locale is given the Latin numbering system. */
object LatinDigits {
    private fun latin(l: Locale): Locale =
        if (l.language == "ar") Locale.Builder().setLocale(l).setUnicodeLocaleKeyword("nu", "latn").build() else l

    fun wrap(base: Context): Context {
        val cfg = Configuration(base.resources.configuration)
        val list = cfg.locales
        if ((0 until list.size()).none { list[it].language == "ar" }) return base
        val fixed = LocaleList(*Array(list.size()) { latin(list[it]) })
        cfg.setLocales(fixed)
        Locale.setDefault(fixed[0])
        LocaleList.setDefault(fixed)
        return base.createConfigurationContext(cfg)
    }
}
