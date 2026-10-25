package com.gathin.tvhome;

import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.view.View;

import java.util.Random;

/** A low-power clock screensaver: black background, dim clock, no images, network, or animation. */
public class ClockDream extends DreamService {

    private static final long MOVE_MS = 60_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private View content;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (content == null) return;
            moveClock();
            handler.postDelayed(this, MOVE_MS);
        }
    };

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false); // Any remote key wakes the TV back to where it was.
        setFullscreen(true);
        setScreenBright(false);
        setContentView(R.layout.dream_clock);
        content = findViewById(R.id.dreamContent);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        if (content != null) content.post(this::moveClock);
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, MOVE_MS);
    }

    @Override
    public void onDreamingStopped() {
        handler.removeCallbacks(tick);
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        handler.removeCallbacks(tick);
        content = null;
        super.onDetachedFromWindow();
    }

    /** Move the clock once a minute to reduce burn-in without running an animation loop. */
    private void moveClock() {
        View clock = content;
        if (clock == null) return;
        View parent = (View) clock.getParent();
        if (parent == null || parent.getWidth() == 0 || parent.getHeight() == 0) {
            clock.post(this::moveClock);
            return;
        }
        int maxX = Math.max(0, parent.getWidth() - clock.getWidth());
        int maxY = Math.max(0, parent.getHeight() - clock.getHeight());
        clock.setX(maxX * (0.08f + 0.84f * random.nextFloat()));
        clock.setY(maxY * (0.08f + 0.84f * random.nextFloat()));
    }
}
