package com.pavlozibro.sufler;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Плаваюче вікно суфлера поверх усіх застосунків (зокрема камери).
 * Текст їде вгору сам; пальцем можна прогорнути назад чи вперед, дотик — пауза.
 */
public class OverlayService extends Service implements Choreographer.FrameCallback {
    static final String ACTION_STOP = "com.pavlozibro.sufler.STOP";
    private static final String CHANNEL = "sufler";
    private static final int NOTIF_ID = 1;
    private static final int ACCENT = Brand.CORAL_VIDEO;
    private static final long MANUAL_HOLD_MS = 600;

    static boolean running;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Prefs prefs;
    private WindowManager wm;
    private WindowManager.LayoutParams lp;
    private LinearLayout root, controls;
    private FrameLayout box;
    private PrompterScrollView scroll;
    private TextView text, countTv, playBtn, speedTv;

    private boolean playing, counting;
    private float pos;
    private long lastFrameNs, lastUserScrollMs;
    private int speed, font, lines, countLeft;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startInForeground();
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        prefs = new Prefs(this);
        Brand.load(this);
        speed = prefs.speed();
        font = prefs.font();
        lines = prefs.lines();
        if (root == null) buildWindow();
        applyLook();
        text.setText(prefs.text());
        goStart();
        running = true;
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        Choreographer.getInstance().removeFrameCallback(this);
        if (root != null) {
            try { wm.removeView(root); } catch (Exception ignored) {}
            root = null;
        }
        super.onDestroy();
    }

    // ---------- Сповіщення ----------
    private void startInForeground() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Суфлер", NotificationManager.IMPORTANCE_LOW));

        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, OverlayService.class).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE);

        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Суфлер працює")
                .setContentText("Дотик до тексту — пауза. Гортай пальцем, щоб повернутись.")
                .setContentIntent(open)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(null, "Зупинити", stop).build())
                .build();

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIF_ID, n);
        }
    }

    // ---------- Вікно ----------
    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private void buildWindow() {
        wm = getSystemService(WindowManager.class);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(10), dp(4), dp(10), dp(8));

        // Верхня смужка: перетягування, зміна розміру, кнопки, закрити
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView drag = iconBtn("⠿⠿");
        drag.setTextColor(Brand.DARK_FAINT);
        drag.setOnTouchListener(new MoveListener());
        top.addView(drag);
        View spacer = new View(this);
        top.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        TextView resize = iconBtn("⤡");
        resize.setOnTouchListener(new ResizeListener());
        top.addView(resize);
        TextView toggle = iconBtn("▾");
        toggle.setOnClickListener(v -> {
            boolean show = controls.getVisibility() != View.VISIBLE;
            controls.setVisibility(show ? View.VISIBLE : View.GONE);
            ((TextView) v).setText(show ? "▾" : "▴");
        });
        top.addView(toggle);
        TextView close = iconBtn("✕");
        close.setOnClickListener(v -> stopSelf());
        top.addView(close);
        root.addView(top);

        // Текст
        box = new FrameLayout(this);
        scroll = new PrompterScrollView(this);
        text = new TextView(this);
        text.setTextColor(Brand.DARK_INK);
        text.setTypeface(Brand.bodyBold);
        text.setLineSpacing(0, 1.15f);
        scroll.addView(text, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        box.addView(scroll, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        countTv = new TextView(this);
        countTv.setTextColor(ACCENT);
        countTv.setTypeface(Brand.serifItalic);
        countTv.setGravity(Gravity.CENTER);
        countTv.setVisibility(View.GONE);
        box.addView(countTv, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        root.addView(box, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(100)));

        scroll.setListener(new PrompterScrollView.Listener() {
            @Override public void onTap() { togglePlay(); }
            @Override public void onUserScroll(int y) {
                pos = y;
                lastUserScrollMs = SystemClock.uptimeMillis();
            }
        });

        // Кнопки керування
        controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(0, dp(6), 0, 0);
        addCtl(Brand.glyph("↺"), v -> goStart());
        addCtl("A−", v -> setFont(font - 2));
        addCtl("A+", v -> setFont(font + 2));
        addCtl(Brand.glyph("⏪"), v -> back());
        playBtn = addCtl(Brand.glyph("⏸"), v -> togglePlay());
        playBtn.setBackground(pill(ACCENT));
        addCtl("−", v -> setSpeed(speed - 1));
        speedTv = new TextView(this);
        speedTv.setTextColor(Brand.DARK_FAINT);
        speedTv.setTypeface(Brand.mono);
        speedTv.setTextSize(13);
        speedTv.setGravity(Gravity.CENTER);
        speedTv.setMinWidth(dp(26));
        controls.addView(speedTv);
        addCtl("+", v -> setSpeed(speed + 1));
        root.addView(controls);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        lp.width = prefs.winW() > 0 ? prefs.winW() : dm.widthPixels - dp(16);
        lp.x = prefs.winX() >= 0 ? prefs.winX() : (dm.widthPixels - lp.width) / 2;
        lp.y = prefs.winY() >= 0 ? prefs.winY() : dp(28); // одразу під фронтальною камерою
        clampWindow();
        wm.addView(root, lp);

        lastFrameNs = 0;
        Choreographer.getInstance().postFrameCallback(this);
    }

    private TextView iconBtn(String label) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextColor(Brand.DARK_MUTED);
        t.setTextSize(16);
        t.setGravity(Gravity.CENTER);
        t.setMinWidth(dp(36));
        t.setMinHeight(dp(30));
        return t;
    }

    private TextView addCtl(String label, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextColor(Brand.DARK_INK);
        t.setTextSize(15);
        t.setTypeface(Brand.bodyBold);
        t.setGravity(Gravity.CENTER);
        t.setBackground(pill(0x1FF2ECE2));
        t.setOnClickListener(l);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(38), dp(34));
        p.setMargins(dp(3), 0, dp(3), 0);
        controls.addView(t, p);
        return t;
    }

    private GradientDrawable pill(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(999));
        return g;
    }

    /** Застосувати шрифт, висоту, прозорість і швидкість з налаштувань. */
    private void applyLook() {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(Math.round(prefs.opacity() * 2.55f), 0x17, 0x14, 0x0F));
        bg.setCornerRadius(dp(14));
        root.setBackground(bg);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, font);
        countTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.max(28, font * 1.6f));
        speedTv.setText(String.valueOf(speed));
        setBoxHeight(lines * text.getLineHeight() + dp(4));
    }

    private void setBoxHeight(int h) {
        LinearLayout.LayoutParams p = (LinearLayout.LayoutParams) box.getLayoutParams();
        p.height = Math.max(text.getLineHeight(), h);
        box.setLayoutParams(p);
        // Запас знизу, щоб останній рядок міг доїхати до верху вікна
        text.setPadding(0, 0, 0, Math.max(0, p.height - text.getLineHeight()));
    }

    private void clampWindow() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        lp.width = Math.max(dp(200), Math.min(lp.width, dm.widthPixels));
        lp.x = Math.max(-lp.width + dp(80), Math.min(lp.x, dm.widthPixels - dp(80)));
        lp.y = Math.max(0, Math.min(lp.y, dm.heightPixels - dp(80)));
    }

    private void saveWindow() { prefs.window(lp.x, lp.y, lp.width); }

    // ---------- Прокрутка ----------
    @Override
    public void doFrame(long now) {
        if (root == null) return;
        float dt = lastFrameNs == 0 ? 0 : Math.min(0.1f, (now - lastFrameNs) / 1e9f);
        lastFrameNs = now;
        boolean manual = scroll.touching || SystemClock.uptimeMillis() - lastUserScrollMs < MANUAL_HOLD_MS;
        if (playing && !counting && !manual) {
            // швидкість 5 ≈ пів рядка на секунду, пропорційно розміру шрифту
            pos += speed * text.getLineHeight() * 0.11f * dt;
            int max = scroll.maxScroll();
            if (pos >= max) { pos = max; setPlaying(false); }
            scroll.setPos(Math.round(pos));
        }
        Choreographer.getInstance().postFrameCallback(this);
    }

    private void setPlaying(boolean v) {
        playing = v;
        playBtn.setText(Brand.glyph(v ? "⏸" : "▶"));
    }

    private void togglePlay() {
        if (counting) { stopCountdown(); setPlaying(false); return; }
        if (!playing && pos >= scroll.maxScroll() && scroll.maxScroll() > 0) {
            pos = 0; scroll.setPos(0);
        }
        setPlaying(!playing);
    }

    private void goStart() {
        pos = 0;
        scroll.setPos(0);
        setPlaying(true);
        startCountdown();
    }

    private void back() {
        pos = Math.max(0, pos - text.getLineHeight());
        scroll.setPos(Math.round(pos));
    }

    private void setSpeed(int v) {
        speed = Math.max(1, Math.min(20, v));
        speedTv.setText(String.valueOf(speed));
        prefs.speed(speed);
    }

    private void setFont(int v) {
        font = Math.max(14, Math.min(56, v));
        prefs.font(font);
        float ratio = text.getLineHeight() == 0 ? 1 : pos / text.getLineHeight();
        applyLook();
        // Залишаємось на тому ж рядку після зміни шрифту
        pos = ratio * text.getLineHeight();
        handler.post(() -> scroll.setPos(Math.round(pos)));
    }

    // ---------- Відлік ----------
    private final Runnable countTick = new Runnable() {
        @Override public void run() {
            countLeft--;
            if (countLeft <= 0) { stopCountdown(); return; }
            countTv.setText(String.valueOf(countLeft));
            handler.postDelayed(this, 1000);
        }
    };

    private void startCountdown() {
        handler.removeCallbacks(countTick);
        countLeft = prefs.countdown();
        if (countLeft <= 0) { stopCountdown(); return; }
        counting = true;
        countTv.setText(String.valueOf(countLeft));
        countTv.setVisibility(View.VISIBLE);
        scroll.setVisibility(View.INVISIBLE);
        handler.postDelayed(countTick, 1000);
    }

    private void stopCountdown() {
        handler.removeCallbacks(countTick);
        counting = false;
        countTv.setVisibility(View.GONE);
        scroll.setVisibility(View.VISIBLE);
    }

    // ---------- Перетягування і зміна розміру ----------
    private class MoveListener implements View.OnTouchListener {
        private float sx, sy;
        private int ox, oy;

        @Override public boolean onTouch(View v, MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    sx = e.getRawX(); sy = e.getRawY(); ox = lp.x; oy = lp.y;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    lp.x = ox + Math.round(e.getRawX() - sx);
                    lp.y = oy + Math.round(e.getRawY() - sy);
                    clampWindow();
                    wm.updateViewLayout(root, lp);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    saveWindow();
                    return true;
            }
            return false;
        }
    }

    private class ResizeListener implements View.OnTouchListener {
        private float sx, sy;
        private int ow, oh;

        @Override public boolean onTouch(View v, MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    sx = e.getRawX(); sy = e.getRawY();
                    ow = lp.width; oh = box.getHeight();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    lp.width = ow + Math.round(e.getRawX() - sx);
                    clampWindow();
                    setBoxHeight(oh + Math.round(e.getRawY() - sy));
                    wm.updateViewLayout(root, lp);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    // Висоту вирівнюємо до цілих рядків
                    int lh = Math.max(1, text.getLineHeight());
                    lines = Math.max(1, Math.min(10, Math.round((float) box.getHeight() / lh)));
                    prefs.lines(lines);
                    setBoxHeight(lines * lh + dp(4));
                    saveWindow();
                    return true;
            }
            return false;
        }
    }
}
