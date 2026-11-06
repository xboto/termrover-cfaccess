package io.github.xboto.termrovercfaccess.cf;

import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.xboto.termrovercfaccess.Names;

final class CfFormUi {
    private static final String TAG = CfHooks.TAG;
    private static final String LABEL = "Cloudflare Access · cloudflared";
    private static final String PORT_OFF = "—";
    private static final String TITLE = "Cloudflare Access";
    private static final String SUB_ON = "On — connects through cloudflared.";
    private static final String SUB_OFF = "Off — connects directly.";
    private static final float PORT_ALPHA = 0.38f;

    private static final int MODIFIER_DEFAULT = 64;

    private final XposedInterface xp;
    private final AtomicBoolean hooked = new AtomicBoolean();
    private final ThreadLocal<int[]> restartDepth = ThreadLocal.withInitial(() -> new int[1]);

    private volatile String lastHost;
    private volatile Object lastHostSetter;

    private Class<?> fn1Cls;
    private Object unit;
    private Method toggleRow;
    private Method graphicsLayer;
    private int graphicsLayerFloats;
    private int alphaOnlyMask;

    CfFormUi(XposedInterface xp) {
        this.xp = xp;
    }

    boolean accessOn() {
        return hasMarker(lastHost);
    }

    static boolean hasMarker(String host) {
        if (host == null) return false;
        String l = host.trim().toLowerCase(Locale.ROOT);
        return l.startsWith("cf:") || l.endsWith("#cf");
    }

    static String stripMarker(String host) {
        if (host == null) return "";
        String h = host.trim();
        boolean again = true;
        while (again) {
            again = false;
            String l = h.toLowerCase(Locale.ROOT);
            if (l.startsWith("cf:")) {
                h = h.substring(3).trim();
                again = true;
            } else if (l.endsWith("#cf")) {
                h = h.substring(0, h.length() - 3).trim();
                again = true;
            }
        }
        return h;
    }

    void install(ClassLoader cl, Names n) {
        if (!hooked.compareAndSet(false, true)) return;
        try {
            Class<?> forms = Class.forName(n.formFields, false, cl);
            Class<?> composer = Class.forName(n.composer, false, cl);
            Class<?> modifier = Class.forName(n.modifier, false, cl);
            fn1Cls = Class.forName(n.fn1, false, cl);
            Method field = forms.getDeclaredMethod(n.formField, String.class, String.class, fn1Cls,
                    String.class, boolean.class, modifier, composer, int.class, int.class);
            Method restart = Class.forName(n.fieldRestart, false, cl)
                    .getDeclaredMethod("invoke", Object.class, Object.class);
            toggleRow = Class.forName(n.rows, false, cl)
                    .getDeclaredMethod(n.toggleRow, String.class, boolean.class, fn1Cls, composer, int.class);
            Class<?> shape = Class.forName(n.shape, false, cl);
            graphicsLayerFloats = n.graphicsLayerFloats;
            alphaOnlyMask = n.graphicsLayerAlphaMask;
            Class<?>[] glSig = new Class<?>[graphicsLayerFloats + 4];
            glSig[0] = modifier;
            for (int i = 1; i <= graphicsLayerFloats; i++) glSig[i] = float.class;
            glSig[graphicsLayerFloats + 1] = shape;
            glSig[graphicsLayerFloats + 2] = boolean.class;
            glSig[graphicsLayerFloats + 3] = int.class;
            graphicsLayer = Class.forName("androidx.compose.ui.graphics.a", false, cl)
                    .getDeclaredMethod("b", glSig);
            Field unitField = Class.forName(n.unit, false, cl).getField("a");
            unit = unitField.get(null);
            for (Method m : new Method[]{field, restart, toggleRow, graphicsLayer}) m.setAccessible(true);

            XposedInterface.HookHandle h1 = xp.hook(restart).intercept(chain -> {
                int[] d = restartDepth.get();
                d[0]++;
                try {
                    return chain.proceed();
                } finally {
                    d[0]--;
                }
            });
            try {
                xp.hook(field).intercept(chain -> {
                    if (restartDepth.get()[0] > 0) return chain.proceed();
                    Object label = chain.getArg(0);
                    if ("Host".equals(label)) return onHost(chain);
                    if ("Port".equals(label)) return onPort(chain);
                    if ("User".equals(label)) return onUser(chain);
                    return chain.proceed();
                });
            } catch (Throwable t) {
                h1.unhook();
                throw t;
            }
            xp.log(Log.INFO, TAG, "hooked " + n.formFields + "." + n.formField + " (Cloudflare Access switch in the host form)");
        } catch (Throwable t) {
            hooked.set(false);
            xp.log(Log.ERROR, TAG, "Cloudflare Access: host form switch unavailable", t);
            return;
        }
        installRowStyle(cl, n);
    }

    private void installRowStyle(ClassLoader cl, Names n) {
        try {
            Class<?> modifierCls = Class.forName(n.modifier, false, cl);
            Class<?> shapeCls = Class.forName(n.shape, false, cl);
            Class<?> composerCls = Class.forName(n.composer, false, cl);
            Class<?> colorsCls = Class.forName(n.colors, false, cl);
            Class<?> size = Class.forName("androidx.compose.foundation.layout.d", false, cl);
            Class<?> pad = Class.forName("androidx.compose.foundation.layout.a", false, cl);
            Class<?> fnd = Class.forName("androidx.compose.foundation.a", false, cl);
            Method width = size.getDeclaredMethod("t", modifierCls, float.class);
            Method fillWidth = size.getDeclaredMethod("f", modifierCls, float.class);
            Method padHV = pad.getDeclaredMethod("l", modifierCls, float.class, float.class);
            Method padAll = pad.getDeclaredMethod("k", modifierCls, float.class);
            Method padSides = pad.getDeclaredMethod("o", modifierCls, float.class, float.class, float.class,
                    float.class, int.class);
            Method clip = Class.forName(n.clipOwner, false, cl).getDeclaredMethod(n.clip, modifierCls, shapeCls);
            Method background = fnd.getDeclaredMethod("c", modifierCls, long.class, shapeCls);
            Method border = Class.forName(n.borderOwner, false, cl)
                    .getDeclaredMethod(n.border, modifierCls, float.class, long.class, shapeCls);
            Method rounded = Class.forName(n.roundedOwner, false, cl).getDeclaredMethod(n.rounded, float.class);
            Field rectField = Class.forName(n.rectShapeOwner, false, cl).getDeclaredField(n.rectShape);
            rectField.setAccessible(true);
            Object rect = rectField.get(null);
            Method sp = Class.forName(n.spOwner, false, cl).getDeclaredMethod(n.sp, int.class);
            Method switchColors = Class.forName(n.switchColorsOwner, false, cl).getDeclaredMethod(
                    n.switchColorsFn, long.class, long.class, long.class, long.class, long.class, long.class,
                    composerCls, int.class);
            Object semiBold = Class.forName(n.fontWeight, false, cl).getField("x").get(null);
            Method text = TextFn.find(cl, n);
            Object modifierCompanion = Class.forName(n.modifierCompanion, false, cl).getField("a").get(null);
            Method[] colors = new Method[6];
            String names = "bgefhi";
            for (int i = 0; i < names.length(); i++) {
                colors[i] = colorsCls.getDeclaredMethod(String.valueOf(names.charAt(i)));
            }
            Method siK = colorsCls.getDeclaredMethod("k");
            Method textFn = text;
            for (Method m : new Method[]{width, fillWidth, padHV, padAll, padSides, clip, background,
                    border, rounded, sp, switchColors, textFn, siK, toggleRow, graphicsLayer}) {
                m.setAccessible(true);
            }
            for (Method m : colors) m.setAccessible(true);

            ThreadLocal<boolean[]> row = ThreadLocal.withInitial(() -> new boolean[2]);

            xp.hook(toggleRow).intercept(chain -> {
                if (!LABEL.equals(chain.getArg(0))) return chain.proceed();
                boolean[] s = row.get();
                boolean prevIn = s[0], prevOn = s[1];
                s[0] = true;
                s[1] = Boolean.TRUE.equals(chain.getArg(1));
                try {
                    return chain.proceed();
                } finally {
                    s[0] = prevIn;
                    s[1] = prevOn;
                }
            });

            xp.hook(width).intercept(chain -> {
                if (!row.get()[0] || (Float) chain.getArg(1) != 340f) return chain.proceed();
                Object shape = rounded.invoke(null, 14f);
                Object m = fillWidth.invoke(null, chain.getArg(0), 1f);
                m = clip.invoke(null, m, shape);
                m = background.invoke(null, m, colors[0].invoke(null), rect);
                return border.invoke(null, m, 1f, colors[1].invoke(null), rounded.invoke(null, 14f));
            });

            xp.hook(padHV).intercept(chain -> {
                if (!row.get()[0] || (Float) chain.getArg(1) != 14f || (Float) chain.getArg(2) != 10f) {
                    return chain.proceed();
                }
                return padAll.invoke(null, chain.getArg(0), 14f);
            });

            xp.hook(graphicsLayer).intercept(chain -> {
                if (!row.get()[0] || (Float) chain.getArg(1) != 0.68f) return chain.proceed();
                return chain.getArg(0);
            });

            xp.hook(switchColors).intercept(chain -> {
                if (!row.get()[0]) return chain.proceed();
                Object[] a = chain.getArgs().toArray();
                a[0] = colors[3].invoke(null);
                a[1] = colors[4].invoke(null);
                a[2] = colors[4].invoke(null);
                a[3] = colors[5].invoke(null);
                a[4] = colors[2].invoke(null);
                a[5] = colors[1].invoke(null);
                a[7] = 65416;
                return chain.proceed(a);
            });

            xp.hook(textFn).intercept(chain -> {
                if (!LABEL.equals(chain.getArg(0))) return chain.proceed();
                boolean[] s = row.get();
                if (!s[0]) return chain.proceed();
                Object composer = chain.getArg(16);
                Object r = chain.proceed(new Object[]{TITLE, null, siK.invoke(null), sp.invoke(null, 14),
                        semiBold, null, 0L, null, null, sp.invoke(null, 18), 0, false, 0, 0, null, null,
                        composer, 199686, 6, 130002});
                String sub = s[1] ? SUB_ON : SUB_OFF;
                Object subMod = padSides.invoke(null, modifierCompanion, 0f, 6f, 0f, 0f, 13);
                try {
                    textFn.invoke(null, sub, subMod, colors[5].invoke(null), sp.invoke(null, 12), null, null,
                            0L, null, null, sp.invoke(null, 16), 0, false, 0, 0, null, null,
                            composer, 3072, 6, 130032);
                } catch (InvocationTargetException e) {
                    throw e.getCause() != null ? e.getCause() : e;
                }
                return r;
            });
            xp.log(Log.INFO, TAG, "hooked " + n.rows + "." + n.toggleRow + " (Cloudflare row in jump host style)");
        } catch (Throwable t) {
            xp.log(Log.ERROR, TAG, "Cloudflare Access: row style unavailable", t);
        }
    }

    private Object onHost(XposedInterface.Chain chain) throws Throwable {
        String value = (String) chain.getArg(1);
        Object setter = chain.getArg(2);
        lastHost = value;
        lastHostSetter = setter;
        if (!hasMarker(value) || setter == null) return chain.proceed();
        Object[] args = chain.getArgs().toArray();
        args[1] = stripMarker(value);
        args[2] = fn(arg -> {
            String typed = arg instanceof String ? (String) arg : String.valueOf(arg);
            invokeFn(setter, "cf:" + stripMarker(typed));
        });
        return chain.proceed(args);
    }

    private Object onPort(XposedInterface.Chain chain) throws Throwable {
        if (!hasMarker(lastHost)) return chain.proceed();
        Object[] args = chain.getArgs().toArray();
        args[1] = PORT_OFF;
        args[2] = fn(arg -> { });
        int defaults = (Integer) args[8];
        if ((defaults & MODIFIER_DEFAULT) == 0 && args[5] != null) {
            args[5] = graphicsLayer.invoke(null, alphaLayerArgs(args[5]));
        }
        return chain.proceed(args);
    }

    private Object onUser(XposedInterface.Chain chain) throws Throwable {
        Object setter = lastHostSetter;
        if (setter != null) {
            boolean on = hasMarker(lastHost);
            Object toggle = fn(arg -> {
                boolean want = Boolean.TRUE.equals(arg);
                String base = stripMarker(lastHost);
                invokeFn(setter, want ? "cf:" + base : base);
            });
            try {
                toggleRow.invoke(null, LABEL, on, toggle, chain.getArg(6), 6);
            } catch (InvocationTargetException e) {
                throw e.getCause() != null ? e.getCause() : e;
            }
        }
        return chain.proceed();
    }

    private Object[] alphaLayerArgs(Object modifier) {
        Object[] a = new Object[graphicsLayerFloats + 4];
        a[0] = modifier;
        a[1] = 1f;
        a[2] = 1f;
        a[3] = PORT_ALPHA;
        for (int i = 4; i <= graphicsLayerFloats; i++) a[i] = 0f;
        a[graphicsLayerFloats + 1] = null;
        a[graphicsLayerFloats + 2] = false;
        a[graphicsLayerFloats + 3] = alphaOnlyMask;
        return a;
    }

    private interface Action {
        void run(Object arg) throws Throwable;
    }

    private Object fn(Action action) {
        InvocationHandler h = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method m, Object[] a) throws Throwable {
                switch (m.getName()) {
                    case "invoke":
                        action.run(a != null && a.length > 0 ? a[0] : null);
                        return unit;
                    case "equals":
                        return proxy == a[0];
                    case "hashCode":
                        return System.identityHashCode(proxy);
                    case "toString":
                        return "CfFormUi$fn";
                    default:
                        return null;
                }
            }
        };
        return Proxy.newProxyInstance(fn1Cls.getClassLoader(), new Class<?>[]{fn1Cls}, h);
    }

    private void invokeFn(Object fn, Object arg) throws Throwable {
        try {
            fn1Cls.getMethod("invoke", Object.class).invoke(fn, arg);
        } catch (InvocationTargetException e) {
            throw e.getCause() != null ? e.getCause() : e;
        }
    }
}
