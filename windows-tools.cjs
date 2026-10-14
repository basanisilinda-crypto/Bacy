'use strict';
const fs = require('node:fs/promises');
const path = require('node:path');
const { spawn } = require('node:child_process');

function powershell(script, timeout = 20000) {
  return new Promise((resolve, reject) => {
    const child = spawn('powershell.exe', ['-STA', '-NoProfile', '-EncodedCommand', Buffer.from(script, 'utf16le').toString('base64')], { windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] });
    let output = '', failed = false;
    const timer = setTimeout(() => { failed = true; child.kill(); reject(new Error('The Windows operation timed out.')); }, timeout);
    child.stdout.setEncoding('utf8');
    child.stdout.on('data', value => {
      output += value;
      if (output.length > 1000000 && !failed) { failed = true; child.kill(); reject(new Error('The app list was too large.')); }
    });
    child.stderr.resume();
    child.on('error', () => { clearTimeout(timer); reject(new Error('Windows PowerShell could not start.')); });
    child.on('close', code => { clearTimeout(timer); if (!failed) code === 0 ? resolve(output.trim()) : reject(new Error('Windows could not complete this operation.')); });
  });
}

async function resolveWithin(root, relative = '') {
  if (typeof relative !== 'string' || relative.includes('\0') || path.isAbsolute(relative)) throw new Error('Choose an item inside the permitted folder.');
  const realRoot = await fs.realpath(root);
  const target = await fs.realpath(path.resolve(realRoot, relative));
  const remaining = path.relative(realRoot, target);
  if (remaining.startsWith('..' + path.sep) || remaining === '..' || path.isAbsolute(remaining)) throw new Error('That item is outside the permitted folder.');
  return target;
}

function launch(executable, args) {
  return new Promise((resolve, reject) => {
    const child = spawn(executable, args, { windowsHide: false, detached: true, stdio: 'ignore' });
    child.once('error', () => reject(new Error('Windows could not open the requested app.')));
    child.once('spawn', () => { child.unref(); resolve(); });
  });
}

function createWindowsTools({ run = powershell, open = launch, platform = process.platform } = {}) {
  let permittedRoot = null;
  let appCache = [];
  const requireWindows = () => { if (platform !== 'win32') throw new Error('App and file controls run on your Windows laptop.'); };
  const requireFolder = () => { if (!permittedRoot) throw new Error('Choose a folder first. Bacy has no file access until you choose one.'); };
  return {
    available: platform === 'win32',
    async apps() {
      requireWindows();
      const data = await run("[Console]::OutputEncoding=[Text.Encoding]::UTF8; Get-StartApps | Select-Object Name,AppID | ConvertTo-Json -Compress");
      const parsed = data ? JSON.parse(data) : [];
      appCache = (Array.isArray(parsed) ? parsed : [parsed]).filter(item => item && typeof item.Name === 'string' && typeof item.AppID === 'string').slice(0, 1500);
      return { apps: appCache.map(item => ({ name: item.Name, id: item.AppID })) };
    },
    async openApp(id) {
      requireWindows();
      const builtins = { browser: ['explorer.exe', ['https://www.bing.com']], notepad: ['notepad.exe', []], calculator: ['calc.exe', []] };
      if (Object.hasOwn(builtins, id)) { await open(...builtins[id]); return { message: 'Windows received the request to open ' + id + '.' }; }
      if (!appCache.length) await this.apps();
      const app = appCache.find(item => item.AppID === id);
      if (!app || !/^[A-Za-z0-9_.!{}\\/: -]+$/.test(app.AppID)) throw new Error('Choose an app from the installed-app list.');
      await open('explorer.exe', ['shell:AppsFolder\\' + app.AppID]);
      return { message: 'Windows received the request to open ' + app.Name + '.' };
    },
    async search(query) {
      requireWindows();
      if (typeof query !== 'string' || !query.trim() || query.length > 500) throw new Error('Enter a short search query.');
      await open('explorer.exe', ['https://www.bing.com/search?q=' + encodeURIComponent(query)]);
      return { message: 'Windows received the request to open a web search for ' + query + '.' };
    },
    async chooseFolder() {
      requireWindows();
      const selected = await run("Add-Type -AssemblyName System.Windows.Forms; $d=New-Object System.Windows.Forms.FolderBrowserDialog; $d.Description='Choose the folder Bacy may browse'; $d.ShowNewFolderButton=$false; if($d.ShowDialog() -eq 'OK'){[Console]::OutputEncoding=[Text.Encoding]::UTF8; $d.SelectedPath}; $d.Dispose()", 120000);
      if (!selected) return { selected: false };
      permittedRoot = await fs.realpath(selected);
      return { selected: true, name: path.basename(permittedRoot) || permittedRoot };
    },
    async list(relative = '') {
      requireWindows(); requireFolder();
      const directory = await resolveWithin(permittedRoot, relative);
      const entries = await fs.readdir(directory, { withFileTypes: true });
      return { name: path.basename(directory), path: path.relative(permittedRoot, directory), entries: entries.filter(item => !item.isSymbolicLink()).sort((a, b) => Number(b.isDirectory()) - Number(a.isDirectory()) || a.name.localeCompare(b.name)).slice(0, 300).map(item => ({ name: item.name, directory: item.isDirectory(), path: path.relative(permittedRoot, path.join(directory, item.name)) })) };
    },
    async openFile(relative) {
      requireWindows(); requireFolder();
      const file = await resolveWithin(permittedRoot, relative);
      const stat = await fs.stat(file);
      if (!stat.isFile() || !/\.(txt|md|csv|json|pdf|png|jpe?g|gif|webp|docx?|xlsx?|pptx?|mp3|mp4)$/i.test(file)) throw new Error('Only documents and media can be opened here. Executable files are not supported.');
      await open('explorer.exe', [file]);
      return { message: 'Windows received the request to open ' + path.basename(file) + '.' };
    }
  };
}
module.exports = { createWindowsTools, resolveWithin };
