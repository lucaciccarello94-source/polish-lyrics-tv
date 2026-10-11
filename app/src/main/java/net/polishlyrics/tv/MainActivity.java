package net.polishlyrics.tv;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.IOException;

/**
 * Full-screen WebView showing the TV page. The page is served by {@link LinkServer} on
 * 127.0.0.1, which tunnels every request to the PC over the PC's outbound WebSocket.
 */
public class MainActivity extends Activity implements LinkServer.Listener {

    static final int PORT = 8789;
    private static final String HOME = "http://127.0.0.1:" + PORT + "/";
    private static final long BACK_TO_WAITING_MS = 10_000;

    private WebView web;
    private LinkServer server;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_FULLSCREEN);

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true); // the TV page keeps its pairing token in localStorage
        s.setMediaPlaybackRequiresUserGesture(false);
        web.setWebViewClient(new WebViewClient()); // keep all navigation inside the app
        web.setBackgroundColor(0xFF0F1114);
        web.setFocusable(true);
        web.setFocusableInTouchMode(true);
        setContentView(web);
        hideSystemUi();

        server = new LinkServer(this, PORT, this);
        try {
            server.start(0, true); // no socket timeout: the PC link is long-lived
        } catch (IOException e) {
            web.loadData("<body style='background:#0f1114;color:#fff;font:28px sans-serif;padding:5vw'>"
                    + "Cannot open port " + PORT + ": " + e.getMessage() + "</body>", "text/html", "utf-8");
            return;
        }
        web.loadUrl(HOME);
        web.requestFocus();
    }

    /** Called from server threads. */
    @Override
    public void onLinkChanged(boolean linked) {
        if (linked) return; // the waiting page notices and opens the TV page by itself
        runOnUiThread(() -> web.postDelayed(() -> {
            if (server != null && !server.isLinked()) web.loadUrl(HOME);
        }, BACK_TO_WAITING_MS));
    }

    /** Remote media keys go to the page as KeyboardEvents (the TV page maps them to commands). */
    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        String key = null;
        switch (e.getKeyCode()) {
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                key = "MediaPlayPause";
                break;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                key = "MediaFastForward";
                break;
            case KeyEvent.KEYCODE_MEDIA_REWIND:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                key = "MediaRewind";
                break;
            default:
                break;
        }
        if (key == null) return super.dispatchKeyEvent(e);
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
            web.evaluateJavascript("window.dispatchEvent(new KeyboardEvent('keydown',{key:'" + key + "'}))", null);
        }
        return true;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    @SuppressWarnings("deprecation")
    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override
    protected void onDestroy() {
        if (server != null) server.stop();
        server = null;
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
