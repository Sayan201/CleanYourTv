package com.sayan.tvhome;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.service.dreams.DreamService;
import android.util.DisplayMetrics;
import android.widget.ImageView;

import java.io.File;
import java.time.LocalDate;
import java.util.Random;

/**
 * Ambient standby screensaver: displays Microsoft Bing's daily standby wallpaper
 * (distinct from the Home screen wallpaper) with a clean fixed clock and date.
 */
public class ClockDream extends DreamService {

    private BingWallpaperClient bingClient;
    private ImageView wallpaperView;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false); // Any remote key wakes the TV back to where it was.
        setFullscreen(true);
        setScreenBright(true); // Maintain vibrant wallpaper display
        setContentView(R.layout.dream_clock);
        wallpaperView = findViewById(R.id.dreamWallpaper);
        bingClient = new BingWallpaperClient(this);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        loadStandbyWallpaper();
    }

    @Override
    public void onDreamingStopped() {
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        if (bingClient != null) bingClient.close();
        wallpaperView = null;
        super.onDetachedFromWindow();
    }

    private void loadStandbyWallpaper() {
        LocalDate today = LocalDate.now();
        File standbyFile = BingWallpaperClient.getStandbyCachedFile(this);
        String standbyDate = BingWallpaperClient.getStandbyCachedDate(this);

        if (standbyFile.exists() && standbyFile.length() > 0 && today.toString().equals(standbyDate)) {
            Bitmap bmp = decodeWallpaper(standbyFile);
            if (bmp != null) {
                applyWallpaper(bmp);
                return;
            }
        }

        // Show cached standby wallpaper or distinct bundled fallback wallpaper first
        if (standbyFile.exists() && standbyFile.length() > 0) {
            Bitmap bmp = decodeWallpaper(standbyFile);
            if (bmp != null) applyWallpaper(bmp);
            else showFallbackWallpaper(today);
        } else {
            showFallbackWallpaper(today);
        }

        // Fetch today's standby wallpaper in background
        if (bingClient != null) {
            bingClient.fetchStandby(today, new BingWallpaperClient.Callback() {
                @Override
                public void onWallpaperReady(File file) {
                    if (file != null && file.exists()) {
                        Bitmap freshBmp = decodeWallpaper(file);
                        if (freshBmp != null) applyWallpaper(freshBmp);
                    }
                }

                @Override
                public void onError(Exception e) {
                    // Retain current wallpaper on failure
                }
            });
        }
    }

    private void applyWallpaper(Bitmap bmp) {
        if (wallpaperView != null && bmp != null) {
            wallpaperView.setImageBitmap(bmp);
        }
    }

    private void showFallbackWallpaper(LocalDate today) {
        try {
            int yearOffset = new Random(today.getYear() * 97L + 31L).nextInt(MainActivity.DAILY_WALLPAPERS.length);
            // Offset by 3 so standby fallback is always different from home screen fallback
            int index = Math.floorMod(today.getDayOfYear() - 1 + yearOffset + 3, MainActivity.DAILY_WALLPAPERS.length);
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap bitmap = BitmapFactory.decodeResource(getResources(), MainActivity.DAILY_WALLPAPERS[index], opts);
            if (bitmap != null) applyWallpaper(bitmap);
        } catch (Exception | OutOfMemoryError ignored) {
        }
    }

    private Bitmap decodeWallpaper(File file) {
        if (file == null || !file.isFile() || file.length() <= 0) return null;
        try {
            DisplayMetrics dm = getResources().getDisplayMetrics();
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), opts);
            int w = opts.outWidth, h = opts.outHeight;
            if (w <= 0 || h <= 0) return null;
            int sample = WallpaperSize.sampleSize(w, h, dm.widthPixels, dm.heightPixels);
            if (!WallpaperSize.accepted(w / sample, h / sample)) return null;
            opts.inJustDecodeBounds = false;
            opts.inSampleSize = sample;
            int[] scale = WallpaperSize.coverScale(w / sample, h / sample, dm.widthPixels, dm.heightPixels);
            if (scale != null) {
                opts.inScaled = true;
                opts.inDensity = scale[0];
                opts.inTargetDensity = scale[1];
            }
            opts.inPreferredConfig = Bitmap.Config.RGB_565; // ~4 MB RAM usage
            return BitmapFactory.decodeFile(file.getPath(), opts);
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }
}
