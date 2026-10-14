'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const path = require('node:path');
const os = require('node:os');
const { createWindowsTools, resolveWithin } = require('../windows-tools.cjs');
const { parseAnswer, buildPrompt } = require('../server.cjs');

test('file scope prevents parent traversal and symlink escape', async t => {
  const base = await fs.mkdtemp(path.join(os.tmpdir(), 'bacy-scope-'));
  t.after(() => fs.rm(base, { recursive: true, force: true }));
  const root = path.join(base, 'allowed'); await fs.mkdir(root);
  await fs.writeFile(path.join(root, 'notes.txt'), 'Local notes');
  await fs.writeFile(path.join(base, 'outside.txt'), 'Outside scope');
  assert.equal(await resolveWithin(root, 'notes.txt'), path.join(root, 'notes.txt'));
  await assert.rejects(resolveWithin(root, '../outside.txt'), /outside/);
  await fs.symlink(path.join(base, 'outside.txt'), path.join(root, 'linked.txt'));
  await assert.rejects(resolveWithin(root, 'linked.txt'), /outside/);
  await assert.rejects(resolveWithin(root, path.join(base, 'outside.txt')), /inside/);
});

test('Windows adapter opens only known apps and approved document types', async t => {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), 'bacy-tools-'));
  t.after(() => fs.rm(root, { recursive: true, force: true }));
  await fs.writeFile(path.join(root, 'notes.txt'), 'Notes');
  await fs.writeFile(path.join(root, 'run.cmd'), 'Executable');
  const calls = [];
  const tools = createWindowsTools({ platform: 'win32', run: async script => script.includes('Get-StartApps') ? JSON.stringify([{ Name: 'Example App', AppID: 'Vendor.Example!App' }]) : root, open: async (...args) => calls.push(args) });
  await assert.rejects(tools.list(), /Choose a folder/);
  await tools.apps();
  await assert.rejects(tools.openApp('calc.exe & bad-command'), /installed-app list/);
  assert.equal(calls.length, 0);
  await tools.openApp('Vendor.Example!App');
  assert.deepEqual(calls.pop(), ['explorer.exe', ['shell:AppsFolder\\Vendor.Example!App']]);
  await tools.search('cats & dogs');
  assert.deepEqual(calls.pop(), ['explorer.exe', ['https://www.bing.com/search?q=cats%20%26%20dogs']]);
  await tools.chooseFolder();
  assert.equal((await tools.list()).entries.length, 2);
  await assert.rejects(tools.openFile('run.cmd'), /Executable files/);
  await tools.openFile('notes.txt');
  assert.deepEqual(calls.pop(), ['explorer.exe', [path.join(root, 'notes.txt')]]);
});

test('AI previews are bounded data; modes give different personality instructions', () => {
  const parsed = parseAnswer(JSON.stringify({ reply: 'Here is the explanation.', mood: 'happy', preview: { title: 'A process', kind: 'flow', items: Array.from({ length: 10 }, () => ({ label: 'x'.repeat(200), detail: 'd'.repeat(1000) })) } }));
  assert.equal(parsed.mood, 'happy');
  assert.equal(parsed.preview.items.length, 6);
  assert.equal(parsed.preview.items[0].label.length, 90);
  assert.equal(parsed.preview.items[0].detail.length, 400);
  assert.throws(() => parseAnswer('{"reply":null}'), /invalid structured reply/);
  assert.match(buildPrompt([{ role: 'user', content: 'Hello' }], 'normal'), /lightly playful/);
  assert.match(buildPrompt([{ role: 'user', content: 'Hello' }], 'crystal'), /organize data/);
});
