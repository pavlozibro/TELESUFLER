package com.pavlozibro.sufler;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.ScrollView;

/** ScrollView, що відрізняє прокрутку пальцем від автопрокрутки і ловить одиночний дотик. */
class PrompterScrollView extends ScrollView {
    interface Listener {
        void onTap();
        void onUserScroll(int y);
    }

    private final int slop;
    private Listener listener;
    private boolean programmatic, moved;
    private float downX, downY;
    boolean touching;

    PrompterScrollView(Context c) {
        super(c);
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        setVerticalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
    }

    void setListener(Listener l) { listener = l; }

    /** Прокрутка з коду — не вважається ручною. */
    void setPos(int y) {
        programmatic = true;
        scrollTo(0, y);
        programmatic = false;
    }

    int maxScroll() {
        if (getChildCount() == 0) return 0;
        return Math.max(0, getChildAt(0).getHeight() - getHeight());
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touching = true; moved = false;
                downX = ev.getX(); downY = ev.getY();
                break;
            case MotionEvent.ACTION_MOVE:
                if (Math.abs(ev.getX() - downX) > slop || Math.abs(ev.getY() - downY) > slop) moved = true;
                break;
            case MotionEvent.ACTION_UP:
                touching = false;
                if (!moved && listener != null) listener.onTap();
                break;
            case MotionEvent.ACTION_CANCEL:
                touching = false;
                break;
        }
        return super.onTouchEvent(ev);
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        if (!programmatic && listener != null) listener.onUserScroll(t);
    }
}
