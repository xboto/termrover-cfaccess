package io.github.xboto.termrovercfaccess.cf;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.xboto.termrovercfaccess.Names;

public final class CfHooks {
    static final String TAG = "TermRoverCf";

    private final XposedInterface xp;
    private final AtomicBoolean hooked = new AtomicBoolean();
    private volatile CloudflaredBridge bridge;
    private final CfFormUi formUi;
    private final CfModeUi modeUi;

    public CfHooks(XposedInterface xp) {
        this.xp = xp;
        this.formUi = new CfFormUi(xp);
        this.modeUi = new CfModeUi(xp, formUi::accessOn);
    }

    private void log(String msg) {
        xp.log(Log.INFO, TAG, msg);
    }

    private CloudflaredBridge bridge() throws IOException {
        CloudflaredBridge b = bridge;
        if (b != null) return b;
        synchronized (this) {
            if (bridge == null) {
                String dir = xp.getModuleApplicationInfo().nativeLibraryDir;
                File bin = new File(dir, "libcloudflared.so");
                if (!bin.canExecute()) {
                    throw new IOException("cloudflared not available at " + bin);
                }
                bridge = new CloudflaredBridge(bin.getAbsolutePath(), this::log);
                log("cloudflared: " + bin);
            }
            return bridge;
        }
    }

    private static Context appContext() throws IOException {
        try {
            Object app = Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication").invoke(null);
            if (app instanceof Context) return (Context) app;
        } catch (Throwable ignored) {
        }
        throw new IOException("no application context");
    }

    public void install(ClassLoader cl, Names n) {
        if (!hooked.compareAndSet(false, true)) return;
        formUi.install(cl, n);
        modeUi.install(cl, n);
        try {
            Class<?> connCls = Class.forName(n.conn, false, cl);
            Field fHost = connCls.getField("o");
            Field fPort = connCls.getField("p");
            Field fSock = connCls.getField(n.connSocket);
            Field fCancel = connCls.getField(n.connCancel);
            Field fSuspend = connCls.getField(n.connSuspend);
            for (Field f : new Field[]{fHost, fPort, fSock, fCancel, fSuspend}) f.setAccessible(true);
            CancelFlags flags = new CancelFlags(fCancel, fSuspend);

            Class<?> suspendCls = Class.forName(n.connSuspendFn, false, cl);
            Field fState = suspendCls.getDeclaredField("v");
            fState.setAccessible(true);
            Field fConn = suspendCls.getDeclaredField("w");
            fConn.setAccessible(true);
            Method invokeSuspend = suspendCls.getDeclaredMethod("invokeSuspend", Object.class);

            Class<?> factory = Class.forName(n.transportFactory, false, cl);
            Field fKind = factory.getDeclaredField("o");
            fKind.setAccessible(true);
            Field fTimeout = factory.getDeclaredField("p");
            fTimeout.setAccessible(true);
            Field fTarget = factory.getDeclaredField("q");
            fTarget.setAccessible(true);
            Class<?> continuation = Class.forName(n.continuation, false, cl);
            Method create = factory.getDeclaredMethod("create", continuation);
            Constructor<?> transport = Class.forName(n.socketTransport, false, cl)
                    .getConstructor(Socket.class);
            int directKind = n.directKind;
            XposedInterface.HookHandle first = xp.hook(invokeSuspend).intercept(chain -> {
                Object self = chain.getThisObject();
                if (fState.getByte(self) == 0) {
                    Object conn = fConn.get(self);
                    String host = conn != null ? CloudflaredBridge.accessHostname((String) fHost.get(conn)) : null;
                    if (host != null && !flags.cancelled(conn)) {

                        bridge().ensureToken(appContext(), host, () -> flags.cancelled(conn));
                    }
                }
                return chain.proceed();
            });
            log("hooked " + n.connSuspendFn + ".invokeSuspend");

            try {
                xp.hook(create).intercept(chain -> {
                    Object self = chain.getThisObject();

                    if (directKind >= 0 && fKind.getByte(self) != directKind) return chain.proceed();
                    Object conn = fTarget.get(self);
                    if (conn == null || !connCls.isInstance(conn)) return chain.proceed();
                    String host = CloudflaredBridge.accessHostname((String) fHost.get(conn));
                    if (host == null) return chain.proceed();
                    if (flags.cancelled(conn)) throw new IOException("SSH connection cancelled");
                    log("Access: " + host + " (profile port " + fPort.getInt(conn) + " ignored)");
                    Socket[] holder = new Socket[1];
                    try {
                        Socket s = bridge().open(appContext(), host, fTimeout.getInt(self), sock -> {
                            holder[0] = sock;
                            try {
                                fSock.set(conn, sock);
                            } catch (IllegalAccessException ignored) {
                            }
                        });
                        if (flags.cancelled(conn)) throw new IOException("SSH connection cancelled");
                        try {
                            return transport.newInstance(s);
                        } catch (java.lang.reflect.InvocationTargetException e) {
                            throw e.getCause() != null ? e.getCause() : e;
                        }
                    } catch (Throwable t) {
                        Socket s = holder[0];
                        if (s != null) {
                            try {
                                s.close();
                            } catch (IOException ignored) {
                            }
                            try {
                                if (fSock.get(conn) == s) fSock.set(conn, null);
                            } catch (Throwable ignored) {
                            }
                        }
                        log("Access: failed to open " + host + ": " + t);
                        throw t;
                    }
                });
            } catch (Throwable t) {

                first.unhook();
                throw t;
            }
            log("hooked " + n.transportFactory + ".create (Cloudflare Access)");
        } catch (Throwable t) {
            hooked.set(false);
            xp.log(Log.ERROR, TAG, "Cloudflare Access hooks failed", t);
        }
    }

    private static final class CancelFlags {
        private final Field cancel;
        private final Field suspend;

        CancelFlags(Field cancel, Field suspend) {
            this.cancel = cancel;
            this.suspend = suspend;
        }

        boolean cancelled(Object conn) {
            try {
                return cancel.getBoolean(conn) || suspend.getBoolean(conn);
            } catch (IllegalAccessException e) {
                return false;
            }
        }
    }
}
