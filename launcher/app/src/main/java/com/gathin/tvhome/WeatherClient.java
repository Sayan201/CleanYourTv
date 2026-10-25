package com.gathin.tvhome;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/** Approximate foreground location plus a current Celsius forecast from Open-Meteo. */
final class WeatherClient {
    interface Callback {
        void onWeather(String summary, String locationLabel);
        void onUnavailable();
    }

    private static final double GAYESHPUR_LAT = 22.9585;
    private static final double GAYESHPUR_LON = 88.4920;
    private static final long LOCATION_TIMEOUT_MS = 7_000L;
    private final Activity activity;
    private final Handler main = new Handler(Looper.getMainLooper());
    private LocationManager manager;
    private LocationListener pendingListener;
    private Runnable locationTimeout;

    WeatherClient(Activity activity) {
        this.activity = activity;
        manager = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
    }

    void fetch(boolean useDeviceLocation, Callback callback) {
        cancelLocationRequest();
        if (!useDeviceLocation || !hasLocationPermission()) {
            fetchForecast(GAYESHPUR_LAT, GAYESHPUR_LON, "Gayeshpur", callback);
            return;
        }

        Location cached = newestLastKnown();
        if (cached != null && System.currentTimeMillis() - cached.getTime() < 12L * 60 * 60 * 1000) {
            fetchForecast(cached.getLatitude(), cached.getLongitude(), "Current location", callback);
            return;
        }
        requestFreshLocation(callback);
    }

    void close() {
        cancelLocationRequest();
        manager = null;
    }

    private boolean hasLocationPermission() {
        return activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private Location newestLastKnown() {
        if (manager == null) return null;
        Location best = null;
        try {
            for (String provider : manager.getProviders(true)) {
                Location candidate = manager.getLastKnownLocation(provider);
                if (candidate != null && (best == null || candidate.getTime() > best.getTime())) best = candidate;
            }
        } catch (SecurityException ignored) {
        }
        return best;
    }

    private void requestFreshLocation(Callback callback) {
        if (manager == null) {
            fetchForecast(GAYESHPUR_LAT, GAYESHPUR_LON, "Gayeshpur", callback);
            return;
        }
        List<String> providers;
        try {
            providers = manager.getProviders(true);
        } catch (Exception e) {
            fetchForecast(GAYESHPUR_LAT, GAYESHPUR_LON, "Gayeshpur", callback);
            return;
        }
        final boolean[] finished = {false};
        pendingListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                if (finished[0]) return;
                finished[0] = true;
                cancelLocationRequest();
                fetchForecast(location.getLatitude(), location.getLongitude(), "Current location", callback);
            }

            @Override public void onProviderEnabled(String provider) { }
            @Override public void onProviderDisabled(String provider) { }
        };
        boolean requested = false;
        for (String provider : providers) {
            if (LocationManager.PASSIVE_PROVIDER.equals(provider)) continue;
            try {
                manager.requestLocationUpdates(provider, 0L, 0f, pendingListener, Looper.getMainLooper());
                requested = true;
            } catch (SecurityException | IllegalArgumentException ignored) {
            }
        }
        if (!requested) {
            pendingListener = null;
            fetchForecast(GAYESHPUR_LAT, GAYESHPUR_LON, "Gayeshpur", callback);
            return;
        }
        locationTimeout = () -> {
            if (finished[0]) return;
            finished[0] = true;
            cancelLocationRequest();
            fetchForecast(GAYESHPUR_LAT, GAYESHPUR_LON, "Gayeshpur", callback);
        };
        main.postDelayed(locationTimeout, LOCATION_TIMEOUT_MS);
    }

    private void cancelLocationRequest() {
        if (manager != null && pendingListener != null) {
            try {
                manager.removeUpdates(pendingListener);
            } catch (SecurityException ignored) {
            }
        }
        pendingListener = null;
        if (locationTimeout != null) main.removeCallbacks(locationTimeout);
        locationTimeout = null;
    }

    private void fetchForecast(double latitude, double longitude, String locationLabel, Callback callback) {
        Thread worker = new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                String coords = String.format(Locale.US, "latitude=%.5f&longitude=%.5f", latitude, longitude);
                URL url = new URL("https://api.open-meteo.com/v1/forecast?" + coords
                        + "&current=temperature_2m,weather_code&temperature_unit=celsius&timezone=auto");
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(8_000);
                connection.setReadTimeout(8_000);
                connection.setRequestProperty("User-Agent", "CleanYourTV-Custom/1.0 (Android TV launcher)");
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) throw new IllegalStateException("Weather response unavailable");
                StringBuilder body = new StringBuilder();
                try (InputStream stream = connection.getInputStream();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) body.append(line);
                }
                JSONObject current = new JSONObject(body.toString()).getJSONObject("current");
                int degrees = (int) Math.round(current.getDouble("temperature_2m"));
                String condition = condition(current.getInt("weather_code"));
                String summary = degrees + "°C  ·  " + condition;
                main.post(() -> callback.onWeather(summary, locationLabel));
            } catch (Exception e) {
                main.post(callback::onUnavailable);
            } finally {
                if (connection != null) connection.disconnect();
            }
        }, "home-weather");
        worker.setDaemon(true);
        worker.start();
    }

    private static String condition(int code) {
        if (code == 0) return "Clear";
        if (code <= 2) return "Partly cloudy";
        if (code == 3) return "Overcast";
        if (code == 45 || code == 48) return "Fog";
        if (code <= 57) return "Drizzle";
        if (code <= 67) return "Rain";
        if (code <= 77) return "Snow";
        if (code <= 82) return "Showers";
        if (code <= 86) return "Snow showers";
        if (code <= 99) return "Thunderstorm";
        return "Weather";
    }
}
