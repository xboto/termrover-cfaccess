package io.github.xboto.termrovercfaccess.cf;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CloudflaredBridge {

    public interface Logger {
        void log(String msg);
    }

    private static final String PREFIX = "cf:";
    private static final String SUFFIX = "#cf";

    static final String SSL_CERT_DIR = "/apex/com.android.conscrypt/cacerts:/system/etc/security/cacerts";

    private static final Pattern LOGIN_REQUIRED = Pattern.compile(
            "(?i)(cdn-cgi/access/cli|Please open the following URL|Leave cloudflared running to download the token)");
    private static final Pattern TOKEN_NAME = Pattern.compile("^(.+)-([0-9a-f]{64})-token$");
    private static final Pattern EXP = Pattern.compile("\"exp\"\\s*:\\s*(\\d+)");
    private static final Pattern PID = Pattern.compile("\"pid\"\\s*:\\s*(\\d+)");
    private static final Pattern JWT = Pattern.compile("eyJ[A-Za-z0-9_-]{4,}\\.[A-Za-z0-9_-]{4,}\\.[A-Za-z0-9_-]*");
    private static final Pattern SECRET = Pattern.compile(
            "(?i)([A-Za-z_-]*token|[A-Za-z_-]*secret|cf_authorization|authorization)"
                    + "([\"']?\\s*(?:=|:|%3D)\\s*(?:Bearer\\s+|Basic\\s+)?[\"']?)([^\\s&;,\"']+)");

    private static final Pattern URL_CRED = Pattern.compile("(?i)(https?://)[^/@\\s:]+:[^/@\\s]+@");
    private static final Pattern HOSTNAME = Pattern.compile(
            "(?i)[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+");

    private static final long TOKEN_SKEW_SEC = 60;
    private static final long LOGIN_TIMEOUT_MS = 5 * 60 * 1000L;
    private static final long STARTUP_GRACE_MS = 150;

    private final String binary;
    private final Logger logger;
    private final Object proxyLock = new Object();

    private final java.util.concurrent.locks.ReentrantLock loginLock = new java.util.concurrent.locks.ReentrantLock();

    private final java.util.Set<String> forceLogin =
            java.util.Collections.synchronizedSet(new java.util.HashSet<>());
    private ConnectProxy proxy;

    public CloudflaredBridge(String binary, Logger logger) {
        this.binary = binary;
        this.logger = logger;
    }

    public static String accessHostname(String host) {
        if (host == null) return null;
        String h = host.trim();
        String lower = h.toLowerCase(Locale.ROOT);
        if (lower.startsWith(PREFIX)) {
            h = h.substring(PREFIX.length());
        } else if (lower.endsWith(SUFFIX)) {
            h = h.substring(0, h.length() - SUFFIX.length());
        } else {
            return null;
        }
        h = h.trim().toLowerCase(Locale.ROOT);
        if (h.length() > 253 || !HOSTNAME.matcher(h).matches()) return null;
        return h;
    }

    public static String redact(String line) {
        String out = JWT.matcher(line).replaceAll("<jwt-redacted>");
        out = URL_CRED.matcher(out).replaceAll("$1<redacted>@");
        return SECRET.matcher(out).replaceAll("$1$2<redacted>");
    }

    private String proxyUrl() throws IOException {
        synchronized (proxyLock) {
            if (proxy == null || proxy.isClosed()) {
                proxy = new ConnectProxy(logger::log);
                logger.log("CONNECT proxy at " + proxy.publicUrl());
            }
            return proxy.url();
        }
    }

    private Map<String, String> env(Context ctx) throws IOException {
        Map<String, String> env = new HashMap<>();
        String url = proxyUrl();
        String home = baseDir(ctx).getAbsolutePath();
        env.put("HTTPS_PROXY", url);
        env.put("HTTP_PROXY", url);
        env.put("NO_PROXY", "");
        env.put("SSL_CERT_DIR", SSL_CERT_DIR);
        env.put("HOME", home);
        env.put("XDG_CONFIG_HOME", home);
        return env;
    }

    private static File baseDir(Context ctx) {
        File base = ctx.getNoBackupFilesDir();
        File old = new File(ctx.getFilesDir(), ".cloudflared");
        File neu = new File(base, ".cloudflared");
        if (old.isDirectory() && !neu.exists()) {

            old.renameTo(neu);
        }
        return base;
    }

    private File cfDir(Context ctx) {
        File d = new File(baseDir(ctx), ".cloudflared");

        d.mkdirs();
        return d;
    }

    private static List<String> candidateDomains(String host) {
        List<String> out = new ArrayList<>();
        out.add(host);
        String[] labels = host.split("\\.");
        for (int i = 1; i < labels.length - 1; i++) {
            StringBuilder sb = new StringBuilder("-");
            for (int j = i; j < labels.length; j++) sb.append('.').append(labels[j]);
            out.add(sb.toString());
        }
        return out;
    }

    private boolean hasValidToken(File dir, String host, boolean prune) {
        List<String> candidates = candidateDomains(host.toLowerCase(Locale.ROOT));
        File[] files = dir.listFiles();
        if (files == null) return false;
        long now = System.currentTimeMillis() / 1000;
        boolean valid = false;
        for (File f : files) {
            if (!f.isFile()) continue;
            Matcher m = TOKEN_NAME.matcher(f.getName());
            if (!m.matches() || !candidates.contains(m.group(1).toLowerCase(Locale.ROOT))) continue;
            Long exp = jwtExp(f);
            if (exp != null && exp > now + TOKEN_SKEW_SEC) {
                logger.log("valid Access token for " + m.group(1) + " (" + (exp - now) / 60 + " min)");
                valid = true;
            } else if (prune) {
                logger.log("Access token discarded (" + (exp == null ? "malformed" : "expired") + "): " + m.group(1));

                f.delete();

                new File(f.getPath() + ".url").delete();
            }
        }
        return valid;
    }

    private static Long jwtExp(File f) {
        try {
            String raw = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim();
            String[] parts = raw.split("\\.");
            if (parts.length != 3 || !raw.startsWith("eyJ")) return null;
            String payload = new String(Base64.getUrlDecoder().decode(parts[1].replace("=", "")),
                    StandardCharsets.UTF_8);
            Matcher m = EXP.matcher(payload);
            return m.find() ? Long.parseLong(m.group(1)) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void removeStaleLocks(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (!f.isFile() || !f.getName().endsWith(".lock")) continue;
            boolean stale;
            try {
                Matcher m = PID.matcher(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
                long age = System.currentTimeMillis() - f.lastModified();

                stale = m.find() ? (!new File("/proc/" + m.group(1)).exists() || age > LOGIN_TIMEOUT_MS)
                        : age > 5_000;
            } catch (Exception e) {
                stale = System.currentTimeMillis() - f.lastModified() > 5_000;
            }
            if (stale && f.delete()) logger.log("stale lock removed: " + f.getName());
        }
    }

    public void ensureToken(Context ctx, String host, CancelCheck cancelled) throws IOException {
        File dir = cfDir(ctx);

        if (!forceLogin.contains(host) && hasValidToken(dir, host, false)) return;
        java.util.concurrent.locks.ReentrantLock lock = loginLock;
        try {
            while (!lock.tryLock(250, TimeUnit.MILLISECONDS)) {
                if (cancelled.cancelled()) throw new IOException("SSH connection cancelled");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted");
        }
        try {
            if (cancelled.cancelled()) throw new IOException("SSH connection cancelled");
            boolean force = forceLogin.remove(host);
            removeStaleLocks(dir);
            if (force) {
                File[] files = dir.listFiles();
                if (files != null) {
                    List<String> candidates = candidateDomains(host);
                    for (File f : files) {
                        Matcher m = TOKEN_NAME.matcher(f.getName());
                        if (m.matches() && candidates.contains(m.group(1).toLowerCase(Locale.ROOT))) {
                            logger.log("token rejected by the edge, discarding: " + m.group(1));

                            f.delete();

                            new File(f.getPath() + ".url").delete();
                        }
                    }
                }
            } else if (hasValidToken(dir, host, true)) {

                return;
            }
            runLogin(ctx, host, cancelled);
            if (cancelled.cancelled()) throw new IOException("SSH connection cancelled");
            if (!hasValidToken(dir, host, true)) {
                throw new IOException("Cloudflare Access: login for " + host
                        + " is incomplete. Finish the login in the browser and connect again.");
            }
        } finally {
            lock.unlock();
        }
    }

    public interface CancelCheck {
        boolean cancelled();
    }

    private void runLogin(Context ctx, String host, CancelCheck cancelled) throws IOException {
        logger.log("no Access token for " + host + ": access login (browser)");
        toast(ctx, "Cloudflare Access: finish the login in the browser, then return to TermRover");
        ProcessBuilder pb = new ProcessBuilder(binary, "access", "login", "--quiet", "--auto-close",
                "https://" + host).redirectErrorStream(true);
        pb.environment().putAll(env(ctx));
        Process p = pb.start();
        AtomicBoolean opened = new AtomicBoolean();
        ArrayDeque<String> last = new ArrayDeque<>();
        Thread reader = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String raw;
                while ((raw = r.readLine()) != null) {
                    String red = redact(raw);
                    synchronized (last) {
                        if (last.size() >= 5) last.removeFirst();
                        if (!red.trim().isEmpty()) last.addLast(red);
                    }
                    logger.log("[login] " + red);
                    String url = extractLoginUrl(raw, host);
                    if (url != null && opened.compareAndSet(false, true)) openBrowser(ctx, url);
                }
            } catch (Exception ignored) {

            }
        }, "TRCf-login-drain");
        reader.setDaemon(true);
        reader.start();
        long deadline = System.currentTimeMillis() + LOGIN_TIMEOUT_MS;
        boolean interrupted = false;
        try {
            while (p.isAlive()) {
                if (System.currentTimeMillis() >= deadline || cancelled.cancelled()) break;
                p.waitFor(500, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException e) {

            interrupted = true;
        } finally {
            destroy(p);
            try {
                reader.join(2_000);
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted");
        }
        int code = p.isAlive() ? -1 : p.exitValue();
        synchronized (last) {
            logger.log("access login finished: exit=" + code + (last.isEmpty() ? "" : " | " + last.peekLast()));
        }
    }

    private static String extractLoginUrl(String line, String host) {
        int start = line.indexOf("https://");
        if (start < 0) return null;
        String low = line.toLowerCase(Locale.ROOT);
        if (!low.contains("cdn-cgi/access") && !low.contains("cloudflareaccess.com")) return null;
        int end = start;
        while (end < line.length()) {
            char c = line.charAt(end);
            if (Character.isWhitespace(c) || c == '"' || c == '\'') break;
            end++;
        }
        String url = line.substring(start, end);
        while (!url.isEmpty() && ".,;)".indexOf(url.charAt(url.length() - 1)) >= 0) {
            url = url.substring(0, url.length() - 1);
        }

        if (url.indexOf('\\') >= 0) return null;
        java.net.URI uri;
        try {
            uri = new java.net.URI(url);
        } catch (java.net.URISyntaxException e) {
            return null;
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getRawUserInfo() != null) return null;
        String uh = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!HOSTNAME.matcher(uh).matches()) return null;
        if (!uh.equals(host) && !uh.endsWith(".cloudflareaccess.com")) return null;
        return url;
    }

    private void openBrowser(Context ctx, String url) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
            logger.log("browser opened for Access login");
        } catch (Exception e) {
            logger.log("failed to open the browser: " + e.getClass().getSimpleName());
        }
    }

    private static void toast(Context ctx, String msg) {
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show();
            } catch (Throwable ignored) {
            }
        });
    }

    public Socket open(Context ctx, String host, int connectTimeoutMs,
                       java.util.function.Consumer<Socket> onSocket) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(binary, "access", "tcp", "--hostname", host)
                .redirectErrorStream(false);
        pb.environment().putAll(env(ctx));

        ServerSocket server = new ServerSocket();
        BridgeSocket client = new BridgeSocket();
        Socket peer = null;
        Process p = null;
        try {
            server.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 8);
            server.setSoTimeout(Math.max(connectTimeoutMs, 2_000));
            client.connect(server.getLocalSocketAddress(), 2_000);

            int ourPort = client.getLocalPort();
            while (true) {
                Socket s = server.accept();
                if (s.getPort() == ourPort) {
                    peer = s;
                    break;
                }
                s.close();
            }
            server.close();
            client.setTcpNoDelay(true);
            client.setKeepAlive(true);
            onSocket.accept(client);

            p = pb.start();
            final Process proc = p;
            final Socket peerFinal = peer;

            client.onClose(() -> {
                closeQuietly(peerFinal);
                destroy(proc);
            });
            AtomicBoolean loginRequired = new AtomicBoolean();
            ArrayDeque<String> lastErr = new ArrayDeque<>();
            Thread err = new Thread(() -> {
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(proc.getErrorStream(), StandardCharsets.UTF_8))) {
                    String raw;
                    while ((raw = r.readLine()) != null) {
                        String red = redact(raw);
                        if (red.length() > 400) red = red.substring(0, 400) + "...";
                        synchronized (lastErr) {
                            if (lastErr.size() >= 5) lastErr.removeFirst();
                            lastErr.addLast(red);
                        }
                        logger.log("[access tcp] " + red);
                        if (LOGIN_REQUIRED.matcher(raw).find() && loginRequired.compareAndSet(false, true)) {

                            logger.log("edge requested login: closing the connection; the next attempt logs in");
                            forceLogin.add(host);
                            closeQuietly(peerFinal);
                            destroy(proc);
                        }
                    }
                } catch (Exception ignored) {
                }
            }, "TRCf-stderr");
            err.setDaemon(true);
            err.start();

            try {
                if (p.waitFor(STARTUP_GRACE_MS, TimeUnit.MILLISECONDS)) {
                    err.join(500);
                    String tail;
                    synchronized (lastErr) {
                        tail = String.join("; ", lastErr);
                    }
                    throw new IOException("cloudflared exited on startup (exit=" + p.exitValue() + ")"
                            + (tail.isEmpty() ? "" : ": " + tail));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted");
            }

            Thread down = pumpThread(p.getInputStream(), peer.getOutputStream(), peer, p, "TRCf-down");
            Thread up = pumpThread(peer.getInputStream(), p.getOutputStream(), peer, p, "TRCf-up");
            down.start();
            up.start();
            logger.log("access tcp bridge ready for " + host + " (pid " + pidOf(p) + ")");
            return client;
        } catch (IOException | RuntimeException e) {
            closeQuietly(server);
            closeQuietly(client);
            if (peer != null) closeQuietly(peer);
            if (p != null) destroy(p);
            throw e;
        }
    }

    private static final class BridgeSocket extends Socket {
        private volatile Runnable onClose;
        private volatile boolean closing;

        void onClose(Runnable r) {
            onClose = r;
            if (closing || isClosed()) r.run();
        }

        @Override
        public synchronized void close() throws IOException {
            closing = true;
            try {
                super.close();
            } finally {
                Runnable r = onClose;
                onClose = null;
                if (r != null) new Thread(r, "TRCf-close").start();
            }
        }
    }

    private Thread pumpThread(InputStream from, OutputStream to, Socket peer, Process p, String name) {
        Thread t = new Thread(() -> {
            byte[] buf = new byte[32 * 1024];
            try {
                int n;
                while ((n = from.read(buf)) >= 0) {
                    to.write(buf, 0, n);
                    to.flush();
                }
            } catch (Exception ignored) {
            } finally {

                closeQuietly(peer);
                destroy(p);
            }
        }, name);
        t.setDaemon(true);
        return t;
    }

    private static String pidOf(Process p) {
        try {
            java.lang.reflect.Field f = p.getClass().getDeclaredField("pid");
            f.setAccessible(true);
            return String.valueOf(f.get(p));
        } catch (Throwable t) {
            return "?";
        }
    }

    static void destroy(Process p) {
        if (!p.isAlive()) return;
        try {
            p.getOutputStream().close();
        } catch (Exception ignored) {
        }
        p.destroy();
        try {
            if (!p.waitFor(500, TimeUnit.MILLISECONDS)) {
                p.destroyForcibly();
                p.waitFor(200, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            p.destroyForcibly();
        }
    }

    private static void closeQuietly(java.io.Closeable c) {
        try {
            c.close();
        } catch (Exception ignored) {
        }
    }
}
