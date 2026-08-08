const express = require('express');
const path = require('path');
const { exec, spawn } = require('child_process');
const fs = require('fs');

const app = express();
const PORT = 3000;

app.use(express.json());
app.use(express.static(path.join(__dirname, 'public')));

// Mock/In-Memory state for player lists
const playerLists = {
  whitelist: [
    { username: '2b2t_Legend', uuid: 'e28a514d-178d-4e4b-9e4a-81a1a5b82100', addedAt: Date.now() - 86400000 },
    { username: 'ZenithAdmin', uuid: 'a1b2c3d4-e5f6-7890-1234-567890abcdef', addedAt: Date.now() - 172800000 }
  ],
  friendsList: [
    { username: '2b2t_Legend', uuid: 'e28a514d-178d-4e4b-9e4a-81a1a5b82100', addedAt: Date.now() - 86400000 },
    { username: 'PvPMaster99', uuid: 'f47ac10b-58cc-4372-a567-0e02b2c3d4e5', addedAt: Date.now() - 43200000 }
  ],
  enemyList: [
    { username: 'GrieferBot', uuid: '11111111-2222-3333-4444-555555555555', addedAt: Date.now() - 21600000 }
  ],
  stalkList: [],
  ignoreList: [
    { username: 'SpamBot_3000', uuid: '99999999-8888-7777-6666-555555555555', addedAt: Date.now() - 10800000 }
  ],
  spectatorWhitelist: [
    { username: 'ZenithAdmin', uuid: 'a1b2c3d4-e5f6-7890-1234-567890abcdef', addedAt: Date.now() - 172800000 }
  ]
};

const moduleConfig = {
  killAura: { enabled: true, targetPlayers: true, enemyListMode: false },
  visualRange: { enabled: true, announceToChat: true },
  autoDisconnect: { enabled: true, enemyListMode: true },
  spawnPatrol: { enabled: false, ignoreFriends: true, targetOnlyBedrock: false }
};

let logs = [
  `[${new Date().toISOString()}] [INFO] [ZenithProxy] Starting ZenithProxy v26.1.2...`,
  `[${new Date().toISOString()}] [INFO] [PlayerListsManager] Initializing player list configurations...`,
  `[${new Date().toISOString()}] [INFO] [PlayerList] Initialized player matching with Username-First priority rule.`,
  `[${new Date().toISOString()}] [INFO] [ProxyServerListener] Proxy server initialized and ready.`
];

// API Endpoints
app.get('/api/status', (req, res) => {
  res.json({
    status: 'ONLINE',
    version: '26.1.2',
    uptimeSeconds: Math.floor(process.uptime()),
    matchingMode: 'Username-First (Fallback to UUID)',
    memoryUsage: `${Math.round(process.memoryUsage().heapUsed / 1024 / 1024)}MB / ${Math.round(process.memoryUsage().heapTotal / 1024 / 1024)}MB`
  });
});

app.get('/api/player-lists', (req, res) => {
  res.json(playerLists);
});

app.post('/api/player-lists/:list/add', (req, res) => {
  const { list } = req.params;
  const { username, uuid } = req.body;
  
  if (!playerLists[list]) {
    return res.status(400).json({ error: 'Invalid player list' });
  }
  
  if (!username && !uuid) {
    return res.status(400).json({ error: 'Username or UUID is required' });
  }

  // Username-first check: matching by username first
  const existing = playerLists[list].find(entry => 
    (username && entry.username && entry.username.toLowerCase() === username.toLowerCase()) ||
    (!username && uuid && entry.uuid === uuid)
  );

  if (existing) {
    return res.status(400).json({ error: `Player '${username || uuid}' already exists in ${list}` });
  }

  const newEntry = {
    username: username || 'Unknown',
    uuid: uuid || null,
    addedAt: Date.now()
  };

  playerLists[list].push(newEntry);
  logs.push(`[${new Date().toISOString()}] [INFO] [PlayerLists] Added '${username || uuid}' to ${list} (Checked username first, UUID fallback).`);
  
  res.json({ success: true, list: playerLists[list] });
});

app.post('/api/player-lists/:list/remove', (req, res) => {
  const { list } = req.params;
  const { username, uuid } = req.body;

  if (!playerLists[list]) {
    return res.status(400).json({ error: 'Invalid player list' });
  }

  const initialLength = playerLists[list].length;
  playerLists[list] = playerLists[list].filter(entry => {
    if (username && entry.username) {
      return entry.username.toLowerCase() !== username.toLowerCase();
    }
    if (uuid && entry.uuid) {
      return entry.uuid !== uuid;
    }
    return true;
  });

  if (playerLists[list].length < initialLength) {
    logs.push(`[${new Date().toISOString()}] [INFO] [PlayerLists] Removed '${username || uuid}' from ${list}.`);
    res.json({ success: true, list: playerLists[list] });
  } else {
    res.status(404).json({ error: 'Player not found in list' });
  }
});

app.get('/api/modules', (req, res) => {
  res.json(moduleConfig);
});

app.post('/api/modules/toggle', (req, res) => {
  const { moduleName } = req.body;
  if (moduleConfig[moduleName]) {
    moduleConfig[moduleName].enabled = !moduleConfig[moduleName].enabled;
    logs.push(`[${new Date().toISOString()}] [INFO] [Modules] ${moduleName} toggled to ${moduleConfig[moduleName].enabled ? 'ENABLED' : 'DISABLED'}`);
    res.json({ success: true, module: moduleConfig[moduleName] });
  } else {
    res.status(400).json({ error: 'Unknown module' });
  }
});

app.get('/api/logs', (req, res) => {
  res.json(logs);
});

// Main Web Dashboard HTML
app.get('/', (req, res) => {
  res.send(`
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>ZenithProxy Dashboard</title>
  <script src="https://cdn.tailwindcss.com"></script>
  <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
  <style>
    @import url('https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500&display=swap');
    body { font-family: 'Plus Jakarta Sans', sans-serif; }
    code, pre { font-family: 'JetBrains Mono', monospace; }
  </style>
</head>
<body class="bg-slate-950 text-slate-100 min-h-screen">
  <!-- Top Navigation Bar -->
  <header class="border-b border-slate-800 bg-slate-900/80 backdrop-blur sticky top-0 z-50">
    <div class="max-w-7xl mx-auto px-6 h-16 flex items-center justify-between">
      <div class="flex items-center gap-3">
        <div class="w-9 h-9 rounded-xl bg-gradient-to-tr from-indigo-500 to-purple-500 flex items-center justify-center font-bold text-white shadow-lg shadow-indigo-500/20">
          Z
        </div>
        <div>
          <h1 class="font-bold text-lg leading-none tracking-tight">ZenithProxy</h1>
          <p class="text-xs text-slate-400 mt-0.5">Control Center & Player Management</p>
        </div>
      </div>
      <div class="flex items-center gap-4">
        <div class="flex items-center gap-2 px-3 py-1.5 rounded-full bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 text-xs font-medium">
          <span class="w-2 h-2 rounded-full bg-emerald-400 animate-pulse"></span>
          Proxy Running (v26.1.2)
        </div>
      </div>
    </div>
  </header>

  <main class="max-w-7xl mx-auto px-6 py-8 space-y-8">
    <!-- Hero Status Banner -->
    <div class="grid grid-cols-1 md:grid-cols-4 gap-4">
      <div class="bg-slate-900 border border-slate-800 rounded-2xl p-5 shadow-sm">
        <div class="text-xs text-slate-400 font-medium">Status</div>
        <div class="text-xl font-bold text-emerald-400 mt-1 flex items-center gap-2">
          <i class="fa-solid fa-circle-check text-sm"></i> Online
        </div>
        <div class="text-xs text-slate-500 mt-2">Java 25 Runtime</div>
      </div>
      <div class="bg-slate-900 border border-slate-800 rounded-2xl p-5 shadow-sm">
        <div class="text-xs text-slate-400 font-medium">Player Match Priority</div>
        <div class="text-lg font-semibold text-indigo-400 mt-1">Username-First</div>
        <div class="text-xs text-slate-500 mt-2">Fallback to UUID if empty</div>
      </div>
      <div class="bg-slate-900 border border-slate-800 rounded-2xl p-5 shadow-sm">
        <div class="text-xs text-slate-400 font-medium">Active Modules</div>
        <div class="text-xl font-bold text-purple-400 mt-1" id="activeModulesCount">3 / 4</div>
        <div class="text-xs text-slate-500 mt-2">KillAura, VisualRange, AutoDisconnect</div>
      </div>
      <div class="bg-slate-900 border border-slate-800 rounded-2xl p-5 shadow-sm">
        <div class="text-xs text-slate-400 font-medium">Memory Usage</div>
        <div class="text-xl font-bold text-slate-200 mt-1" id="memoryUsage">Loading...</div>
        <div class="text-xs text-slate-500 mt-2">JVM Memory Allocation</div>
      </div>
    </div>

    <!-- Main Grid: Player Lists & Modules -->
    <div class="grid grid-cols-1 lg:grid-cols-3 gap-8">
      <!-- Left Column: Player Lists Manager (2 cols) -->
      <div class="lg:col-span-2 space-y-6">
        <div class="bg-slate-900 border border-slate-800 rounded-2xl p-6 shadow-sm">
          <div class="flex items-center justify-between pb-4 border-b border-slate-800">
            <div>
              <h2 class="text-lg font-bold text-slate-100">Player Lists Management</h2>
              <p class="text-xs text-slate-400 mt-1">Checks prioritize username matching first, falling back to UUID.</p>
            </div>
            <select id="listSelect" onchange="loadPlayerList()" class="bg-slate-800 border border-slate-700 text-slate-200 text-sm rounded-xl px-4 py-2 font-medium focus:outline-none focus:border-indigo-500">
              <option value="whitelist">Whitelist</option>
              <option value="friendsList">Friends List</option>
              <option value="enemyList">Enemy List</option>
              <option value="ignoreList">Ignore List</option>
              <option value="spectatorWhitelist">Spectator Whitelist</option>
              <option value="stalkList">Stalk List</option>
            </select>
          </div>

          <!-- Add Player Form -->
          <form onsubmit="addPlayer(event)" class="mt-6 flex gap-3">
            <input type="text" id="usernameInput" placeholder="Player Username (e.g. 2b2t_Legend)" class="flex-1 bg-slate-800/60 border border-slate-700/80 rounded-xl px-4 py-2.5 text-sm text-slate-100 placeholder-slate-500 focus:outline-none focus:border-indigo-500">
            <input type="text" id="uuidInput" placeholder="Optional UUID" class="w-1/3 bg-slate-800/60 border border-slate-700/80 rounded-xl px-4 py-2.5 text-sm text-slate-100 placeholder-slate-500 focus:outline-none focus:border-indigo-500">
            <button type="submit" class="bg-indigo-600 hover:bg-indigo-500 text-white font-medium text-sm px-5 py-2.5 rounded-xl transition flex items-center gap-2">
              <i class="fa-solid fa-plus text-xs"></i> Add
            </button>
          </form>

          <!-- List Table -->
          <div class="mt-6 overflow-x-auto">
            <table class="w-full text-left text-sm">
              <thead class="text-xs text-slate-400 uppercase bg-slate-950/50 rounded-lg">
                <tr>
                  <th class="px-4 py-3 font-semibold">Username</th>
                  <th class="px-4 py-3 font-semibold">UUID</th>
                  <th class="px-4 py-3 font-semibold text-right">Actions</th>
                </tr>
              </thead>
              <tbody id="playerTableBody" class="divide-y divide-slate-800/60">
                <!-- Rows injected dynamically -->
              </tbody>
            </table>
          </div>
        </div>
      </div>

      <!-- Right Column: Module Controls & Logs (1 col) -->
      <div class="space-y-6">
        <!-- Module Toggles -->
        <div class="bg-slate-900 border border-slate-800 rounded-2xl p-6 shadow-sm">
          <h2 class="text-lg font-bold text-slate-100 pb-4 border-b border-slate-800">Module Status</h2>
          <div class="mt-4 space-y-4" id="moduleToggles">
            <!-- Toggles injected dynamically -->
          </div>
        </div>

        <!-- Live Proxy Logs -->
        <div class="bg-slate-900 border border-slate-800 rounded-2xl p-6 shadow-sm">
          <div class="flex items-center justify-between pb-3 border-b border-slate-800">
            <h2 class="text-md font-bold text-slate-100">Live Proxy Logs</h2>
            <button onclick="fetchLogs()" class="text-xs text-indigo-400 hover:text-indigo-300">Refresh</button>
          </div>
          <div class="mt-3 bg-slate-950 border border-slate-800 rounded-xl p-3 h-48 overflow-y-auto text-xs text-slate-300 font-mono space-y-1" id="logContainer">
            <!-- Logs injected dynamically -->
          </div>
        </div>
      </div>
    </div>
  </main>

  <script>
    let currentLists = {};

    async function fetchStatus() {
      const res = await fetch('/api/status');
      const data = await res.json();
      document.getElementById('memoryUsage').innerText = data.memoryUsage;
    }

    async function loadPlayerList() {
      const res = await fetch('/api/player-lists');
      currentLists = await res.json();
      renderTable();
    }

    function renderTable() {
      const selectedList = document.getElementById('listSelect').value;
      const entries = currentLists[selectedList] || [];
      const tbody = document.getElementById('playerTableBody');

      if (entries.length === 0) {
        tbody.innerHTML = \`<tr><td colspan="3" class="px-4 py-6 text-center text-slate-500">No players in this list.</td></tr>\`;
        return;
      }

      tbody.innerHTML = entries.map(entry => \`
        <tr class="hover:bg-slate-800/30 transition">
          <td class="px-4 py-3 font-medium text-slate-200">\${entry.username || 'N/A'}</td>
          <td class="px-4 py-3 font-mono text-xs text-slate-400">\${entry.uuid || 'None'}</td>
          <td class="px-4 py-3 text-right">
            <button onclick="removePlayer('\${selectedList}', '\${entry.username}', '\${entry.uuid}')" class="text-xs text-rose-400 hover:text-rose-300 transition">
              <i class="fa-solid fa-trash"></i> Remove
            </button>
          </td>
        </tr>
      \`).join('');
    }

    async function addPlayer(e) {
      e.preventDefault();
      const list = document.getElementById('listSelect').value;
      const username = document.getElementById('usernameInput').value.trim();
      const uuid = document.getElementById('uuidInput').value.trim();

      if (!username && !uuid) return;

      const res = await fetch(\`/api/player-lists/\${list}/add\`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, uuid })
      });

      if (res.ok) {
        document.getElementById('usernameInput').value = '';
        document.getElementById('uuidInput').value = '';
        loadPlayerList();
        fetchLogs();
      } else {
        const err = await res.json();
        alert(err.error || 'Failed to add player');
      }
    }

    async function removePlayer(list, username, uuid) {
      const res = await fetch(\`/api/player-lists/\${list}/remove\`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, uuid })
      });

      if (res.ok) {
        loadPlayerList();
        fetchLogs();
      }
    }

    async function loadModules() {
      const res = await fetch('/api/modules');
      const modules = await res.json();
      const container = document.getElementById('moduleToggles');
      
      let activeCount = 0;
      container.innerHTML = Object.keys(modules).map(key => {
        const mod = modules[key];
        if (mod.enabled) activeCount++;
        return \`
          <div class="flex items-center justify-between p-3 rounded-xl bg-slate-950 border border-slate-800">
            <div>
              <div class="font-semibold text-sm text-slate-200 capitalize">\${key}</div>
              <div class="text-xs text-slate-500">\${mod.enabled ? 'Active' : 'Disabled'}</div>
            </div>
            <button onclick="toggleModule('\${key}')" class="px-3 py-1.5 rounded-lg text-xs font-semibold transition \${mod.enabled ? 'bg-indigo-600 text-white hover:bg-indigo-500' : 'bg-slate-800 text-slate-400 hover:bg-slate-700'}\">
              \${mod.enabled ? 'Enabled' : 'Disabled'}
            </button>
          </div>
        \`;
      }).join('');

      document.getElementById('activeModulesCount').innerText = \`\${activeCount} / \${Object.keys(modules).length}\`;
    }

    async function toggleModule(moduleName) {
      await fetch('/api/modules/toggle', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ moduleName })
      });
      loadModules();
      fetchLogs();
    }

    async function fetchLogs() {
      const res = await fetch('/api/logs');
      const logData = await res.json();
      const logContainer = document.getElementById('logContainer');
      logContainer.innerHTML = logData.map(l => \`<div>\${l}</div>\`).join('');
      logContainer.scrollTop = logContainer.scrollHeight;
    }

    // Initial load
    fetchStatus();
    loadPlayerList();
    loadModules();
    fetchLogs();
    setInterval(fetchStatus, 5000);
  </script>
</body>
</html>
  `);
});

app.listen(PORT, () => {
  console.log(`ZenithProxy Control Server listening on port ${PORT}`);
});
