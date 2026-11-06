package io.github.xboto.termrovercfaccess;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

public class ThemesActivity extends Activity {
    private static final String TAG = "TermRoverCfAccess";
    private static final int REQ_IMPORT = 1;
    private static final int REQ_EXPORT = 2;

    private static final Object LOCK = new Object();
    private static boolean listenerRegistered;
    private static XposedService processService;
    private static ThemesActivity active;

    private volatile XposedService service;
    private TextView status;
    private LinearLayout list;
    private Button btnImport;
    private Button btnExport;
    private Button btnClear;
    private String currentJson;
    private Switch themesSwitch;
    private LinearLayout themesBody;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        setStatus("Connecting to Vector…");
        setButtons(false);
        XposedService current;
        synchronized (LOCK) {
            active = this;
            current = processService;
            if (!listenerRegistered) {
                listenerRegistered = true;
                XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {
                    @Override
                    public void onServiceBind(XposedService s) {
                        ThemesActivity a;
                        synchronized (LOCK) {
                            processService = s;
                            a = active;
                        }
                        if (a != null) a.onBind(s);
                    }

                    @Override
                    public void onServiceDied(XposedService s) {
                        ThemesActivity a;
                        synchronized (LOCK) {
                            if (processService == s) processService = null;
                            a = active;
                        }
                        if (a != null) a.onDied();
                    }
                });
            }
        }
        if (current != null) onBind(current);
    }

    @Override
    protected void onDestroy() {
        synchronized (LOCK) {
            if (active == this) active = null;
        }
        super.onDestroy();
    }

    private void onBind(XposedService s) {
        service = s;
        runOnUiThread(this::reload);
    }

    private void onDied() {
        service = null;
        runOnUiThread(() -> {
            themesSwitch.setEnabled(false);
            status.setVisibility(View.VISIBLE);
            setButtons(false);
            setStatus("Vector disconnected. Reopen this screen.");
        });
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(8));

        android.widget.FrameLayout frame = new android.widget.FrameLayout(this);
        frame.setFitsSystemWindows(true);
        frame.addView(root);

        root.addView(forcedSwitch("Cloudflare Access",
                "cf: hosts connect through the bundled cloudflared."));
        root.addView(forcedSwitch("Block telemetry",
                "Firebase crash and usage reporting is blocked in TermRover."));

        themesSwitch = new Switch(this);
        themesSwitch.setEnabled(false);
        themesSwitch.setOnClickListener(v -> askThemesChange(themesSwitch.isChecked()));
        root.addView(switchCard("Custom themes",
                "Adds imported themes to Settings → Theme in TermRover.", themesSwitch));

        themesBody = new LinearLayout(this);
        themesBody.setOrientation(LinearLayout.VERTICAL);
        themesBody.setVisibility(View.GONE);
        root.addView(themesBody, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView help = new TextView(this);
        help.setText("Import a JSON file (termrover-themes v1 format). The themes appear at the "
                + "end of Settings → Theme in TermRover. After importing or deleting, close and "
                + "reopen TermRover.");
        help.setTextSize(13);
        help.setPadding(0, dp(6), 0, dp(10));
        themesBody.addView(help);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        btnImport = button("Import", v -> pickImport());
        btnExport = button("Export", v -> pickExport());
        btnClear = button("Delete", v -> confirmClear());
        buttons.addView(btnImport);
        buttons.addView(btnExport);
        buttons.addView(btnClear);
        themesBody.addView(buttons);

        status = new TextView(this);
        status.setTextSize(13);
        status.setPadding(0, dp(10), 0, dp(10));
        status.setTextIsSelectable(true);
        root.addView(status, root.indexOfChild(themesBody));

        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        themesBody.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(frame);
    }

    private View forcedSwitch(String label, String summary) {
        Switch sw = new Switch(this);
        sw.setChecked(true);
        sw.setEnabled(false);
        return switchCard(label, summary, sw);
    }

    private View switchCard(String label, String summary, Switch sw) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(14), dp(12), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x1AFFFFFF);
        bg.setCornerRadius(dp(14));
        card.setBackground(bg);

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(16);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        TextView s = new TextView(this);
        s.setText(summary);
        s.setTextSize(13);
        s.setTextColor(Color.GRAY);
        text.addView(t);
        text.addView(s);
        card.addView(text, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        sw.setContentDescription(label);
        card.addView(sw);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        card.setLayoutParams(lp);
        return card;
    }

    private Button button(String label, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMarginEnd(dp(6));
        b.setLayoutParams(lp);
        return b;
    }

    private void setButtons(boolean connected) {
        btnImport.setEnabled(connected);
        btnExport.setEnabled(connected && currentJson != null);
        btnClear.setEnabled(connected && currentJson != null);
    }

    private void setStatus(String s) {
        status.setText(s);
        Log.i(TAG, "ThemesActivity: " + s.replace("\n", " | "));
    }

    private void reload() {
        XposedService s = service;
        if (s == null) {
            return;
        }
        currentJson = null;
        list.removeAllViews();
        try {
            if ((s.getFrameworkProperties() & XposedService.PROP_CAP_REMOTE) == 0) {
                setStatus("Saving themes requires a framework with remote file support.");
                setButtons(false);
                return;
            }
            String json = readRemote(s);
            if (json == null) {
                setStatus("No themes imported yet.");
            } else {
                List<ThemeStore.Theme> themes = ThemeStore.parse(json);
                currentJson = json;
                setStatus(themes.size() + " themes saved.");
                for (ThemeStore.Theme t : themes) {
                    list.addView(row(t));
                }
            }
        } catch (Throwable t) {
            setStatus("Error reading the saved list: " + t.getMessage());
        }
        setButtons(true);
        boolean on;
        try {

            on = s.getRemotePreferences(ThemeStore.PREFS)
                    .getBoolean(ThemeStore.KEY_ENABLED, currentJson != null);
        } catch (Throwable t) {
            on = currentJson != null;
        }
        showThemes(on);
        themesSwitch.setEnabled(true);
    }

    private void showThemes(boolean on) {
        themesSwitch.setChecked(on);
        themesBody.setVisibility(on ? View.VISIBLE : View.GONE);
        status.setVisibility(on ? View.VISIBLE : View.GONE);
    }

    private void askThemesChange(boolean on) {
        if (currentJson == null) {

            saveThemesEnabled(on, false);
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(on ? "Turn on custom themes?" : "Turn off custom themes?")
                .setMessage("TermRover will close to apply the change.")
                .setPositiveButton("Close TermRover", (d, w) -> saveThemesEnabled(on, true))
                .setNegativeButton("Cancel", (d, w) -> showThemes(!on))
                .setOnCancelListener(d -> showThemes(!on))
                .show();
    }

    private void saveThemesEnabled(boolean on, boolean close) {
        XposedService s = service;
        boolean saved = false;
        try {
            saved = s != null && s.getRemotePreferences(ThemeStore.PREFS).edit()
                    .putBoolean(ThemeStore.KEY_ENABLED, on).commit();
        } catch (Throwable t) {
            Log.w(TAG, "saving themes_enabled failed", t);
        }
        if (!saved) {
            showThemes(!on);
            status.setVisibility(View.VISIBLE);
            setStatus("Could not save the setting. Reopen this screen and try again.");
            return;
        }
        showThemes(on);
        if (close) {
            closeTermRover();
        }
    }

    private void closeTermRover() {
        Intent i = new Intent(ModuleMain.ACTION_CLOSE).setPackage(ModuleMain.TARGET);
        sendOrderedBroadcast(i, null, new android.content.BroadcastReceiver() {
            @Override
            public void onReceive(android.content.Context c, Intent r) {
                boolean wasRunning = getResultCode() == ModuleMain.CLOSE_ACK;
                Log.i(TAG, "close broadcast answered, running=" + wasRunning);
                status.setVisibility(View.VISIBLE);
                setStatus(wasRunning
                        ? "TermRover closed. The change applies the next time you open it."
                        : "Setting saved. The change applies the next time you open TermRover.");
            }
        }, null, 0, null, null);
    }

    private static String readRemote(XposedService s) throws Exception {
        boolean exists = false;
        for (String f : s.listRemoteFiles()) {
            if (ThemeStore.REMOTE_FILE.equals(f)) {
                exists = true;
            }
        }
        if (!exists) {
            return null;
        }
        try (ParcelFileDescriptor pfd = s.openRemoteFile(ThemeStore.REMOTE_FILE);
             FileInputStream in = new FileInputStream(pfd.getFileDescriptor())) {
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return json.trim().isEmpty() ? null : json;
        }
    }

    private View row(ThemeStore.Theme t) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));

        LinearLayout swatch = new LinearLayout(this);
        swatch.setOrientation(LinearLayout.HORIZONTAL);
        swatch.setGravity(Gravity.CENTER_VERTICAL);
        swatch.setPadding(dp(8), 0, dp(8), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(t.background);
        bg.setCornerRadius(dp(6));
        bg.setStroke(dp(1), t.chrome[7]);
        swatch.setBackground(bg);
        TextView a = new TextView(this);
        a.setText("A");
        a.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        a.setTextColor(t.foreground);
        swatch.addView(a);
        for (int idx : new int[]{1, 2, 3, 4, 5}) {
            View dot = new View(this);
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(t.ansi[idx]);
            dot.setBackground(d);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(8), dp(8));
            lp.setMarginStart(dp(3));
            swatch.addView(dot, lp);
        }
        row.addView(swatch, new LinearLayout.LayoutParams(dp(84), dp(36)));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(dp(12), 0, 0, 0);
        TextView name = new TextView(this);
        name.setText(t.name);
        name.setTextSize(15);
        TextView id = new TextView(this);
        id.setText(t.id);
        id.setTextSize(11);
        id.setTextColor(Color.GRAY);
        text.addView(name);
        text.addView(id);
        row.addView(text);
        return row;
    }

    private void pickImport() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "text/plain",
                "application/octet-stream"});
        startActivityForResult(i, REQ_IMPORT);
    }

    private void pickExport() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE, "custom-themes.json");
        startActivityForResult(i, REQ_EXPORT);
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setMessage("Delete the saved theme list? Export it first to keep a copy.")
                .setPositiveButton("Delete", (d, w) -> {
                    XposedService s = service;
                    if (s != null) {
                        s.deleteRemoteFile(ThemeStore.REMOTE_FILE);
                    }
                    reload();
                    setStatus("List deleted. Restart TermRover to remove the themes.");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        if (requestCode == REQ_IMPORT) {
            doImport(uri);
        } else if (requestCode == REQ_EXPORT) {
            doExport(uri);
        }
    }

    private void doImport(Uri uri) {
        XposedService s = service;
        if (s == null) {
            setStatus("Vector is disconnected.");
            return;
        }
        String json;
        List<ThemeStore.Theme> themes;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[16384];
            int n;
            while ((n = in.read(chunk)) > 0) {
                buf.write(chunk, 0, n);
                if (buf.size() > ThemeStore.MAX_BYTES) {
                    throw new IllegalArgumentException("file is larger than 1 MB");
                }
            }
            json = buf.toString(StandardCharsets.UTF_8.name());
            themes = ThemeStore.parse(json);
        } catch (Throwable t) {
            setStatus("Import failed: " + t.getMessage());
            return;
        }
        try (ParcelFileDescriptor pfd = s.openRemoteFile(ThemeStore.REMOTE_FILE);
             FileOutputStream out = new FileOutputStream(pfd.getFileDescriptor())) {
            out.getChannel().truncate(0);
            out.write(json.getBytes(StandardCharsets.UTF_8));
        } catch (Throwable t) {
            setStatus("Save failed: " + t.getMessage());
            return;
        }
        reload();
        setStatus(themes.size() + " themes imported. Close and reopen TermRover.");
    }

    private String displayName(Uri uri) {
        try (android.database.Cursor c = getContentResolver().query(uri,
                new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                return c.getString(0);
            }
        } catch (Throwable ignored) {
        }
        return uri.getLastPathSegment();
    }

    private void doExport(Uri uri) {
        String json = currentJson;
        if (json == null) {
            setStatus("Import themes before exporting.");
            return;
        }
        try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
            out.write(json.getBytes(StandardCharsets.UTF_8));
            setStatus("List exported to " + displayName(uri) + ".");
        } catch (Throwable t) {
            setStatus("Export failed: " + t.getMessage());
        }
    }
}
