package net.polishlyrics.tv;

/** Local page shown until the PC links: address + one-time code, then jumps to the TV page. */
final class WaitingPage {
    private WaitingPage() {
    }

    static final String HTML = "<!doctype html><html><head><meta charset='utf-8'>"
            + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
            + "<title>Polish Lyrics TV</title><style>"
            + "html,body{margin:0;height:100%;background:#0f1114;color:#f2f2f2;font-family:system-ui,sans-serif}"
            + "main{height:100%;display:flex;flex-direction:column;align-items:center;justify-content:center;text-align:center;padding:4vh 5vw;box-sizing:border-box;font-size:clamp(16px,1.8vw,36px)}"
            + "h1{font-size:2em;margin:0 0 .8em}.muted{color:#9aa0a8}"
            + ".row{display:flex;gap:4vw;justify-content:center;margin:1.2em 0}"
            + ".box{background:#1d2026;border-radius:.6em;padding:.8em 1.4em}"
            + ".label{font-size:.75em;color:#9aa0a8;text-transform:uppercase;letter-spacing:.1em}"
            + ".value{font-size:2.6em;font-weight:800;letter-spacing:.08em;color:#e8a33d;font-variant-numeric:tabular-nums}"
            + "ol{text-align:left;line-height:1.6}"
            + "</style></head><body><main>"
            + "<h1>Polish Lyrics TV</h1>"
            + "<p class='muted'>Waiting for the app on your PC…</p>"
            + "<div class='row'>"
            + "<div class='box'><div class='label'>TV address</div><div class='value' id='ip'>…</div></div>"
            + "<div class='box'><div class='label'>Code</div><div class='value' id='code'>…</div></div>"
            + "</div>"
            + "<ol class='muted'><li>On the PC open Polish Lyrics Translator → <b>TV</b> tab → <b>Fire TV app</b>.</li>"
            + "<li>Enter the address and the code shown here, then press <b>Connect</b>.</li></ol>"
            + "<p class='muted' id='note'></p>"
            + "</main><script>"
            + "function show(s){var ip=document.getElementById('ip');"
            + "ip.textContent=s.ips&&s.ips.length?s.ips[0]:'no Wi-Fi';"
            + "var c=s.code||'';document.getElementById('code').textContent=c.slice(0,3)+' '+c.slice(3);"
            + "document.getElementById('note').textContent=s.ips&&s.ips.length>1?'Other addresses: '+s.ips.slice(1).join(', '):'';}"
            + "function poll(){fetch('/__local/status',{cache:'no-store'}).then(function(r){return r.json()}).then(function(s){"
            + "if(s.linked){location.replace('/tv');return;}show(s);setTimeout(poll,1500);"
            + "}).catch(function(){setTimeout(poll,3000);});}"
            + "poll();"
            + "</script></body></html>";
}
