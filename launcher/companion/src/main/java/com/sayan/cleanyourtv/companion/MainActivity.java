package com.sayan.cleanyourtv.companion;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import java.util.ArrayList;
import java.util.List;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import com.wirelessdebug.PairState;
import com.wirelessdebug.service.AdbPairClient;
import com.wirelessdebug.service.AdbShellController;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.File;
import java.nio.charset.StandardCharsets;
import com.wirelessdebug.WdbContext;
import com.wirelessdebug.service.ShizukuBootstrap;
import com.wirelessdebug.service.AdbClient;

public final class MainActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private LinearLayout root, page;
    private TextView status, log;
    private EditText host, port, code, packageName, legacyHost, legacyPort;
    private AdbClient legacyAdb;
    private ScrollView scroll;
    private NsdManager nsd;
    private NsdManager.DiscoveryListener discoveryListener;
    private final List<NsdServiceInfo> discovered = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final int BG = 0xff080911, PANEL = 0xff151522, MUTED = 0xffaaa9bf, ACCENT = 0xfff2f0f8;
    private static final String APK = "https://github.com/Sayan201/CleanYourTv/releases/latest/download/standalone-release.apk";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(24), dp(22), dp(24), dp(22));
        scroll.addView(root);
        setContentView(scroll);
        if (PairState.isPaired(this)) showControl(); else showWelcome();
    }

    private void showWelcome() {
        root.removeAllViews();
        root.setGravity(Gravity.TOP);
        TextView brand = text("C L E A N  Y O U R  T V", 13, 0xffb5b2c9);
        brand.setPadding(0, dp(18), 0, dp(26)); root.addView(brand);
        LinearLayout tv = new LinearLayout(this);
        tv.setOrientation(LinearLayout.VERTICAL);
        tv.setPadding(dp(14), dp(14), dp(14), dp(14));
        tv.setBackground(round(0xff17131b, 28, 0xff39313b, 1));
        LinearLayout.LayoutParams tvp = new LinearLayout.LayoutParams(-1, dp(242));
        tvp.setMargins(dp(12), dp(8), dp(12), dp(50)); root.addView(tv, tvp);
        LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        TextView time = text("GOOD EVENING\n2:17", 21, Color.WHITE);
        time.setLineSpacing(dp(4), 1f); top.addView(time, new LinearLayout.LayoutParams(0, -2, 1));
        TextView dots = text("●   ●   ●", 15, 0xffc7b3c5); top.addView(dots); tv.addView(top);
        TextView appTitle = text("Your TV", 22, 0xfff2eaf0);
        LinearLayout.LayoutParams atp = new LinearLayout.LayoutParams(-1, -2); atp.topMargin = dp(38); tv.addView(appTitle, atp);
        LinearLayout tiles = new LinearLayout(this); tiles.setPadding(dp(5), dp(5), dp(5), dp(5));
        tiles.setBackground(round(0xff0b0a10, 18, 0xff51414b, 1));
        LinearLayout.LayoutParams tilep = new LinearLayout.LayoutParams(-1, dp(65)); tilep.topMargin = dp(9); tv.addView(tiles, tilep);
        String[] names = {"YouTube", "Prime", "Apps", "Clean"};
        int[] colors = {0xff8d2030, 0xff15517e, 0xff55545d, 0xff167d75};
        for (int i=0;i<names.length;i++) {
            TextView tile = text(names[i], 11, 0xfff8f5fa); tile.setGravity(Gravity.CENTER);
            tile.setBackground(round(colors[i], 12, 0x00000000, 0));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -1, 1); p.setMargins(dp(3),0,dp(3),0); tiles.addView(tile,p);
        }
        TextView headline = text("Your TV, tidied up.", 34, 0xfff5f3fa);
        headline.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        headline.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2); hp.topMargin = dp(4); root.addView(headline,hp);
        TextView desc = text("Switch off the clutter, keep the apps you use, and get a calmer home screen. Five minutes. Everything can be put back.", 17, 0xffb4b1c5);
        desc.setLineSpacing(dp(7), 1.08f);
        LinearLayout.LayoutParams dpv = new LinearLayout.LayoutParams(-1,-2); dpv.topMargin=dp(22); root.addView(desc,dpv);
        Space spacer = new Space(this); root.addView(spacer,new LinearLayout.LayoutParams(1,0,1));
        TextView chip = text("⌁  Ready to connect to your TV", 14, 0xffc8c5d6);
        chip.setGravity(Gravity.CENTER); chip.setPadding(dp(12),dp(13),dp(12),dp(13));
        chip.setBackground(round(0xff171720, 30, 0xff4c4956, 1));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1,-2); cp.topMargin=dp(32); root.addView(chip,cp);
        TextView start = text("Start", 18, 0xff16141d); start.setGravity(Gravity.CENTER);
        start.setBackground(round(ACCENT, 40, 0x00000000, 0)); start.setPadding(0,dp(18),0,dp(18));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1,-2); sp.topMargin=dp(16); root.addView(start,sp);
        start.setOnClickListener(v -> showPairing());
        TextView returning = text("I've set up a TV before", 16, 0xff9692c6); returning.setGravity(Gravity.CENTER);
        returning.setPadding(0,dp(22),0,dp(8)); root.addView(returning);
        returning.setOnClickListener(v -> showPairing());
    }

    private void showPairing() {
        root.removeAllViews(); root.setGravity(Gravity.TOP);
        TextView back = text("‹  Back", 16, 0xffbcb8d0); root.addView(back); back.setOnClickListener(v -> showWelcome());
        TextView title = text("Connect your TV", 30, Color.WHITE);
        title.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1,-2); tp.topMargin=dp(28); root.addView(title,tp);
        TextView info = text("Choose the connection method your TV supports. Keep your phone and TV on the same Wi-Fi network.",16,MUTED);
        info.setLineSpacing(dp(5),1f); LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,-2); ip.topMargin=dp(12); root.addView(info,ip);
        button("Find TVs on this Wi-Fi", v -> discoverTvs(), true);
        host = field("TV IP address (manual fallback)");
        port = field("Pairing port (filled when discovered)");
        code = field("6-digit pairing code shown on TV");
        button("Pair with TV (newer Android)", v -> pair(), true);
        TextView legacyTitle = text("LEGACY ADB · NO PAIRING CODE", 12, 0xffb5b2c9);
        legacyTitle.setPadding(0, dp(24), 0, dp(4)); root.addView(legacyTitle);
        TextView legacyInfo = text("For TVs with network ADB enabled on port 5555. The TV must trust this phone’s ADB key.", 14, MUTED);
        legacyInfo.setPadding(0, dp(4), 0, dp(4)); root.addView(legacyInfo);
        legacyHost = field("TV IP address");
        legacyPort = field("ADB port (default 5555)"); legacyPort.setText("5555");
        button("Connect using legacy ADB", v -> connectLegacy(), true);
        status = text("Not connected",14,0xffc3c0d0); status.setPadding(0,dp(14),0,dp(8)); root.addView(status);
        log = text("Pairing instructions and results will appear here.",14,0xffd8d5e3);
        log.setTextIsSelectable(true); log.setPadding(dp(12),dp(12),dp(12),dp(12));
        log.setBackground(round(PANEL,16,0xff2b2938,1)); root.addView(log);
        button("Continue to TV tools", v -> showControl(), false);
    }

    private void showControl() {
        root.removeAllViews(); root.setGravity(Gravity.TOP);
        TextView brand=text("C L E A N  Y O U R  T V",12,0xffb5b2c9); brand.setPadding(0,dp(10),0,dp(14)); root.addView(brand);
        TextView title=text("TV tools",30,Color.WHITE); title.setTypeface(Typeface.create("sans-serif-light",0)); root.addView(title);
        status=text(PairState.isPaired(this)?"Paired — verify TV connection":"Pair your TV to get started",14,0xffb9e4c7);
        status.setPadding(0,dp(10),0,dp(16)); root.addView(status);
        if (!PairState.isPaired(this)) {
            button("Connect to a TV",v->showPairing(),true);
        } else {
            button("Check TV connection",v->run("getprop ro.product.model; getprop ro.build.version.release","Connection check"),true);
            button("List installed third-party apps",v->run("pm list packages -3","Third-party apps"),false);
            packageName=field("Package name to disable / restore");
            button("Disable selected app",v->packageAction(false),false);
            button("Restore selected app",v->packageAction(true),false);
            button("Install / update CleanYourTV launcher",v->installLauncher(),false);
            button("Reconnect / pair another TV",v->showPairing(),false);
        }
        TextView logTitle=text("ACTIVITY",12,0xffaaa6bf); logTitle.setPadding(0,dp(24),0,dp(8)); root.addView(logTitle);
        log=text("Actions and command output will appear here.",14,0xffd8d5e3); log.setTextIsSelectable(true);
        log.setPadding(dp(12),dp(12),dp(12),dp(12)); log.setBackground(round(PANEL,16,0xff2b2938,1)); root.addView(log);
        if(PairState.isPaired(this)) run("getprop ro.product.model","Connection check");
    }

    private TextView text(String value,int size,int color) {
        TextView t=new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); return t;
    }
    private GradientDrawable round(int color,int radius,int stroke,int sw) {
        GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius));
        if(sw>0)d.setStroke(dp(sw),stroke); return d;
    }
    private EditText field(String hint) {
        EditText e=new EditText(this); e.setSingleLine(true); e.setTextColor(Color.WHITE); e.setHintTextColor(0xff858194);
        e.setHint(hint); e.setTextSize(15); e.setPadding(dp(15),dp(12),dp(15),dp(12));
        e.setBackground(round(0xff171722,14,0xff393646,1));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.topMargin=dp(12); root.addView(e,p); return e;
    }
    private void button(String label,View.OnClickListener click,boolean primary) {
        TextView b=text(label,16,primary?0xff17141c:0xffeeebf5); b.setGravity(Gravity.CENTER);
        b.setPadding(dp(12),dp(15),dp(12),dp(15));
        b.setBackground(round(primary?ACCENT:PANEL,28,primary?0x00000000:0xff383544,primary?0:1));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.topMargin=dp(10); root.addView(b,p); b.setOnClickListener(click);
    }
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}


    private void discoverTvs() {
        nsd = (NsdManager) getSystemService(NSD_SERVICE);
        discovered.clear();
        append("Searching this Wi-Fi network for Android TV ADB pairing services…");
        discoveryListener = new NsdManager.DiscoveryListener() {
            @Override public void onDiscoveryStarted(String type) {}
            @Override public void onServiceFound(NsdServiceInfo service) {
                if (!"_adb-tls-pairing._tcp.".equals(service.getServiceType())) return;
                nsd.resolveService(service, new NsdManager.ResolveListener() {
                    @Override public void onResolveFailed(NsdServiceInfo s, int error) {}
                    @Override public void onServiceResolved(NsdServiceInfo resolved) {
                        runOnUiThread(() -> {
                            for (NsdServiceInfo old : discovered) {
                                if (old.getHost().equals(resolved.getHost()) && old.getPort() == resolved.getPort()) return;
                            }
                            discovered.add(resolved);
                            showDiscoveredTvs();
                        });
                    }
                });
            }
            @Override public void onServiceLost(NsdServiceInfo service) {}
            @Override public void onDiscoveryStopped(String type) {}
            @Override public void onStartDiscoveryFailed(String type, int error) {
                nsd.stopServiceDiscovery(this);
                runOnUiThread(() -> append("TV discovery failed (code " + error + "). You can still enter the TV IP manually."));
            }
            @Override public void onStopDiscoveryFailed(String type, int error) { nsd.stopServiceDiscovery(this); }
        };
        try {
            nsd.discoverServices("_adb-tls-pairing._tcp.", NsdManager.PROTOCOL_DNS_SD, discoveryListener);
            mainHandler.postDelayed(() -> {
                try { if (discoveryListener != null) nsd.stopServiceDiscovery(discoveryListener); } catch (Exception ignored) {}
                runOnUiThread(() -> {
                    if (discovered.isEmpty()) append("No ADB pairing service found. Keep Wireless debugging open on the TV and try again; some TVs do not advertise this service.");
                    else showDiscoveredTvs();
                });
            }, 8000);
        } catch (Exception e) {
            append("Could not start discovery: " + e.getMessage());
        }
    }

    private void showDiscoveredTvs() {
        if (discovered.isEmpty()) return;
        String[] labels = new String[discovered.size()];
        for (int i = 0; i < discovered.size(); i++) {
            NsdServiceInfo s = discovered.get(i);
            labels[i] = s.getServiceName() + " — " + s.getHost().getHostAddress() + ":" + s.getPort();
        }
        new AlertDialog.Builder(this).setTitle("TVs found on your network")
            .setItems(labels, (dialog, which) -> {
                NsdServiceInfo selected = discovered.get(which);
                host.setText(selected.getHost().getHostAddress());
                port.setText(String.valueOf(selected.getPort()));
                append("Found " + labels[which] + ". Enter the pairing code displayed by the TV to authorize this first connection.");
            }).setNegativeButton("Cancel", null).show();
    }

    private void connectLegacy() {
        String h = legacyHost == null ? "" : legacyHost.getText().toString().trim();
        int p;
        try { p = Integer.parseInt(legacyPort.getText().toString().trim()); }
        catch (Exception e) { append("Enter port 5555."); return; }
        if (h.isEmpty() || p < 1 || p > 65535) { append("Enter a valid TV IP address and port."); return; }
        append("Connecting to legacy ADB at " + h + ":" + p + " (no pairing code) …");
        worker.execute(() -> {
            AdbClient candidate = new AdbClient();
            try {
                File dir = new File(getFilesDir(), ShizukuBootstrap.ASSET_DIR);
                ShizukuBootstrap.ensureAssets(this);
                File keyFile = new File(dir, ShizukuBootstrap.ADB_KEY);
                if (!keyFile.isFile()) throw new IllegalStateException("ADB key asset is missing.");
                String pem = new String(java.nio.file.Files.readAllBytes(keyFile.toPath()), StandardCharsets.UTF_8);
                java.security.PrivateKey key = AdbClient.loadKey(pem);
                candidate.tls = false;
                if (!candidate.connect(h, p, key)) throw new IllegalStateException("ADB connection was not accepted.");
                String model = candidate.shell("getprop ro.product.model");
                legacyAdb = candidate;
                PairState.markPaired(this);
                runOnUiThread(() -> {
                    append("✓ Legacy ADB connected to " + h + ":" + p + "\\nTV model: " + model.trim());
                    showControl();
                });
            } catch (Throwable t) {
                candidate.close();
                String message = t.getMessage() == null ? t.toString() : t.getMessage();
                runOnUiThread(() -> append("Legacy ADB failed: " + message
                    + "\\nCheck that port 5555 is reachable and this phone's ADB key is authorized on the TV."));
            }
        });
    }

    private void pair() {
        String h=host.getText().toString().trim(), c=code.getText().toString().trim(); int p;
        try { p=Integer.parseInt(port.getText().toString().trim()); } catch(Exception e){append("Enter the pairing port shown on TV.");return;}
        if(h.isEmpty()||p<1||p>65535||!c.matches("[0-9]{6}")){append("Enter TV IP, pairing port and 6-digit code.");return;}
        append("Pairing with "+h+":"+p+" …");
        worker.execute(() -> {
            AdbPairClient.Result r=AdbPairClient.pair(h,p,c);
            runOnUiThread(() -> append((r.ok?"✓ ":"✕ ")+r.message));
            if(r.ok) run("getprop ro.product.model; getprop ro.build.version.release","Verify connection");
        });
    }
    private void packageAction(boolean restore) {
        String pkg=packageName.getText().toString().trim();
        if(!pkg.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")){append("Enter a valid package name.");return;}
        new AlertDialog.Builder(this).setTitle(restore?"Restore app?":"Disable app?")
            .setMessage((restore?"Re-enable ":"Disable ")+pkg+"? Only act on packages you recognize. Never disable Settings, System UI, Home/launcher, input or account components.")
            .setNegativeButton("Cancel",null).setPositiveButton(restore?"Restore":"Disable",(d,w)->
                run("pm "+(restore?"enable":"disable-user --user 0")+" "+pkg,(restore?"Restore ":"Disable ")+pkg)).show();
    }
    private void installLauncher() {
        new AlertDialog.Builder(this).setTitle("Install CleanYourTV launcher?")
            .setMessage("Downloads the latest APK from GitHub Releases onto the TV and asks Android Package Manager to install/update it. The TV needs internet access; some TV firmware may block this.")
            .setNegativeButton("Cancel",null).setPositiveButton("Install",(d,w)->{
                String cmd="mkdir -p /data/local/tmp/cleanyourtv && (curl -fL --connect-timeout 15 --max-time 180 '"+APK+"' -o /data/local/tmp/cleanyourtv/launcher.apk || wget -O /data/local/tmp/cleanyourtv/launcher.apk '"+APK+"') && pm install -r /data/local/tmp/cleanyourtv/launcher.apk";
                run(cmd,"Install launcher");
            }).show();
    }
    private void run(String cmd,String label) {
        append(label+" …");
        worker.execute(() -> {
            AdbShellController.Result r=AdbShellController.exec(cmd);
            runOnUiThread(() -> {
                append((r.ok?"✓ ":"✕ ")+label+"\n"+r.output);
                if(label.contains("Connection")) status.setText(r.ok?"Connected to TV":"Not connected — pair using TV Wireless debugging");
            });
        });
    }
    private void append(String s){if(log!=null)log.append("\n"+s+"\n");}
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
}