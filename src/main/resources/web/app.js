// CROWN Web Manager App
const urlParams = new URLSearchParams(window.location.search);
const AUTH_TOKEN = urlParams.get('token') || 'secret';
const ADMIN_UUID = urlParams.get('adminUuid');
const ADMIN_NAME = urlParams.get('adminName') || 'WebAdmin';

const API_BASE = '/api';
let currentPage = 'dashboard';

// Initial Load
document.addEventListener('DOMContentLoaded', () => {
    updateAdminProfile();
    showPage('dashboard');
    createToastContainer();
});

// Toast Notification System
function createToastContainer() {
    if (!document.getElementById('toast-container')) {
        const container = document.createElement('div');
        container.id = 'toast-container';
        container.className = 'fixed top-4 right-4 z-50 space-y-2';
        document.body.appendChild(container);
    }
}

function showToast(message, type = 'info', duration = 4000) {
    const container = document.getElementById('toast-container');
    const toast = document.createElement('div');

    const colors = {
        success: 'bg-green-600 border-green-500',
        error: 'bg-red-600 border-red-500',
        warning: 'bg-yellow-600 border-yellow-500',
        info: 'bg-blue-600 border-blue-500'
    };

    const icons = {
        success: 'check-circle',
        error: 'x-circle',
        warning: 'alert-triangle',
        info: 'info'
    };

    toast.className = `${colors[type]} border-l-4 p-4 rounded-lg shadow-lg flex items-center space-x-3 min-w-[300px] animate-slide-in`;
    toast.innerHTML = `
        <i data-lucide="${icons[type]}" class="w-5 h-5 text-white"></i>
        <span class="text-white text-sm font-medium">${message}</span>
    `;

    container.appendChild(toast);
    lucide.createIcons();

    setTimeout(() => {
        toast.classList.add('animate-fade-out');
        setTimeout(() => toast.remove(), 300);
    }, duration);
}

function updateAdminProfile() {
    const profileContainer = document.querySelector('.sidebar .p-4');
    if (profileContainer) {
        const skinUrl = ADMIN_UUID ? `https://mc-heads.net/avatar/${ADMIN_UUID}/32` : `https://mc-heads.net/avatar/steve/32`;
        profileContainer.innerHTML = `
            <div class="flex items-center space-x-3 p-2">
                <img src="${skinUrl}" class="w-8 h-8 rounded shadow-sm">
                <div>
                    <p class="text-sm font-medium">${ADMIN_NAME}</p>
                    <p class="text-xs text-green-500">Connected</p>
                </div>
            </div>
        `;
    }
}

async function apiFetch(endpoint, options = {}) {
    const headers = {
        'Authorization': `Bearer ${AUTH_TOKEN}`,
        'Content-Type': 'application/json',
        ...options.headers
    };
    try {
        const response = await fetch(`${API_BASE}${endpoint}`, { ...options, headers });
        if (response.status === 401) {
            alert('Unauthorized! Check your token.');
            return null;
        }
        return response.json();
    } catch (e) {
        console.error('Fetch error:', e);
        return null;
    }
}

function showPage(page) {
    currentPage = page;

    // Update nav active state
    document.querySelectorAll('.nav-item').forEach(el => {
        el.classList.remove('bg-slate-800', 'text-white');
        el.classList.add('text-slate-400');
        if (el.getAttribute('onclick').includes(`'${page}'`)) {
            el.classList.add('bg-slate-800', 'text-white');
            el.classList.remove('text-slate-400');
        }
    });

    const content = document.getElementById('main-content');
    content.innerHTML = '<div class="flex items-center justify-center h-full"><div class="animate-spin rounded-full h-12 w-12 border-b-2 border-orange-500"></div></div>';

    switch (page) {
        case 'dashboard': renderDashboard(); break;
        case 'punishments': renderPunishments(); break;
        case 'reports': renderReports(); break;
        case 'moderators': renderModerators(); break;
        default: content.innerHTML = `<h2 class="text-2xl font-bold">${page.charAt(0).toUpperCase() + page.slice(1)} coming soon...</h2>`;
    }
}

async function renderDashboard() {
    const punishments = await apiFetch('/punishments?limit=5');
    const content = document.getElementById('main-content');

    content.innerHTML = `
        <div class="grid grid-cols-1 md:grid-cols-3 gap-6 mb-8">
            <div class="card p-6 rounded-xl shadow-lg border-l-4 border-orange-500">
                <div class="flex items-center justify-between mb-4">
                    <h3 class="text-slate-400 font-medium">Punishments (Recent)</h3>
                    <i data-lucide="gavel" class="w-5 h-5 text-orange-500"></i>
                </div>
                <p class="text-3xl font-bold">${punishments?.length || 0}</p>
                <p class="text-xs text-slate-400 mt-2">Latest entries in history</p>
            </div>
            <div class="card p-6 rounded-xl shadow-lg border-l-4 border-red-500">
                <div class="flex items-center justify-between mb-4">
                    <h3 class="text-slate-400 font-medium">Pending Reports</h3>
                    <i data-lucide="flag" class="w-5 h-5 text-red-500"></i>
                </div>
                <p class="text-3xl font-bold" id="pending-reports-count">...</p>
                <p class="text-xs text-red-400 mt-2 flex items-center">
                    <i data-lucide="alert-circle" class="w-3 h-3 mr-1"></i> Needs verification
                </p>
            </div>
            <div class="card p-6 rounded-xl shadow-lg border-l-4 border-blue-500">
                <div class="flex items-center justify-between mb-4">
                    <h3 class="text-slate-400 font-medium">Server Status</h3>
                    <i data-lucide="server" class="w-5 h-5 text-blue-500"></i>
                </div>
                <p class="text-3xl font-bold text-green-500">Online</p>
                <p class="text-xs text-slate-400 mt-2">Web Manager connected</p>
            </div>
        </div>

        <div class="grid grid-cols-1 lg:grid-cols-2 gap-8">
            <div class="card p-6 rounded-xl shadow-lg">
                <h3 class="text-lg font-bold mb-6 flex items-center"><i data-lucide="bar-chart-3" class="w-5 h-5 mr-2 accent"></i> Punishment Types Distribution</h3>
                <canvas id="punishmentChart" height="200"></canvas>
            </div>
            <div class="card p-6 rounded-xl shadow-lg">
                <h3 class="text-lg font-bold mb-6 flex items-center"><i data-lucide="clock" class="w-5 h-5 mr-2 accent"></i> Recent Activity</h3>
                <div class="space-y-4">
                    ${punishments?.map(p => `
                        <div class="flex items-center justify-between p-3 bg-slate-800/50 hover:bg-slate-800 rounded-lg transition border border-slate-700 cursor-pointer" onclick="viewPunishmentDetails('${p.punishmentId}')">
                            <div class="flex items-center space-x-3">
                                <img src="https://mc-heads.net/avatar/${p.playerUUID}/32" class="w-8 h-8 rounded shadow-sm">
                                <div>
                                    <p class="text-sm font-medium"><span class="text-orange-400 uppercase text-xs font-bold mr-1">${p.type}</span> on <span class="text-slate-200">${p.playerName || 'Unknown'}</span></p>
                                    <p class="text-xs text-slate-500">${new Date(p.timestamp).toLocaleString()}</p>
                                </div>
                            </div>
                            <div class="text-right">
                                <p class="text-xs font-mono text-slate-500">#${p.punishmentId}</p>
                                <i data-lucide="chevron-right" class="w-4 h-4 text-slate-600 inline"></i>
                            </div>
                        </div>
                    `).join('') || '<p class="text-slate-500">No recent activity</p>'}
                </div>
                <button onclick="showPage('punishments')" class="w-full mt-6 py-2 text-sm text-slate-400 hover:text-white transition">View all punishments</button>
            </div>
        </div>
    `;

    lucide.createIcons();
    initDashboardChart(punishments);

    // Fetch real report count
    const reports = await apiFetch('/reports?status=OPEN');
    const reportsCount = document.getElementById('pending-reports-count');
    if (reportsCount) reportsCount.innerText = reports?.length || 0;
}

function initDashboardChart(punishments) {
    if (!punishments || punishments.length === 0) return;
    const ctx = document.getElementById('punishmentChart');
    if (!ctx) return;

    const counts = {};
    punishments.forEach(p => counts[p.type] = (counts[p.type] || 0) + 1);

    new Chart(ctx, {
        type: 'doughnut',
        data: {
            labels: Object.keys(counts),
            datasets: [{
                data: Object.values(counts),
                backgroundColor: ['#ff801a', '#3b82f6', '#ef4444', '#10b981', '#f59e0b', '#8b5cf6'],
                borderWidth: 0
            }]
        },
        options: {
            responsive: true,
            plugins: {
                legend: { position: 'bottom', labels: { color: '#94a3b8' } }
            },
            cutout: '70%'
        }
    });
}

async function renderPunishments() {
    const punishments = await apiFetch('/punishments?limit=50');
    const content = document.getElementById('main-content');

    content.innerHTML = `
        <div class="flex justify-between items-center mb-6">
            <h2 class="text-2xl font-bold">Punishment History</h2>
            <div class="flex space-x-2">
                <button onclick="renderPunishments()" class="bg-slate-700 hover:bg-slate-600 px-3 py-2 rounded-lg text-sm flex items-center space-x-2 transition">
                    <i data-lucide="refresh-cw" class="w-4 h-4"></i>
                    <span>Refresh</span>
                </button>
                <select id="filter-type" class="bg-slate-800 border border-slate-700 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-1 focus:ring-orange-500">
                    <option value="">All Types</option>
                    <option value="ban">Ban</option>
                    <option value="mute">Mute</option>
                    <option value="softban">SoftBan</option>
                    <option value="warn">Warn</option>
                    <option value="kick">Kick</option>
                    <option value="freeze">Freeze</option>
                </select>
            </div>
        </div>

        <div class="card rounded-xl overflow-hidden">
            <table class="w-full text-left">
                <thead class="bg-slate-800 text-slate-400 text-xs uppercase font-bold">
                    <tr>
                        <th class="px-6 py-4">Target</th>
                        <th class="px-6 py-4">Type</th>
                        <th class="px-6 py-4">Reason</th>
                        <th class="px-6 py-4">Status</th>
                        <th class="px-6 py-4">Duration</th>
                        <th class="px-6 py-4">Method</th>
                        <th class="px-6 py-4">Moderator</th>
                        <th class="px-6 py-4">Date</th>
                        <th class="px-6 py-4 text-right">Action</th>
                    </tr>
                </thead>
                <tbody class="divide-y divide-slate-700">
                    ${punishments?.map(p => `
                        <tr class="hover:bg-slate-800/50 transition cursor-pointer" onclick="viewPunishmentDetails('${p.punishmentId}')">
                            <td class="px-6 py-4">
                                <div class="flex items-center space-x-3">
                                    <img src="https://mc-heads.net/avatar/${p.playerUUID}/24" class="w-6 h-6 rounded">
                                    <span class="text-sm font-medium">${p.playerName || 'Unknown'}</span>
                                </div>
                            </td>
                            <td class="px-6 py-4">
                                <span class="px-2 py-1 rounded text-[10px] font-bold uppercase ${getPunishTypeColor(p.type)}">
                                    ${p.type}
                                </span>
                            </td>
                            <td class="px-6 py-4 text-sm text-slate-400 max-w-xs truncate">${p.reason}</td>
                            <td class="px-6 py-4">
                                <span class="px-2 py-1 rounded text-[10px] font-bold ${getStatusColorForType(p.type, p.status, p.active)}">
                                    ${getStatusForType(p.type, p.status, p.active)}
                                </span>
                            </td>
                            <td class="px-6 py-4 text-sm text-slate-400">${p.durationString || 'N/A'}</td>
                            <td class="px-6 py-4">
                                <span class="px-2 py-1 rounded text-[10px] font-bold uppercase ${p.byIp ? 'bg-purple-500/20 text-purple-400' : 'bg-slate-500/20 text-slate-400'}">
                                    ${p.byIp ? 'IP' : 'Local'}
                                </span>
                            </td>
                            <td class="px-6 py-4 text-sm">${p.punisherName}</td>
                            <td class="px-6 py-4 text-xs text-slate-500">${new Date(p.timestamp).toLocaleDateString()}</td>
                            <td class="px-6 py-4 text-right">
                                <button class="p-2 hover:bg-slate-700 rounded transition">
                                    <i data-lucide="external-link" class="w-4 h-4 text-slate-400"></i>
                                </button>
                            </td>
                        </tr>
                    `).join('') || '<tr><td colspan="9" class="px-6 py-10 text-center text-slate-500">No punishments found</td></tr>'}
                </tbody>
            </table>
        </div>
    `;
    lucide.createIcons();
}

function getPunishTypeColor(type) {
    switch (type.toLowerCase()) {
        case 'ban': return 'bg-red-500/20 text-red-500';
        case 'mute': return 'bg-orange-500/20 text-orange-500';
        case 'warn': return 'bg-yellow-500/20 text-yellow-500';
        case 'softban': return 'bg-purple-500/20 text-purple-500';
        case 'kick': return 'bg-slate-500/20 text-slate-400';
        case 'freeze': return 'bg-blue-500/20 text-blue-500';
        default: return 'bg-slate-500/20 text-slate-500';
    }
}

function getStatusColor(status) {
    if (!status) return 'bg-slate-500/20 text-slate-400';
    switch (status.toLowerCase()) {
        case 'active': return 'bg-green-500/20 text-green-500';
        case 'expired': return 'bg-gray-500/20 text-gray-400';
        case 'removed': return 'bg-red-500/20 text-red-500';
        case 'n/a': return 'bg-slate-500/20 text-slate-400';
        default: return 'bg-slate-500/20 text-slate-400';
    }
}

function getStatusForType(type, status, active) {
    const lowerType = type.toLowerCase();
    if (lowerType === 'kick' || lowerType === 'freeze') {
        return 'N/A';
    }
    return status || (active ? 'Active' : 'Removed');
}

function getStatusColorForType(type, status, active) {
    const lowerType = type.toLowerCase();
    if (lowerType === 'kick' || lowerType === 'freeze') {
        return 'bg-slate-500/20 text-slate-400';
    }
    return getStatusColor(status || (active ? 'active' : 'removed'));
}

async function renderReports() {
    const reports = await apiFetch('/reports');
    const content = document.getElementById('main-content');

    content.innerHTML = `
        <div class="flex justify-between items-center mb-6">
            <h2 class="text-2xl font-bold">Player Reports</h2>
        </div>

        <div class="grid grid-cols-1 gap-4">
            ${reports?.map(r => `
                <div class="card p-5 rounded-xl shadow-md flex items-center justify-between border-l-4 ${r.status === 'OPEN' ? 'border-red-500' : 'border-green-500'}">
                    <div class="flex items-center space-x-6">
                        <div class="text-center">
                            <img src="https://mc-heads.net/avatar/${r.targetUUID}/48" class="w-12 h-12 rounded-lg shadow-sm mx-auto mb-1">
                            <p class="text-xs font-bold text-slate-300">${r.targetName}</p>
                        </div>
                        <div>
                            <div class="flex items-center space-x-2 mb-1">
                                <span class="bg-slate-700 text-slate-300 px-2 py-0.5 rounded text-[10px] font-bold uppercase">${r.category}</span>
                                <span class="text-slate-500 text-xs">${new Date(r.timestamp).toLocaleString()}</span>
                            </div>
                            <h4 class="font-semibold text-slate-200">${r.reason}</h4>
                            <p class="text-sm text-slate-400 mt-1">Reporter: <span class="text-slate-300">Player</span></p>
                        </div>
                    </div>
                    <div class="flex items-center space-x-3">
                        <div class="text-right mr-4">
                            <span class="text-xs font-bold ${r.status === 'OPEN' ? 'text-red-500' : 'text-green-500'}">${r.status}</span>
                            <p class="text-[10px] text-slate-500">ID: ${r.reportId}</p>
                        </div>
                        ${r.status === 'OPEN' ? `
                            <button onclick="resolveReport('${r.reportId}', 'RESOLVED')" class="bg-green-600 hover:bg-green-500 p-2 rounded-lg transition" title="Resolve">
                                <i data-lucide="check" class="w-5 h-5 text-white"></i>
                            </button>
                            <button onclick="resolveReport('${r.reportId}', 'REJECTED')" class="bg-red-600 hover:bg-red-500 p-2 rounded-lg transition" title="Reject">
                                <i data-lucide="x" class="w-5 h-5 text-white"></i>
                            </button>
                        ` : ''}
                        <button onclick="viewReportDetails('${r.reportId}')" class="bg-slate-700 hover:bg-slate-600 p-2 rounded-lg transition">
                            <i data-lucide="eye" class="w-5 h-5 text-slate-300"></i>
                        </button>
                    </div>
                </div>
            `).join('') || '<div class="card p-10 rounded-xl text-center text-slate-500">No reports found</div>'}
        </div>
    `;
    lucide.createIcons();
}

async function viewPunishmentDetails(id) {
    const data = await apiFetch(`/punishments/${id}`);
    if (!data) return;

    const p = data.punishment;
    const info = data.playerInfo;

    const modal = document.getElementById('modal-container');
    modal.classList.remove('hidden');
    modal.innerHTML = `
        <div class="bg-slate-900 border border-slate-700 w-full max-w-4xl rounded-2xl shadow-2xl overflow-hidden flex flex-col md:flex-row h-[80vh]">
            <!-- Side: Skin Statues -->
            <div class="w-full md:w-1/3 bg-slate-800 p-6 flex flex-col items-center justify-around border-r border-slate-700">
                <div class="text-center">
                    <p class="text-xs font-bold text-red-500 uppercase mb-2 tracking-widest">Target</p>
                    <img src="https://mc-heads.net/body/${p.playerUUID}/200" class="h-64 object-contain mb-2 drop-shadow-2xl">
                    <p class="font-bold text-lg">${p.playerName || 'Unknown'}</p>
                </div>
                <div class="w-full h-px bg-slate-700 my-4"></div>
                <div class="text-center">
                    <p class="text-xs font-bold text-blue-500 uppercase mb-2 tracking-widest">Moderator</p>
                    <img src="https://mc-heads.net/body/${p.punisherName === 'Console' ? 'steve' : (p.punisherUuid || 'steve')}/120" class="h-40 object-contain mb-2 opacity-80">
                    <p class="font-medium text-slate-400">${p.punisherName}</p>
                </div>
            </div>
            
            <!-- Side: Details -->
            <div class="flex-1 p-8 overflow-y-auto">
                <div class="flex justify-between items-start mb-8">
                    <div>
                        <span class="px-3 py-1 rounded-full text-xs font-bold uppercase ${getPunishTypeColor(p.type)} mb-2 inline-block">
                            ${p.type}
                        </span>
                        <h2 class="text-3xl font-bold">Punishment Details</h2>
                        <p class="text-slate-500 font-mono mt-1">ID: #${p.punishmentId}</p>
                    </div>
                    <button onclick="closeModal()" class="text-slate-500 hover:text-white"><i data-lucide="x" class="w-8 h-8"></i></button>
                </div>

                <div class="grid grid-cols-2 gap-6 mb-8">
                    <div class="card p-4 rounded-xl bg-slate-800/30">
                        <p class="text-xs text-slate-500 uppercase font-bold mb-1">Reason</p>
                        <p class="text-sm font-medium">${p.reason}</p>
                    </div>
                    <div class="card p-4 rounded-xl bg-slate-800/30">
                        <p class="text-xs text-slate-500 uppercase font-bold mb-1">Duration</p>
                        <p class="text-sm font-medium">${p.durationString}</p>
                    </div>
                    <div class="card p-4 rounded-xl bg-slate-800/30">
                        <p class="text-xs text-slate-500 uppercase font-bold mb-1">Date Issued</p>
                        <p class="text-sm font-medium">${new Date(p.timestamp).toLocaleString()}</p>
                    </div>
                    <div class="card p-4 rounded-xl bg-slate-800/30">
                        <p class="text-xs text-slate-500 uppercase font-bold mb-1">Status</p>
                        <p class="text-sm font-medium ${getStatusColorForType(p.type, p.status, p.active).replace('/20', '')}">${getStatusForType(p.type, p.status, p.active)}</p>
                    </div>
                </div>

                <h3 class="text-xl font-bold mb-4 border-b border-slate-700 pb-2">Technical Context</h3>
                <div class="space-y-3">
                    <div class="flex justify-between text-sm">
                        <span class="text-slate-500">IP Address:</span>
                        <span class="text-slate-300 font-mono">${info?.ip || 'Hidden/Not Logged'}</span>
                    </div>
                    <div class="flex justify-between text-sm">
                        <span class="text-slate-500">Location:</span>
                        <span class="text-slate-300">${info?.location || 'Unknown'}</span>
                    </div>
                    <div class="flex justify-between text-sm">
                        <span class="text-slate-500">GameMode:</span>
                        <span class="text-slate-300 uppercase">${info?.gamemode || 'Unknown'}</span>
                    </div>
                    <div class="flex justify-between text-sm">
                        <span class="text-slate-500">Ping:</span>
                        <span class="text-slate-300">${info?.ping || 0} ms</span>
                    </div>
                    <div class="flex justify-between text-sm">
                        <span class="text-slate-500">By IP:</span>
                        <span class="text-slate-300">${p.byIp ? 'Yes' : 'No'}</span>
                    </div>
                </div>
            </div>
        </div>
    `;
    lucide.createIcons();
}

function closeModal() {
    document.getElementById('modal-container').classList.add('hidden');
}

async function resolveReport(id, status) {
    const result = await apiFetch(`/reports/${id}/status`, {
        method: 'POST',
        body: JSON.stringify({ status, moderatorUuid: ADMIN_UUID })
    });
    if (result?.success) {
        renderReports();
    }
}

function openNewPunishmentModal() {
    const modal = document.getElementById('modal-container');
    modal.classList.remove('hidden');
    modal.innerHTML = `
        <div class="card p-8 rounded-2xl w-full max-w-md shadow-2xl border border-slate-700">
            <h2 class="text-2xl font-bold mb-6 flex items-center"><i data-lucide="plus-circle" class="w-6 h-6 mr-2 accent"></i> Create Punishment</h2>
            <form id="punishment-form" class="space-y-4">
                <div>
                    <label class="block text-xs font-bold text-slate-500 uppercase mb-1">Target Player</label>
                    <input type="text" id="p-target" required class="w-full bg-slate-800 border border-slate-700 rounded-lg p-2.5 text-sm focus:ring-2 focus:ring-orange-500 outline-none">
                </div>
                <div class="grid grid-cols-2 gap-4">
                    <div>
                        <label class="block text-xs font-bold text-slate-500 uppercase mb-1">Type</label>
                        <select id="p-type" class="w-full bg-slate-800 border border-slate-700 rounded-lg p-2.5 text-sm outline-none">
                            <option value="ban">Ban</option>
                            <option value="mute">Mute</option>
                            <option value="softban">SoftBan</option>
                            <option value="warn">Warn</option>
                            <option value="kick">Kick</option>
                            <option value="freeze">Freeze</option>
                        </select>
                    </div>
                    <div id="duration-container">
                        <label class="block text-xs font-bold text-slate-500 uppercase mb-1">Duration</label>
                        <input type="text" id="p-duration" placeholder="e.g. 1d, 2h, perm" class="w-full bg-slate-800 border border-slate-700 rounded-lg p-2.5 text-sm outline-none">
                    </div>
                </div>
                <div>
                    <label class="block text-xs font-bold text-slate-500 uppercase mb-1">Reason</label>
                    <textarea id="p-reason" rows="3" required class="w-full bg-slate-800 border border-slate-700 rounded-lg p-2.5 text-sm outline-none"></textarea>
                </div>
                <div id="byip-container" class="flex items-center space-x-2">
                    <input type="checkbox" id="p-byip" class="w-4 h-4 rounded border-slate-700 bg-slate-800 text-orange-500 focus:ring-orange-500">
                    <label for="p-byip" class="text-sm text-slate-400">Apply to IP Address</label>
                </div>
                <div class="flex space-x-3 mt-6">
                    <button type="button" onclick="closeModal()" class="flex-1 py-2.5 rounded-lg border border-slate-700 text-slate-400 hover:text-white transition text-sm">Cancel</button>
                    <button type="submit" class="flex-1 py-2.5 rounded-lg bg-orange-500 hover:bg-orange-600 text-white font-bold transition text-sm">Issue Punishment</button>
                </div>
            </form>
        </div>
    `;
    lucide.createIcons();

    // Type change listener to enable/disable fields
    const typeSelect = document.getElementById('p-type');
    const durationInput = document.getElementById('p-duration');
    const durationContainer = document.getElementById('duration-container');
    const byIpCheckbox = document.getElementById('p-byip');
    const byIpContainer = document.getElementById('byip-container');

    function updateFieldStates() {
        const type = typeSelect.value.toLowerCase();
        const noDuration = ['kick', 'freeze', 'warn'].includes(type);
        const noByIp = ['freeze', 'warn'].includes(type);

        durationInput.disabled = noDuration;
        durationInput.value = noDuration ? '' : durationInput.value;
        durationContainer.classList.toggle('opacity-50', noDuration);
        if (noDuration) {
            durationInput.placeholder = 'N/A for this type';
        } else {
            durationInput.placeholder = 'e.g. 1d, 2h, perm';
        }

        byIpCheckbox.disabled = noByIp;
        byIpCheckbox.checked = noByIp ? false : byIpCheckbox.checked;
        byIpContainer.classList.toggle('opacity-50', noByIp);
    }

    typeSelect.addEventListener('change', updateFieldStates);
    updateFieldStates(); // Initial state

    document.getElementById('punishment-form').onsubmit = async (e) => {
        e.preventDefault();
        const type = document.getElementById('p-type').value;
        const noDuration = ['kick', 'freeze', 'warn'].includes(type.toLowerCase());
        const noByIp = ['freeze', 'warn'].includes(type.toLowerCase());

        const body = {
            target: document.getElementById('p-target').value,
            type: type,
            reason: document.getElementById('p-reason').value,
            duration: noDuration ? '' : document.getElementById('p-duration').value,
            byIp: noByIp ? false : document.getElementById('p-byip').checked,
            adminName: ADMIN_NAME
        };

        const result = await apiFetch('/punishments', {
            method: 'POST',
            body: JSON.stringify(body)
        });

        if (result?.success) {
            closeModal();
            showToast(`Punishment executed successfully: ${result.type.toUpperCase()} on ${result.target}`, 'success');
            renderPunishments();
        } else {
            showToast(result?.message || 'Failed to create punishment. Ensure player exists.', 'error');
        }
    };
}

async function renderModerators() {
    const content = document.getElementById('main-content');
    content.innerHTML = '<div class="card p-20 text-center"><p class="text-slate-500 text-lg">Moderator directory coming soon...</p></div>';
}
