package com.pavlozibro.sufler;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Typeface;

/** Дизайн-система Павла, напрям A «Тихий журнал»: кольори й шрифти. */
final class Brand {
    // Світлий «папір»
    static final int PAPER = 0xFFF4EFE6, PAPER_FIELD = 0xFFFBF8F2, INK = 0xFF17140F,
            MUTED = 0xFF5C5A54, FAINT = 0xFF8A857C, LINE = 0xFFD9D1C3, CORAL = 0xFFE8492B;
    // Темний тон (і для тексту поверх відео)
    static final int DARK = 0xFF17140F, DARK_FIELD = 0xFF201B15, DARK_INK = 0xFFF2ECE2,
            DARK_MUTED = 0xFFC9C1B4, DARK_FAINT = 0xFFA39C90, DARK_LINE = 0xFF4A443B, CORAL_VIDEO = 0xFFFF6A45;

    static Typeface serif, serifItalic, body, bodySemi, bodyBold, mono;

    static void load(Context c) {
        if (body != null) return;
        serif = Typeface.createFromAsset(c.getAssets(), "fonts/cormorant_semibold.ttf");
        serifItalic = Typeface.createFromAsset(c.getAssets(), "fonts/cormorant_italic.ttf");
        body = Typeface.createFromAsset(c.getAssets(), "fonts/intertight_regular.ttf");
        bodySemi = Typeface.createFromAsset(c.getAssets(), "fonts/intertight_semibold.ttf");
        bodyBold = Typeface.createFromAsset(c.getAssets(), "fonts/intertight_bold.ttf");
        mono = Typeface.createFromAsset(c.getAssets(), "fonts/jetbrainsmono_medium.ttf");
    }

    static boolean isDark(Context c) {
        return (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    /** Символ-кнопка без емодзі-стилю. */
    static String glyph(String s) { return s + "︎"; }
}
