'use strict';

(() => {
  const byId = id => document.getElementById(id);
  const form = byId('chat-form');
  const input = byId('message-input');
  const log = byId('messages');
  const welcome = byId('welcome');
  const sendButton = byId('send-button');
  const clearButton = byId('clear-chat');
  const requestStatus = byId('request-status');
  const requestStatusText = byId('request-status-text');
  const errorNotice = byId('error-notice');
  const errorText = byId('error-text');
  const token = document.querySelector('meta[name="bacy-token"]').content;
  const modeCaptions = {
    normal: 'Normal · Conversational',
    professional: 'Pro · Focused & practical',
    hardcore: 'Power · Analytical',
    crystal: 'Crystal · Visual & structured'
  };
  let history = [];
  let mode = 'professional';
  let busy = false;
  let available = false;
  let controller = null;
  let lastFailedMessage = null;
  let localBusy = false;
  let lastLocalAction = null;
  let aiProvider = 'codex';

  function updateSend() {
    sendButton.disabled = busy || localBusy || !input.value.trim();
  }

  function scrollToLatest() {
    log.scrollTop = log.scrollHeight;
  }

  function appendMessage(role, content) {
    welcome.hidden = true;
    const article = document.createElement('article');
    article.className = `message ${role}`;
    article.setAttribute('aria-label', role === 'user' ? 'Your message' : 'Bacy response');
    const heading = document.createElement('div');
    heading.className = 'message-heading';
    const icon = document.createElement('span');
    icon.className = 'message-icon';
    icon.setAttribute('aria-hidden', 'true');
    icon.textContent = role === 'user' ? 'Y' : 'B';
    const label = document.createElement('span');
    label.textContent = role === 'user' ? 'YOU' : 'BACY';
    const time = document.createElement('time');
    time.className = 'message-time';
    const now = new Date();
    time.dateTime = now.toISOString();
    time.textContent = now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    heading.append(icon, label, time);
    const body = document.createElement('div');
    body.className = 'message-body';
    body.textContent = content;
    article.append(heading, body);
    log.append(article);
    if (role === 'assistant') {
      byId('latest-reply').textContent = content;
      byId('latest-reply').hidden = false;
    }
    scrollToLatest();
    return article;
  }

  function showAI(open) {
    byId('ai-panel').hidden = !open;
    byId('ai-button').setAttribute('aria-expanded', String(open));
  }
  function updateProviderFields() {
    const google = byId('ai-provider').value === 'gemini';
    byId('gemini-fields').hidden = !google;
    byId('test-ai').hidden = !google;
    byId('forget-key').hidden = !google;
  }
  byId('ai-button').addEventListener('click', () => showAI(byId('ai-panel').hidden));
  byId('close-ai').addEventListener('click', () => showAI(false));
  byId('ai-provider').addEventListener('change', updateProviderFields);
  async function settingsAction(action) {
    const buttons = ['save-ai', 'test-ai', 'forget-key'].map(byId);
    buttons.forEach(button => { button.disabled = true; });
    byId('ai-feedback').textContent = 'Connecting…';
    try {
      let result;
      if (action === 'test') {
        result = await api('/api/ai/test');
        byId('gemini-model-list').replaceChildren(...(result.models || []).map(model => {
          const option = document.createElement('option'); option.value = model.id; option.label = model.name; return option;
        }));
      } else {
        const key = byId('gemini-key').value;
        byId('gemini-key').value = '';
        result = await api('/api/ai/configure', { provider: byId('ai-provider').value,
          key, model: byId('gemini-model').value.trim(), remember: byId('remember-key').checked,
          forget: action === 'forget' });
      }
      byId('ai-feedback').textContent = result.message || 'Settings updated. Test the connection next.';
      await checkConnection();
    } catch (error) { byId('ai-feedback').textContent = error.message || 'Connection failed.'; }
    finally { buttons.forEach(button => { button.disabled = false; }); }
  }
  byId('save-ai').addEventListener('click', () => settingsAction('save'));
  byId('test-ai').addEventListener('click', () => settingsAction('test'));
  byId('forget-key').addEventListener('click', () => settingsAction('forget'));

  async function checkConnection() {
    const button = byId('check-connection');
    button.disabled = true;
    button.textContent = 'Checking…';
    byId('connection-detail').textContent = 'Checking your AI connection…';
    const statusController = new AbortController();
    const timeout = setTimeout(() => statusController.abort(), 30000);
    try {
      const response = await fetch('/api/status', { signal: statusController.signal, cache: 'no-store' });
      if (!response.ok) throw new Error('The local server could not check AI settings.');
      const status = await response.json();
      aiProvider = status.provider || 'codex';
      byId('ai-provider').value = aiProvider;
      updateProviderFields();
      if (status.model) byId('gemini-model').value = status.model;
      byId('remember-key').disabled = status.platform !== 'win32';
      available = Boolean(status.available);
      byId('workspace-status').textContent = status.windowsTools ? 'Windows controls available' : 'Windows laptop required';
      byId('header-status').textContent = available ? aiProvider === 'gemini' && !status.verified ? 'Key configured' : 'Ready' : 'Needs setup';
      byId('connection-detail').textContent = status.detail || (available ? 'Codex is available. Your first message will verify the AI connection.' : 'Install Codex and sign in on this laptop, then check again.');
      byId('header-dot').className = `status-dot ${available ? 'ready' : 'error'}`;
      byId('connection-dot').className = `status-dot ${available ? 'ready' : 'error'}`;
      byId('connection-banner').classList.toggle('warning', !available);
      if (!available) byId('connection-banner').hidden = false;
      if (!available && aiProvider === 'gemini') showAI(true);
      byId('runtime-label').textContent = aiProvider === 'gemini' ? 'LOCAL / GOOGLE GEMINI' : status.version ? `LOCAL / CODEX ${String(status.version).replace(/^codex(?:-cli)?\s*/i, '').slice(0, 40)}` : 'LOCAL INTERFACE / CODEX BRIDGE';
    } catch (error) {
      available = false;
      byId('header-status').textContent = 'Connection unavailable';
      byId('connection-detail').textContent = 'Could not reach the local Bacy server. Check that it is still running in PowerShell.';
      byId('header-dot').className = 'status-dot error';
      byId('connection-dot').className = 'status-dot error';
      byId('connection-banner').classList.add('warning');
      byId('connection-banner').hidden = false;
    } finally {
      clearTimeout(timeout);
      button.disabled = false;
      button.textContent = 'Check again';
      updateSend();
    }
  }

  async function submitMessage(content, retry = false) {
    if (busy || !available || !content.trim()) return;
    const message = content.trim();
    busy = true;
    controller = new AbortController();
    lastFailedMessage = null;
    byId('latest-reply').hidden = true;
    lastLocalAction = null;
    byId('retry-request').hidden = false;
    errorNotice.hidden = true;
    input.value = '';
    input.disabled = true;
    clearButton.disabled = true;
    updateSend();
    if (!retry) appendMessage('user', message);
    requestStatus.hidden = false;
    requestStatusText.textContent = 'Bacy is working on your response…';
    document.body.classList.add('is-thinking');
    const slowNotice = setTimeout(() => {
      requestStatusText.textContent = 'Still waiting for ' + (aiProvider === 'gemini' ? 'Gemini' : 'Codex') + '. Check your proxy if this takes too long.';
    }, 30000);
    try {
      const messages = [...history.slice(-18), { role: 'user', content: message }];
      const response = await fetch('/api/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-Bacy-Token': token },
        body: JSON.stringify({ messages, mode }),
        signal: controller.signal
      });
      const data = await response.json().catch(() => null);
      if (!response.ok) throw new Error(data && typeof data.error === 'string' ? data.error : 'Bacy could not get a response. Check AI settings and your proxy, then try again.');
      const reply = data && (data.reply || data.response || data.message);
      if (typeof reply !== 'string' || !reply.trim()) throw new Error('Your AI provider returned no answer. Please try again.');
      history = [...messages, { role: 'assistant', content: reply }].slice(-20);
      appendMessage('assistant', reply);
      if (['neutral', 'happy', 'playful', 'thinking', 'serious', 'angry'].includes(data.mood)) document.body.dataset.mood = data.mood;
      showPreview(data.preview);
      speak(reply);
      byId('header-status').textContent = 'Connected';
      byId('connection-detail').textContent = 'AI replies through ' + (data.provider === 'gemini' ? 'Google Gemini' : 'Codex') + '. Windows controls remain local.';
    } catch (error) {
      lastFailedMessage = message;
      input.value = message;
      errorText.textContent = error.name === 'AbortError' ? 'You stopped this request. Your message is ready to retry or edit.' : error.message || 'The request failed. Your message is ready to retry.';
      errorNotice.hidden = false;
    } finally {
      clearTimeout(slowNotice);
      busy = false;
      controller = null;
      input.disabled = false;
      clearButton.disabled = false;
      requestStatus.hidden = true;
      document.body.classList.remove('is-thinking');
      updateSend();
      input.focus();
    }
  }

  form.addEventListener('submit', event => {
    event.preventDefault();
    const content = input.value.trim();
    handleInput(content, lastFailedMessage === content);
  });
  input.addEventListener('input', updateSend);
  input.addEventListener('keydown', event => {
    if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
      event.preventDefault();
      if (!sendButton.disabled) form.requestSubmit();
    }
  });
  byId('cancel-request').addEventListener('click', () => { if (controller) controller.abort(); });
  byId('retry-request').addEventListener('click', () => { if (lastLocalAction) performLocal(lastLocalAction.message, lastLocalAction.action); else if (lastFailedMessage) handleInput(lastFailedMessage, true); });
  byId('check-connection').addEventListener('click', checkConnection);
  clearButton.addEventListener('click', () => {
    if (busy) return;
    for (const article of log.querySelectorAll('.message')) article.remove();
    history = [];
    lastFailedMessage = null;
    lastLocalAction = null;
    input.value = '';
    errorNotice.hidden = true;
    welcome.hidden = false;
    byId('latest-reply').hidden = true;
    updateSend();
    input.focus();
  });
  document.querySelectorAll('[data-prompt]').forEach(button => button.addEventListener('click', () => {
    if (busy) return;
    input.value = button.dataset.prompt;
    updateSend();
    input.focus();
  }));
  document.querySelectorAll('button[data-mode]').forEach(button => button.addEventListener('click', () => {
    setMode(button.dataset.mode);
  }));
  function setMode(next) {
    if (!Object.hasOwn(modeCaptions, next)) return;
    mode = next;
    document.body.dataset.mode = mode;
    document.body.dataset.mood = 'neutral';
    byId('mode-caption').textContent = modeCaptions[mode];
    const identities = {
      normal: ['01','Here for you.\nReady for anything.','A little curiosity.\nA conversation that feels natural.','NORMAL'],
      professional: ['02','Precision.\nWith presence.','A sharper perspective.\nA calmer way to get things done.','PRO'],
      hardcore: ['03','More depth.\nMore possibility.','Follow the connections.\nThink through the bigger picture.','POWER'],
      crystal: ['04','See it clearly.\nMake it happen.','Give your ideas structure.\nTurn complexity into clarity.','CRYSTAL']
    };
    const [number,title,description,label] = identities[mode];
    byId('mode-number').textContent = number;
    byId('identity-title').textContent = title;
    byId('identity-description').textContent = description;
    byId('identity-mode').textContent = label + ' MODE';
    byId('avatar-mode-label').textContent = label;
    document.querySelectorAll('button[data-mode]').forEach(button => button.setAttribute('aria-pressed', String(button.dataset.mode === mode)));
  }
  async function api(route, body = {}) {
    const response = await fetch(route, { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-Bacy-Token': token }, body: JSON.stringify(body) });
    const data = await response.json();
    if (!response.ok) throw new Error(data.error || 'The local operation failed.');
    return data;
  }
  function localReply(message, reply) {
    input.value = '';
    lastFailedMessage = null;
    lastLocalAction = null;
    errorNotice.hidden = true;
    appendMessage('user', message);
    appendMessage('assistant', reply);
    // Local file/app receipts stay out of the online model's conversation context.
    speak(reply);
    updateSend();
  }
  async function performLocal(message, action) {
    if (busy || localBusy) return;
    localBusy = true; updateSend();
    try { localReply(message, await action()); }
    catch (error) {
      lastFailedMessage = message; input.value = message;
      lastLocalAction = { message, action };
      errorText.textContent = error.message; errorNotice.hidden = false; byId('retry-request').hidden = false;
    } finally { localBusy = false; updateSend(); }
  }
  async function handleInput(message, retry = false) {
    if (!message.trim()) return;
    const command = message.toLowerCase().trim().replace(/[.!?]+$/, '').replace(/^(?:hey )?bacy[, ]+/, '').replace(/^please /, '').replace(/(?: for me| please)$/, '');
    if (/^(pause|stop|stop speaking|be quiet)$/.test(command)) {
      controller?.abort(); synthesizer?.cancel(); byId('read-replies').checked = false;
      document.body.classList.remove('is-speaking'); speaking = false;
      return localReply(message, 'Paused. Voice playback is off.');
    }
    if (busy || localBusy) return;
    if (/^(enable voice|turn on voice|read replies)$/.test(command)) {
      if (!voices.length) return localReply(message, 'There are no browser voices available yet. Open Voice to check your options.');
      byId('read-replies').checked = true; return localReply(message, 'Voice is on.');
    }
    const modeMatch = command.match(/^(?:switch|change|go)(?: (?:the )?mode)?(?: to)? (?:the )?(face|normal|brain|professional|pro|power|neural|hardcore|crystal|crystalline)(?: mode)?$/);
    if (modeMatch) {
      const mapping = { face: 'normal', normal: 'normal', brain: 'professional', professional: 'professional', pro: 'professional', power: 'hardcore', neural: 'hardcore', hardcore: 'hardcore', crystal: 'crystal', crystalline: 'crystal' };
      setMode(mapping[modeMatch[1]]);
      return localReply(message, 'Switched to ' + modeCaptions[mode] + ' mode.');
    }
    const mood = command.match(/^(?:look|be|feel|show)(?: (?:a|your) face)? (angry|happy|funny|playful|serious|neutral)$/);
    if (mood) {
      document.body.dataset.mood = mood[1] === 'funny' ? 'playful' : mood[1];
      return localReply(message, 'Expression changed to ' + mood[1] + '.');
    }
    if (/^(?:show|open)(?: my)? files$/.test(command)) return performLocal(message, async () => { showFilesPanel(); return 'Choose a folder and I’ll show its files here.'; });
    if (/^(?:show|open|list)(?: my)? apps$/.test(command)) return performLocal(message, async () => { await showApps(); return 'Here are the installed apps Windows reported.'; });
    if (/^(?:open|show|start)(?: my| the)? camera$/.test(command)) return performLocal(message, async () => { await startCamera(); return 'Your camera preview stays on this page.'; });
    if (/^(?:close|stop)(?: my| the)? camera$/.test(command)) { stopCamera(); return localReply(message, 'Camera stopped.'); }
    const systemActions = { 'show desktop':'show-desktop', 'show my desktop':'show-desktop', 'volume up':'volume-up', 'turn up the volume':'volume-up', 'volume down':'volume-down', 'turn down the volume':'volume-down', 'mute':'mute', 'mute sound':'mute', 'unmute':'mute' };
    if (Object.hasOwn(systemActions,command)) return performLocal(message, async () => (await api('/api/computer',{action:systemActions[command]})).message);
    const fileCommand = command.match(/^open (?:the )?file (.+)$/);
    if (fileCommand) return performLocal(message, async () => {
      const data = await api('/api/files/list', {path:filePath});
      const file = data.entries.find(entry => !entry.directory && entry.name.toLowerCase() === fileCommand[1]);
      if (!file) { showFilesPanel(); return 'Choose the folder with that file, then select it from Files.'; }
      return (await api('/api/files/open', {path:file.path})).message;
    });
    const search = command.match(/^(?:search(?: the web)?(?: for)?|look up) (.+)$/);
    if (search) return performLocal(message, async () => (await api('/api/search', { query: search[1] })).message);
    const app = command.match(/^(?:open|launch|start) (.+)$/);
    if (app) return performLocal(message, async () => {
      let id = { browser:'browser', chrome:'chrome', 'google chrome':'chrome', edge:'edge', 'microsoft edge':'edge', firefox:'firefox', word:'word', excel:'excel', outlook:'outlook', 'vs code':'vscode', notepad:'notepad', calculator:'calculator', 'file explorer':'explorer', settings:'settings', 'windows settings':'settings' }[app[1]];
      if (!id) {
        const data = await api('/api/apps');
        const exact = data.apps.find(item => item.name.toLowerCase() === app[1]);
        const candidates = data.apps.filter(item => item.name.toLowerCase().includes(app[1]));
        const selected = exact || (candidates.length === 1 ? candidates[0] : null);
        if (!selected) { await showApps(app[1]); return 'Choose the app you mean from the list.'; }
        id = selected.id;
      }
      return (await api('/api/apps/open', { id })).message;
    });
    if (!available) { errorText.textContent = 'Open AI settings to connect your provider. Local mode commands still work.'; errorNotice.hidden = false; return; }
    submitMessage(message, retry);
  }
  function showPreview(preview) {
    if (!preview || !['flow', 'steps', 'comparison'].includes(preview.kind) || !Array.isArray(preview.items) || !preview.items.length) return;
    byId('preview-title').textContent = preview.title || 'Explanation';
    const container = byId('preview-content'); container.replaceChildren(); container.dataset.kind = preview.kind;
    for (const [index, item] of preview.items.slice(0, 6).entries()) {
      const card = document.createElement('article'); card.className = 'preview-card';
      const number = document.createElement('span'); number.className = 'preview-number'; number.textContent = String(index + 1).padStart(2, '0');
      const heading = document.createElement('h2'); heading.textContent = item.label;
      const detail = document.createElement('p'); detail.textContent = item.detail;
      card.append(number, heading, detail); container.append(card);
    }
    byId('preview-panel').hidden = false;
  }
  byId('close-preview').addEventListener('click', () => { byId('preview-panel').hidden = true; });
  function showTools(title, note) {
    byId('tools-title').textContent = title; byId('tools-note').textContent = note;
    byId('tools-content').replaceChildren(); byId('tools-panel').hidden = false;
  }
  function toolError(error) { byId('tools-note').textContent = error.message; }
  async function showApps(filter = '') {
    showTools('Your apps', 'Reading the installed-app list from Windows…');
    byId('choose-folder').hidden = true; byId('folder-up').hidden = true;
    const data = await api('/api/apps');
    byId('tools-note').textContent = 'Installed apps. Opening an app does not connect Bacy to its account.';
    for (const app of data.apps.filter(item => !filter || item.name.toLowerCase().includes(filter))) {
      const button = document.createElement('button'); button.className = 'tool-item'; button.textContent = app.name;
      button.addEventListener('click', () => performLocal('Open ' + app.name, async () => (await api('/api/apps/open', { id: app.id })).message));
      byId('tools-content').append(button);
    }
  }
  let filePath = '';
  function showFilesPanel() {
    showTools('Your files', 'Choose the folder Bacy may browse. File names are not automatically included in AI chat.');
    byId('choose-folder').hidden = false; byId('folder-up').hidden = true;
  }
  async function listFiles(relative = '') {
    const data = await api('/api/files/list', { path: relative });
    filePath = data.path; byId('tools-title').textContent = data.name || 'Your files';
    byId('tools-note').textContent = 'Click a folder to browse, or a document to open it in its Windows app.';
    byId('folder-up').hidden = !filePath; byId('tools-content').replaceChildren();
    for (const entry of data.entries) {
      const button = document.createElement('button'); button.className = 'tool-item'; button.textContent = (entry.directory ? '▸  ' : '·  ') + entry.name;
      button.addEventListener('click', () => {
        if (entry.directory) listFiles(entry.path).catch(toolError);
        else performLocal('Open ' + entry.name, async () => (await api('/api/files/open', { path: entry.path })).message);
      });
      byId('tools-content').append(button);
    }
  }
  byId('choose-folder').addEventListener('click', async () => {
    byId('choose-folder').disabled = true;
    try { const result = await api('/api/files/choose'); if (result.selected) await listFiles(); }
    catch (error) { toolError(error); }
    finally { byId('choose-folder').disabled = false; }
  });
  byId('folder-up').addEventListener('click', () => listFiles(filePath.split(/[\\/]/).slice(0, -1).join('/')).catch(toolError));
  byId('open-browser').addEventListener('click', () => performLocal('Open browser', async () => (await api('/api/apps/open', { id: 'browser' })).message));
  byId('open-apps').addEventListener('click', () => showApps().catch(toolError));
  byId('open-files').addEventListener('click', showFilesPanel);
  byId('close-tools').addEventListener('click', () => { byId('tools-panel').hidden = true; });
  byId('open-system').addEventListener('click', () => {
    showTools('Computer', 'These commands run locally on Windows.');
    byId('choose-folder').hidden = true; byId('folder-up').hidden = true;
    for (const [label,action] of [['Show desktop','show-desktop'],['Volume up','volume-up'],['Volume down','volume-down'],['Toggle mute','mute']]) {
      const button = document.createElement('button');button.className='tool-item';button.textContent=label;
      button.addEventListener('click',()=>performLocal(label, async () => (await api('/api/computer',{action})).message));
      byId('tools-content').append(button);
    }
  });
  let cameraStream = null, cameraRequest=0;
  async function startCamera() {
    if (cameraStream) { byId('camera-panel').hidden = false; return; }
    if (!navigator.mediaDevices?.getUserMedia) throw new Error('Camera access is unavailable in this browser.');
    const request=++cameraRequest;
    const stream=await navigator.mediaDevices.getUserMedia({video:true,audio:false});
    if (request!==cameraRequest) { stream.getTracks().forEach(track=>track.stop());throw new Error('Camera request cancelled.'); }
    cameraStream=stream;
    byId('camera-video').srcObject = cameraStream;
    byId('camera-panel').hidden = false;
    byId('camera-note').textContent = 'Live preview stays on this page. No images are sent to the AI.';
  }
  function stopCamera() {
    cameraRequest++;
    cameraStream?.getTracks().forEach(track=>track.stop()); cameraStream=null;
    byId('camera-video').srcObject = null; byId('camera-panel').hidden=true;
  }
  byId('open-camera').addEventListener('click', ()=>performLocal('Open camera', async ()=>{await startCamera();return 'Camera preview is on. Close the panel to turn it off.';}));
  byId('close-camera').addEventListener('click',stopCamera);
  byId('mood-select').addEventListener('change',()=>{document.body.dataset.mood=byId('mood-select').value;});
  const updatePresence = () => {
    byId('presence-state').textContent = document.body.classList.contains('is-speaking')?'SPEAKING':document.body.classList.contains('is-thinking')?'THINKING':document.body.classList.contains('is-listening')?'LISTENING':'STANDING BY';
    const mood=document.body.dataset.mood;
    if (Array.from(byId('mood-select').options).some(option=>option.value===mood)) byId('mood-select').value=mood;
  };
  new MutationObserver(updatePresence).observe(document.body,{attributes:true,attributeFilter:['class','data-mood']});
  const updateClock = () => { byId('studio-clock').textContent=new Date().toLocaleTimeString([], {hour:'2-digit',minute:'2-digit'}) + ' / LOCAL TIME'; };
  updateClock();setInterval(updateClock,30000);
  document.querySelectorAll('[data-command]').forEach(button=>button.addEventListener('click',()=>handleInput(button.dataset.command)));
  const drawer = byId('conversation-drawer');
  function showDrawer(show) {
    drawer.hidden = !show;
    byId('conversation-button').setAttribute('aria-expanded', String(show));
    if (show) byId('close-chat').focus();
  }
  byId('conversation-button').addEventListener('click', () => showDrawer(drawer.hidden));
  byId('close-chat').addEventListener('click', () => { showDrawer(false); byId('conversation-button').focus(); });
  document.addEventListener('keydown', event => { if (event.key === 'Escape' && !drawer.hidden) showDrawer(false); });
  byId('details-button').addEventListener('click', () => {
    byId('connection-banner').hidden = !byId('connection-banner').hidden;
    byId('details-button').setAttribute('aria-expanded', String(!byId('connection-banner').hidden));
  });
  byId('theme-button').addEventListener('click', () => {
    document.body.dataset.theme = document.body.dataset.theme === 'dark' ? 'light' : 'dark';
    updateThemeLabel();
  });
  function updateThemeLabel() {
    const light = document.body.dataset.theme === 'light';
    byId('theme-button').textContent = light ? 'Dark' : 'Light';
    byId('theme-button').setAttribute('aria-label', light ? 'Switch to dark theme' : 'Switch to light theme');
  }
  let microphone = null;
  let audioContext = null;
  let microphonePending = false;
  let recognition = null;
  let recognitionRunning = false;
  let recognitionFailed = false;
  let speaking = false;
  let restartSpeech;
  let audioFrame;
  let previousTheme = 'dark';
  window.bacyAudioLevel = 0;
  const synthesizer = window.speechSynthesis;
  let voices = [];
  function loadVoices() {
    voices = synthesizer ? synthesizer.getVoices() : [];
    const select = byId('voice-select'); const previous = select.value; select.replaceChildren();
    if (!voices.length) {
      const option = document.createElement('option'); option.textContent = 'No browser voices available'; option.value = ''; select.append(option);
    } else {
      voices.forEach((voice, index) => { const option = document.createElement('option'); option.value = String(index); option.textContent = voice.name + ' · ' + voice.lang; select.append(option); });
      const preferred = voices.findIndex(voice => /^en/i.test(voice.lang) && /David|Mark|Guy|Ryan|Daniel|George/i.test(voice.name));
      select.value = previous && voices[Number(previous)] ? previous : String(preferred >= 0 ? preferred : Math.max(0, voices.findIndex(voice => /^en/i.test(voice.lang))));
    }
    byId('read-replies').disabled = !voices.length;
  }
  if (synthesizer) synthesizer.addEventListener('voiceschanged', loadVoices);
  loadVoices();
  byId('voice-button').addEventListener('click', () => {
    byId('voice-panel').hidden = !byId('voice-panel').hidden;
    byId('voice-button').setAttribute('aria-expanded', String(!byId('voice-panel').hidden));
  });
  byId('read-replies').addEventListener('change', () => { if (!byId('read-replies').checked) { synthesizer?.cancel(); speaking=false;document.body.classList.remove('is-speaking');restartRecognition(); } });
  byId('stop-voice').addEventListener('click', () => { synthesizer?.cancel(); speaking = false; document.body.classList.remove('is-speaking'); restartRecognition(); });
  function speak(text) {
    if (!byId('read-replies').checked || !voices.length || !synthesizer) return;
    synthesizer.cancel();
    const utterance = new SpeechSynthesisUtterance(text.slice(0, 3000));
    utterance.voice = voices[Number(byId('voice-select').value)] || voices[0];
    utterance.pitch = Number(byId('voice-pitch').value); utterance.rate = .96;
    utterance.onstart = () => { speaking = true; document.body.classList.add('is-speaking'); recognition?.abort(); };
    const finish = () => { speaking = false; document.body.classList.remove('is-speaking'); if (microphone) restartRecognition(); };
    utterance.onend = finish; utterance.onerror = finish;
    synthesizer.speak(utterance);
  }
  function restartRecognition() {
    clearTimeout(restartSpeech);
    if (!recognition || !microphone || speaking || recognitionFailed || recognitionRunning) return;
    restartSpeech = setTimeout(() => {
      if (!recognition || !microphone || speaking || recognitionFailed || recognitionRunning) return;
      try { recognition.start(); recognitionRunning = true; } catch { /* A browser may still be ending the previous session. */ }
    }, 350);
  }
  function startRecognition() {
    const Recognition = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!Recognition) { byId('microphone-note').textContent = 'Speech recognition is unavailable in this browser. The scene reacts to sound; type to chat.'; return; }
    recognitionFailed = false;
    recognition = new Recognition(); recognition.lang = 'en-US'; recognition.continuous = true; recognition.interimResults = true;
    recognition.onresult = event => {
      let final = '';
      for (let i = event.resultIndex; i < event.results.length; i++) if (event.results[i].isFinal) final += event.results[i][0].transcript;
      const interrupt = /^(?:hey bacy[, ]+)?(?:pause|stop|stop speaking|be quiet)[.!]?$/i.test(final.trim());
      if (final.trim() && !speaking && ((!busy && !localBusy) || interrupt)) { input.value = final.trim(); updateSend(); handleInput(final.trim()); }
    };
    recognition.onerror = event => {
      if (event.error === 'aborted' || event.error === 'no-speech') return;
      recognitionFailed = true;
      byId('microphone-note').textContent = 'Speech recognition could not connect. The scene still reacts to sound; type your command.';
    };
    recognition.onend = () => { recognitionRunning = false; restartRecognition(); };
    byId('microphone-note').textContent = 'Listening. Try “Hey Bacy, switch to power mode.”';
    restartRecognition();
  }
  function stopMicrophone() {
    clearTimeout(restartSpeech);
    const previousRecognition = recognition; recognition = null; recognitionRunning = false;
    previousRecognition?.abort();
    if (audioFrame) cancelAnimationFrame(audioFrame);
    if (microphone) microphone.getTracks().forEach(track => track.stop());
    if (audioContext) audioContext.close().catch(() => {});
    microphone = null;
    audioContext = null;
    window.bacyAudioLevel = 0;
    document.body.classList.remove('is-listening', 'voice-active');
    document.body.dataset.theme = previousTheme;
    updateThemeLabel();
    byId('microphone-button').setAttribute('aria-pressed', 'false');
    byId('microphone-button').setAttribute('aria-label', 'Start microphone');
    byId('microphone-note').hidden = true;
  }
  byId('microphone-button').addEventListener('click', async () => {
    if (microphonePending) return;
    if (microphone) return stopMicrophone();
    microphonePending = true;
    byId('microphone-button').disabled = true;
    previousTheme = document.body.dataset.theme;
    try {
      if (!navigator.mediaDevices?.getUserMedia) throw new Error('Microphone access is unavailable in this browser. Chat still works.');
      microphone = await navigator.mediaDevices.getUserMedia({ audio: true });
      audioContext = new AudioContext();
      await audioContext.resume();
      const analyser = audioContext.createAnalyser();
      analyser.fftSize = 256;
      audioContext.createMediaStreamSource(microphone).connect(analyser);
      const samples = new Uint8Array(analyser.fftSize);
      const measure = () => {
        analyser.getByteTimeDomainData(samples);
        let power = 0;
        for (const sample of samples) power += ((sample - 128) / 128) ** 2;
        window.bacyAudioLevel = Math.min(1, Math.sqrt(power / samples.length) * 5);
        audioFrame = requestAnimationFrame(measure);
      };
      measure();
      document.body.classList.add('is-listening');
      document.body.classList.add('voice-active');
      updateThemeLabel();
      byId('microphone-note').hidden = false;
      byId('microphone-button').setAttribute('aria-pressed', 'true');
      byId('microphone-button').setAttribute('aria-label', 'Stop microphone');
      startRecognition();
      microphone.getTracks().forEach(track => track.addEventListener('ended', stopMicrophone, { once: true }));
    } catch (error) {
      stopMicrophone();
      byId('retry-request').hidden = true;
      errorText.textContent = error.name === 'NotAllowedError' ? 'Microphone permission was declined. You can keep chatting by typing.' : 'The microphone could not start. You can keep chatting by typing.';
      errorNotice.hidden = false;
    } finally {
      microphonePending = false;
      byId('microphone-button').disabled = false;
    }
  });
  window.addEventListener('pagehide', () => { stopCamera(); stopMicrophone(); synthesizer?.cancel(); });
  checkConnection();
})();
