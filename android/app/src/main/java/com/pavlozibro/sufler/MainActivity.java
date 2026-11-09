package com.pavlozibro.sufler;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** Екран налаштувань: текст, швидкість, шрифт, висота віконця, відлік. */
public class MainActivity extends Activity {
    private Prefs prefs;
    private EditText input;
    private TextView stopBtn;
    private boolean pendingStart;
    private int bg, field, ink, muted, faint, line, accent;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = new Prefs(this);
        pickColors();
        setContentView(buildUi());
        handleShare(getIntent());

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleShare(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        stopBtn.setVisibility(OverlayService.running ? View.VISIBLE : View.GONE);
        if (pendingStart && Settings.canDrawOverlays(this)) {
            pendingStart = false;
            launch();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        prefs.text(input.getText().toString());
    }

    private void handleShare(Intent i) {
        if (i != null && Intent.ACTION_SEND.equals(i.getAction())) {
            String t = i.getStringExtra(Intent.EXTRA_TEXT);
            if (t != null && !t.trim().isEmpty()) {
                input.setText(t);
                prefs.text(t);
                Toast.makeText(this, "Текст додано", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ---------- Запуск ----------
    private void launch() {
        String t = input.getText().toString();
        prefs.text(t);
        if (t.trim().isEmpty()) {
            Toast.makeText(this, "Спершу додай текст", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            new AlertDialog.Builder(this)
                    .setTitle("Дозволь показ поверх інших застосунків")
                    .setMessage("Щоб текст було видно над камерою, у налаштуваннях увімкни «Показ поверх інших застосунків» для Суфлера. Потім повернись сюди — суфлер запуститься сам.")
                    .setPositiveButton("Відкрити налаштування", (d, w) -> {
                        pendingStart = true;
                        startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + getPackageName())));
                    })
                    .setNegativeButton("Пізніше", null)
                    .show();
            return;
        }
        startForegroundService(new Intent(this, OverlayService.class));
        stopBtn.setVisibility(View.VISIBLE);
        if (prefs.openCamera()) openCamera();
        else Toast.makeText(this, "Суфлер запущено. Відкрий камеру.", Toast.LENGTH_LONG).show();
    }

    private void openCamera() {
        String[] actions = {MediaStore.INTENT_ACTION_VIDEO_CAMERA, MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA};
        for (String a : actions) {
            try {
                startActivity(new Intent(a).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            } catch (Exception ignored) {}
        }
        Toast.makeText(this, "Суфлер запущено. Відкрий камеру.", Toast.LENGTH_LONG).show();
    }

    private void stopPrompter() {
        startService(new Intent(this, OverlayService.class).setAction(OverlayService.ACTION_STOP));
        stopBtn.setVisibility(View.GONE);
    }

    // ---------- Інтерфейс (дизайн-система Павла, «Тихий журнал») ----------
    private void pickColors() {
        Brand.load(this);
        boolean dark = Brand.isDark(this);
        if (dark) {
            bg = Brand.DARK; field = Brand.DARK_FIELD; ink = Brand.DARK_INK; muted = Brand.DARK_MUTED;
            faint = Brand.DARK_FAINT; line = Brand.DARK_LINE; accent = Brand.CORAL_VIDEO;
        } else {
            bg = Brand.PAPER; field = Brand.PAPER_FIELD; ink = Brand.INK; muted = Brand.MUTED;
            faint = Brand.FAINT; line = Brand.LINE; accent = Brand.CORAL;
        }
        getWindow().getDecorView().setBackgroundColor(bg);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        if (!dark) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
    }

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private GradientDrawable rounded(int fill, int stroke, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        if (stroke != 0) g.setStroke(dp(1), stroke);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private TextView text(String s, Typeface tf, float size, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTypeface(tf);
        t.setTextSize(size);
        t.setTextColor(color);
        return t;
    }

    /** Моно-підпис капсом із розрядкою, як «КРОК 02 · ВАЙБКОДИНГ». */
    private TextView label(String s, int color) {
        TextView t = text(s, Brand.mono, 11.5f, color);
        t.setLetterSpacing(0.14f);
        return t;
    }

    private View hairline() {
        View v = new View(this);
        v.setBackgroundColor(line);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1))));
        return v;
    }

    private View buildUi() {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(22), dp(32), dp(22), dp(32));
        sv.addView(col);

        col.addView(label("СУФЛЕР · ПОВЕРХ КАМЕРИ", accent));
        TextView t1 = text("Твій текст", Brand.serif, 46, ink);
        t1.setLineSpacing(0, 0.9f);
        t1.setPadding(0, dp(14), 0, 0);
        col.addView(t1);
        TextView t2 = text("над камерою", Brand.serifItalic, 46, accent);
        t2.setLineSpacing(0, 0.9f);
        col.addView(t2);
        TextView lead = text("Текст пливе у віконці біля об'єктива, а знімаєш ти у звичайній камері телефона.",
                Brand.body, 16, muted);
        lead.setLineSpacing(0, 1.35f);
        lead.setPadding(0, dp(12), 0, dp(24));
        col.addView(lead);

        input = new EditText(this);
        input.setText(prefs.text());
        input.setHint("Встав або надрукуй свій текст тут…");
        input.setHintTextColor(faint);
        input.setTextColor(ink);
        input.setTypeface(Brand.body);
        input.setTextSize(16);
        input.setLineSpacing(0, 1.3f);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setMinLines(7);
        input.setMaxLines(14);
        input.setPadding(dp(16), dp(14), dp(16), dp(14));
        input.setBackground(rounded(field, line, 6));
        col.addView(input);

        TextView sec = label("НАЛАШТУВАННЯ", faint);
        sec.setPadding(0, dp(30), 0, dp(10));
        col.addView(sec);

        col.addView(slider("01", "Швидкість", "Можна міняти і під час зйомки.", 1, 20, prefs.speed(),
                v -> String.valueOf(v), prefs::speed));
        col.addView(slider("02", "Розмір шрифту", "Величина тексту у віконці.", 14, 56, prefs.font(),
                v -> String.valueOf(v), prefs::font));
        col.addView(slider("03", "Висота віконця", "Скільки рядків видно одночасно.", 1, 8, prefs.lines(),
                v -> v + " " + plural(v, "рядок", "рядки", "рядків"), prefs::lines));
        col.addView(slider("04", "Прозорість фону", "Наскільки темне тло під текстом.", 20, 100, prefs.opacity(),
                v -> v + "%", prefs::opacity));
        col.addView(slider("05", "Відлік перед стартом", "Час, щоб натиснути запис у камері.", 0, 10, prefs.countdown(),
                v -> v == 0 ? "без відліку" : v + " сек", prefs::countdown));
        col.addView(toggleRow("06", "Відкривати камеру", "Після запуску одразу відкриється камера телефона.",
                prefs.openCamera(), prefs::openCamera));
        col.addView(hairline());

        TextView start = text(Brand.glyph("▶") + "  Запустити поверх камери", Brand.bodySemi, 17, Color.WHITE);
        start.setGravity(Gravity.CENTER);
        start.setPadding(dp(20), dp(17), dp(20), dp(17));
        start.setBackground(rounded(accent, 0, 999));
        start.setOnClickListener(v -> launch());
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.topMargin = dp(28);
        col.addView(start, sp);

        stopBtn = text("Зупинити суфлер", Brand.bodySemi, 16, ink);
        stopBtn.setGravity(Gravity.CENTER);
        stopBtn.setPadding(dp(20), dp(15), dp(20), dp(15));
        stopBtn.setBackground(rounded(Color.TRANSPARENT, ink, 999));
        stopBtn.setOnClickListener(v -> stopPrompter());
        LinearLayout.LayoutParams stp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        stp.topMargin = dp(10);
        col.addView(stopBtn, stp);

        TextView how = label("ЯК КОРИСТУВАТИСЬ", faint);
        how.setPadding(0, dp(34), 0, dp(8));
        col.addView(how);
        String[][] tips = {
                {"⠿⠿", "Перетягни віконце ближче до фронтальної камери."},
                {"⤡", "Змінити ширину й висоту віконця."},
                {"дотик", "Пауза. Ще дотик — далі."},
                {"палець", "Гортай текст назад чи вперед, якщо треба перечитати."},
                {"↺", "Спочатку.  A− A+ — шрифт.  ⏪ — рядок назад.  − + — швидкість."},
                {"✕", "Закрити суфлер. Або «Зупинити» в сповіщенні."},
                {"текст", "Можна надіслати сюди з будь-якого застосунку через «Поділитися»."},
        };
        for (String[] tip : tips) col.addView(tipRow(tip[0], tip[1]));
        return sv;
    }

    private View tipRow(String key, String desc) {
        LinearLayout r = new LinearLayout(this);
        r.setPadding(0, dp(6), 0, dp(6));
        TextView k = text(key, Brand.mono, 12.5f, accent);
        r.addView(k, new LinearLayout.LayoutParams(dp(64), ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView d = text(desc, Brand.body, 14, muted);
        d.setLineSpacing(0, 1.3f);
        r.addView(d, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return r;
    }

    private static String plural(int n, String one, String few, String many) {
        int m10 = n % 10, m100 = n % 100;
        if (m10 == 1 && m100 != 11) return one;
        if (m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14)) return few;
        return many;
    }

    interface IntFmt { String fmt(int v); }
    interface IntSink { void set(int v); }
    interface BoolSink { void set(boolean v); }

    /** Рядок налаштування: тонка лінія зверху, номер, назва, значення. */
    private LinearLayout row(String num, String name, View trailing) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.addView(hairline());
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(0, dp(14), 0, 0);
        TextView n = text(num, Brand.mono, 12, faint);
        top.addView(n, new LinearLayout.LayoutParams(dp(34), ViewGroup.LayoutParams.WRAP_CONTENT));
        top.addView(text(name, Brand.bodySemi, 16.5f, ink),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        top.addView(trailing);
        c.addView(top);
        return c;
    }

    private TextView hint(String s) {
        TextView h = text(s, Brand.body, 13.5f, faint);
        h.setPadding(dp(34), dp(2), 0, 0);
        return h;
    }

    private View slider(String num, String name, String hint, int min, int max, int value, IntFmt fmt, IntSink sink) {
        TextView val = text(fmt.fmt(value), Brand.bodyBold, 15.5f, accent);
        LinearLayout c = row(num, name, val);
        c.addView(hint(hint));

        SeekBar sb = new SeekBar(this);
        sb.setMax(max - min);
        sb.setProgress(value - min);
        sb.setThumbTintList(ColorStateList.valueOf(accent));
        sb.setProgressTintList(ColorStateList.valueOf(accent));
        sb.setProgressBackgroundTintList(ColorStateList.valueOf(line));
        sb.setPadding(dp(34), dp(10), dp(12), dp(16));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) {
                val.setText(fmt.fmt(p + min));
                sink.set(p + min);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        c.addView(sb);
        return c;
    }

    private View toggleRow(String num, String name, String hint, boolean value, BoolSink sink) {
        Switch sw = new Switch(this);
        sw.setChecked(value);
        int[][] states = {{android.R.attr.state_checked}, {}};
        sw.setThumbTintList(new ColorStateList(states, new int[]{accent, Color.WHITE}));
        sw.setTrackTintList(new ColorStateList(states, new int[]{(accent & 0x00FFFFFF) | 0x80000000, faint}));
        sw.setOnCheckedChangeListener((b, v) -> sink.set(v));
        LinearLayout c = row(num, name, sw);
        TextView h = hint(hint);
        h.setPadding(dp(34), dp(2), 0, dp(16));
        c.addView(h);
        return c;
    }
}
