package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Downloads and caches Microsoft Bing's daily 1080p photo of the day.
 * Caches two single files locally:
 * - bing_daily.jpg (today's wallpaper, idx=0, for Home screen)
 * - bing_standby.jpg (yesterday's wallpaper, idx=1, for Standby mode)
 * Ensuring Home screen and Standby mode always show distinct pictures.
 */
final class BingWallpaperClient {
    interface Callback {
        void onWallpaperReady(File file);
        void onError(Exception e);
    }

    private static final String BING_ARCHIVE_URL =
            "https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=2&mkt=en-US";
    private static final String CACHE_FILE_NAME = "bing_daily.jpg";
    private static final String TMP_FILE_NAME = "bing_daily_tmp.jpg";
    private static final String PREF_KEY_DATE = "bing_wallpaper_date";

    private static final String STANDBY_CACHE_FILE_NAME = "bing_standby.jpg";
    private static final String STANDBY_TMP_FILE_NAME = "bing_standby_tmp.jpg";
    private static final String PREF_KEY_STANDBY_DATE = "bing_standby_date";

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    BingWallpaperClient(Context context) {
        this.context = context.getApplicationContext();
    }

    static File getCachedFile(Context context) {
        return new File(context.getFilesDir(), CACHE_FILE_NAME);
    }

    static File getStandbyCachedFile(Context context) {
        return new File(context.getFilesDir(), STANDBY_CACHE_FILE_NAME);
    }

    static String getCachedDate(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(PREF_KEY_DATE, "");
    }

    static String getStandbyCachedDate(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(PREF_KEY_STANDBY_DATE, "");
    }

    void fetch(LocalDate date, Callback callback) {
        File cached = getCachedFile(context);
        String todayStr = date != null ? date.toString() : LocalDate.now().toString();

        if (cached.exists() && cached.length() > 0 && todayStr.equals(getCachedDate(context))) {
            if (callback != null) callback.onWallpaperReady(cached);
            return;
        }

        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                // 1. Fetch metadata JSON from Bing for the 2 most recent wallpapers
                URL url = new URL(BING_ARCHIVE_URL);
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(6_000);
                conn.setReadTimeout(8_000);
                conn.setRequestProperty("User-Agent", "CleanYourTV-Custom/1.0 (Android TV launcher)");
                if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    throw new IllegalStateException("HTTP response: " + conn.getResponseCode());
                }

                String jsonText = readStream(conn.getInputStream());
                JSONObject root = new JSONObject(jsonText);
                JSONArray images = root.getJSONArray("images");
                if (images.length() == 0) {
                    throw new IllegalStateException("No images array in Bing response");
                }

                conn.disconnect();
                conn = null;

                // 2. Download today's wallpaper (images[0]) for Home Screen
                String imgPath0 = images.getJSONObject(0).getString("url");
                String fullImgUrl0 = imgPath0.startsWith("http") ? imgPath0 : ("https://www.bing.com" + imgPath0);
                File targetFile0 = getCachedFile(context);
                File tmpFile0 = new File(context.getFilesDir(), TMP_FILE_NAME);
                downloadImage(fullImgUrl0, tmpFile0, targetFile0);

                SharedPreferences prefs = context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
                prefs.edit().putString(PREF_KEY_DATE, todayStr).apply();

                // 3. Download yesterday's wallpaper (images[1]) for Standby Mode
                if (images.length() > 1) {
                    try {
                        String imgPath1 = images.getJSONObject(1).getString("url");
                        String fullImgUrl1 = imgPath1.startsWith("http") ? imgPath1 : ("https://www.bing.com" + imgPath1);
                        File targetFile1 = getStandbyCachedFile(context);
                        File tmpFile1 = new File(context.getFilesDir(), STANDBY_TMP_FILE_NAME);
                        downloadImage(fullImgUrl1, tmpFile1, targetFile1);
                        prefs.edit().putString(PREF_KEY_STANDBY_DATE, todayStr).apply();
                    } catch (Exception ignored) {
                        // Standby wallpaper download failure should not block home screen wallpaper
                    }
                }

                mainHandler.post(() -> {
                    if (callback != null) callback.onWallpaperReady(targetFile0);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (callback != null) callback.onError(e);
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    void fetchStandby(LocalDate date, Callback callback) {
        File cachedStandby = getStandbyCachedFile(context);
        String todayStr = date != null ? date.toString() : LocalDate.now().toString();

        if (cachedStandby.exists() && cachedStandby.length() > 0 && todayStr.equals(getStandbyCachedDate(context))) {
            if (callback != null) callback.onWallpaperReady(cachedStandby);
            return;
        }

        fetch(date, new Callback() {
            @Override
            public void onWallpaperReady(File file) {
                File standby = getStandbyCachedFile(context);
                if (standby.exists() && standby.length() > 0) {
                    if (callback != null) callback.onWallpaperReady(standby);
                } else if (file != null && file.exists()) {
                    if (callback != null) callback.onWallpaperReady(file);
                } else {
                    if (callback != null) callback.onError(new IllegalStateException("Standby wallpaper not available"));
                }
            }

            @Override
            public void onError(Exception e) {
                if (callback != null) callback.onError(e);
            }
        });
    }

    void close() {
        executor.shutdownNow();
    }

    private static void downloadImage(String imgUrlStr, File tmpFile, File targetFile) throws Exception {
        HttpURLConnection conn = null;
        InputStream in = null;
        FileOutputStream out = null;
        try {
            URL imgUrl = new URL(imgUrlStr);
            conn = (HttpURLConnection) imgUrl.openConnection();
            conn.setConnectTimeout(8_000);
            conn.setReadTimeout(12_000);
            conn.setRequestProperty("User-Agent", "CleanYourTV-Custom/1.0 (Android TV launcher)");
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IllegalStateException("HTTP image response: " + conn.getResponseCode());
            }

            in = conn.getInputStream();
            out = new FileOutputStream(tmpFile);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
            out.close();
            out = null;

            if (tmpFile.length() < 10_000) {
                tmpFile.delete();
                throw new IllegalStateException("Downloaded file is too small");
            }
            if (targetFile.exists()) targetFile.delete();
            if (!tmpFile.renameTo(targetFile)) {
                throw new IllegalStateException("Failed to rename temporary wallpaper file");
            }
        } finally {
            try {
                if (out != null) out.close();
            } catch (Exception ignored) {}
            try {
                if (in != null) in.close();
            } catch (Exception ignored) {}
            if (conn != null) conn.disconnect();
        }
    }

    private static String readStream(InputStream stream) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }
}
