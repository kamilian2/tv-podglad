package pl.kamil.tvpodglad;

/** Strona podglądu na telefon. */
final class WebPage {
    private WebPage() {}

    static String html(String query) {
        String key = HttpServer.param(query, "key");
        String k = (key == null || key.isEmpty()) ? "" : "key=" + key.replaceAll("[^A-Za-z0-9_-]", "");
        String amp = k.isEmpty() ? "" : "&" + k;
        String qs = k.isEmpty() ? "" : "?" + k;

        return "<!DOCTYPE html><html lang=\"pl\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<meta name=\"apple-mobile-web-app-capable\" content=\"yes\">"
                + "<title>TV Podgląd</title><style>"
                + "*{box-sizing:border-box}body{margin:0;background:#0b1520;color:#e6eef6;"
                + "font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif}"
                + "header{display:flex;align-items:center;gap:10px;padding:12px 14px;background:#14212e}"
                + "h1{font-size:18px;margin:0;flex:1}.dot{width:10px;height:10px;border-radius:50%;background:#e74c3c}"
                + ".dot.on{background:#2ecc71}#info{font-size:12px;color:#8fa6bb}"
                + "#wrap{padding:10px}#tv{width:100%;display:block;background:#000;border-radius:8px;"
                + "aspect-ratio:16/9;object-fit:contain}"
                + ".bar{display:flex;flex-wrap:wrap;gap:8px;padding:0 10px 14px}"
                + "button,a.btn{flex:1;min-width:120px;padding:12px;border:0;border-radius:8px;background:#2e86de;"
                + "color:#fff;font-size:15px;text-align:center;text-decoration:none;cursor:pointer}"
                + "button.sec{background:#24384b}"
                + "p.note{font-size:12px;color:#8fa6bb;padding:0 14px 20px;margin:0}"
                + "</style></head><body>"
                + "<header><span class=\"dot\" id=\"dot\"></span><h1>TV Podgląd</h1><span id=\"info\"></span></header>"
                + "<div id=\"wrap\"><img id=\"tv\" alt=\"Ekran TV\"></div>"
                + "<div class=\"bar\">"
                + "<button id=\"mode\">Tryb: na żywo</button>"
                + "<button class=\"sec\" id=\"fs\">Pełny ekran</button>"
                + "<a class=\"btn\" id=\"dl\" href=\"/snapshot.jpg?download" + amp + "\">Pobierz zrzut</a>"
                + "</div>"
                + "<p class=\"note\">Netflix, Disney+ i inne treści z DRM mogą być widoczne jako czarny ekran - "
                + "to zabezpieczenie systemu, nie błąd aplikacji.</p>"
                + "<script>"
                + "var img=document.getElementById('tv'),live=true,timer=null;"
                + "var KQ='" + qs + "',KA='" + amp + "';"
                + "function startLive(){clearInterval(timer);img.src='/stream.mjpg'+KQ+(KQ?'&':'?')+'t='+Date.now();}"
                + "function startSnap(){img.src='';var f=function(){var n=new Image();"
                + "n.onload=function(){img.src=n.src};n.src='/snapshot.jpg?t='+Date.now()+KA};f();"
                + "clearInterval(timer);timer=setInterval(f,2000);}"
                + "document.getElementById('mode').onclick=function(){live=!live;"
                + "this.textContent=live?'Tryb: na żywo':'Tryb: zrzut co 2 s';live?startLive():startSnap();};"
                + "document.getElementById('fs').onclick=function(){var e=img;"
                + "(e.requestFullscreen||e.webkitRequestFullscreen||function(){}).call(e);};"
                + "img.onerror=function(){if(live)setTimeout(startLive,2000);};"
                + "function st(){fetch('/status'+KQ).then(function(r){return r.json()}).then(function(s){"
                + "document.getElementById('dot').className='dot'+(s.running?' on':'');"
                + "document.getElementById('info').textContent=s.width+'×'+s.height;"
                + "}).catch(function(){document.getElementById('dot').className='dot';});}"
                + "st();setInterval(st,5000);"
                + "document.addEventListener('visibilitychange',function(){"
                + "if(document.hidden){clearInterval(timer);img.src='';}else{live?startLive():startSnap();}});"
                + "startLive();"
                + "</script></body></html>";
    }
}
