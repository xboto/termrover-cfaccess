package io.github.xboto.termrovercfaccess.cf;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import io.github.libxposed.api.XposedInterface;
import io.github.xboto.termrovercfaccess.Names;

final class CfModeUi {
    private static final String TAG = CfHooks.TAG;
    private static final float DIM = 0.38f;
    private static final String HINT_STOCK =
            "Recommended: start with SSH. Once connected, you can switch to Mosh anytime.";
    private static final String HINT_CF = "Mosh is unavailable with Cloudflare Access (cloudflared).";
    private static final String NOTE_CF = "Mosh isn\u2019t available through Cloudflare.";
    private static final String NOTE_JUMP = "Mosh isn\u2019t available through jump hosts.";

    private final XposedInterface xp;
    private final BooleanSupplier formCf;
    private final AtomicBoolean hooked = new AtomicBoolean();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<Object, Object> gateCache = new WeakHashMap<>();
    private final Map<Object, Object> menuCache = new WeakHashMap<>();
    private final Map<Object, Object> sessionCache = new WeakHashMap<>();
    private final AtomicBoolean authFixPending = new AtomicBoolean();
    private final AtomicBoolean transportFixPending = new AtomicBoolean();

    private int dimText;

    private int sessionState;
    private int noteCf;

    private int quickState;
    private final Map<Object, Object> quickCache = new WeakHashMap<>();
    private Method textFn;
    private Class<?> fn0;

    private Class<?> fn1Cls;
    private Object unit;
    private Object authTailscale;
    private Object authKey;
    private Object moshT;
    private Object sshT;
    private Field profileHost;

    CfModeUi(XposedInterface xp, BooleanSupplier formCf) {
        this.xp = xp;
        this.formCf = formCf;
    }

    void install(ClassLoader cl, Names n) {
        if (!hooked.compareAndSet(false, true)) return;
        try {
            fn1Cls = Class.forName(n.fn1, false, cl);
            fn0 = Class.forName(n.fn0, false, cl);
            unit = Class.forName(n.unit, false, cl).getField("a").get(null);
            Class<?> transportCls = Class.forName(n.transportEnum, false, cl);
            sshT = staticField(transportCls, "p");
            moshT = staticField(transportCls, "q");
            Class<?> profile = Class.forName(n.profile, false, cl);
            profileHost = profile.getDeclaredField("c");
            profileHost.setAccessible(true);
            textFn = TextFn.find(cl, n);
        } catch (Throwable t) {
            hooked.set(false);
            xp.log(Log.ERROR, TAG, "Cloudflare Access: Tailscale/Mosh lock unavailable", t);
            return;
        }

        installText();
        installForm(cl, n);
        installProfile(cl, n);
        installMenu(cl, n);
        if (n.switcherNoteArg) {
            installSessionSwitchNote(cl, n);
            installQuickSettings(cl, n);
        } else {
            installSessionSwitch(cl, n);
        }
    }

    private void installText() {
        try {
            xp.hook(textFn).intercept(chain -> {
                Object s = chain.getArg(0);
                boolean hint = dimText == 0 && HINT_STOCK.equals(s) && formCf.getAsBoolean();
                boolean note = noteCf > 0 && NOTE_JUMP.equals(s);
                if (dimText == 0 && !hint && !note) return chain.proceed();
                Object[] args = chain.getArgs().toArray();
                if (hint) {
                    args[0] = HINT_CF;
                } else if (note) {
                    args[0] = NOTE_CF;
                } else {
                    args[2] = dimColor((Long) args[2]);
                }
                return chain.proceed(args);
            });
        } catch (Throwable t) {
            xp.log(Log.ERROR, TAG, "Cloudflare Access: text hook unavailable", t);
        }
    }

    private void installSessionSwitchNote(ClassLoader cl, Names n) {
        try {
            Class<?> rows = Class.forName(n.rows, false, cl);
            Class<?> profile = Class.forName(n.profile, false, cl);
            Method session = sessionMethod(rows, profile, n);
            Class<?> transportCls = sshT.getClass();
            Class<?> composer = Class.forName(n.composer, false, cl);
            Method switcher = rows.getDeclaredMethod(n.switcher, boolean.class, transportCls, boolean.class,
                    boolean.class, boolean.class, String.class, transportCls, fn0, fn0, fn1Cls, composer, int.class);
            hookSessionState(session);
            xp.hook(switcher).intercept(chain -> {
                Object onSelect = chain.getArg(9);
                boolean cf = sessionState == 2 || (sessionState == 0 && isSessionBlock(onSelect));
                if (!cf || onSelect == null) return chain.proceed();
                Object[] args = chain.getArgs().toArray();
                args[5] = NOTE_CF;
                args[9] = sessionBlock(onSelect);
                return chain.proceed(args);
            });
            xp.log(Log.INFO, TAG, "hooked " + n.rows + "." + n.session + "/" + n.switcher
                    + " (Switch transport is SSH-only on cf: hosts)");
        } catch (Throwable t) {
            xp.log(Log.ERROR, TAG, "Cloudflare Access: session Mosh lock unavailable", t);
        }
    }

    private void installQuickSettings(ClassLoader cl, Names n) {
        try {
            Class<?> rows = Class.forName(n.rows, false, cl);
            Class<?> state = Class.forName(n.quickSettingsHost, false, cl);
            Class<?> profileCls = Class.forName(n.profile, false, cl);

            Field foundProfile = null;
            for (Field f : state.getDeclaredFields()) {
                if (f.getType() == profileCls) {
                    foundProfile = f;
                    break;
                }
            }
            if (foundProfile == null) {
                throw new NoSuchFieldException(n.quickSettingsHost + ".<profile:" + n.profile + ">");
            }
            final Field stateProfile = foundProfile;
            stateProfile.setAccessible(true);
            Class<?> modifierCls = Class.forName(n.modifier, false, cl);
            Method host = null, sheet = null, body = null;
            for (Method m : rows.getDeclaredMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (m.getName().equals("a") && p.length == 6 && p[0] == state) host = m;

                if (m.getName().equals(n.quickSettings) && p.length == 10 && p[0] == String.class
                        && p[1] == boolean.class && p[5] == fn1Cls && p[7] == fn0) sheet = m;
                if (m.getName().equals(n.quickSettingsBody) && p.length == 10 && p[0] == String.class
                        && p[1] == boolean.class && p[5] == fn1Cls && p[7] == modifierCls) body = m;
            }
            if (host == null || sheet == null || body == null) {
                throw new NoSuchMethodException("Quick settings: " + host + ", " + sheet + ", " + body);
            }
            xp.hook(host).intercept(chain -> {
                Object st = chain.getArg(0);
                Object profile = st != null ? stateProfile.get(st) : null;
                boolean cf = profile != null && CfFormUi.hasMarker((String) profileHost.get(profile));
                int prev = quickState;
                quickState = cf ? 2 : 1;
                try {
                    return chain.proceed();
                } finally {
                    quickState = prev;
                }
            });
            xp.hook(sheet).intercept(chain -> {
                Object onTransport = chain.getArg(5);
                boolean cf = quickState == 2 || (quickState == 0 && isQuickBlock(onTransport));
                if (!cf || onTransport == null) return chain.proceed();
                Object[] args = chain.getArgs().toArray();
                args[1] = false;
                args[5] = quickBlock(onTransport);
                return chain.proceed(args);
            });
            xp.hook(body).intercept(chain -> {
                Object onTransport = chain.getArg(5);
                if (!isQuickBlock(onTransport)) return chain.proceed();
                Object[] args = chain.getArgs().toArray();
                args[1] = false;
                noteCf++;
                try {
                    return chain.proceed(args);
                } finally {
                    noteCf--;
                }
            });
            xp.log(Log.INFO, TAG, "hooked " + n.rows + ".a/" + n.quickSettings + "/" + n.quickSettingsBody
                    + " (Quick settings default transport is SSH on cf: hosts)");
        } catch (Throwable t) {
            xp.log(Log.ERROR, TAG, "Cloudflare Access: Quick settings Mosh lock unavailable", t);
        }
    }

    private static Method sessionMethod(Class<?> rows, Class<?> profile, Names n) throws NoSuchMethodException {
        for (Method m : rows.getDeclaredMethods()) {
            if (m.getName().equals(n.session) && m.getParameterCount() == n.sessionParams
                    && m.getParameterTypes()[0] == profile) return m;
        }
        throw new NoSuchMethodException(n.rows + "." + n.session + "(" + n.profile + ", …" + n.sessionParams + ")");
    }

    private void hookSessionState(Method session) {
        xp.hook(session).intercept(chain -> {
            Object profile = chain.getArg(0);
            boolean cf = profile != null && CfFormUi.hasMarker((String) profileHost.get(profile));
            int prev = sessionState;
            sessionState = cf ? 2 : 1;
            try {
                return chain.proceed();
            } finally {
                sessionState = prev;
            }
        });
    }

    private void installSessionSwitch(ClassLoader cl, Names n) {
        try {
            Class<?> rowsCls = Class.forName(n.rows, false, cl);
            Class<?> profileCls = Class.forName(n.profile, false, cl);
            Class<?> composerCls = Class.forName(n.composer, false, cl);
            Method session = sessionMethod(rowsCls, profileCls, n);
            Class<?> transportCls = sshT.getClass();
            Method switcher = rowsCls.getDeclaredMethod(n.switcher, transportCls, boolean.class, boolean.class, boolean.class,
                    boolean.class, transportCls, fn0, fn0, fn1Cls, composerCls, int.class);
            Method note = rowsCls.getDeclaredMethod(n.note, String.class, composerCls, int.class);
            Class<?> popupCls = Class.forName(n.popup, false, cl);
            Method popup = popupCls.getDeclaredMethod("invoke", Object.class, Object.class, Object.class);
            Field popupKind = popupCls.getDeclaredField("o");
            Field popupSelect = popupCls.getDeclaredField("t");
            popupKind.setAccessible(true);
            popupSelect.setAccessible(true);

            hookSessionState(session);
            xp.hook(switcher).intercept(chain -> {
                Object onSelect = chain.getArg(8);
                boolean cf = sessionState == 2 || (sessionState == 0 && isSessionBlock(onSelect));
                if (!cf || onSelect == null) return chain.proceed();
                Object[] args = chain.getArgs().toArray();
                args[4] = false;
                args[8] = sessionBlock(onSelect);
                return chain.proceed(args);
            });

            xp.hook(popup).intercept(chain -> {
                Object self = chain.getThisObject();
                if ((Byte) popupKind.get(self) != 2 || !isSessionBlock(popupSelect.get(self))) {
                    return chain.proceed();
                }
                noteCf++;
                try {
                    return chain.proceed();
                } finally {
                    noteCf--;
                }
            });
            xp.hook(note).intercept(chain -> {
                if (noteCf == 0 || !NOTE_JUMP.equals(chain.getArg(0))) return chain.proceed();
                Object[] args = chain.getArgs().toArray();
                args[0] = NOTE_CF;
                return chain.proceed(args);
            });
            xp.log(Log.INFO, TAG, "hooked Switch transport session/picker/note/popup (SSH-only on cf: hosts)");
        } catch (Throwable t) {
            xp.log(Log.ERROR, TAG, "Cloudflare Access: session Mosh lock unavailable", t);
        }
    }

    private void installForm(ClassLoader cl, Names n) {
        try {
            Class<?> composerCls = Class.forName(n.composer, false, cl);
            Class<?> formFieldsCls = Class.forName(n.formFields, false, cl);
            Class<?> authCls = Class.forName(n.authEnum, false, cl);
            authKey = staticField(authCls, "p");
            authTailscale = staticField(authCls, "q");
            Class<?> transportCls = sshT.getClass();
            Method authRow = formFieldsCls.getDeclaredMethod(n.authRow, authCls, fn1Cls, composerCls, int.class);
            Method authOpt = formFieldsCls.getDeclaredMethod(n.authOption, String.class, authCls, authCls, fn1Cls, composerCls, int.class);
            Method transport = Class.forName(n.transportPickerOwner, false, cl)
                    .getDeclaredMethod(n.transportPicker, transportCls, boolean.class, fn1Cls, composerCls, int.class);

            xp.hook(authRow).intercept(chain -> {
                Object sel = chain.getArg(0);
                Object onSelect = chain.getArg(1);
                if (formCf.getAsBoolean() && sel == authTailscale && onSelect != null
                        && authFixPending.compareAndSet(false, true)) {
                    main.post(() -> {
                        authFixPending.set(false);
                        if (formCf.getAsBoolean()) safeInvoke(onSelect, authKey);
                    });
                }

                Object[] args = chain.getArgs().toArray();
                args[3] = (Integer) args[3] | 1;
                return chain.proceed(args);
            });
            xp.hook(authOpt).intercept(chain -> {
                if (chain.getArg(1) != authTailscale) return chain.proceed();
                Object[] args = chain.getArgs().toArray();

                if (args[3] != null) args[3] = gate(args[3]);
                args[5] = (Integer) args[5] | 1;
                if (!formCf.getAsBoolean()) return chain.proceed(args);
                dimText++;
                try {
                    return chain.proceed(args);
                } finally {
                    dimText--;
                }
            });
            xp.hook(transport).intercept(chain -> {

                Object[] args = chain.getArgs().toArray();
                args[4] = (Integer) args[4] | 1;
                if (!formCf.getAsBoolean()) return chain.proceed(args);
                Object sel = chain.getArg(0);
                Object onSelect = chain.getArg(2);
                if (sel == moshT && onSelect != null && transportFixPending.compareAndSet(false, true)) {
                    main.post(() -> {
                        transportFixPending.set(false);
                        if (formCf.getAsBoolean()) safeInvoke(onSelect, sshT);
                    });
                }
                args[1] = false;
                return chain.proceed(args);
            });
            xp.log(Log.INFO, TAG, "hooked " + n.formFields + "." + n.authRow + "/" + n.authOption + " + "
                    + n.transportPickerOwner + "." + n.transportPicker
                    + " (Access hosts use SSH key auth and SSH transport)");
        } catch (Throwable t) {
            xp.log(Log.ERROR, TAG, "Cloudflare Access: host form lock unavailable", t);
        }
    }

    private void installProfile(ClassLoader cl, Names n) {
        try {
            Class<?> profileCls = Class.forName(n.profile, false, cl);
            Method effective = profileCls.getDeclaredMethod("a", sshT.getClass());
            xp.hook(effective).intercept(chain -> {
                if (CfFormUi.hasMarker((String) profileHost.get(chain.getThisObject()))) return sshT;
                return chain.proceed();
            });
            xp.log(Log.INFO, TAG, "hooked " + n.profile + ".a (cf: hosts always use SSH)");
        } catch (Throwable t) {
            xp.log(Log.ERROR, TAG, "Cloudflare Access: effective SSH transport unavailable", t);
        }
    }

    private void installMenu(ClassLoader cl, Names n) {
        try {
            Class<?> profileCls = Class.forName(n.profile, false, cl);
            Class<?> fn0Cls = fn0;
            Class<?> transportCls = sshT.getClass();
            Class<?> composerCls = Class.forName(n.composer, false, cl);
            Class<?> menuCls = Class.forName(n.menu, false, cl);
            Method row = menuCls.getDeclaredMethod(n.menuRow, profileCls, fn1Cls, fn0Cls, fn0Cls, boolean.class, fn0Cls, fn0Cls, composerCls, int.class);
            Method items = menuCls.getDeclaredMethod(n.menuItems, transportCls, boolean.class, fn1Cls, fn0Cls, fn0Cls, boolean.class,
                    fn0Cls, fn0Cls, composerCls, int.class);

            xp.hook(row).intercept(chain -> {
                Object profile = chain.getArg(0);
                Object onConnect = chain.getArg(1);
                if (profile == null || onConnect == null
                        || !CfFormUi.hasMarker((String) profileHost.get(profile))) {
                    return chain.proceed();
                }
                Object[] args = chain.getArgs().toArray();
                args[1] = menuBlock(onConnect);
                return chain.proceed(args);
            });

            xp.hook(items).intercept(chain -> {
                if (!isMenuBlock(chain.getArg(2))) return chain.proceed();
                Object[] args = chain.getArgs().toArray();
                args[1] = false;
                return chain.proceed(args);
            });
            xp.log(Log.INFO, TAG, "hooked " + n.menu + "." + n.menuRow + "/" + n.menuItems + " (Connect with Mosh hidden on cf: hosts)");
        } catch (Throwable t) {
            xp.log(Log.ERROR, TAG, "Cloudflare Access: menu Mosh lock unavailable", t);
        }
    }

    private Object gate(Object onSelect) {
        if (isProxyOf(onSelect, Gate.class)) return onSelect;
        synchronized (gateCache) {
            Object p = gateCache.get(onSelect);
            if (p == null) {
                p = proxy(new Gate(onSelect));
                gateCache.put(onSelect, p);
            }
            return p;
        }
    }

    private Object menuBlock(Object onConnect) {
        if (isMenuBlock(onConnect)) return onConnect;
        synchronized (menuCache) {
            Object p = menuCache.get(onConnect);
            if (p == null) {
                p = proxy(new MenuBlock(onConnect));
                menuCache.put(onConnect, p);
            }
            return p;
        }
    }

    private boolean isMenuBlock(Object o) {
        return isProxyOf(o, MenuBlock.class);
    }

    private Object sessionBlock(Object onSelect) {
        if (isSessionBlock(onSelect)) return onSelect;
        synchronized (sessionCache) {
            Object p = sessionCache.get(onSelect);
            if (p == null) {
                p = proxy(new SessionBlock(onSelect));
                sessionCache.put(onSelect, p);
            }
            return p;
        }
    }

    private boolean isSessionBlock(Object o) {
        return isProxyOf(o, SessionBlock.class);
    }

    private Object quickBlock(Object onTransport) {
        if (isQuickBlock(onTransport)) return onTransport;
        synchronized (quickCache) {
            Object p = quickCache.get(onTransport);
            if (p == null) {
                p = proxy(new QuickBlock(onTransport));
                quickCache.put(onTransport, p);
            }
            return p;
        }
    }

    private boolean isQuickBlock(Object o) {
        return isProxyOf(o, QuickBlock.class);
    }

    private static boolean isProxyOf(Object o, Class<?> handlerType) {
        return o != null && Proxy.isProxyClass(o.getClass())
                && handlerType.isInstance(Proxy.getInvocationHandler(o));
    }

    private Object proxy(InvocationHandler h) {
        return Proxy.newProxyInstance(fn1Cls.getClassLoader(), new Class<?>[]{fn1Cls}, h);
    }

    private abstract class Fn1 implements InvocationHandler {
        final Object target;

        Fn1(Object target) {
            this.target = target;
        }

        abstract boolean blocked(Object arg);

        @Override
        public Object invoke(Object proxy, Method m, Object[] a) throws Throwable {
            switch (m.getName()) {
                case "invoke": {
                    Object arg = a != null && a.length > 0 ? a[0] : null;
                    if (blocked(arg)) return unit;
                    return invokeFn(target, arg);
                }
                case "equals":
                    return proxy == a[0];
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "toString":
                    return "CfModeUi$" + getClass().getSimpleName();
                default:
                    return null;
            }
        }
    }

    private final class Gate extends Fn1 {
        Gate(Object target) {
            super(target);
        }

        @Override
        boolean blocked(Object arg) {
            return arg == authTailscale && formCf.getAsBoolean();
        }
    }

    private final class MenuBlock extends Fn1 {
        MenuBlock(Object target) {
            super(target);
        }

        @Override
        boolean blocked(Object arg) {
            return arg == moshT;
        }
    }

    private final class SessionBlock extends Fn1 {
        SessionBlock(Object target) {
            super(target);
        }

        @Override
        boolean blocked(Object arg) {
            return arg == moshT;
        }
    }

    private final class QuickBlock extends Fn1 {
        QuickBlock(Object target) {
            super(target);
        }

        @Override
        boolean blocked(Object arg) {
            return arg == moshT;
        }
    }

    private Object invokeFn(Object fn, Object arg) throws Throwable {
        try {
            return fn1Cls.getMethod("invoke", Object.class).invoke(fn, arg);
        } catch (InvocationTargetException e) {
            throw e.getCause() != null ? e.getCause() : e;
        }
    }

    private void safeInvoke(Object fn, Object arg) {
        try {
            invokeFn(fn, arg);
        } catch (Throwable t) {
            xp.log(Log.WARN, TAG, "Cloudflare Access: failed to adjust the selection", t);
        }
    }

    private static long dimColor(long c) {
        if ((c & 0x3fL) != 0) return c;
        int argb = (int) (c >>> 32);
        int alpha = (argb >>> 24) & 0xff;
        if (alpha == 0) return c;
        int na = Math.round(alpha * DIM);
        return ((long) ((argb & 0x00ffffff) | (na << 24))) << 32;
    }

    private static Object staticField(Class<?> c, String name) throws ReflectiveOperationException {
        Field f = c.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(null);
    }
}
