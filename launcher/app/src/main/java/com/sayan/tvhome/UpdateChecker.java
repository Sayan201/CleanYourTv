package com.sayan.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.core.content.FileProvider;

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
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Checks GitHub repository releases for launcher updates and manages notifications and downloads.
 */
public final class UpdateChecker {

    @FunctionalInterface
    public interface Callback {
        void onResult(boolean updateAvailable, String version, String downloadUrl, String body);
        default void onError(Exception e) {}
    }

    private static final String GITHUB_RELEASE_URL =
            "https://api.github.com/repos/Sayan201/CleanYourTv/releases/latest";
    private static final String GITHUB_FALLBACK_COMMITS_URL =
            "https://api.github.com/repos/Sayan201/CleanYourTv/commits/main";
    private static final String DEFAULT_APK_URL =
            "https://github.com/Sayan201/CleanYourTv/releases/download/latest/launcher.apk";

    public static final String PREF_UPDATE_AVAILABLE = "update_available";
    public static final String PREF_UPDATE_VERSION = "update_version";
    public static final String PREF_UPDATE_URL = "update_url";
    public static final String PREF_UPDATE_BODY = "update_body";
    public static final String PREF_UPDATE_TIME = "update_time";
    public static final String PREF_LAST_CHECK = "update_last_check";

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private UpdateChecker() {}

    public static boolean isUpdateAvailable(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(PREF_UPDATE_AVAILABLE, false);
    }

    public static void check(Context context, Callback callback) {
        EXECUTOR.execute(() -> {
            try {
                long appBuildTime = BuildConfig.BUILD_TIME;
                long updateTimestamp = 0L;
                String versionName = "Latest Release";
                String downloadUrl = DEFAULT_APK_URL;
                String releaseBody = "";

                HttpURLConnection conn = (HttpURLConnection) new URL(GITHUB_RELEASE_URL).openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(10000);
                conn.setRequestProperty("User-Agent", "CleanYourTV-Launcher/" + BuildConfig.VERSION_NAME);

                if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    String json = readStream(conn.getInputStream());
                    JSONObject release = new JSONObject(json);
                    versionName = release.optString("name", "Latest Build");
                    releaseBody = release.optString("body", "");
                    String publishedAt = release.optString("published_at", "");
                    if (!publishedAt.isEmpty()) {
                        try {
                            updateTimestamp = Instant.parse(publishedAt).toEpochMilli();
                        } catch (Exception ignored) {}
                    }

                    JSONArray assets = release.optJSONArray("assets");
                    if (assets != null) {
                        for (int i = 0; i < assets.length(); i++) {
                            JSONObject asset = assets.getJSONObject(i);
                            String name = asset.optString("name", "");
                            if ("launcher.apk".equalsIgnoreCase(name) || name.endsWith(".apk")) {
                                downloadUrl = asset.optString("browser_download_url", downloadUrl);
                                String assetUpdated = asset.optString("updated_at", "");
                                if (!assetUpdated.isEmpty()) {
                                    try {
                                        long assetTime = Instant.parse(assetUpdated).toEpochMilli();
                                        if (assetTime > updateTimestamp) updateTimestamp = assetTime;
                                    } catch (Exception ignored) {}
                                }
                                break;
                            }
                        }
                    }
                } else {
                    // Fallback to latest commit on main branch
                    HttpURLConnection commitConn = (HttpURLConnection) new URL(GITHUB_FALLBACK_COMMITS_URL).openConnection();
                    commitConn.setConnectTimeout(8000);
                    commitConn.setReadTimeout(10000);
                    commitConn.setRequestProperty("User-Agent", "CleanYourTV-Launcher/" + BuildConfig.VERSION_NAME);
                    if (commitConn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                        String commitJson = readStream(commitConn.getInputStream());
                        JSONObject commitObj = new JSONObject(commitJson);
                        JSONObject commitDetail = commitObj.optJSONObject("commit");
                        if (commitDetail != null) {
                            releaseBody = commitDetail.optString("message", "");
                            JSONObject committer = commitDetail.optJSONObject("committer");
                            if (committer != null) {
                                String dateStr = committer.optString("date", "");
                                if (!dateStr.isEmpty()) {
                                    try {
                                        updateTimestamp = Instant.parse(dateStr).toEpochMilli();
                                    } catch (Exception ignored) {}
                                }
                            }
                        }
                    }
                }

                // If remote timestamp is newer than when this APK was compiled:
                boolean hasUpdate = updateTimestamp > appBuildTime;

                SharedPreferences prefs = context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
                prefs.edit()
                        .putBoolean(PREF_UPDATE_AVAILABLE, hasUpdate)
                        .putString(PREF_UPDATE_VERSION, versionName)
                        .putString(PREF_UPDATE_URL, downloadUrl)
                        .putString(PREF_UPDATE_BODY, releaseBody)
                        .putLong(PREF_UPDATE_TIME, updateTimestamp)
                        .putLong(PREF_LAST_CHECK, System.currentTimeMillis())
                        .apply();

                final boolean finalHasUpdate = hasUpdate;
                final String finalVersion = versionName;
                final String finalUrl = downloadUrl;
                final String finalBody = releaseBody;

                MAIN_HANDLER.post(() -> {
                    if (callback != null) callback.onResult(finalHasUpdate, finalVersion, finalUrl, finalBody);
                });

            } catch (Exception e) {
                MAIN_HANDLER.post(() -> {
                    if (callback != null) callback.onError(e);
                });
            }
        });
    }

    public static void showUpdateDialog(Activity activity, String version, String downloadUrl, String body) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle("CleanYourTV Update Available")
                .setMessage(version + "\n\nA new build has been pushed to GitHub with updates and fixes.")
                .setPositiveButton("Download & Install", (dialog, which) -> downloadAndInstall(activity, downloadUrl))
                .setNeutralButton("View on GitHub", (dialog, which) -> {
                    try {
                        activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Sayan201/CleanYourTv/releases/latest")));
                    } catch (Exception e) {
                        Toast.makeText(activity, "Could not open browser", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null);
        builder.show();
    }

    public static void downloadAndInstall(Activity activity, String downloadUrl) {
        ProgressDialog progress = new ProgressDialog(activity);
        progress.setMessage("Downloading launcher update…");
        progress.setIndeterminate(false);
        progress.setMax(100);
        progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progress.setCancelable(false);
        progress.show();

        EXECUTOR.execute(() -> {
            HttpURLConnection conn = null;
            InputStream in = null;
            FileOutputStream out = null;
            try {
                URL url = new URL(downloadUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(20000);
                conn.setInstanceFollowRedirects(true);
                conn.setRequestProperty("User-Agent", "CleanYourTV-Launcher/" + BuildConfig.VERSION_NAME);

                int fileLength = conn.getContentLength();
                in = conn.getInputStream();

                File apkFile = new File(activity.getCacheDir(), "launcher_update.apk");
                out = new FileOutputStream(apkFile);

                byte[] data = new byte[8192];
                long total = 0;
                int count;
                while ((count = in.read(data)) != -1) {
                    total += count;
                    if (fileLength > 0) {
                        int pct = (int) (total * 100 / fileLength);
                        MAIN_HANDLER.post(() -> progress.setProgress(pct));
                    }
                    out.write(data, 0, count);
                }
                out.flush();

                MAIN_HANDLER.post(() -> {
                    progress.dismiss();
                    installApk(activity, apkFile);
                });

            } catch (Exception e) {
                MAIN_HANDLER.post(() -> {
                    progress.dismiss();
                    Toast.makeText(activity, "Download failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            } finally {
                try {
                    if (out != null) out.close();
                    if (in != null) in.close();
                    if (conn != null) conn.disconnect();
                } catch (Exception ignored) {}
            }
        });
    }

    public static void installApk(Activity activity, File apkFile) {
        try {
            Uri contentUri = FileProvider.getUriForFile(activity,
                    activity.getPackageName() + ".fileprovider", apkFile);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(contentUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(activity, "Installation prompt failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private static String readStream(InputStream is) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line).append('\n');
        reader.close();
        return sb.toString();
    }
}
