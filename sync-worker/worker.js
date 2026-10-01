// zyne-iptv-sync: OTT-style web setup for ZyneLabs IPTV.
// Phone browser -> form -> 6-digit code -> TV/phone/tablet app polls and imports.
// Codes live 15 minutes in KV and are deleted after the app consumes them.

const CODE_RE = /^[0-9]{6}$/;

function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "Content-Type": "application/json", "Cache-Control": "no-store" },
  });
}

function formPage(code) {
  const safe = CODE_RE.test(code || "") ? code : "";
  return new Response(`<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>ZyneLabs IPTV — Add playlist</title>
<style>
body{background:#0C0906;color:#F5EDE0;font-family:system-ui,sans-serif;margin:0;padding:20px}
.card{max-width:480px;margin:0 auto;background:#17100A;border:1px solid #7A4A1E;border-radius:14px;padding:22px}
h2{margin:0 0 4px;color:#FFAA33}.sub{color:#A89A86;font-size:13px;margin-bottom:16px}
label{display:block;font-size:13px;color:#A89A86;margin:12px 0 4px}
input,select{width:100%;box-sizing:border-box;background:#20150C;border:1px solid #7A4A1E;color:#F5EDE0;border-radius:10px;padding:11px;font-size:15px}
button{width:100%;margin-top:18px;background:linear-gradient(135deg,#FFAA33,#7A4A1E);border:0;color:#0C0906;font-weight:700;font-size:16px;border-radius:12px;padding:13px}
.code{font-size:34px;letter-spacing:8px;text-align:center;color:#00E5FF;font-weight:700;margin:6px 0 2px}
.ok{text-align:center;padding:30px 0}.ok div{font-size:52px}
.hidden{display:none}
</style></head><body><div class="card">
<div id="form">
<h2>📲 Add playlist to your device</h2>
<div class="sub">Enter the code shown on your TV / tablet / phone, fill in your playlist, hit Save — the app picks it up automatically.</div>
<label>6-digit code from the app</label>
<input id="code" inputmode="numeric" maxlength="6" placeholder="123456" value="${safe}">
<label>Playlist name</label>
<input id="name" placeholder="My IPTV">
<label>Type</label>
<select id="type">
<option value="m3u">M3U playlist URL</option>
<option value="xtream">Xtream Codes</option>
<option value="stalker">Stalker / MAC portal</option>
</select>
<div id="f_url"><label>Playlist URL</label><input id="url" placeholder="http://example.com/list.m3u"></div>
<div id="f_server" class="hidden"><label>Server URL</label><input id="server" placeholder="http://host:port"></div>
<div id="f_user" class="hidden"><label>Username</label><input id="user"></div>
<div id="f_pass" class="hidden"><label>Password</label><input id="pass" type="password"></div>
<div id="f_mac" class="hidden"><label>MAC address</label><input id="mac" placeholder="00:1A:79:xx:xx:xx"></div>
<button onclick="save()">Save to device</button>
</div>
<div id="done" class="ok hidden"><div>✅</div><h2>Saved!</h2><div class="sub">Your device will add the playlist automatically.</div></div>
<script>
const t=document.getElementById('type');
function upd(){const v=t.value;
 for(const[id,show]of[['f_url',v==='m3u'],['f_server',v!=='m3u'],['f_user',v==='xtream'],['f_pass',v==='xtream'],['f_mac',v==='stalker']])
 document.getElementById(id).classList.toggle('hidden',!show);}
t.onchange=upd;upd();
async function save(){
 const code=document.getElementById('code').value.trim();
 if(!/^[0-9]{6}$/.test(code)){alert('Enter the 6-digit code from the app');return;}
 const body={code,type:t.value,name:document.getElementById('name').value.trim(),
  url:document.getElementById('url').value.trim(),server:document.getElementById('server').value.trim(),
  username:document.getElementById('user').value.trim(),password:document.getElementById('pass').value,
  mac:document.getElementById('mac').value.trim()};
 const r=await fetch('/save',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});
 if(r.ok){document.getElementById('form').classList.add('hidden');document.getElementById('done').classList.remove('hidden');}
 else alert('Save failed: '+(await r.text()));
}
</script></div></body></html>`,
    { headers: { "Content-Type": "text/html; charset=utf-8", "Cache-Control": "no-store" } });
}

export default {
  async fetch(req, env) {
    const url = new URL(req.url);
    try {
      if (req.method === "GET" && (url.pathname === "/" || url.pathname === "/add")) {
        return formPage(url.searchParams.get("code") || "");
      }
      if (req.method === "POST" && url.pathname === "/save") {
        const b = await req.json();
        if (!b || !CODE_RE.test(b.code || "")) return new Response("bad code", { status: 400 });
        const type = ["m3u", "xtream", "stalker"].includes(b.type) ? b.type : "m3u";
        const payload = {
          name: String(b.name || "Playlist").slice(0, 80),
          type,
          url: String(b.url || b.server || "").slice(0, 500),
          username: String(b.username || "").slice(0, 120),
          password: String(b.password || "").slice(0, 120),
          mac: String(b.mac || "").slice(0, 32),
          ts: Date.now(),
        };
        if (!payload.url) return new Response("URL required", { status: 400 });
        await env.SYNC.put("code:" + b.code, JSON.stringify(payload), { expirationTtl: 900 });
        return json({ ok: true });
      }
      if (req.method === "GET" && url.pathname === "/api/fetch") {
        const code = url.searchParams.get("code") || "";
        if (!CODE_RE.test(code)) return json({ error: "bad code" }, 400);
        const v = await env.SYNC.get("code:" + code);
        if (!v) return json({ error: "not found" }, 404);
        return new Response(v, {
          headers: { "Content-Type": "application/json", "Cache-Control": "no-store" },
        });
      }
      if (req.method === "POST" && url.pathname === "/api/consume") {
        const b = await req.json().catch(() => ({}));
        if (b && CODE_RE.test(b.code || "")) await env.SYNC.delete("code:" + b.code);
        return json({ ok: true });
      }
      return new Response("not found", { status: 404 });
    } catch (e) {
      return new Response("error", { status: 500 });
    }
  },
};
