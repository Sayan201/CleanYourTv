package com.sayan.cleanyourtv.companion;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import com.wirelessdebug.PairState;
import com.wirelessdebug.service.AdbPairClient;
import com.wirelessdebug.service.AdbShellController;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private LinearLayout root;
    private TextView status, log;
    private EditText host, port, code, packageName;
    private static final String APK = "https://github.com/Sayan201/CleanYourTv/releases/latest/download/standalone-release.apk";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this); root.setOrientation(1); root.setPadding(dp(20),dp(24),dp(20),dp(24));
        root.setBackgroundColor(Color.rgb(15,19,30)); scroll.addView(root); setContentView(scroll);
        TextView title = new TextView(this); title.setText("CleanYourTV"); title.setTextColor(Color.WHITE); title.setTextSize(28); root.addView(title);
        TextView hint = new TextView(this); hint.setText("Phone companion • TV cleaner • Launcher installer"); hint.setTextColor(0xffadbbd2); root.addView(hint);
        status = new TextView(this); status.setTextColor(0xff89dcaa); status.setPadding(0,dp(12),0,dp(8)); root.addView(status);
        host = field("TV IP address"); port = field("Pairing port from TV"); code = field("6-digit pairing code");
        button("Pair with TV", v -> pair());
        button("Check connection", v -> run("getprop ro.product.model; getprop ro.build.version.release", "Connection check"));
        button("List installed third-party apps", v -> run("pm list packages -3", "Third-party apps"));
        packageName = field("Package name to disable / restore");
        button("Disable selected app", v -> packageAction(false));
        button("Restore selected app", v -> packageAction(true));
        button("Install / update CleanYourTV launcher", v -> installLauncher());
        TextView label = new TextView(this); label.setText("Activity log"); label.setTextColor(Color.WHITE); label.setPadding(0,dp(18),0,dp(6)); root.addView(label);
        log = new TextView(this); log.setTextColor(0xffd6deed); log.setTextIsSelectable(true); root.addView(log);
        status.setText(PairState.isPaired(this) ? "Pairing saved — checking connection" : "Not paired");
        run("getprop ro.product.model", "Connection check");
    }

    private EditText field(String hint) {
        EditText e = new EditText(this); e.setSingleLine(true); e.setHint(hint); e.setTextColor(Color.WHITE); e.setHintTextColor(Color.GRAY); root.addView(e,lp()); return e;
    }
    private void button(String text, View.OnClickListener click) {
        Button b = new Button(this); b.setText(text); b.setAllCaps(false); b.setOnClickListener(click);
        LinearLayout.LayoutParams p=lp(); p.topMargin=dp(7); root.addView(b,p);
    }
    private LinearLayout.LayoutParams lp(){return new LinearLayout.LayoutParams(-1,-2);}
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
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
            .setMessage("Downloads the latest APK from your GitHub Releases page onto the TV and asks Android Package Manager to install/update it. The TV needs internet access.")
            .setNegativeButton("Cancel",null).setPositiveButton("Install",(d,w)->{
                String cmd="mkdir -p /data/local/tmp/cleanyourtv && (curl -fL --connect-timeout 15 --max-time 180 '"+APK+"' -o /data/local/tmp/cleanyourtv/launcher.apk || wget -O /data/local/tmp/cleanyourtv/launcher.apk '"+APK+"') && pm install -r /data/local/tmp/cleanyourtv/launcher.apk";
                run(cmd,"Install launcher");
            }).show();
    }
    private void run(String cmd,String label) {
        append(label+" …");
        worker.execute(() -> {
            AdbShellController.Result r=AdbShellController.exec(cmd);
            runOnUiThread(() -> {append((r.ok?"✓ ":"✕ ")+label+"\n"+r.output); if(label.contains("Connection")) status.setText(r.ok?"Connected to TV":"Not connected — pair using TV Wireless debugging");});
        });
    }
    private void append(String s){if(log!=null)log.append("\n"+s+"\n");}
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
}