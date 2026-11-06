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
 * Caches a single file locally (bing_daily.jpg) to prevent unbounded disk growth.
 */
final class BingWallpaperClient {
    interface Callback {
        void onWallpaperReady(File file);
        void onError(Exception e);
    }

    private static final String BING_ARCHIVE_URL =
            "https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=1&mkt=en-US";
    private static final String CACHE_FILE_NAME = "bing_daily.jpg";
    private static final String TMP_FILE_NAME = "bing_daily_tmp.jpg";
    private static final String PREF_KEY_DATE = "bing_wallpaper_date";

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    BingWallpaperClient(Context context) {
        this.context = context.getApplicationContext();
    }

    static File getCachedFile(Context context) {
        return new File(context.getFilesDir(), CACHE_FILE_NAME);
    }

    static String getCachedDate(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(PREF_KEY_DATE, "");
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
            InputStream in = null;
            FileOutputStream out = null;
            try {
                // 1. Fetch metadata JSON from Bing
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
                String imgPath = images.getJSONObject(0).getString("url");
                String fullImgUrl = imgPath.startsWith("http") ? imgPath : ("https://www.bing.com" + imgPath);

                conn.disconnect();
                conn = null;

                // 2. Download the wallpaper image to temporary file
                URL imgUrl = new URL(fullImgUrl);
                conn = (HttpURLConnection) imgUrl.openConnection();
                conn.setConnectTimeout(8_000);
                conn.setReadTimeout(12_000);
                conn.setRequestProperty("User-Agent", "CleanYourTV-Custom/1.0 (Android TV launcher)");
                if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    throw new IllegalStateException("HTTP image response: " + conn.getResponseCode());
                }

                File tmpFile = new File(context.getFilesDir(), TMP_FILE_NAME);
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

                // 3. Atomically rename temporary file to cache file
                if (tmpFile.length() < 10_000) {
                    tmpFile.delete();
                    throw new IllegalStateException("Downloaded file is too small");
                }
                File targetFile = getCachedFile(context);
                if (targetFile.exists()) targetFile.delete();
                if (!tmpFile.renameTo(targetFile)) {
                    throw new IllegalStateException("Failed to rename temporary wallpaper file");
                }

                // 4. Save cache date
                SharedPreferences prefs = context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
                prefs.edit().putString(PREF_KEY_DATE, todayStr).apply();

                mainHandler.post(() -> {
                    if (callback != null) callback.onWallpaperReady(targetFile);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (callback != null) callback.onError(e);
                });
            } finally {
                try {
                    if (out != null) out.close();
                } catch (Exception ignored) {}
                try {
                    if (in != null) in.close();
                } catch (Exception ignored) {}
                if (conn != null) conn.disconnect();
            }
        });
    }

    void close() {
        executor.shutdownNow();
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
