package net.polishlyrics.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import fi.iki.elonen.NanoWSD;

/**
 * Embedded server on port 8789 (all interfaces).
 *
 * - WebSocket /link: the PC connects OUT to the TV here (works when the PC firewall blocks
 *   inbound connections). First time it must present the 6-digit code shown on screen; it then
 *   receives a secret for reconnects. Five wrong codes generate a new code.
 * - HTTP: only for this device's WebView (loopback). Requests are tunnelled to the PC as
 *   {t:'req'} messages and answered from {t:'res'} messages. "/" is the local waiting page.
 */
final class LinkServer extends NanoWSD {

    interface Listener {
        void onLinkChanged(boolean linked);
    }

    private static final int MAX_BAD_HELLOS = 5;
    private static final long REQUEST_TIMEOUT_S = 20;
    private static final String[] REQUEST_HEADERS = {"authorization", "content-type", "accept"};
    private static final String[] RESPONSE_HEADERS = {"cache-control", "content-security-policy", "location"};

    private final SharedPreferences prefs;
    private final Listener listener;
    private final int port;
    private final SecureRandom random = new SecureRandom();
    private final AtomicLong ids = new AtomicLong();
    private final Map<Long, CompletableFuture<JSONObject>> pending = new ConcurrentHashMap<>();

    private volatile Link link;
    private volatile String code;
    private int badHellos;

    LinkServer(Context ctx, int port, Listener listener) {
        super(port);
        this.port = port;
        this.prefs = ctx.getSharedPreferences("link", Context.MODE_PRIVATE);
        this.listener = listener;
        newCode();
    }

    boolean isLinked() {
        return link != null;
    }

    private synchronized void newCode() {
        code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        badHellos = 0;
    }

    // ---------------------------------------------------------------- HTTP (WebView only)

    @Override
    protected Response serveHttp(IHTTPSession s) {
        if (!isLoopback(s.getRemoteIpAddress())) {
            return text(Response.Status.FORBIDDEN, "text/plain", "forbidden");
        }
        String uri = s.getUri();
        if ("/__local/status".equals(uri)) return status();
        Link l = link;
        if ("/".equals(uri) || (l == null && "/tv".equals(uri))) {
            return text(Response.Status.OK, "text/html; charset=utf-8", WaitingPage.HTML);
        }
        if (l == null) {
            return text(statusOf(503), "application/json", "{\"error\":\"pc_offline\"}");
        }
        return forward(l, s);
    }

    private Response status() {
        JSONObject o = new JSONObject();
        try {
            o.put("linked", link != null);
            o.put("code", code);
            o.put("port", port);
            JSONArray ips = new JSONArray();
            for (String ip : localIps()) ips.put(ip);
            o.put("ips", ips);
            o.put("model", Build.MODEL);
        } catch (JSONException ignored) {
            // cannot happen with these value types
        }
        Response r = text(Response.Status.OK, "application/json", o.toString());
        r.addHeader("Cache-Control", "no-store");
        return r;
    }

    private Response forward(Link l, IHTTPSession s) {
        long id = ids.incrementAndGet();
        CompletableFuture<JSONObject> future = new CompletableFuture<>();
        pending.put(id, future);
        try {
            String body = "";
            if (s.getMethod() == Method.POST || s.getMethod() == Method.PUT) {
                Map<String, String> files = new HashMap<>();
                s.parseBody(files);
                String post = files.get("postData");
                if (post != null) body = Base64.encodeToString(post.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
            }
            JSONObject h = new JSONObject();
            for (String k : REQUEST_HEADERS) {
                String v = s.getHeaders().get(k);
                if (v != null) h.put(k, v);
            }
            String qs = s.getQueryParameterString();
            String path = s.getUri() + (qs != null && !qs.isEmpty() ? "?" + qs : "");
            JSONObject req = new JSONObject()
                    .put("t", "req").put("id", id).put("m", s.getMethod().name())
                    .put("p", path).put("h", h).put("b", body);
            l.sendSafe(req.toString());

            JSONObject res = future.get(REQUEST_TIMEOUT_S, TimeUnit.SECONDS);
            byte[] bytes = Base64.decode(res.optString("b", ""), Base64.DEFAULT);
            JSONObject rh = res.optJSONObject("h");
            String type = rh != null ? rh.optString("content-type", "application/octet-stream") : "application/octet-stream";
            Response r = newFixedLengthResponse(statusOf(res.optInt("s", 502)), type, new ByteArrayInputStream(bytes), bytes.length);
            if (rh != null) {
                for (String k : RESPONSE_HEADERS) {
                    String v = rh.optString(k, null);
                    if (v != null) r.addHeader(k, v);
                }
            }
            return r;
        } catch (TimeoutException e) {
            return text(statusOf(504), "application/json", "{\"error\":\"pc_timeout\"}");
        } catch (Exception e) {
            return text(statusOf(503), "application/json", "{\"error\":\"pc_offline\"}");
        } finally {
            pending.remove(id);
        }
    }

    private static Response.IStatus statusOf(final int code) {
        for (Response.Status st : Response.Status.values()) {
            if (st.getRequestStatus() == code) return st;
        }
        return new Response.IStatus() {
            @Override
            public String getDescription() {
                return code + " Status";
            }

            @Override
            public int getRequestStatus() {
                return code;
            }
        };
    }

    private static Response text(Response.IStatus status, String type, String body) {
        return newFixedLengthResponse(status, type, body);
    }

    private static boolean isLoopback(String ip) {
        return "127.0.0.1".equals(ip) || "::1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip);
    }

    private static Iterable<String> localIps() {
        java.util.List<String> out = new java.util.ArrayList<>();
        try {
            for (NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nif.isUp() || nif.isLoopback()) continue;
                for (InetAddress a : Collections.list(nif.getInetAddresses())) {
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) out.add(a.getHostAddress());
                }
            }
        } catch (Exception ignored) {
            // no network
        }
        return out;
    }

    private static boolean constantTimeEquals(String a, String b) {
        return a != null && b != null && MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private String newSecret() {
        byte[] b = new byte[32];
        random.nextBytes(b);
        return Base64.encodeToString(b, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    // ---------------------------------------------------------------- WebSocket (PC link)

    @Override
    protected WebSocket openWebSocket(IHTTPSession handshake) {
        return new Link(handshake);
    }

    private final class Link extends WebSocket {
        private boolean authed;
        private final String uri;

        Link(IHTTPSession handshake) {
            super(handshake);
            uri = handshake.getUri();
        }

        void sendSafe(String payload) {
            try {
                send(payload);
            } catch (IOException ignored) {
                // onClose / onException will clean up
            }
        }

        void closeQuietly() {
            try {
                close(WebSocketFrame.CloseCode.NormalClosure, "replaced", false);
            } catch (IOException ignored) {
                // already closed
            }
        }

        @Override
        protected void onOpen() {
            if (!"/link".equals(uri)) closeQuietly();
        }

        @Override
        protected void onClose(WebSocketFrame.CloseCode code, String reason, boolean initiatedByRemote) {
            if (link == this) {
                link = null;
                for (CompletableFuture<JSONObject> f : pending.values()) f.completeExceptionally(new IOException("link closed"));
                listener.onLinkChanged(false);
            }
        }

        @Override
        protected void onMessage(WebSocketFrame frame) {
            JSONObject m;
            try {
                m = new JSONObject(frame.getTextPayload());
            } catch (JSONException e) {
                return;
            }
            String t = m.optString("t");
            if (!authed) {
                if ("hello".equals(t)) hello(m);
                return;
            }
            if ("res".equals(t)) {
                CompletableFuture<JSONObject> f = pending.get(m.optLong("id", -1));
                if (f != null) f.complete(m);
            } else if ("ping".equals(t)) {
                sendSafe("{\"t\":\"pong\"}");
            }
        }

        private void hello(JSONObject m) {
            String stored = prefs.getString("secret", null);
            String gotSecret = m.optString("secret", null);
            String gotCode = m.optString("code", null);
            String issued = null;
            if (stored != null && constantTimeEquals(gotSecret, stored)) {
                issued = stored;
            } else if (constantTimeEquals(gotCode, code)) {
                issued = newSecret();
                prefs.edit().putString("secret", issued).apply();
                newCode(); // one-time code
            }
            if (issued == null) {
                synchronized (LinkServer.this) {
                    if (++badHellos >= MAX_BAD_HELLOS) newCode();
                }
                sendSafe("{\"t\":\"denied\"}");
                closeQuietly();
                return;
            }
            authed = true;
            Link old = link;
            link = this;
            if (old != null && old != this) old.closeQuietly();
            try {
                sendSafe(new JSONObject().put("t", "welcome").put("secret", issued).put("name", deviceName()).toString());
            } catch (JSONException ignored) {
                // cannot happen
            }
            listener.onLinkChanged(true);
        }

        @Override
        protected void onPong(WebSocketFrame pong) {
            // keepalive handled with JSON pings
        }

        @Override
        protected void onException(IOException exception) {
            // onClose follows
        }
    }

    private static String deviceName() {
        String model = Build.MODEL != null ? Build.MODEL : "";
        return model.startsWith("AFT") ? "Fire TV" : ("Fire TV " + model).trim();
    }
}
