package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Draws a frosted liquid-glass tray behind the dock row (row 1) and provides breathing room around it.
 */
public class DockDecoration extends RecyclerView.ItemDecoration {

    private final int dockSize;
    private final float density;
    private final float radius;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint specular = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();

    DockDecoration(int dockSize, float density) {
        this.dockSize = dockSize;
        this.density = density;
        this.radius = 22f * density;

        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(1.2f * density);
        rim.setColor(0x38FFFFFF);

        glow.setStyle(Paint.Style.STROKE);
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent,
                               @NonNull RecyclerView.State state) {
        int pos = parent.getChildAdapterPosition(view);
        if (pos >= 0 && pos < dockSize) {
            outRect.top = (int) (6 * density);
            outRect.bottom = (int) (12 * density);
        }
    }

    @Override
    public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        float minLeft = Float.MAX_VALUE;
        float maxRight = -Float.MAX_VALUE;
        float minTop = Float.MAX_VALUE;
        float maxBottom = -Float.MAX_VALUE;
        boolean found = false;

        int count = parent.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = parent.getChildAt(i);
            int pos = parent.getChildAdapterPosition(child);
            if (pos >= 0 && pos < dockSize) {
                View card = child.findViewById(R.id.card);
                View target = card != null ? card : child;

                float left = child.getLeft() + target.getLeft() + target.getTranslationX();
                float right = child.getLeft() + target.getRight() + target.getTranslationX();
                float top = child.getTop() + target.getTop() + target.getTranslationY();
                float bottom = child.getTop() + target.getBottom() + target.getTranslationY();

                if (left < minLeft) minLeft = left;
                if (right > maxRight) maxRight = right;
                if (top < minTop) minTop = top;
                if (bottom > maxBottom) maxBottom = bottom;
                found = true;
            }
        }

        if (!found) return;

        // Insets around the dock cards to form the macOS-like capsule dock
        float padH = 18f * density;
        float padV = 10f * density;
        bounds.set(minLeft - padH, minTop - padV, maxRight + padH, maxBottom + padV);
        float capsuleRadius = Math.min(bounds.height() / 2f, 26f * density);

        // Translucent liquid glass gradient fill
        fill.setShader(new LinearGradient(0, bounds.top, 0, bounds.bottom,
                0x33FFFFFF, 0x14FFFFFF, Shader.TileMode.CLAMP));

        // Specular sheen across top 40%
        specular.setShader(new LinearGradient(0, bounds.top, 0, bounds.top + bounds.height() * 0.40f,
                0x50FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));

        // Outer ambient glow / shadow
        for (int i = 1; i <= 2; i++) {
            glow.setStrokeWidth(i * 2.5f * density);
            glow.setColor((0x15 / i) << 24 | 0xFFFFFF);
            float o = i * density;
            c.drawRoundRect(bounds.left - o, bounds.top - o, bounds.right + o, bounds.bottom + o,
                    capsuleRadius + o, capsuleRadius + o, glow);
        }

        // Base glass slab
        c.drawRoundRect(bounds, capsuleRadius, capsuleRadius, fill);

        // Top specular reflection
        c.save();
        c.clipRect(bounds.left, bounds.top, bounds.right, bounds.top + bounds.height() * 0.40f);
        c.drawRoundRect(bounds, capsuleRadius, capsuleRadius, specular);
        c.restore();

        // Crisp glass rim
        c.drawRoundRect(bounds, capsuleRadius, capsuleRadius, rim);
    }
}
