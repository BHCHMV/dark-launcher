package com.bhchmv.darklauncher;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;

final class IconUtil {
    private IconUtil() {}

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static Drawable selectable(Context c) {
        TypedValue tv = new TypedValue();
        c.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        return c.getDrawable(tv.resourceId);
    }
}
