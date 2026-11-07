package com.sayan.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Remote-friendly settings owned by the launcher; system settings stay one row away. */
public final class LauncherSettingsActivity extends Activity {
    private SharedPreferences choices;
    private final TextView[] columnChoices = new TextView[4];
    private TextView animationsChoice;
    private TextView advancedChoice;
    private TextView followThemeChoice;
    private View updateChoice;
    private TextView updateTitle;
    private TextView updateStatus;
    private View updateDot;
    private TextView screensaverTimeoutChoice;
    private int columns;
    private boolean animations;
    private boolean advancedEffects;
    private int screensaverTimeoutMin;

    private static final int[] SCREENSAVER_TIMEOUTS = {5, 10, 15, 3, 0};

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_launcher_settings);
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        Config config = Config.load(this);
        columns = config.columns;
        animations = config.animations;
        advancedEffects = config.animations && config.advancedEffects;
        screensaverTimeoutMin = choices.getInt("screensaver_timeout_min", 5);

        int[] ids = {R.id.columns3, R.id.columns4, R.id.columns5, R.id.columns6};
        for (int i = 0; i < ids.length; i++) {
            final int value = i + 3;
            columnChoices[i] = findViewById(ids[i]);
            columnChoices[i].setOnClickListener(v -> {
                if (columns == value) return;
                columns = value;
                choices.edit().putInt("columns", value).apply();
                setResult(RESULT_OK);
                refresh();
            });
        }
        // Keep the numeric choices increasing in the Hebrew reading direction. Reorder the
        // actual views so D-pad focus follows their positions without changing the saved values.
        if (getResources().getBoolean(R.bool.rtl_app_grid)) {
            LinearLayout row = (LinearLayout) columnChoices[0].getParent();
            row.removeAllViews();
            for (int i = columnChoices.length - 1; i >= 0; i--) row.addView(columnChoices[i]);
        }

        // Update button & notification dot
        updateChoice = findViewById(R.id.updateChoice);
        updateTitle = findViewById(R.id.updateTitle);
        updateStatus = findViewById(R.id.updateStatus);
        updateDot = findViewById(R.id.updateDot);
        updateChoice.setOnClickListener(v -> onUpdateClicked());

        // Screensaver options
        screensaverTimeoutChoice = findViewById(R.id.screensaverTimeoutChoice);
        screensaverTimeoutChoice.setOnClickListener(v -> {
            int nextIdx = 0;
            for (int i = 0; i < SCREENSAVER_TIMEOUTS.length; i++) {
                if (SCREENSAVER_TIMEOUTS[i] == screensaverTimeoutMin) {
                    nextIdx = (i + 1) % SCREENSAVER_TIMEOUTS.length;
                    break;
                }
            }
            screensaverTimeoutMin = SCREENSAVER_TIMEOUTS[nextIdx];
            choices.edit().putInt("screensaver_timeout_min", screensaverTimeoutMin).apply();
            setResult(RESULT_OK);
            refresh();
        });

        findViewById(R.id.screensaverPreviewChoice).setOnClickListener(v -> {
            startActivity(new Intent(this, ScreensaverActivity.class));
        });

        findViewById(R.id.screensaverSettingsChoice).setOnClickListener(v -> {
            Intent dreamSettings = new Intent("android.settings.DREAM_SETTINGS");
            if (dreamSettings.resolveActivity(getPackageManager()) == null) {
                dreamSettings = new Intent(Settings.ACTION_DISPLAY_SETTINGS);
            }
            open(dreamSettings);
        });

        animationsChoice = findViewById(R.id.animationsChoice);
        animationsChoice.setOnClickListener(v -> {
            animations = !animations;
            if (!animations) advancedEffects = false;
            choices.edit().putBoolean("animations", animations)
                    .putBoolean("advanced_effects", advancedEffects).apply();
            setResult(RESULT_OK);
            refresh();
        });
        advancedChoice = findViewById(R.id.advancedChoice);
        advancedChoice.setOnClickListener(v -> {
            if (advancedEffects) {
                setAdvanced(false);
            } else {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.advanced_effects)
                        .setMessage(R.string.advanced_effects_warning)
                        .setPositiveButton(R.string.enable, (dialog, which) -> setAdvanced(true))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        });
        followThemeChoice = findViewById(R.id.followThemeChoice);
        followThemeChoice.setOnClickListener(v -> {
            if (!hasOnTvChoices()) return;
            choices.edit().remove("columns").remove("animations").remove("advanced_effects")
                    .remove("screensaver_timeout_min").commit();
            Config current = Config.load(this);
            columns = current.columns;
            animations = current.animations;
            advancedEffects = current.animations && current.advancedEffects;
            screensaverTimeoutMin = 5;
            setResult(RESULT_OK);
            refresh();
        });
        findViewById(R.id.defaultHomeChoice).setOnClickListener(v -> {
            Toast.makeText(this, R.string.home_settings_help, Toast.LENGTH_LONG).show();
            open(new Intent(Settings.ACTION_HOME_SETTINGS));
        });
        findViewById(R.id.tvSettingsChoice).setOnClickListener(v ->
                open(new Intent(Settings.ACTION_SETTINGS)));
        refresh();
        columnChoices[Math.max(0, Math.min(columns - 3, 3))].requestFocus();
    }

    private void onUpdateClicked() {
        if (UpdateChecker.isUpdateAvailable(this)) {
            String version = choices.getString(UpdateChecker.PREF_UPDATE_VERSION, "CleanYourTV Update");
            String url = choices.getString(UpdateChecker.PREF_UPDATE_URL, "");
            String body = choices.getString(UpdateChecker.PREF_UPDATE_BODY, "");
            UpdateChecker.showUpdateDialog(this, version, url, body);
        } else {
            updateStatus.setText(R.string.update_checking);
            Toast.makeText(this, R.string.update_checking, Toast.LENGTH_SHORT).show();
            UpdateChecker.check(this, new UpdateChecker.Callback() {
                @Override
                public void onResult(boolean updateAvailable, String version, String downloadUrl, String body) {
                    refresh();
                    if (updateAvailable) {
                        UpdateChecker.showUpdateDialog(LauncherSettingsActivity.this, version, downloadUrl, body);
                    } else {
                        Toast.makeText(LauncherSettingsActivity.this, R.string.up_to_date, Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onError(Exception e) {
                    refresh();
                    Toast.makeText(LauncherSettingsActivity.this, "Check failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void setAdvanced(boolean enabled) {
        advancedEffects = enabled;
        choices.edit().putBoolean("advanced_effects", enabled).apply();
        setResult(RESULT_OK);
        refresh();
    }

    private void refresh() {
        for (int i = 0; i < columnChoices.length; i++) {
            columnChoices[i].setSelected(columns == i + 3);
        }

        boolean hasUpdate = UpdateChecker.isUpdateAvailable(this);
        updateDot.setVisibility(hasUpdate ? View.VISIBLE : View.GONE);
        if (hasUpdate) {
            String ver = choices.getString(UpdateChecker.PREF_UPDATE_VERSION, getString(R.string.update_available_title));
            updateTitle.setText(ver);
            updateStatus.setText(R.string.update_ready_hint);
        } else {
            updateTitle.setText(R.string.check_updates);
            updateStatus.setText(R.string.up_to_date);
        }

        String timeoutLabel = screensaverTimeoutMin == 0
                ? getString(R.string.off)
                : screensaverTimeoutMin + " min";
        screensaverTimeoutChoice.setText(getString(R.string.screensaver_idle_state, timeoutLabel));

        animationsChoice.setText(getString(R.string.animations_state,
                getString(animations ? R.string.on : R.string.off)));
        advancedChoice.setText(getString(R.string.advanced_effects_state,
                getString(advancedEffects ? R.string.on : R.string.off)));
        advancedChoice.setEnabled(animations);
        advancedChoice.setAlpha(animations ? 1f : 0.5f);
        followThemeChoice.setSelected(!hasOnTvChoices());
    }

    private boolean hasOnTvChoices() {
        return choices.contains("columns") || choices.contains("animations")
                || choices.contains("advanced_effects") || choices.contains("screensaver_timeout_min");
    }

    private void open(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }
}
