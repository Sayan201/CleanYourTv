package com.gathin.tvhome;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
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

    private static final long LOCATION_TIMEOUT_MS = 4_000L;
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
        if (useDeviceLocation && hasLocationPermission()) {
            Location cached = newestLastKnown();
            if (cached != null && System.currentTimeMillis() - cached.getTime() < 12L * 60 * 60 * 1000) {
                fetchForecastWithResolution(cached.getLatitude(), cached.getLongitude(), null, callback);
                return;
            }
            requestFreshLocation(callback);
            return;
        }
        // Fall back directly to IP-based geolocation
        fetchWithIpFallback(callback);
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
            fetchWithIpFallback(callback);
            return;
        }
        List<String> providers;
        try {
            providers = manager.getProviders(true);
        } catch (Exception e) {
            fetchWithIpFallback(callback);
            return;
        }
        final boolean[] finished = {false};
        pendingListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                if (finished[0]) return;
                finished[0] = true;
                cancelLocationRequest();
                fetchForecastWithResolution(location.getLatitude(), location.getLongitude(), null, callback);
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
            fetchWithIpFallback(callback);
            return;
        }
        locationTimeout = () -> {
            if (finished[0]) return;
            finished[0] = true;
            cancelLocationRequest();
            fetchWithIpFallback(callback);
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

    private void fetchWithIpFallback(Callback callback) {
        Thread worker = new Thread(() -> {
            LocationResult loc = queryIpLocation();
            if (loc != null) {
                fetchForecastInternal(loc.lat, loc.lon, loc.city, callback);
            } else {
                main.post(callback::onUnavailable);
            }
        }, "home-weather-ip");
        worker.setDaemon(true);
        worker.start();
    }

    private void fetchForecastWithResolution(double lat, double lon, String cityHint, Callback callback) {
        Thread worker = new Thread(() -> {
            String city = cityHint;
            if (city == null || city.isEmpty()) {
                city = resolveCity(lat, lon);
            }
            if (city == null || city.isEmpty()) {
                LocationResult ipLoc = queryIpLocation();
                if (ipLoc != null && ipLoc.city != null && !ipLoc.city.isEmpty()) {
                    city = ipLoc.city;
                }
            }
            fetchForecastInternal(lat, lon, city != null ? city : "", callback);
        }, "home-weather-res");
        worker.setDaemon(true);
        worker.start();
    }

    private static class LocationResult {
        final double lat;
        final double lon;
        final String city;

        LocationResult(double lat, double lon, String city) {
            this.lat = lat;
            this.lon = lon;
            this.city = city;
        }
    }

    private static LocationResult queryIpLocation() {
        LocationResult res = fetchIpWhoIs();
        if (res != null) return res;
        return fetchIpApiCo();
    }

    private static LocationResult fetchIpWhoIs() {
        HttpURLConnection conn = null;
        try {
            URL url = new URL("https://ipwho.is/");
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(4_000);
            conn.setReadTimeout(4_000);
            conn.setRequestProperty("User-Agent", "CleanYourTV-Custom/1.0 (Android TV launcher)");
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) return null;
            String text = readStream(conn.getInputStream());
            JSONObject json = new JSONObject(text);
            if (!json.optBoolean("success", true)) return null;
            double lat = json.getDouble("latitude");
            double lon = json.getDouble("longitude");
            String city = json.optString("city", "").trim();
            if (city.isEmpty()) city = json.optString("region", "").trim();
            return new LocationResult(lat, lon, city);
        } catch (Exception ignored) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static LocationResult fetchIpApiCo() {
        HttpURLConnection conn = null;
        try {
            URL url = new URL("https://ipapi.co/json/");
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(4_000);
            conn.setReadTimeout(4_000);
            conn.setRequestProperty("User-Agent", "CleanYourTV-Custom/1.0 (Android TV launcher)");
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) return null;
            String text = readStream(conn.getInputStream());
            JSONObject json = new JSONObject(text);
            double lat = json.getDouble("latitude");
            double lon = json.getDouble("longitude");
            String city = json.optString("city", "").trim();
            if (city.isEmpty()) city = json.optString("region", "").trim();
            return new LocationResult(lat, lon, city);
        } catch (Exception ignored) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private String resolveCity(double lat, double lon) {
        if (activity != null && Geocoder.isPresent()) {
            try {
                Geocoder geocoder = new Geocoder(activity, Locale.getDefault());
                List<Address> addresses = geocoder.getFromLocation(lat, lon, 1);
                if (addresses != null && !addresses.isEmpty()) {
                    Address a = addresses.get(0);
                    if (a.getLocality() != null && !a.getLocality().isEmpty()) return a.getLocality();
                    if (a.getSubAdminArea() != null && !a.getSubAdminArea().isEmpty()) return a.getSubAdminArea();
                    if (a.getAdminArea() != null && !a.getAdminArea().isEmpty()) return a.getAdminArea();
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static String readStream(InputStream stream) throws Exception {
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) body.append(line);
        }
        return body.toString();
    }

    private void fetchForecastInternal(double latitude, double longitude, String locationLabel, Callback callback) {
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
                String body = readStream(connection.getInputStream());
                JSONObject current = new JSONObject(body).getJSONObject("current");
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
