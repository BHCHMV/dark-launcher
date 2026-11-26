package com.bhchmv.darklauncher;

import android.view.View;

/** One look of the home screen (iPhone, Windows or terminal). */
interface HomeMode {
    View getView();

    /** Called every few seconds to refresh clocks and battery. */
    void tick();

    void onBack();

    /** Home button pressed while the launcher is already showing. */
    void onHomePressed();

    int statusBarColor();
}
