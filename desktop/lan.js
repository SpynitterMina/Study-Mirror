const os = require('os');

// Adapters that are almost never the one your phone can reach.
const VIRTUAL = ['vethernet', 'wsl', 'docker', 'vmware', 'virtualbox', 'vbox', 'hyper-v', 'bluetooth',
  'loopback', 'tailscale', 'zerotier', 'hamachi', 'npcap', 'tap-', 'tun'];

function isPrivate(ip) {
  return /^10\./.test(ip) || /^192\.168\./.test(ip) || /^172\.(1[6-9]|2\d|3[01])\./.test(ip);
}

/**
 * Every IPv4 address this laptop has, best guess first.
 * Returns [{ ip, name }]. Wi-Fi and Ethernet on a normal home range come first,
 * virtual adapters last (kept, in case the phone is on one of those).
 */
function getLocalIps(ifaces = os.networkInterfaces()) {
  const out = [];
  for (const [name, list] of Object.entries(ifaces || {})) {
    const lower = name.toLowerCase();
    const virtual = VIRTUAL.some(v => lower.includes(v));
    for (const a of list || []) {
      const v4 = a.family === 'IPv4' || a.family === 4;
      if (!v4 || a.internal || !a.address) continue;
      if (a.address.startsWith('169.254.')) continue;           // no real network
      let score = 0;
      if (/wi-?fi|wlan|wireless/.test(lower)) score += 30;
      if (/ethernet|^en\d|^eth/.test(lower)) score += 20;
      if (a.address.startsWith('192.168.')) score += 10;
      else if (a.address.startsWith('10.')) score += 8;
      else if (isPrivate(a.address)) score += 5;
      if (virtual) score -= 100;
      out.push({ ip: a.address, name, score });
    }
  }
  out.sort((x, y) => y.score - x.score);
  return out.map(({ ip, name }) => ({ ip, name }));
}

module.exports = { getLocalIps, isPrivate };
