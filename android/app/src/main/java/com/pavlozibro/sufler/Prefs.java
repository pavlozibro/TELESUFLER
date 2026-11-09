package com.pavlozibro.sufler;

import android.content.Context;
import android.content.SharedPreferences;

/** Налаштування суфлера, спільні для екрана налаштувань і плаваючого вікна. */
final class Prefs {
    static final String DEFAULT_TEXT =
            "Привіт! Це твій текст для суфлера.\n\n"
            + "Заміни його на свій сценарій. Текст повільно їде вгору у віконці над камерою, "
            + "а ти дивишся майже в об'єктив і знімаєш у звичайній камері телефона.";

    private final SharedPreferences sp;

    Prefs(Context c) { sp = c.getSharedPreferences("sufler", Context.MODE_PRIVATE); }

    String text() { return sp.getString("text", DEFAULT_TEXT); }
    void text(String v) { sp.edit().putString("text", v).apply(); }

    int speed() { return sp.getInt("speed", 5); }          // 1..20
    void speed(int v) { sp.edit().putInt("speed", v).apply(); }

    int font() { return sp.getInt("font", 26); }           // sp, 14..56
    void font(int v) { sp.edit().putInt("font", v).apply(); }

    int lines() { return sp.getInt("lines", 3); }          // висота вікна в рядках, 1..8
    void lines(int v) { sp.edit().putInt("lines", v).apply(); }

    int opacity() { return sp.getInt("opacity", 75); }     // фон вікна, %
    void opacity(int v) { sp.edit().putInt("opacity", v).apply(); }

    int countdown() { return sp.getInt("countdown", 3); }  // сек
    void countdown(int v) { sp.edit().putInt("countdown", v).apply(); }

    boolean openCamera() { return sp.getBoolean("openCamera", true); }
    void openCamera(boolean v) { sp.edit().putBoolean("openCamera", v).apply(); }

    // Положення і ширина вікна (-1 — ще не задано)
    int winX() { return sp.getInt("winX", -1); }
    int winY() { return sp.getInt("winY", -1); }
    int winW() { return sp.getInt("winW", -1); }
    void window(int x, int y, int w) {
        sp.edit().putInt("winX", x).putInt("winY", y).putInt("winW", w).apply();
    }
}
