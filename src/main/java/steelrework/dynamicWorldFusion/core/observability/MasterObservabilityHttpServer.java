package steelrework.dynamicWorldFusion.core.observability;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executors;

public final class MasterObservabilityHttpServer {
    private final JavaPlugin plugin;
    private final MasterTopologyProvider topologyProvider;
    private final String bindHost;
    private final int bindPort;
    private final String websocketHostForUi;
    private final int websocketPortForUi;

    private HttpServer server;

    public MasterObservabilityHttpServer(
            JavaPlugin plugin,
            MasterTopologyProvider topologyProvider,
            String bindHost,
            int bindPort,
            String websocketHostForUi,
            int websocketPortForUi
    ) {
        this.plugin = plugin;
        this.topologyProvider = topologyProvider;
        this.bindHost = bindHost;
        this.bindPort = bindPort;
        this.websocketHostForUi = websocketHostForUi;
        this.websocketPortForUi = websocketPortForUi;
    }

    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress(bindHost, bindPort), 0);
            server.createContext("/api/topology", this::handleTopology);
            server.createContext("/api/reassign", this::handleReassign);
            server.createContext("/", this::handleUi);
            server.setExecutor(Executors.newFixedThreadPool(4));
            server.start();
            plugin.getLogger().info("Master observability API started on http://" + bindHost + ":" + bindPort);
        } catch (IOException ex) {
            plugin.getLogger().severe("Failed to start observability API: " + ex.getMessage());
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    private void handleTopology(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"method_not_allowed\"}");
            return;
        }
        sendJson(exchange, 200, topologyJson());
    }

    private void handleReassign(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod()) && !"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"method_not_allowed\"}");
            return;
        }

        Map<String, String> params = HttpQuery.parse(exchange.getRequestURI().getRawQuery());
        String zoneId = params.get("zoneId");
        String targetNodeId = params.get("targetNodeId");
        if (zoneId == null || targetNodeId == null) {
            sendJson(exchange, 400, "{\"error\":\"missing_zoneId_or_targetNodeId\"}");
            return;
        }

        boolean accepted = topologyProvider.requestZoneMove(zoneId, targetNodeId);
        if (!accepted) {
            sendJson(exchange, 404, "{\"status\":\"not_applied\"}");
            return;
        }
        sendJson(exchange, 200, "{\"status\":\"accepted\"}");
    }

    private void handleUi(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendText(exchange, 405, "Method not allowed", "text/plain; charset=utf-8");
            return;
        }
        sendText(exchange, 200, buildUiHtml(), "text/html; charset=utf-8");
    }

    private String topologyJson() {
        StringBuilder json = new StringBuilder("{\"nodes\":[");
        boolean firstNode = true;
        for (Map.Entry<String, Map<String, String>> entry : topologyProvider.nodeTopologySnapshot().entrySet()) {
            if (!firstNode) {
                json.append(',');
            }
            json.append("{\"nodeId\":\"").append(escape(entry.getKey())).append("\"");
            for (Map.Entry<String, String> field : entry.getValue().entrySet()) {
                json.append(",\"").append(escape(field.getKey())).append("\":\"")
                        .append(escape(field.getValue())).append("\"");
            }
            json.append('}');
            firstNode = false;
        }
        json.append("],\"zones\":[");
        boolean firstZone = true;
        for (Map.Entry<String, String> zone : topologyProvider.zoneOwnershipSnapshot().entrySet()) {
            if (!firstZone) {
                json.append(',');
            }
            json.append("{\"zoneId\":\"").append(escape(zone.getKey())).append("\",\"nodeId\":\"")
                    .append(escape(zone.getValue())).append("\"}");
            firstZone = false;
        }
        json.append("],\"events\":[");
        boolean firstEvent = true;
        for (Map<String, String> event : topologyProvider.recentEventsSnapshot()) {
            if (!firstEvent) {
                json.append(',');
            }
            json.append('{');
            boolean firstField = true;
            for (Map.Entry<String, String> field : event.entrySet()) {
                if (!firstField) {
                    json.append(',');
                }
                json.append("\"").append(escape(field.getKey())).append("\":\"")
                        .append(escape(field.getValue())).append("\"");
                firstField = false;
            }
            json.append('}');
            firstEvent = false;
        }
        json.append("]}");
        return json.toString();
    }

    private String buildUiHtml() {
        return """
                <!doctype html>
                <html lang="ru">
                <head>
                  <meta charset="utf-8"/>
                  <meta name="viewport" content="width=device-width,initial-scale=1"/>
                  <title>Nexus Core · Панель наблюдения</title>
                  <link rel="preconnect" href="https://fonts.googleapis.com">
                  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
                  <link href="https://fonts.googleapis.com/css2?family=Manrope:wght@400;600;700&display=swap" rel="stylesheet">
                  <style>
                    :root{
                      --bg:#0b121a;--panel:#111c27;--panel-2:#0f1822;--accent:#2bd1a5;--accent-2:#f7b731;
                      --text:#e6f0ff;--muted:#97a3b7;--border:#243244;
                    }
                    body{margin:0;font-family:'Manrope',Segoe UI,Arial,sans-serif;background:radial-gradient(80% 120% at 10% 10%,#122133 0%,#0b121a 55%);color:var(--text)}
                    .wrap{max-width:1280px;margin:24px auto;padding:0 16px}
                    .head{display:flex;flex-wrap:wrap;gap:12px;justify-content:space-between;align-items:center;margin-bottom:16px}
                    .status{font-size:12px;padding:8px 12px;border-radius:16px;background:#1a2a3b;border:1px solid var(--border)}
                    .grid{display:grid;grid-template-columns:1.15fr 0.85fr;gap:14px}
                    .grid-2{display:grid;grid-template-columns:1fr 1fr;gap:14px;margin-top:14px}
                    .grid-3{display:grid;grid-template-columns:1fr 1fr 1fr;gap:14px;margin-top:14px}
                    .card{background:linear-gradient(180deg,var(--panel) 0%,var(--panel-2) 100%);border:1px solid var(--border);border-radius:14px;padding:14px}
                    table{width:100%;border-collapse:collapse;font-size:13px}
                    th,td{padding:8px;border-bottom:1px solid var(--border);text-align:left}
                    th{color:var(--muted);font-weight:600}
                    tr.active{background:#1a2a3b}
                    .heat{height:10px;border-radius:6px;background:linear-gradient(90deg,#1dc96f,#f5c542,#db3a34)}
                    .bar{height:10px;border-radius:6px;background:#7ec6ff}
                    .pill{display:inline-block;font-size:11px;padding:2px 6px;border-radius:10px;background:#22364d;margin-right:6px}
                    .list{font-size:12px;line-height:1.6}
                    .mono{font-family:Consolas,monospace}
                    .muted{color:var(--muted)}
                    .row{display:flex;gap:8px;align-items:center}
                    .btn{background:var(--accent);border:none;color:#0b121a;padding:6px 10px;border-radius:10px;cursor:pointer;font-weight:700}
                    .btn:disabled{opacity:.5;cursor:not-allowed}
                    .scroll{max-height:180px;overflow:auto}
                    @media (max-width: 980px){
                      .grid,.grid-2,.grid-3{grid-template-columns:1fr}
                    }
                  </style>
                </head>
                <body>
                  <div class="wrap">
                    <div class="head">
                      <div>
                        <h2 style="margin:0">Nexus Core · Живая карта нагрузки</h2>
                        <div class="muted" style="font-size:12px">Узлы, зоны, игроки, чанки и события в реальном времени</div>
                      </div>
                      <div id="wsState" class="status">WebSocket: подключение…</div>
                    </div>

                    <div class="grid">
                      <div class="card">
                        <h3>Узлы</h3>
                        <table>
                          <thead><tr><th>Узел</th><th>Зона</th><th>TPS</th><th>Игроки</th><th>Сущности</th><th>Чанки</th></tr></thead>
                          <tbody id="rows"></tbody>
                        </table>
                      </div>
                      <div class="card">
                        <h3>Тепловая карта нагрузки</h3>
                        <div id="heatList"></div>
                      </div>
                    </div>

                    <div class="grid-2">
                      <div class="card">
                        <h3>Зоны и их владельцы</h3>
                        <div class="row" style="margin-bottom:8px">
                          <div id="actionStatus" class="muted">Выберите зону и узел для переназначения.</div>
                        </div>
                        <div class="scroll">
                          <table>
                            <thead><tr><th>Зона</th><th>Узел</th><th>Переназначить</th></tr></thead>
                            <tbody id="zoneRows"></tbody>
                          </table>
                        </div>
                      </div>
                      <div class="card">
                        <h3>Журнал событий</h3>
                        <div id="eventList" class="list scroll">Пока пусто.</div>
                      </div>
                    </div>

                    <div class="grid-3">
                      <div class="card">
                        <h3>Выбранный узел</h3>
                        <div id="nodeDetails" class="list">Кликните по узлу для деталей.</div>
                      </div>
                      <div class="card">
                        <h3>Игроки</h3>
                        <input id="playerFilter" placeholder="Фильтр по нику" style="width:100%;margin-bottom:8px;padding:6px 8px;border-radius:8px;border:1px solid var(--border);background:#0f1c2b;color:var(--text)"/>
                        <div id="playerList" class="list scroll">Нет данных.</div>
                      </div>
                      <div class="card">
                        <h3>Чанки по мирам</h3>
                        <div id="chunkStats" class="list">Нет данных.</div>
                      </div>
                      <div class="card">
                        <h3>Карта чанков по игрокам</h3>
                        <div style="display:flex;gap:8px;align-items:center;margin-bottom:8px">
                          <label for="worldSelect" style="font-size:12px">Мир</label>
                          <select id="worldSelect" style="flex:1;padding:6px 8px;border-radius:8px;border:1px solid var(--border);background:#0f1c2b;color:var(--text)"></select>
                        </div>
                        <div style="display:flex;gap:8px;align-items:center;margin-bottom:8px;font-size:12px">
                          <label><input id="lockPlayer" type="checkbox"/> Фиксировать игрока</label>
                          <div id="mapScale" class="mono"></div>
                        </div>
                        <div style="display:flex;gap:8px;align-items:center;margin-bottom:8px;font-size:12px">
                          <label for="trailLen">След</label>
                          <input id="trailLen" type="range" min="3" max="30" value="12" style="flex:1"/>
                          <span id="trailVal" class="mono">12</span>
                        </div>
                        <canvas id="chunkMap" width="280" height="280" style="width:100%;height:auto;border:1px solid var(--border);border-radius:8px;background:#0f1c2b"></canvas>
                        <div id="chunkMapNote" class="list">Нет позиций игроков.</div>
                        <div id="mapLegend" class="list" style="margin-top:8px;max-height:120px;overflow:auto"></div>
                        <div style="margin-top:10px">
                          <div style="font-size:12px;margin-bottom:6px">Топ чанков (по игрокам)</div>
                          <div id="topChunks" class="list">Нет данных.</div>
                        </div>
                      </div>
                    </div>
                  </div>

                  <script>                    const wsState = document.getElementById('wsState');
                    const rows = document.getElementById('rows');
                    const heatList = document.getElementById('heatList');
                    const nodeDetails = document.getElementById('nodeDetails');
                    const playerFilter = document.getElementById('playerFilter');
                    const playerList = document.getElementById('playerList');
                    const chunkStats = document.getElementById('chunkStats');
                    const worldSelect = document.getElementById('worldSelect');
                    const lockPlayer = document.getElementById('lockPlayer');
                    const chunkMap = document.getElementById('chunkMap');
                    const chunkMapNote = document.getElementById('chunkMapNote');
                    const mapScale = document.getElementById('mapScale');
                    const mapLegend = document.getElementById('mapLegend');
                    const trailLen = document.getElementById('trailLen');
                    const trailVal = document.getElementById('trailVal');
                    const topChunks = document.getElementById('topChunks');
                    const zoneRows = document.getElementById('zoneRows');
                    const eventList = document.getElementById('eventList');
                    const actionStatus = document.getElementById('actionStatus');

                    const configuredWsHost = "__WS_HOST__";
                    const wsHost = configuredWsHost === "0.0.0.0" ? window.location.hostname : configuredWsHost;
                    const wsUrl = `ws://${wsHost}:__WS_PORT__/live`;
                    const ws = new WebSocket(wsUrl);

                    let selectedNodeId = null;
                    let selectedWorld = null;
                    let pinnedCenter = null;
                    let selectedPlayer = null;
                    const trailByPlayer = new Map();
                    let maxTrail = 12;
                    const playerColors = new Map();

                    const parseCsv = (raw) => (raw || '').split(',').map(v => v.trim()).filter(Boolean);
                    const parseWorldChunks = (raw) => parseCsv(raw).map(entry => {
                      const [world, count] = entry.split(':');
                      return { world: world || '', count: count || '' };
                    });
                    const parsePlayerDetails = (raw) => parseCsv(raw).map(entry => {
                      const [namePart, rest] = entry.split('@');
                      const parts = (rest || '').split(':');
                      return {
                        name: namePart || '',
                        world: parts[0] || '',
                        x: parts[1] || '',
                        y: parts[2] || '',
                        z: parts[3] || '',
                        ping: parts[4] || '',
                        cx: parts[5] || '',
                        cz: parts[6] || ''
                      };
                    });

                    const colorForPlayer = (name) => {
                      if (!name) {
                        return '#7ec6ff';
                      }
                      if (playerColors.has(name)) {
                        return playerColors.get(name);
                      }
                      let hash = 0;
                      for (let i = 0; i < name.length; i++) {
                        hash = ((hash << 5) - hash) + name.charCodeAt(i);
                        hash |= 0;
                      }
                      const hue = Math.abs(hash) % 360;
                      const color = `hsl(${hue}, 70%, 60%)`;
                      playerColors.set(name, color);
                      return color;
                    };

                    const updateLegend = (players, world) => {
                      const filtered = (players || []).filter(p => !world || p.world === world);
                      if (filtered.length === 0) {
                        mapLegend.textContent = '';
                        return;
                      }
                      const unique = [];
                      const seen = new Set();
                      for (const p of filtered) {
                        if (!p.name || seen.has(p.name)) {
                          continue;
                        }
                        seen.add(p.name);
                        unique.push(p);
                      }
                      mapLegend.innerHTML = unique.map(p => {
                        const c = colorForPlayer(p.name);
                        return `<span class="pill" style="background:${c};color:#0e1621">${p.name}</span>`;
                      }).join(' ');
                    };

                    const renderHeat = (nodes) => {
                      heatList.innerHTML = '';
                      nodes.forEach((n) => {
                        const tps = Math.max(0, Math.min(20, parseFloat(n.tps||'0')));
                        const entities = Math.max(0, parseFloat(n.entities||'0'));
                        const players = Math.max(0, parseFloat(n.players||'0'));
                        const pressure = Math.min(100, (100 - (tps/20)*100) * 0.7 + Math.min(100, entities/5) * 0.2 + Math.min(100, players*2) * 0.1);
                        const block = document.createElement('div');
                        block.style.marginBottom = '10px';
                        block.innerHTML = `<div style="display:flex;justify-content:space-between;font-size:12px;margin-bottom:4px"><span>${n.nodeId||''}</span><span>${pressure.toFixed(0)}%</span></div><div class="heat"><div class="bar" style="width:${pressure}%%"></div></div>`;
                        heatList.appendChild(block);
                      });
                    };

                    const renderChunkMap = (players, world) => {
                      const ctx = chunkMap.getContext('2d');
                      ctx.clearRect(0, 0, chunkMap.width, chunkMap.height);
                      ctx.fillStyle = '#0f1c2b';
                      ctx.fillRect(0, 0, chunkMap.width, chunkMap.height);

                      const filtered = (players || []).filter(p => !world || p.world === world);
                      if (!filtered || filtered.length === 0) {
                        chunkMapNote.textContent = 'Нет позиций игроков.';
                        return;
                      }
                      chunkMapNote.textContent = '';

                      const coords = filtered.map(p => ({
                        cx: parseInt(p.cx || '0', 10),
                        cz: parseInt(p.cz || '0', 10)
                      }));
                      const avg = coords.reduce((acc, v) => ({ cx: acc.cx + v.cx, cz: acc.cz + v.cz }), { cx: 0, cz: 0 });
                      const autoCenter = { cx: Math.round(avg.cx / coords.length), cz: Math.round(avg.cz / coords.length) };
                      const center = pinnedCenter && (pinnedCenter.world === world) ? pinnedCenter : autoCenter;

                      const grid = 21;
                      const cell = Math.floor(chunkMap.width / grid);
                      const offset = Math.floor(grid / 2);
                      mapScale.textContent = `Радиус: ${offset} чанков`;

                      ctx.strokeStyle = '#1b2b3d';
                      ctx.lineWidth = 1;
                      for (let i = 0; i <= grid; i++) {
                        ctx.beginPath();
                        ctx.moveTo(i * cell, 0);
                        ctx.lineTo(i * cell, grid * cell);
                        ctx.stroke();
                        ctx.beginPath();
                        ctx.moveTo(0, i * cell);
                        ctx.lineTo(grid * cell, i * cell);
                        ctx.stroke();
                      }
                      const counts = new Map();
                      coords.forEach(p => {
                        const key = `${p.cx}:${p.cz}`;
                        counts.set(key, (counts.get(key) || 0) + 1);
                      });
                      const maxCount = Math.max(...counts.values());
                      counts.forEach((count, key) => {
                        const [cx, cz] = key.split(':').map(v => parseInt(v, 10));
                        const dx = cx - center.cx;
                        const dz = cz - center.cz;
                        if (Math.abs(dx) > offset || Math.abs(dz) > offset) {
                          return;
                        }
                        const x = (dx + offset) * cell;
                        const y = (dz + offset) * cell;
                        const intensity = Math.min(1, 0.2 + (count / maxCount) * 0.8);
                        ctx.fillStyle = `rgba(126,198,255,${intensity})`;
                        ctx.fillRect(x + 2, y + 2, cell - 4, cell - 4);
                      });
                      ctx.fillStyle = '#f7b731';
                      ctx.fillRect(offset * cell + 2, offset * cell + 2, cell - 4, cell - 4);

                      if (selectedPlayer && trailByPlayer.has(selectedPlayer)) {
                        const trail = trailByPlayer.get(selectedPlayer).filter(t => !world || t.world === world);
                        if (trail.length > 1) {
                          ctx.strokeStyle = '#f7b731';
                          ctx.lineWidth = 2;
                          ctx.beginPath();
                          trail.forEach((t, idx) => {
                            const dx = t.cx - center.cx;
                            const dz = t.cz - center.cz;
                            if (Math.abs(dx) > offset || Math.abs(dz) > offset) {
                              return;
                            }
                            const px = (dx + offset) * cell + cell / 2;
                            const py = (dz + offset) * cell + cell / 2;
                            if (idx === 0) {
                              ctx.moveTo(px, py);
                            } else {
                              ctx.lineTo(px, py);
                            }
                          });
                          ctx.stroke();
                        }
                      }

                      filtered.forEach(p => {
                        const cx = parseInt(p.cx || '0', 10);
                        const cz = parseInt(p.cz || '0', 10);
                        const dx = cx - center.cx;
                        const dz = cz - center.cz;
                        if (Math.abs(dx) > offset || Math.abs(dz) > offset) {
                          return;
                        }
                        const px = (dx + offset) * cell + cell / 2;
                        const py = (dz + offset) * cell + cell / 2;
                        ctx.fillStyle = colorForPlayer(p.name);
                        ctx.beginPath();
                        ctx.arc(px, py, 4, 0, Math.PI * 2);
                        ctx.fill();
                      });
                    };

                    const renderTopChunks = (players, world) => {
                      const filtered = (players || []).filter(p => !world || p.world === world);
                      if (filtered.length === 0) {
                        topChunks.textContent = 'Нет данных.';
                        return;
                      }
                      const counts = new Map();
                      filtered.forEach(p => {
                        const key = `${p.world}:${p.cx}:${p.cz}`;
                        counts.set(key, (counts.get(key) || 0) + 1);
                      });
                      const top = [...counts.entries()]
                        .sort((a, b) => b[1] - a[1])
                        .slice(0, 6)
                        .map(([key, count]) => {
                          const parts = key.split(':');
                          return { world: parts[0], cx: parts[1], cz: parts[2], count };
                        });
                      topChunks.innerHTML = top.map(t => {
                        return `<div class="mono" data-chunk="${t.world}:${t.cx}:${t.cz}" style="cursor:pointer">${t.world} (${t.cx}, ${t.cz}) — ${t.count}</div>`;
                      }).join(' ');
                      topChunks.querySelectorAll('[data-chunk]').forEach(el => {
                        el.addEventListener('click', () => {
                          const raw = el.getAttribute('data-chunk') || '';
                          const parts = raw.split(':');
                          if (parts.length < 3) {
                            return;
                          }
                          selectedWorld = parts[0];
                          worldSelect.value = selectedWorld;
                          pinnedCenter = { world: parts[0], cx: parseInt(parts[1], 10), cz: parseInt(parts[2], 10) };
                          renderChunkMap(filtered, selectedWorld);
                        });
                      });
                    };                    const renderZones = (zones, nodes) => {
                      zoneRows.innerHTML = '';
                      const nodeIds = nodes.map(n => n.nodeId).filter(Boolean);
                      zones.forEach(z => {
                        const tr = document.createElement('tr');
                        const select = document.createElement('select');
                        select.style.padding = '4px 6px';
                        select.style.borderRadius = '8px';
                        select.style.border = '1px solid var(--border)';
                        select.style.background = '#0f1c2b';
                        select.style.color = 'var(--text)';
                        nodeIds.forEach(id => {
                          const opt = document.createElement('option');
                          opt.value = id;
                          opt.textContent = id;
                          if (id === z.nodeId) {
                            opt.selected = true;
                          }
                          select.appendChild(opt);
                        });
                        const btn = document.createElement('button');
                        btn.className = 'btn';
                        btn.textContent = 'Переназначить';
                        btn.addEventListener('click', async () => {
                          const target = select.value;
                          btn.disabled = true;
                          actionStatus.textContent = 'Отправка запроса…';
                          try {
                            const res = await fetch(`/api/reassign?zoneId=${encodeURIComponent(z.zoneId)}&targetNodeId=${encodeURIComponent(target)}`);
                            actionStatus.textContent = res.ok ? 'Запрос принят.' : 'Не удалось переназначить.';
                          } catch (e) {
                            actionStatus.textContent = 'Ошибка запроса.';
                          } finally {
                            btn.disabled = false;
                          }
                        });
                        tr.innerHTML = `<td>${z.zoneId}</td><td>${z.nodeId}</td>`;
                        const td = document.createElement('td');
                        td.appendChild(select);
                        td.appendChild(btn);
                        td.className = 'row';
                        td.style.gap = '6px';
                        tr.appendChild(td);
                        zoneRows.appendChild(tr);
                      });
                    };

                    const renderEvents = (events) => {
                      if (!events || events.length === 0) {
                        eventList.textContent = 'Пока пусто.';
                        return;
                      }
                      const list = events.slice(0, 20).map(e => {
                        const ts = parseInt(e.ts || '0', 10);
                        const time = ts ? new Date(ts).toLocaleTimeString('ru-RU') : '—';
                        return `<div class="mono">${time} · зона ${e.zoneId || '?'}: ${e.sourceNodeId || '?'} → ${e.targetNodeId || '?'} (${e.reason || 'без причины'})</div>`;
                      });
                      eventList.innerHTML = list.join('');
                    };

                    const renderSelected = (node) => {
                      if (!node) {
                        nodeDetails.textContent = "Кликните по узлу для деталей.";
                        playerList.textContent = "Нет данных.";
                        chunkStats.textContent = "Нет данных.";
                        return;
                      }
                      const sinceMs = Date.now() - (parseInt(node.lastHeartbeatMillis || '0', 10) || 0);
                      nodeDetails.innerHTML =
                        `<div class="pill">${node.nodeId || ''}</div><div class="pill">${node.zoneId || ''}</div>` +
                        `<div class="mono">TPS: ${node.tps || ""} | Игроки: ${node.players || ""} | Сущности: ${node.entities || ""} | Чанки: ${node.loadedChunks || ""}</div>` +
                        `<div class="mono">Heartbeat: ${Math.max(0, Math.floor(sinceMs / 1000))}s назад</div>`;

                      const details = parsePlayerDetails(node.playerDetails);
                      if (lockPlayer.checked && selectedPlayer) {
                        const stillOnline = details.some(p => p.name === selectedPlayer);
                        if (!stillOnline) {
                          selectedPlayer = null;
                        }
                      }
                      details.forEach(p => {
                        if (!p.name) {
                          return;
                        }
                        const trail = trailByPlayer.get(p.name) || [];
                        trail.push({
                          world: p.world,
                          cx: parseInt(p.cx || '0', 10),
                          cz: parseInt(p.cz || '0', 10)
                        });
                        while (trail.length > maxTrail) {
                          trail.shift();
                        }
                        trailByPlayer.set(p.name, trail);
                      });
                      const worlds = [...new Set(details.map(p => p.world).filter(Boolean))];
                      if (!selectedWorld || !worlds.includes(selectedWorld)) {
                        selectedWorld = worlds[0] || '';
                      }
                      worldSelect.innerHTML = worlds.map(w => `<option value="${w}">${w}</option>`).join('');
                      worldSelect.value = selectedWorld || '';
                      const filter = (playerFilter.value || '').toLowerCase();
                      const filtered = details.filter(p => !filter || (p.name || '').toLowerCase().includes(filter));
                      if (filtered.length === 0) {
                        playerList.textContent = "Нет игроков онлайн.";
                      } else {
                        playerList.innerHTML = filtered.map(p => {
                          const color = colorForPlayer(p.name);
                          const active = selectedPlayer && p.name === selectedPlayer;
                          return `<div class="mono" data-player="${p.name}" style="cursor:pointer${active ? ';background:#22364d' : ''}"><span class="pill" style="background:${color};color:#0e1621">${p.name}</span> ${p.ping}ms | ${p.world} (${p.x}, ${p.y}, ${p.z})</div>`;
                        }).join('');
                        playerList.querySelectorAll('[data-player]').forEach(el => {
                          el.addEventListener('click', () => {
                            const name = el.getAttribute('data-player');
                            const target = details.find(p => p.name === name);
                            if (!target) {
                              return;
                            }
                            selectedWorld = target.world;
                            worldSelect.value = selectedWorld;
                            pinnedCenter = { world: target.world, cx: parseInt(target.cx || '0', 10), cz: parseInt(target.cz || '0', 10) };
                            selectedPlayer = target.name;
                            renderChunkMap(details, selectedWorld);
                            renderTopChunks(details, selectedWorld);
                            updateLegend(details, selectedWorld);
                          });
                        });
                      }
                      renderChunkMap(details, selectedWorld);
                      renderTopChunks(details, selectedWorld);
                      updateLegend(details, selectedWorld);

                      const worldStats = parseWorldChunks(node.worldChunks);
                      if (worldStats.length === 0) {
                        chunkStats.textContent = "Нет данных по мирам.";
                      } else {
                        chunkStats.innerHTML = worldStats.map(w => `<div class="mono">${w.world}: ${w.count}</div>`).join('');
                      }
                    };

                    ws.onopen = () => wsState.textContent = 'WebSocket: подключено';
                    ws.onclose = () => wsState.textContent = 'WebSocket: отключено';
                    ws.onerror = () => wsState.textContent = 'WebSocket: ошибка';
                    ws.onmessage = (event) => {
                      const payload = JSON.parse(event.data);
                      window.__lastPayload = payload;
                      const nodes = payload.nodes || [];
                      rows.innerHTML = '';
                      if (!selectedNodeId && nodes.length > 0) {
                        selectedNodeId = nodes[0].nodeId || null;
                      }

                      nodes.forEach((n) => {
                        const tr = document.createElement('tr');
                        tr.dataset.nodeId = n.nodeId || '';
                        tr.className = (n.nodeId === selectedNodeId) ? 'active' : '';
                        tr.innerHTML = `<td>${n.nodeId||''}</td><td>${n.zoneId||''}</td><td>${n.tps||''}</td><td>${n.players||''}</td><td>${n.entities||''}</td><td>${n.loadedChunks||''}</td>`;
                        tr.addEventListener('click', () => {
                          selectedNodeId = n.nodeId || null;
                          renderSelected(n);
                          [...rows.querySelectorAll('tr')].forEach(row => row.classList.toggle('active', row.dataset.nodeId === selectedNodeId));
                        });
                        rows.appendChild(tr);
                      });

                      renderHeat(nodes);
                      renderZones(payload.zones || [], nodes);
                      renderEvents(payload.events || []);

                      const selected = nodes.find(n => n.nodeId === selectedNodeId) || null;
                      renderSelected(selected);
                    };

                    playerFilter.addEventListener('input', () => {
                      const payload = window.__lastPayload || { nodes: [] };
                      const node = (payload.nodes || []).find(n => n.nodeId === selectedNodeId) || null;
                      renderSelected(node);
                    });
                    worldSelect.addEventListener('change', () => {
                      selectedWorld = worldSelect.value || '';
                      pinnedCenter = null;
                      if (!lockPlayer.checked) {
                        selectedPlayer = null;
                      }
                      const payload = window.__lastPayload || { nodes: [] };
                      const node = (payload.nodes || []).find(n => n.nodeId === selectedNodeId) || null;
                      renderSelected(node);
                    });
                    lockPlayer.addEventListener('change', () => {
                      if (!lockPlayer.checked) {
                        selectedPlayer = null;
                      }
                    });
                    trailLen.addEventListener('input', () => {
                      maxTrail = Math.max(3, parseInt(trailLen.value || '12', 10));
                      trailVal.textContent = String(maxTrail);
                      const payload = window.__lastPayload || { nodes: [] };
                      const node = (payload.nodes || []).find(n => n.nodeId === selectedNodeId) || null;
                      renderSelected(node);
                    });
                    trailVal.textContent = String(maxTrail);
                    window.__lastPayload = { nodes: [] };
                  </script>
                </body>
                </html>
                """
                .replace("__WS_HOST__", websocketHostForUi)
                .replace("__WS_PORT__", String.valueOf(websocketPortForUi));
    }

    private void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        sendText(exchange, status, body, "application/json; charset=utf-8");
    }

    private void sendText(HttpExchange exchange, int status, String body, String contentType) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static String escape(String raw) {
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
