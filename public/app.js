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
    normal: 'Face',
    professional: 'Brain',
    hardcore: 'Neural'
  };
  let history = [];
  let mode = 'professional';
  let busy = false;
  let available = false;
  let controller = null;
  let lastFailedMessage = null;

  function updateSend() {
    sendButton.disabled = busy || !available || !input.value.trim();
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

  async function checkConnection() {
    const button = byId('check-connection');
    button.disabled = true;
    button.textContent = 'Checking…';
    byId('connection-detail').textContent = 'Checking your local Codex installation…';
    const statusController = new AbortController();
    const timeout = setTimeout(() => statusController.abort(), 15000);
    try {
      const response = await fetch('/api/status', { signal: statusController.signal, cache: 'no-store' });
      if (!response.ok) throw new Error('The local server could not check Codex.');
      const status = await response.json();
      available = Boolean(status.available);
      byId('header-status').textContent = available ? 'Ready' : 'Needs setup';
      byId('connection-detail').textContent = status.detail || (available ? 'Codex is available. Your first message will verify the AI connection.' : 'Install Codex and sign in on this laptop, then check again.');
      byId('header-dot').className = `status-dot ${available ? 'ready' : 'error'}`;
      byId('connection-dot').className = `status-dot ${available ? 'ready' : 'error'}`;
      byId('connection-banner').classList.toggle('warning', !available);
      if (!available) byId('connection-banner').hidden = false;
      byId('runtime-label').textContent = status.version ? `LOCAL / CODEX ${String(status.version).replace(/^codex(?:-cli)?\s*/i, '').slice(0, 40)}` : 'LOCAL INTERFACE / CODEX BRIDGE';
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
      requestStatusText.textContent = 'Still waiting for Codex. Your proxy or the AI service may take a little time.';
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
      if (!response.ok) throw new Error(data && typeof data.error === 'string' ? data.error : 'Bacy could not get a response. Check Codex sign-in and your proxy, then try again.');
      const reply = data && (data.reply || data.response || data.message);
      if (typeof reply !== 'string' || !reply.trim()) throw new Error('Codex returned no answer. Please try again.');
      history = [...messages, { role: 'assistant', content: reply }].slice(-20);
      appendMessage('assistant', reply);
      byId('header-status').textContent = 'Connected';
      byId('connection-detail').textContent = 'Real AI through your Codex account. Tools and service connections are not enabled yet.';
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
    const retry = lastFailedMessage === content;
    submitMessage(content, retry);
  });
  input.addEventListener('input', updateSend);
  input.addEventListener('keydown', event => {
    if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
      event.preventDefault();
      if (!sendButton.disabled) form.requestSubmit();
    }
  });
  byId('cancel-request').addEventListener('click', () => { if (controller) controller.abort(); });
  byId('retry-request').addEventListener('click', () => { if (lastFailedMessage) submitMessage(lastFailedMessage, true); });
  byId('check-connection').addEventListener('click', checkConnection);
  clearButton.addEventListener('click', () => {
    if (busy) return;
    for (const article of log.querySelectorAll('.message')) article.remove();
    history = [];
    lastFailedMessage = null;
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
    mode = button.dataset.mode;
    document.body.dataset.mode = mode;
    byId('mode-caption').textContent = modeCaptions[mode];
    document.querySelectorAll('button[data-mode]').forEach(other => other.setAttribute('aria-pressed', String(other === button)));
  }));
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
  let audioFrame;
  let previousTheme = 'dark';
  window.bacyAudioLevel = 0;
  function stopMicrophone() {
    if (audioFrame) cancelAnimationFrame(audioFrame);
    if (microphone) microphone.getTracks().forEach(track => track.stop());
    if (audioContext) audioContext.close().catch(() => {});
    microphone = null;
    audioContext = null;
    window.bacyAudioLevel = 0;
    document.body.classList.remove('is-listening');
    document.body.dataset.theme = previousTheme;
    updateThemeLabel();
    byId('microphone-button').setAttribute('aria-pressed', 'false');
    byId('microphone-button').setAttribute('aria-label', 'Start microphone visualization');
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
      document.body.dataset.theme = 'light';
      updateThemeLabel();
      byId('microphone-note').hidden = false;
      byId('microphone-button').setAttribute('aria-pressed', 'true');
      byId('microphone-button').setAttribute('aria-label', 'Stop microphone visualization');
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
  window.addEventListener('pagehide', stopMicrophone);
  checkConnection();
})();
