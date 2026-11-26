package com.bhchmv.darklauncher;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

/** Detects up/down/left/right swipes over its children without breaking taps and scrolling. */
final class SwipeLayout extends FrameLayout {
    static final int UP = 0, DOWN = 1, LEFT = 2, RIGHT = 3;

    interface Listener {
        boolean canSwipe(int direction, float startY);

        void onSwipe(int direction);
    }

    private final int slop;
    private final int minDistance;
    private Listener listener;
    private float x0, y0;
    private int gesture = -1;

    SwipeLayout(Context c) {
        super(c);
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        minDistance = IconUtil.dp(c, 60);
    }

    void setListener(Listener l) {
        listener = l;
    }

    private boolean decide(MotionEvent ev) {
        if (gesture != -1) return true;
        if (listener == null) return false;
        float dx = ev.getX() - x0, dy = ev.getY() - y0;
        float adx = Math.abs(dx), ady = Math.abs(dy);
        int dir = -1;
        if (ady > slop * 2 && ady > adx * 1.5f) dir = dy > 0 ? DOWN : UP;
        else if (adx > slop * 2 && adx > ady * 1.5f) dir = dx < 0 ? LEFT : RIGHT;
        if (dir != -1 && listener.canSwipe(dir, y0)) {
            gesture = dir;
            return true;
        }
        return false;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                x0 = ev.getX();
                y0 = ev.getY();
                gesture = -1;
                return false;
            case MotionEvent.ACTION_MOVE:
                return decide(ev);
            default:
                return false;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                x0 = ev.getX();
                y0 = ev.getY();
                gesture = -1;
                return true;
            case MotionEvent.ACTION_MOVE:
                decide(ev);
                return true;
            case MotionEvent.ACTION_UP:
                if (gesture != -1 && listener != null) {
                    float dist = (gesture == UP || gesture == DOWN)
                            ? Math.abs(ev.getY() - y0) : Math.abs(ev.getX() - x0);
                    if (dist >= minDistance) listener.onSwipe(gesture);
                }
                gesture = -1;
                return true;
            case MotionEvent.ACTION_CANCEL:
                gesture = -1;
                return true;
            default:
                return true;
        }
    }
}
