package io.github.xboto.termrovercfaccess.cf;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Locale;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ConnectProxy implements Closeable {
    private static final int HEADER_TIMEOUT_MS = 10_000;
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int MAX_HEAD = 8 * 1024;
    private static final int MAX_CONNS = 64;
    private static final long UPSTREAM_JOIN_MS = 2_000L;

    private final ServerSocket server;
    private final Set<Socket> sockets = new HashSet<>();
    private final java.util.concurrent.Semaphore slots = new java.util.concurrent.Semaphore(MAX_CONNS);
    private final Logger logger;

    private final String user = "cf";
    private final String pass;
    private final byte[] expectedAuth;
    private volatile boolean closed;

    public interface Logger {
        void log(String msg);
    }

    public ConnectProxy(Logger logger) throws IOException {
        this.logger = logger;
        byte[] rnd = new byte[18];
        new SecureRandom().nextBytes(rnd);
        pass = Base64.getUrlEncoder().withoutPadding().encodeToString(rnd);
        expectedAuth = ("basic " + Base64.getEncoder().encodeToString(
                (user + ":" + pass).getBytes(StandardCharsets.ISO_8859_1))).getBytes(StandardCharsets.ISO_8859_1);
        server = new ServerSocket();
        server.setReuseAddress(true);

        server.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 50);
        Thread t = new Thread(this::acceptLoop, "TRCf-proxy-accept");
        t.setDaemon(true);
        t.start();
    }

    public String url() {
        return "http://" + user + ":" + pass + "@127.0.0.1:" + server.getLocalPort();
    }

    public String publicUrl() {
        return "http://127.0.0.1:" + server.getLocalPort();
    }

    private boolean authorized(String head) {
        for (String line : head.split("\r\n")) {
            int c = line.indexOf(':');
            if (c <= 0 || !line.substring(0, c).trim().equalsIgnoreCase("Proxy-Authorization")) continue;
            String v = line.substring(c + 1).trim();

            int sp = v.indexOf(' ');
            if (sp <= 0) return false;
            String norm = v.substring(0, sp).toLowerCase(Locale.ROOT) + " " + v.substring(sp + 1).trim();
            return MessageDigest.isEqual(norm.getBytes(StandardCharsets.ISO_8859_1), expectedAuth);
        }
        return false;
    }

    public boolean isClosed() {
        return closed || server.isClosed();
    }

    private void acceptLoop() {
        while (!closed) {
            Socket client;
            try {
                client = server.accept();
            } catch (Exception e) {
                break;
            }
            if (!track(client)) break;
            if (!slots.tryAcquire()) {
                closeSocket(client);
                continue;
            }
            Thread t = new Thread(() -> {
                try {
                    handle(client);
                } finally {
                    slots.release();
                }
            }, "TRCf-proxy-conn");
            t.setDaemon(true);
            t.start();
        }
    }

    private void handle(Socket client) {
        Socket upstream = null;
        try {
            client.setSoTimeout(HEADER_TIMEOUT_MS);
            InputStream in = client.getInputStream();
            OutputStream out = client.getOutputStream();
            String head = readHead(client, in);
            if (head == null) return;
            String requestLine = head.split("\r\n", 2)[0];
            String[] parts = requestLine.split(" ");
            if (!authorized(head)) {
                logger.log("proxy: rejected connection with missing or wrong credential");
                respond(out, "407 Proxy Authentication Required\r\nProxy-Authenticate: Basic realm=\"cf\"");
                return;
            }
            if (parts.length < 3 || !parts[0].equalsIgnoreCase("CONNECT")) {
                respond(out, "405 Method Not Allowed");
                return;
            }
            String authority = parts[1];
            int idx = authority.lastIndexOf(':');
            if (idx <= 0) {
                respond(out, "400 Bad Request");
                return;
            }
            String host = authority.substring(0, idx);
            if (host.startsWith("[") && host.endsWith("]")) host = host.substring(1, host.length() - 1);
            int port;
            try {
                port = Integer.parseInt(authority.substring(idx + 1));
            } catch (NumberFormatException e) {
                respond(out, "400 Bad Request");
                return;
            }
            if (port != 443) {
                logger.log("proxy: CONNECT rejected " + host + ":" + port);
                respond(out, "403 Forbidden");
                return;
            }
            Socket target;
            try {
                target = connectAny(host, port);
            } catch (Exception e) {
                logger.log("proxy: CONNECT " + host + ":" + port + " failed: "
                        + e.getClass().getSimpleName() + ": " + e.getMessage());
                respond(out, "502 Bad Gateway");
                return;
            }
            upstream = target;
            client.setSoTimeout(0);
            target.setTcpNoDelay(true);
            target.setKeepAlive(true);
            client.setTcpNoDelay(true);
            respond(out, "200 Connection Established");
            final Socket tgt = target;
            Thread up = new Thread(() -> {
                try {
                    pump(in, tgt.getOutputStream());
                } catch (IOException ignored) {
                }

                closeSocket(client);
                closeSocket(tgt);
            }, "TRCf-proxy-up");
            up.setDaemon(true);
            up.start();
            pump(target.getInputStream(), out);
            closeSocket(client);
            closeSocket(target);
            up.join(UPSTREAM_JOIN_MS);
        } catch (Exception ignored) {
        } finally {
            closeSocket(client);
            if (upstream != null) closeSocket(upstream);
        }
    }

    private Socket connectAny(String host, int port) throws IOException {
        IOException last = null;
        for (InetAddress address : InetAddress.getAllByName(host)) {
            if (address.isLoopbackAddress() || address.isAnyLocalAddress()
                    || address.isLinkLocalAddress() || address.isMulticastAddress()) {
                last = new IOException("local destination refused: " + address.getHostAddress());
                continue;
            }
            Socket s = new Socket();

            if (!track(s)) throw new IOException("proxy closed");
            try {
                s.connect(new InetSocketAddress(address, port), CONNECT_TIMEOUT_MS);
                return s;
            } catch (IOException e) {
                last = e;
                closeSocket(s);
            }
        }
        throw last != null ? last : new UnknownHostException(host);
    }

    private static void pump(InputStream from, OutputStream to) {
        byte[] buf = new byte[16 * 1024];
        try {
            int n;
            while ((n = from.read(buf)) >= 0) {
                to.write(buf, 0, n);
                to.flush();
            }
        } catch (Exception ignored) {
        }
    }

    private static String readHead(Socket client, InputStream in) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int matched = 0;
        byte[] term = {'\r', '\n', '\r', '\n'};
        long deadline = System.nanoTime() + HEADER_TIMEOUT_MS * 1_000_000L;
        try {
            while (bytes.size() < MAX_HEAD) {
                long left = (deadline - System.nanoTime()) / 1_000_000L;
                if (left <= 0) return null;
                client.setSoTimeout((int) Math.max(1, left));
                int b = in.read();
                if (b < 0) return null;
                bytes.write(b);
                matched = (b == term[matched]) ? matched + 1 : (b == '\r' ? 1 : 0);
                if (matched == 4) return new String(bytes.toByteArray(), StandardCharsets.ISO_8859_1);
            }
        } catch (SocketTimeoutException e) {
            return null;
        }
        return null;
    }

    private static void respond(OutputStream out, String status) throws IOException {
        out.write(("HTTP/1.1 " + status + "\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
        out.flush();
    }

    private boolean track(Socket s) {
        synchronized (sockets) {
            if (!closed) {
                sockets.add(s);
                return true;
            }
        }
        try {
            s.close();
        } catch (IOException ignored) {
        }
        return false;
    }

    private void closeSocket(Socket s) {
        synchronized (sockets) {
            sockets.remove(s);
        }
        try {
            s.close();
        } catch (IOException ignored) {
        }
    }

    @Override
    public void close() {
        List<Socket> snapshot;
        synchronized (sockets) {
            closed = true;
            snapshot = new ArrayList<>(sockets);
        }
        try {
            server.close();
        } catch (IOException ignored) {
        }
        for (Socket s : snapshot) closeSocket(s);
    }
}
