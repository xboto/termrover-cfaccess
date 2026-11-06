package io.github.xboto.termrovercfaccess;

import android.app.Application;
import android.content.Context;
import android.os.BaseBundle;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedModule;
import io.github.xboto.termrovercfaccess.cf.CfHooks;

public class ModuleMain extends XposedModule {
    static final String TAG = "TermRoverCfAccess";
    static final String TARGET = "sh.termrover.app";

    static final String ACTION_CLOSE = "io.github.xboto.termrovercfaccess.CLOSE_TERMROVER";
    static final String CLOSE_PERMISSION = "io.github.xboto.termrovercfaccess.permission.CLOSE_TERMROVER";
    static final int CLOSE_ACK = 1;

    private static final Set<String> OFF_KEYS = new HashSet<>();
    private static final Set<String> DEACTIVATED_KEYS = new HashSet<>();

    static {
        OFF_KEYS.add("firebase_analytics_collection_enabled");
        OFF_KEYS.add("firebase_crashlytics_collection_enabled");
        OFF_KEYS.add("firebase_sessions_enabled");
        OFF_KEYS.add("firebase_data_collection_default_enabled");
        OFF_KEYS.add("firebase_performance_collection_enabled");
        OFF_KEYS.add("google_analytics_adid_collection_enabled");
        OFF_KEYS.add("google_analytics_automatic_screen_reporting_enabled");
        OFF_KEYS.add("google_analytics_default_allow_ad_storage");
        OFF_KEYS.add("google_analytics_default_allow_analytics_storage");
        OFF_KEYS.add("google_analytics_default_allow_ad_user_data");
        OFF_KEYS.add("google_analytics_default_allow_ad_personalization_signals");

        OFF_KEYS.add("telemetry_enabled");

        DEACTIVATED_KEYS.add("firebase_analytics_collection_deactivated");
        DEACTIVATED_KEYS.add("firebase_performance_collection_deactivated");
    }

    private final AtomicBoolean bundleHooked = new AtomicBoolean();
    private final AtomicBoolean prefsHooked = new AtomicBoolean();
    private final AtomicBoolean metaHooked = new AtomicBoolean();
    private final AtomicBoolean sessionsHooked = new AtomicBoolean();
    private final AtomicBoolean cctHooked = new AtomicBoolean();
    private final AtomicBoolean urlHooked = new AtomicBoolean();
    private final AtomicBoolean appLifecycleHooked = new AtomicBoolean();
    private final AtomicBoolean uiLocksHooked = new AtomicBoolean();
    private final AtomicBoolean telemetrySwitchLogged = new AtomicBoolean();

    private volatile ClassLoader appCl;
    private volatile Names names;
    private final CfHooks cfHooks = new CfHooks(this);

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        log(Log.INFO, TAG, "onModuleLoaded process=" + param.getProcessName()
                + " framework=" + getFrameworkName()
                + " api=" + getApiVersion()
                + " version=" + BuildConfig.VERSION_NAME);
    }

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        if (!TARGET.equals(param.getPackageName())) {
            return;
        }
        ClassLoader cl = param.getDefaultClassLoader();
        appCl = cl;
        log(Log.INFO, TAG, "onPackageLoaded " + TARGET);
        resolveNames(cl);
        hookTelemetry(cl);
        hookUrlOpenConnection();
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        if (!TARGET.equals(param.getPackageName())) {
            return;
        }
        ClassLoader cl = param.getClassLoader();
        appCl = cl;
        log(Log.INFO, TAG, "onPackageReady " + TARGET);
        resolveNames(cl);
        hookTelemetry(cl);
        hookUrlOpenConnection();
        hookAppLifecycle(cl);
        hookUiLocks(cl);
        installCustomThemes(cl);
        writeDisablePrefs(null);
        Names n = names;
        if (n != null) cfHooks.install(cl, n);
    }

    private void resolveNames(ClassLoader cl) {
        if (names != null) return;
        Names n = Names.detect(cl);
        if (n == null) {
            log(Log.WARN, TAG, "unsupported TermRover build: app-specific hooks are off");
            return;
        }
        names = n;
        log(Log.INFO, TAG, "TermRover " + n.version + " names");
    }

    private void hookTelemetry(ClassLoader cl) {
        hookBundleFlags();
        hookSharedPreferencesFlags();
        Names n = names;
        if (n == null) return;
        hookMetaReader(cl, n);
        hookSessionsEnabled(cl, n);
        hookCctUpload(cl, n);
    }

    private void hookBundleFlags() {
        if (!bundleHooked.compareAndSet(false, true)) {
            return;
        }
        try {
            Method get1 = BaseBundle.class.getDeclaredMethod("getBoolean", String.class);
            hook(get1).intercept(chain -> {
                Boolean forced = forcedFlag((String) chain.getArg(0));
                return forced != null ? forced : chain.proceed();
            });
            Method get2 = BaseBundle.class.getDeclaredMethod("getBoolean", String.class, boolean.class);
            hook(get2).intercept(chain -> {
                Boolean forced = forcedFlag((String) chain.getArg(0));
                return forced != null ? forced : chain.proceed();
            });
            log(Log.INFO, TAG, "hooked BaseBundle.getBoolean");
        } catch (Throwable t) {
            bundleHooked.set(false);
            log(Log.ERROR, TAG, "BaseBundle hook failed", t);
        }
    }

    private void hookSharedPreferencesFlags() {
        if (!prefsHooked.compareAndSet(false, true)) {
            return;
        }
        try {
            Class<?> impl = Class.forName("android.app.SharedPreferencesImpl");
            Method get = impl.getDeclaredMethod("getBoolean", String.class, boolean.class);
            hook(get).intercept(chain -> {
                Boolean forced = forcedFlag((String) chain.getArg(0));
                return forced != null ? forced : chain.proceed();
            });
            log(Log.INFO, TAG, "hooked SharedPreferencesImpl.getBoolean");
        } catch (Throwable t) {
            prefsHooked.set(false);
            log(Log.ERROR, TAG, "SharedPreferencesImpl hook failed", t);
        }
    }

    private void hookMetaReader(ClassLoader cl, Names n) {
        if (metaHooked.get()) {
            return;
        }
        try {
            Class<?> reader = Class.forName(n.metaReader, false, cl);
            Method s = reader.getDeclaredMethod("S", String.class);
            hook(s).intercept(chain -> {
                Boolean forced = forcedFlag((String) chain.getArg(0));
                return forced != null ? forced : chain.proceed();
            });
            metaHooked.set(true);
            log(Log.INFO, TAG, "hooked " + n.metaReader + ".S");
        } catch (Throwable t) {
            log(Log.WARN, TAG, n.metaReader + ".S hook skipped: " + t.getMessage());
        }
    }

    private void hookSessionsEnabled(ClassLoader cl, Names n) {
        if (sessionsHooked.get()) {
            return;
        }
        try {
            Class<?> reader = Class.forName(n.sessionsReader, false, cl);
            Method a = reader.getDeclaredMethod("a");
            hook(a).intercept(chain -> Boolean.FALSE);
            sessionsHooked.set(true);
            log(Log.INFO, TAG, "hooked " + n.sessionsReader + ".a → sessions disabled");
        } catch (Throwable t) {
            log(Log.WARN, TAG, n.sessionsReader + ".a hook skipped: " + t.getMessage());
        }
    }

    private void hookCctUpload(ClassLoader cl, Names n) {
        if (cctHooked.get()) {
            return;
        }
        try {
            Class<?> backend = Class.forName(n.cctBackend, false, cl);
            Class<?> request = Class.forName(n.cctRequest, false, cl);
            Method e = backend.getDeclaredMethod(n.cctUpload, request);
            Class<?> response = Class.forName(n.cctResponse, false, cl);
            Constructor<?> responseCtor = response.getDeclaredConstructor(
                    int.class, URL.class, long.class, String.class);
            Field pField = request.getField("p");
            hook(e).intercept(chain -> {
                Object req = chain.getArg(0);
                URL url = null;
                if (req != null) {
                    try {
                        Object p = pField.get(req);
                        if (p instanceof URL) {
                            url = (URL) p;
                        }
                    } catch (Throwable ignored) {
                    }
                }
                if (url != null && isBlockedHost(url.getHost())) {
                    log(Log.INFO, TAG, "CCT blocked: " + url.getHost());
                    return responseCtor.newInstance(200, url, 0L, null);
                }
                return chain.proceed();
            });
            cctHooked.set(true);
            log(Log.INFO, TAG, "hooked " + n.cctBackend + "." + n.cctUpload + " (CctTransportBackend)");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "CCT hook failed", t);
        }
    }

    private void hookUrlOpenConnection() {
        if (!urlHooked.compareAndSet(false, true)) {
            return;
        }
        try {
            Method open = URL.class.getDeclaredMethod("openConnection");
            hook(open).intercept(chain -> blockUrlIfNeeded(chain));
            try {
                Method openProxy = URL.class.getDeclaredMethod(
                        "openConnection", java.net.Proxy.class);
                hook(openProxy).intercept(chain -> blockUrlIfNeeded(chain));
            } catch (NoSuchMethodException ignored) {
            }

            Method byName = java.net.InetAddress.class.getDeclaredMethod(
                    "getAllByName", String.class);
            hook(byName).intercept(chain -> {
                String host = (String) chain.getArg(0);
                if (isBlockedHost(host)) {
                    throw new UnknownHostException("blocked:" + host);
                }
                return chain.proceed();
            });
            log(Log.INFO, TAG, "hooked URL.openConnection + InetAddress.getAllByName");
        } catch (Throwable t) {
            urlHooked.set(false);
            log(Log.WARN, TAG, "URL.openConnection hook failed: " + t.getMessage());
        }
    }

    private Object blockUrlIfNeeded(io.github.libxposed.api.XposedInterface.Chain chain)
            throws Throwable {
        Object thiz = chain.getThisObject();
        if (thiz instanceof URL) {
            String host = ((URL) thiz).getHost();
            if (isBlockedHost(host)) {
                log(Log.INFO, TAG, "URL.openConnection blocked: " + host);
                throw new UnknownHostException("blocked:" + host);
            }
        }
        return chain.proceed();
    }

    private static boolean isBlockedHost(String host) {
        return isTelemetryHost(host);
    }

    private static boolean isTelemetryHost(String host) {
        if (host == null) {
            return false;
        }
        String h = host.toLowerCase();
        return h.contains("firebaselogging")
                || h.contains("app-measurement")
                || h.contains("crashlytics")
                || h.contains("firebaseinstallations")
                || h.contains("firebaseremoteconfig")
                || h.contains("googleadservices")
                || h.contains("firebasedynamiclinks")
                || h.contains("firebase-settings");
    }

    private static Boolean forcedFlag(String key) {
        if (key == null) {
            return null;
        }
        if (DEACTIVATED_KEYS.contains(key)) {
            return Boolean.TRUE;
        }
        if (OFF_KEYS.contains(key)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private void hookAppLifecycle(ClassLoader cl) {
        if (!appLifecycleHooked.compareAndSet(false, true)) {
            return;
        }
        try {
            Method attach = Application.class.getDeclaredMethod("attach", Context.class);
            hook(attach).intercept(chain -> {
                Object result = chain.proceed();
                Object ctx = chain.getArg(0);
                if (ctx instanceof Context) {
                    writeDisablePrefs((Context) ctx);
                }
                return result;
            });
            log(Log.INFO, TAG, "hooked Application.attach");
        } catch (Throwable t) {
            log(Log.WARN, TAG, "Application.attach hook failed: " + t.getMessage());
        }
        try {
            Class<?> app = Class.forName("sh.termrover.app.TermRoverApp", false, cl);
            Method onCreate = app.getDeclaredMethod("onCreate");
            hook(onCreate).intercept(chain -> {
                Object result = chain.proceed();
                Object thiz = chain.getThisObject();
                if (thiz instanceof Context) {
                    writeDisablePrefs((Context) thiz);
                    registerCloseReceiver((Context) thiz);
                }
                return result;
            });
            log(Log.INFO, TAG, "hooked TermRoverApp.onCreate");
        } catch (Throwable t) {
            log(Log.WARN, TAG, "TermRoverApp.onCreate hook skipped: " + t.getMessage());
        }
    }

    private void registerCloseReceiver(Context ctx) {
        try {
            android.content.BroadcastReceiver r = new android.content.BroadcastReceiver() {
                @Override
                public void onReceive(Context c, android.content.Intent i) {
                    log(Log.INFO, TAG, "close requested by the module screen");
                    setResultCode(CLOSE_ACK);

                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                            () -> android.os.Process.killProcess(android.os.Process.myPid()), 300);
                }
            };
            android.content.IntentFilter f = new android.content.IntentFilter(ACTION_CLOSE);
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                ctx.registerReceiver(r, f, CLOSE_PERMISSION, null, Context.RECEIVER_EXPORTED);
            } else {
                ctx.registerReceiver(r, f, CLOSE_PERMISSION, null);
            }
            log(Log.INFO, TAG, "close receiver registered");
        } catch (Throwable t) {
            log(Log.WARN, TAG, "close receiver failed: " + t.getMessage());
        }
    }

    private void writeDisablePrefs(Context ctx) {
        try {
            if (ctx == null) {
                Object app = Class.forName("android.app.ActivityThread")
                        .getMethod("currentApplication")
                        .invoke(null);
                if (!(app instanceof Context)) {
                    return;
                }
                ctx = (Context) app;
            }
            ctx.getSharedPreferences("com.google.firebase.crashlytics", 0)
                    .edit()
                    .putBoolean("firebase_crashlytics_collection_enabled", false)
                    .apply();

            ctx.getSharedPreferences("ui_settings", 0)
                    .edit()
                    .putBoolean("telemetry_enabled", false)
                    .apply();

            Context dps = ctx.createDeviceProtectedStorageContext();
            java.io.File dir = new java.io.File(dps.getDataDir(), "shared_prefs");
            if (dir.isDirectory()) {
                java.io.File[] files = dir.listFiles();
                if (files != null) {
                    for (java.io.File f : files) {
                        String name = f.getName();
                        if (name.startsWith("com.google.firebase.common.prefs:")
                                && name.endsWith(".xml")) {
                            String pref = name.substring(0, name.length() - 4);
                            dps.getSharedPreferences(pref, 0).edit()
                                    .putBoolean("firebase_data_collection_default_enabled", false)
                                    .apply();
                        }
                    }
                }
            }

            Names n = names;
            if (n != null) {
                try {
                    ClassLoader cl = appCl != null ? appCl : ctx.getClassLoader();
                    Class.forName(n.crashWriter, false, cl)
                            .getDeclaredMethod(n.crashWriterMethod, Context.class, boolean.class)
                            .invoke(null, ctx, Boolean.FALSE);
                } catch (Throwable ignored) {
                }
            }
            log(Log.INFO, TAG, "wrote disable prefs");
        } catch (Throwable t) {
            log(Log.WARN, TAG, "writeDisablePrefs: " + t.getMessage());
        }
    }

    private void hookUiLocks(ClassLoader cl) {
        Names n = names;
        if (n == null || !uiLocksHooked.compareAndSet(false, true)) {
            return;
        }
        try {

            Class<?> reader = Class.forName(n.aboutReader, false, cl);
            Method d = reader.getDeclaredMethod("d", Context.class);
            hook(d).intercept(chain -> Boolean.FALSE);
            log(Log.INFO, TAG, "hooked " + n.aboutReader + ".d → telemetry always false");
        } catch (Throwable t) {
            log(Log.WARN, TAG, n.aboutReader + ".d hook skipped: " + t.getMessage());
        }

        try {

            Class<?> writer = Class.forName(n.crashWriter, false, cl);
            Method e = writer.getDeclaredMethod(n.crashWriterMethod, Context.class, boolean.class);
            hook(e).intercept(chain -> {
                Object[] args = chain.getArgs().toArray();
                args[1] = Boolean.FALSE;
                return chain.proceed(args);
            });
            log(Log.INFO, TAG, "hooked " + n.crashWriter + "." + n.crashWriterMethod + " → always false");
        } catch (Throwable t) {
            log(Log.WARN, TAG, n.crashWriter + " hook skipped: " + t.getMessage());
        }

        Object unit = unit(cl, n);
        try {

            Class<?> cb = Class.forName(n.switchCallback, false, cl);
            Method cbInvoke = cb.getDeclaredMethod("invoke", Object.class);
            Field cbKind = cb.getDeclaredField("o");
            cbKind.setAccessible(true);
            Field cbState = cb.getDeclaredField("q");
            cbState.setAccessible(true);
            hook(cbInvoke).intercept(chain -> {
                Object thiz = chain.getThisObject();
                if (thiz != null && cbKind.getByte(thiz) == 1) {
                    forceMutableStateFalse(cbState.get(thiz));
                    return unit;
                }
                return chain.proceed();
            });
            log(Log.INFO, TAG, "hooked " + n.switchCallback + ".invoke (telemetry switch)");
        } catch (Throwable t) {
            log(Log.WARN, TAG, n.switchCallback + ".invoke hook skipped: " + t.getMessage());
        }

        try {

            Class<?> row = Class.forName(n.rowToggle, false, cl);
            Method rowInvoke = row.getDeclaredMethod("invoke");
            Field rowKind = row.getDeclaredField("o");
            rowKind.setAccessible(true);
            Field rowState = row.getDeclaredField("p");
            rowState.setAccessible(true);
            hook(rowInvoke).intercept(chain -> {
                Object thiz = chain.getThisObject();
                if (thiz != null && rowKind.getByte(thiz) == 7) {
                    forceMutableStateFalse(rowState.get(thiz));
                    return unit;
                }
                return chain.proceed();
            });
            log(Log.INFO, TAG, "hooked " + n.rowToggle + ".invoke (telemetry row)");
        } catch (Throwable t) {
            log(Log.WARN, TAG, n.rowToggle + ".invoke hook skipped: " + t.getMessage());
        }

        try {

            Class<?> switchCls = Class.forName("androidx.compose.material3.a", false, cl);
            Class<?> fn1 = Class.forName(n.fn1, false, cl);
            Class<?> modifier = Class.forName(n.modifier, false, cl);
            Class<?> colors = Class.forName(n.switchColors, false, cl);
            Class<?> composer = Class.forName(n.composer, false, cl);
            Method sw = switchCls.getDeclaredMethod(
                    "a", boolean.class, fn1, modifier, boolean.class, colors, composer, int.class, int.class);
            Class<?> cb = Class.forName(n.switchCallback, false, cl);
            Field cbKind = cb.getDeclaredField("o");
            cbKind.setAccessible(true);
            hook(sw).intercept(chain -> {
                Object onChange = chain.getArg(1);
                if (onChange != null && cb.isInstance(onChange) && cbKind.getByte(onChange) == 1) {
                    if (telemetrySwitchLogged.compareAndSet(false, true)) {
                        log(Log.INFO, TAG, "telemetry Switch forced OFF+disabled");
                    }
                    Object[] args = chain.getArgs().toArray();
                    args[0] = Boolean.FALSE;
                    args[1] = null;
                    args[3] = Boolean.FALSE;

                    int i2 = args[7] instanceof Integer ? (Integer) args[7] : 0;
                    args[7] = i2 & ~8;
                    return chain.proceed(args);
                }
                return chain.proceed();
            });
            log(Log.INFO, TAG, "hooked material3 Switch (telemetry lock)");
        } catch (Throwable t) {
            log(Log.WARN, TAG, "Switch hook skipped: " + t.getMessage());
        }
    }

    private static Object unit(ClassLoader cl, Names n) {
        try {
            return Class.forName(n.unit, false, cl).getField("a").get(null);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void forceMutableStateFalse(Object state) {
        if (state == null) {
            return;
        }
        try {
            state.getClass().getMethod("setValue", Object.class).invoke(state, Boolean.FALSE);
        } catch (Throwable ignored) {
        }
    }

    private void installCustomThemes(ClassLoader cl) {
        Names n = names;
        if (cl == null || n == null) {
            return;
        }
        List<ThemeStore.Theme> themes;
        try {
            if ((getFrameworkProperties() & PROP_CAP_REMOTE) == 0) {
                log(Log.WARN, TAG, "custom themes: framework has no remote file support");
                return;
            }
            boolean exists = false;
            for (String f : listRemoteFiles()) {
                if (ThemeStore.REMOTE_FILE.equals(f)) {
                    exists = true;
                }
            }
            if (!exists) {
                log(Log.INFO, TAG, "custom themes: no imported list");
                return;
            }

            if (!getRemotePreferences(ThemeStore.PREFS).getBoolean(ThemeStore.KEY_ENABLED, true)) {
                log(Log.INFO, TAG, "custom themes: switched off");
                return;
            }
            String json;
            try (android.os.ParcelFileDescriptor pfd = openRemoteFile(ThemeStore.REMOTE_FILE);
                 java.io.FileInputStream in = new java.io.FileInputStream(pfd.getFileDescriptor())) {
                json = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
            if (json.trim().isEmpty()) {
                log(Log.INFO, TAG, "custom themes: list is empty");
                return;
            }
            themes = ThemeStore.parse(json);
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "custom themes: failed to read the list", t);
            return;
        }
        try {
            Class<?> registry = Class.forName(n.themes, true, cl);
            Field fa = registry.getField("a");
            fa.setAccessible(true);
            List<?> current = (List<?>) fa.get(null);
            Class<?> themeCls = Class.forName(n.theme, false, cl);
            Field idField = themeCls.getField("a");
            Set<Object> existing = new HashSet<>();
            for (Object t : current) {
                existing.add(idField.get(t));
            }
            java.util.ArrayList<Object> extended = new java.util.ArrayList<>(current);
            List<String> added = new java.util.ArrayList<>();
            int skipped = 0;
            for (ThemeStore.Theme t : themes) {
                if (existing.contains(t.id)) {
                    skipped++;
                    continue;
                }
                try {
                    extended.add(buildTheme(cl, n, registry, t));
                    added.add(t.id);
                } catch (Throwable e) {
                    skipped++;
                    log(Log.WARN, TAG, "custom theme " + t.id + " skipped: " + e);
                }
            }
            if (added.isEmpty()) {
                log(Log.INFO, TAG, "custom themes: nothing to add (skipped " + skipped + ")");
                return;
            }
            fa.set(null, Collections.unmodifiableList(extended));
            try {
                @SuppressWarnings("unchecked")
                Set<Object> free = (Set<Object>) registry.getField("b").get(null);
                free.addAll(added);
            } catch (Throwable t) {
                log(Log.WARN, TAG, "free theme set not extended: " + t.getMessage());
            }
            log(Log.INFO, TAG, "custom themes installed: " + added.size()
                    + " (skipped " + skipped + ", total " + extended.size() + ")");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "installCustomThemes failed", t);
        }
    }

    private static Object buildTheme(ClassLoader cl, Names n, Class<?> registry, ThemeStore.Theme t)
            throws Exception {
        long[] ansi = new long[16];
        for (int i = 0; i < 16; i++) {
            ansi[i] = t.ansi[i] & 0xFFFFFFFFL;
        }
        Method c = registry.getMethod("c", long.class, long.class, long.class, long[].class);
        Object palette = c.invoke(null, t.foreground & 0xFFFFFFFFL, t.background & 0xFFFFFFFFL,
                t.cursor & 0xFFFFFFFFL, ansi);
        Class<?> chrome = Class.forName(n.themeChrome, false, cl);
        Class<?>[] sig = new Class<?>[16];
        Object[] args = new Object[16];
        for (int i = 0; i < 16; i++) {
            sig[i] = long.class;

            args[i] = (t.chrome[i] & 0xFFFFFFFFL) << 32;
        }
        Object colors = chrome.getConstructor(sig).newInstance(args);
        Class<?> themeCls = Class.forName(n.theme, false, cl);
        return themeCls.getConstructor(String.class, String.class, palette.getClass(), chrome)
                .newInstance(t.id, t.name, palette, colors);
    }
}
