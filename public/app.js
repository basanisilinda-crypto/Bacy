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
    normal: 'A friendly face for your everyday questions and ideas.',
    professional: 'A point-cloud presence. Your future connections, held in view.',
    hardcore: 'A neural view. The same AI, with a different visual presence.'
  };
  let history = [];
  let mode = 'professional';
  let busy = false;
  let available = false;
  let controller = null;
  let lastFailedMessage = null;

  function drawPoints(target, bounds, step, phase) {
    const fragment = document.createDocumentFragment();
    const ns = 'http://www.w3.org/2000/svg';
    for (let y = bounds.y; y < bounds.y + bounds.height; y += step) {
      for (let x = bounds.x; x < bounds.x + bounds.width; x += step) {
        const circle = document.createElementNS(ns, 'circle');
        const wave = (Math.sin(x * .081 + y * .057 + phase) + 1) / 2;
        const center = Math.max(.15, 1 - Math.abs(x - 230) / 240);
        circle.setAttribute('cx', String(x + Math.sin(y * .033) * 1.25));
        circle.setAttribute('cy', String(y));
        circle.setAttribute('r', String(.45 + wave * .65));
        circle.setAttribute('fill', 'url(#point-light)');
        circle.setAttribute('opacity', String(.12 + wave * center * .75));
        fragment.append(circle);
      }
    }
    byId(target).append(fragment);
  }
  drawPoints('human-points', { x: 23, y: 33, width: 410, height: 321 }, 4.5, 1);
  drawPoints('brain-points', { x: 73, y: 68, width: 315, height: 251 }, 5, 2);

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
      byId('header-status').textContent = available ? 'Codex available' : 'Codex needs setup';
      byId('connection-detail').textContent = status.detail || (available ? 'Codex is available. Your first message will verify the AI connection.' : 'Install Codex and sign in on this laptop, then check again.');
      byId('header-dot').className = `status-dot ${available ? 'ready' : 'error'}`;
      byId('connection-dot').className = `status-dot ${available ? 'ready' : 'error'}`;
      byId('connection-banner').classList.toggle('warning', !available);
      byId('runtime-label').textContent = status.version ? `LOCAL / CODEX ${String(status.version).replace(/^codex(?:-cli)?\s*/i, '').slice(0, 40)}` : 'LOCAL INTERFACE / CODEX BRIDGE';
    } catch (error) {
      available = false;
      byId('header-status').textContent = 'Connection unavailable';
      byId('connection-detail').textContent = 'Could not reach the local Bacy server. Check that it is still running in PowerShell.';
      byId('header-dot').className = 'status-dot error';
      byId('connection-dot').className = 'status-dot error';
      byId('connection-banner').classList.add('warning');
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
      byId('header-status').textContent = 'AI connected';
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
  checkConnection();
})();
