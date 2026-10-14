'use strict';
(() => {
  const $ = id => document.getElementById(id);
  const token = document.querySelector('meta[name="bacy-token"]').content;
  const input = $('message-input');
  const form = $('chat-form');
  const sendButton = $('send-button');
  const micButton = $('mic-button');
  const messagesEl = $('messages');
  const emptyState = $('empty-state');
  const voiceState = $('voice-state');
  const voiceStateText = $('voice-state-text');
  const errorBar = $('error-bar');
  const errorText = $('error-text');

  const HISTORY_KEY = 'bacy-conversation-v4';
  const PREFS_KEY = 'bacy-prefs-v4';
  let history = [];
  let busy = false;
  let lastFailed = '';
  let lastFailedEl = null;
  let chatController = null;
  let aiConfigured = false;
  let aiVerified = false;
  let voiceConnected = false;
  let voiceVerified = false;
  let voiceId = '';
  let gmailConnected = false;
  let gmailConfigured = false;
  let currentView = 'chat';

  let prefs = { speak: true, voiceQuality: 'fast', wakePhrase: 'Hey Bacy' };
  try { prefs = { ...prefs, ...(JSON.parse(localStorage.getItem(PREFS_KEY) || '{}') || {}) }; } catch {}
  $('speak-replies').checked = prefs.speak !== false;
  $('voice-quality').value = prefs.voiceQuality === 'natural' ? 'natural' : 'fast';
  $('wake-phrase').value = prefs.wakePhrase || 'Hey Bacy';

  function savePrefs() {
    prefs = { speak: $('speak-replies').checked, voiceQuality: $('voice-quality').value, wakePhrase: $('wake-phrase').value.trim() || 'Hey Bacy' };
    localStorage.setItem(PREFS_KEY, JSON.stringify(prefs));
    updateVoiceOutputLabel();
  }

  function getJson(url) {
    return fetch(url, { cache: 'no-store' }).then(async response => {
      const data = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(data.error || 'Bacy could not load that connection.');
      return data;
    });
  }

  async function postJson(url, body = {}, signal) {
    const response = await fetch(url, {
      method: 'POST', signal,
      headers: { 'Content-Type': 'application/json', 'X-Bacy-Token': token },
      body: JSON.stringify(body)
    });
    const data = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(data.error || 'Bacy could not complete that request.');
    return data;
  }

  function toast(message) {
    const el = $('toast');
    el.textContent = message; el.hidden = false;
    clearTimeout(toast.timer); toast.timer = setTimeout(() => { el.hidden = true; }, 3200);
  }

  function setError(message, retry = true) {
    errorText.textContent = message || '';
    errorBar.hidden = !message;
    $('retry-button').hidden = !retry || !lastFailed;
  }
  function clearError() { setError(''); }

  function setStatusDot(id, state) {
    const el = $(id); if (!el) return;
    el.className = 'status-dot' + (state ? ' ' + state : '');
  }

  function updateOverallStatus() {
    const rail = $('rail-status');
    rail.className = 'rail-status ' + (aiConfigured ? 'online' : 'error');
    $('status-line').textContent = aiConfigured
      ? (gmailConnected ? 'Gemini online · Gmail connected' : 'Gemini online')
      : 'Gemini connection required';
  }

  function switchView(name) {
    currentView = name;
    document.querySelectorAll('.view').forEach(view => { view.hidden = view.id !== name + '-view'; });
    document.querySelectorAll('.rail-button').forEach(button => button.classList.toggle('active', button.dataset.panel === name));
    $('mobile-sheet').hidden = true;
    if (name === 'chat') setTimeout(() => input.focus(), 0);
  }

  document.querySelectorAll('[data-panel]').forEach(button => button.addEventListener('click', () => switchView(button.dataset.panel)));
  $('home-button').addEventListener('click', () => switchView('chat'));
  $('mobile-menu').addEventListener('click', () => { $('mobile-sheet').hidden = !$('mobile-sheet').hidden; });

  function saveHistory() {
    try { localStorage.setItem(HISTORY_KEY, JSON.stringify(history.slice(-20))); } catch {}
  }

  function appendMessage(role, content, { store = true, latency = '' } = {}) {
    emptyState.hidden = true;
    const article = document.createElement('article');
    article.className = 'message ' + role;
    const avatar = document.createElement('div'); avatar.className = 'avatar'; avatar.textContent = role === 'user' ? 'YOU' : 'B';
    const wrap = document.createElement('div'); wrap.className = 'message-content';
    const p = document.createElement('p'); p.textContent = content;
    wrap.append(p);
    if (latency) { const meta = document.createElement('div'); meta.className = 'message-meta'; meta.textContent = latency; wrap.append(meta); }
    article.append(avatar, wrap); messagesEl.append(article); messagesEl.scrollTop = messagesEl.scrollHeight;
    if (store) { history.push({ role, content }); history = history.slice(-20); saveHistory(); }
    return article;
  }

  function renderHistory() {
    try {
      const saved = JSON.parse(localStorage.getItem(HISTORY_KEY) || '[]');
      if (Array.isArray(saved)) history = saved.filter(item => item && ['user','assistant'].includes(item.role) && typeof item.content === 'string').slice(-20);
    } catch {}
    if (!history.length) return;
    emptyState.hidden = true;
    history.forEach(item => appendMessage(item.role, item.content, { store: false }));
  }

  let thinkingEl = null;
  function showThinking() {
    if (thinkingEl) thinkingEl.remove();
    thinkingEl = document.createElement('div'); thinkingEl.className = 'thinking-line';
    thinkingEl.innerHTML = '<span>Bacy is thinking</span><span class="thinking-dots"><i></i><i></i><i></i></span>';
    messagesEl.append(thinkingEl); messagesEl.scrollTop = messagesEl.scrollHeight;
  }
  function hideThinking() { thinkingEl?.remove(); thinkingEl = null; }

  function autosize() { input.style.height = 'auto'; input.style.height = Math.min(150, input.scrollHeight) + 'px'; }
  function updateSend() {
    sendButton.classList.toggle('is-busy', busy);
    sendButton.disabled = busy ? false : !input.value.trim();
    sendButton.setAttribute('aria-label', busy ? 'Stop response' : 'Send');
  }
  input.addEventListener('input', () => { autosize(); updateSend(); });

  function clearConversation() {
    history = []; saveHistory();
    [...messagesEl.querySelectorAll('.message,.thinking-line')].forEach(node => node.remove());
    emptyState.hidden = false; lastFailed = ''; lastFailedEl = null; clearError();
  }
  $('new-chat').addEventListener('click', clearConversation);
  $('clear-history').addEventListener('click', clearConversation);

  async function refreshConnections() {
    const [ai, voice, gmail] = await Promise.allSettled([getJson('/api/status'), getJson('/api/voice/status'), getJson('/api/gmail/status')]);
    if (ai.status === 'fulfilled') {
      aiConfigured = ai.value.provider === 'gemini' && Boolean(ai.value.available);
      aiVerified = Boolean(ai.value.verified);
      $('gemini-status').textContent = aiConfigured ? (aiVerified ? 'Connected' : 'Key saved · ready on first message') : 'Not connected';
      setStatusDot('gemini-dot', aiConfigured ? 'ready' : 'error');
    } else {
      aiConfigured = false; $('gemini-status').textContent = 'Local server error'; setStatusDot('gemini-dot', 'error');
    }
    if (voice.status === 'fulfilled') {
      voiceConnected = Boolean(voice.value.configured); voiceVerified = Boolean(voice.value.verified); voiceId = voice.value.voiceId || '';
      $('eleven-status').textContent = voiceConnected ? (voiceVerified ? 'Connected' : 'Key saved') : 'Optional natural voice';
      setStatusDot('eleven-dot', voiceConnected ? 'ready' : '');
      if (voiceConnected && voiceVerified) loadVoices().catch(() => {});
    }
    if (gmail.status === 'fulfilled') {
      gmailConfigured = Boolean(gmail.value.configured); gmailConnected = Boolean(gmail.value.connected);
      $('gmail-status').textContent = gmailConnected ? (gmail.value.verified?'Verified Gmail MCP':'Google signed in · MCP unchecked') : gmailConfigured ? 'OAuth setup saved · connect Google' : 'Not connected';
      setStatusDot('gmail-dot', gmailConnected ? 'ready' : gmailConfigured ? 'wait' : '');
      $('disconnect-gmail').hidden = !gmailConnected;
      $('connect-gmail').textContent = gmailConnected ? 'Reconnect Google' : 'Save & connect Google';
    }
    updateOverallStatus(); updateVoiceOutputLabel();
    $('first-run').hidden = aiConfigured;
  }

  async function loadVoices() {
    const data = await postJson('/api/voice/voices');
    const select = $('voice-select'); select.replaceChildren();
    const list = Array.isArray(data.voices) ? data.voices : [];
    if (!list.length) { const option = document.createElement('option'); option.textContent = 'No voices returned'; select.append(option); return; }
    list.forEach(item => {
      const option = document.createElement('option'); option.value = item.id;
      option.textContent = item.name + ([item.age,item.gender,item.accent].filter(Boolean).length ? ' · ' + [item.age,item.gender,item.accent].filter(Boolean).join(' · ') : '');
      select.append(option);
    });
    if (list.some(v => v.id === voiceId)) select.value = voiceId;
  }

  async function connectGemini(key, remember, firstRun = false) {
    const clean = String(key || '').trim();
    if (!clean) throw new Error('Paste your Gemini API key first.');
    await postJson('/api/ai/configure', { provider: 'gemini', key: clean, remember });
    const tested = await postJson('/api/ai/test');
    aiConfigured = true; aiVerified = true;
    $('gemini-key').value = ''; $('first-gemini-key').value = '';
    $('gemini-status').textContent = 'Connected · ' + (tested.model || 'fast model'); setStatusDot('gemini-dot','ready');
    updateOverallStatus(); $('first-run').hidden = true;
    if (!firstRun) toast('Gemini connected.');
  }

  $('connect-gemini').addEventListener('click', async () => {
    clearError(); const button = $('connect-gemini'); button.disabled = true; button.textContent = 'Connecting…';
    try { await connectGemini($('gemini-key').value, $('remember-gemini').checked); }
    catch (error) { setError(error.message, false); }
    finally { button.disabled = false; button.textContent = 'Connect'; }
  });
  $('first-connect').addEventListener('click', async () => {
    const button = $('first-connect'); $('first-error').textContent = ''; button.disabled = true; button.textContent = 'Connecting…';
    try { await connectGemini($('first-gemini-key').value, $('first-remember').checked, true); switchView('chat'); }
    catch (error) { $('first-error').textContent = error.message; }
    finally { button.disabled = false; button.textContent = 'Connect & open Bacy'; }
  });

  $('connect-eleven').addEventListener('click', async () => {
    const button = $('connect-eleven'); button.disabled = true; button.textContent = 'Connecting…'; clearError();
    try {
      await postJson('/api/voice/configure', { key: $('eleven-key').value.trim(), remember: $('remember-eleven').checked, voiceId: $('voice-select').value || undefined });
      const tested = await postJson('/api/voice/test');
      voiceConnected = true; voiceVerified = true; voiceId = tested.voiceId || '';
      $('eleven-key').value = ''; $('eleven-status').textContent = 'Connected'; setStatusDot('eleven-dot','ready');
      const select = $('voice-select'); select.replaceChildren();
      (tested.voices || []).forEach(item => { const option = document.createElement('option'); option.value = item.id; option.textContent = item.name + ([item.age,item.gender,item.accent].filter(Boolean).length ? ' · ' + [item.age,item.gender,item.accent].filter(Boolean).join(' · ') : ''); select.append(option); });
      if (voiceId) { select.value = voiceId; await postJson('/api/voice/configure', { voiceId, remember: $('remember-eleven').checked }); }
      toast('ElevenLabs connected. Choose Natural voice in Settings when you want it.'); updateVoiceOutputLabel();
    } catch (error) { setError(error.message, false); }
    finally { button.disabled = false; button.textContent = 'Connect'; }
  });
  $('voice-select').addEventListener('change', async () => {
    if (!$('voice-select').value || !voiceConnected) return;
    try { voiceId = $('voice-select').value; await postJson('/api/voice/configure', { voiceId, remember: $('remember-eleven').checked }); toast('Voice changed.'); }
    catch (error) { setError(error.message, false); }
  });

  $('connect-gmail').addEventListener('click', async () => {
    const button = $('connect-gmail'); button.disabled = true; button.textContent = 'Preparing Google…'; clearError();
    try {
      const clientId = $('gmail-client-id').value.trim(), clientSecret = $('gmail-client-secret').value.trim();
      if (clientId || clientSecret || !gmailConfigured) await postJson('/api/gmail/configure', { clientId, clientSecret, remember: $('remember-gmail').checked });
      const result = await postJson('/api/gmail/oauth/start');
      window.location.assign(result.url);
    } catch (error) { setError(error.message, false); button.disabled = false; button.textContent = gmailConnected ? 'Reconnect Google' : 'Save & connect Google'; }
  });
  $('disconnect-gmail').addEventListener('click', async () => {
    try { await postJson('/api/gmail/disconnect'); gmailConnected = false; $('gmail-status').textContent = 'Disconnected'; setStatusDot('gmail-dot','wait'); $('disconnect-gmail').hidden = true; updateOverallStatus(); toast('Gmail disconnected.'); }
    catch (error) { setError(error.message, false); }
  });

  $('speak-replies').addEventListener('change', savePrefs);
  $('voice-quality').addEventListener('change', savePrefs);
  $('wake-phrase').addEventListener('change', savePrefs);
  function updateVoiceOutputLabel() {
    const enabled = $('speak-replies').checked;
    const quality = $('voice-quality').value;
    $('voice-output-label').textContent = enabled ? (quality === 'natural' ? 'Natural voice' : 'Fast voice') : 'Voice off';
    $('voice-output-button').classList.toggle('active', enabled);
  }
  $('voice-output-button').addEventListener('click', () => {
    $('speak-replies').checked = !$('speak-replies').checked; savePrefs();
    if (!$('speak-replies').checked) stopSpeaking();
  });

  // ---------- Voice output ----------
  let currentAudio = null;
  let currentUtterance = null;
  let speaking = false, speechGeneration=0,voiceController=null,playbackFinish=null;
  function stopSpeaking() {
    speechGeneration++;voiceController?.abort();voiceController=null;playbackFinish?.();playbackFinish=null;
    window.speechSynthesis?.cancel();
    if (currentAudio) { try { currentAudio.pause(); currentAudio.src = ''; } catch {} }
    currentAudio = null; currentUtterance = null; speaking = false;
    document.body.classList.remove('speaking');
    restartContinuousSoon();
  }
  function chooseDeviceVoice() {
    const voices = window.speechSynthesis?.getVoices?.() || [];
    return voices.find(v => /^en(-|_)?ZA/i.test(v.lang)) || voices.find(v => /^en(-|_)?GB/i.test(v.lang)) || voices.find(v => /^en/i.test(v.lang)) || voices[0];
  }
  function speakDevice(text) {
    return new Promise(resolve=>{
      if(!window.speechSynthesis)return resolve();
      const session=speechGeneration;window.speechSynthesis.cancel();
      const u=new SpeechSynthesisUtterance(String(text).slice(0,3000));currentUtterance=u;let ended=false;
      const finish=()=>{if(ended)return;ended=true;clearTimeout(startTimer);if(playbackFinish===finish)playbackFinish=null;
        if(session===speechGeneration){currentUtterance=null;speaking=false;document.body.classList.remove('speaking');restartContinuousSoon();}resolve();};
      const startTimer=setTimeout(()=>{if(currentUtterance===u)window.speechSynthesis.cancel();finish();},4000);
      playbackFinish=finish;u.onstart=()=>clearTimeout(startTimer);u.onend=u.onerror=finish;
      const v=chooseDeviceVoice();if(v)u.voice=v;u.rate=1.03;u.pitch=1;u.volume=1;
      speaking=true;pauseContinuousInput();document.body.classList.add('speaking');window.speechSynthesis.speak(u);
    });
  }
  async function speakNatural(text) {
    const session=speechGeneration,controller=new AbortController();voiceController=controller;
    const timer=setTimeout(()=>controller.abort(),5000);let audio=null,url=null,reader=null;
    speaking=true;pauseContinuousInput();document.body.classList.add('speaking');
    try{
      const response=await fetch('/api/voice/speak-stream',{method:'POST',signal:controller.signal,headers:{'Content-Type':'application/json','X-Bacy-Token':token},body:JSON.stringify({text:String(text).slice(0,3000)})});
      if(!response.ok){const data=await response.json().catch(()=>({}));throw new Error(data.error||'Natural voice could not start.');}
      const canStream=response.body&&window.MediaSource&&MediaSource.isTypeSupported?.('audio/mpeg');
      let media;if(canStream){media=new MediaSource();url=URL.createObjectURL(media);}else url=URL.createObjectURL(await response.blob());
      if(session!==speechGeneration||controller.signal.aborted)throw new DOMException('Voice stopped','AbortError');
      audio=new Audio(url);currentAudio=audio;
      await new Promise((resolve,reject)=>{
        let finished=false;
        const finish=error=>{if(finished)return;finished=true;if(playbackFinish===cancel)playbackFinish=null;controller.signal.removeEventListener('abort',cancel);reader?.cancel().catch(()=>{});error?reject(error):resolve();};
        const cancel=()=>finish(new DOMException('Voice stopped','AbortError'));playbackFinish=cancel;
        controller.signal.addEventListener('abort',cancel,{once:true});
        audio.onplaying=()=>clearTimeout(timer);audio.onended=()=>finish();audio.onerror=()=>finish(new Error('The voice audio could not play.'));
        if(!canStream){audio.play().catch(finish);return;}
        media.addEventListener('sourceopen',async()=>{
          let source;try{source=media.addSourceBuffer('audio/mpeg');}catch(error){finish(error);return;}
          reader=response.body.getReader();const queue=[];let done=false,started=false;
          const pump=()=>{if(finished||source.updating)return;if(queue.length){try{source.appendBuffer(queue.shift());if(!started){started=true;audio.play().catch(finish);}}catch(error){finish(error);}}
            else if(done&&media.readyState==='open'){try{media.endOfStream();}catch{}}};
          source.addEventListener('updateend',pump);
          try{while(!finished){const part=await reader.read();if(part.done){done=true;pump();break;}if(part.value?.byteLength){queue.push(part.value);pump();}}}catch(error){finish(error);}
        },{once:true});
      });
    }finally{
      clearTimeout(timer);controller.abort();reader?.cancel().catch(()=>{});if(audio){audio.pause();audio.src='';}if(url)URL.revokeObjectURL(url);
      if(voiceController===controller)voiceController=null;
      if(session===speechGeneration){currentAudio=null;speaking=false;document.body.classList.remove('speaking');restartContinuousSoon();}
    }
  }
  function speak(text) {
    if (!$('speak-replies').checked || !text) return;
    stopSpeaking();
    const session=speechGeneration;const natural = $('voice-quality').value === 'natural' && voiceConnected;
    (natural ? speakNatural(text) : speakDevice(text)).catch(error => {if(session!==speechGeneration)return; if (natural) { toast('Natural voice was slow/unavailable. Switched to fast device voice.'); speakDevice(text).catch(()=>{}); } else setError(error.message, false); });
  }

  // ---------- Voice input ----------
  let micStream = null, recognition = null, recognitionRunning = false;
  let micGeneration=0,recognitionStarting=false,restartTimer=null,voiceTurnPending=false,tapTranscript='',tapCompleting=false,wakeUntil=0,micChanging=false,transcriptionController=null;
  let voiceMode = 'off'; // off | tap | handsfree | live
  let recorder = null, recorderChunks = [], fallbackNeeded = false;
  let audioContext = null, analyser = null, levelTimer = null, audioLevel = 0;
  let cloudRecorder = null, cloudChunks = [], cloudTimer = null, cloudTranscribing = false, cloudFallback = false;

  function recognitionSupported() { return Boolean(window.SpeechRecognition || window.webkitSpeechRecognition); }
  async function ensureMic() {
    if (micStream) return micStream;
    if (!navigator.mediaDevices?.getUserMedia) throw new Error('Use current Chrome or Edge for microphone access.');
    try { const generation=micGeneration;const stream=await navigator.mediaDevices.getUserMedia({ audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true } });if(generation!==micGeneration){stream.getTracks().forEach(track=>track.stop());throw new Error('Microphone request cancelled.');}micStream=stream; }
    catch (error) {
      if (error?.name === 'NotAllowedError') throw new Error('Microphone permission is blocked. Allow the microphone from the browser address bar.');
      if (error?.name === 'NotFoundError') throw new Error('Windows could not find a microphone.');
      throw new Error('Bacy could not open the microphone.');
    }
    try {
      audioContext = new (window.AudioContext || window.webkitAudioContext)(); await audioContext.resume?.();
      const source = audioContext.createMediaStreamSource(micStream); analyser = audioContext.createAnalyser(); analyser.fftSize = 256; source.connect(analyser);
      const data = new Uint8Array(analyser.frequencyBinCount);
      const measure = () => { if (!analyser) return; analyser.getByteFrequencyData(data); audioLevel = data.reduce((a,b)=>a+b,0)/(data.length*255); levelTimer = requestAnimationFrame(measure); }; measure();
    } catch {}
    return micStream;
  }
  function setVoiceState(text = '') { voiceStateText.textContent = text; voiceState.hidden = !text; }
  function setVoiceMode(mode) {
    voiceMode = mode; micButton.setAttribute('aria-pressed', String(mode === 'tap'));
    $('handsfree-button').classList.toggle('active', mode === 'handsfree'); $('live-button').classList.toggle('active', mode === 'live');
    if (mode === 'off') setVoiceState('');
    else if (mode === 'handsfree') setVoiceState('Say “' + (prefs.wakePhrase || 'Hey Bacy') + '”…');
    else if (mode === 'live') setVoiceState('Live talk · listening…');
    else setVoiceState('Listening…');
  }
  function stopRecorder(discard = true) {
    if (!recorder) return;
    try { if (recorder.state !== 'inactive') recorder.stop(); } catch {}
    if (discard) recorderChunks = [];
  }
  function stopCloud() {
    cloudFallback = false; cloudTranscribing = false;
    if (cloudTimer) clearTimeout(cloudTimer); cloudTimer = null;
    if (cloudRecorder) { try { if (cloudRecorder.state !== 'inactive') cloudRecorder.stop(); } catch {} }
    cloudRecorder = null; cloudChunks = [];
  }
  function disposeRecognition(){const previous=recognition;recognition=null;recognitionRunning=false;recognitionStarting=false;try{previous?.abort();}catch{}}
  function stopMicAll() {
    micGeneration++;clearTimeout(restartTimer);restartTimer=null;wakeUntil=0;transcriptionController?.abort();transcriptionController=null;
    disposeRecognition();
    stopRecorder(); recorder = null; fallbackNeeded = false; stopCloud();
    if (levelTimer) cancelAnimationFrame(levelTimer); levelTimer = null; analyser = null;
    audioContext?.close?.().catch(()=>{}); audioContext = null;
    micStream?.getTracks().forEach(track => track.stop()); micStream = null; audioLevel = 0; setVoiceMode('off');
  }
  function preferredMime() { return ['audio/webm;codecs=opus','audio/webm','audio/ogg;codecs=opus'].find(type => MediaRecorder.isTypeSupported?.(type)) || ''; }
  function startPassiveRecorder() {
    if (!voiceConnected || !window.MediaRecorder || recorder) return;
    const chunks=[];recorderChunks=chunks; const type = preferredMime(); recorder = new MediaRecorder(micStream, type ? { mimeType:type } : undefined);
    recorder.ondataavailable = event => { if (event.data?.size) chunks.push(event.data); };
    recorder.start(180);
  }
  async function transcribeChunks(active, chunks) {
    const mime = active?.mimeType || chunks[0]?.type || 'audio/webm';
    const blob = new Blob(chunks, { type:mime });
    if (blob.size < 300) throw new Error('The recording was too short.');
    setVoiceState('Transcribing…');
    transcriptionController?.abort();transcriptionController=new AbortController();const controller=transcriptionController;const timer=setTimeout(()=>controller.abort(),35000);
    try{const response = await fetch('/api/voice/transcribe', { method:'POST', signal:controller.signal, headers:{ 'Content-Type':mime, 'X-Bacy-Token':token }, body:blob });
    const data = await response.json().catch(()=>({})); if (!response.ok) throw new Error(data.error || 'Voice transcription failed.');
    return String(data.text || '').trim();}finally{clearTimeout(timer);if(transcriptionController===controller)transcriptionController=null;}
  }
  async function finishTapFallback() {
    if (!recorder) return;
    const active = recorder; recorder = null;
    const chunks = recorderChunks; recorderChunks = [];
    await new Promise(resolve => { active.addEventListener('stop', resolve, { once:true }); try { active.stop(); } catch { resolve(); } });
    disposeRecognition();
    if (!voiceConnected) throw new Error('Connect ElevenLabs for transcription fallback.');
    const generation=micGeneration;const text = await transcribeChunks(active, chunks);if(generation!==micGeneration)return;fallbackNeeded = false;stopMicAll();
    await processVoiceText(text, 'tap');
  }
  function makeRecognition(modeAtStart) {
    const R = window.SpeechRecognition || window.webkitSpeechRecognition; if (!R) return null;
    const rec = new R(); rec.lang = navigator.language || 'en-ZA'; rec.interimResults = true; rec.continuous = modeAtStart !== 'tap';
    let hadFinal = false;
    rec.onstart = () => { if(recognition!==rec)return;recognitionStarting=false;recognitionRunning = true; setVoiceState(modeAtStart === 'handsfree' ? 'Say “' + prefs.wakePhrase + '”…' : 'Listening…'); };
    rec.onresult = event => {
      if(recognition!==rec||hadFinal||busy||voiceTurnPending)return;
      let finalText = '', interim = '';
      for (let i=event.resultIndex;i<event.results.length;i++) { const text = event.results[i][0].transcript; if (event.results[i].isFinal) finalText += text; else interim += text; }
      const shown = (finalText + ' ' + interim).trim();if(modeAtStart==='tap'&&shown)tapTranscript=shown; if (shown) setVoiceState(shown);
      if (finalText.trim() && !speaking) {
        hadFinal = true;
        if (modeAtStart === 'tap') { stopMicAll(); }
        else { disposeRecognition(); }
        processVoiceText(finalText, modeAtStart).catch(error => setError(error.message, false));
      }
    };
    rec.onerror = event => {
      if(recognition!==rec)return;recognitionStarting=false;recognitionRunning = false;
      if (['aborted','no-speech'].includes(event.error)) return;
      const networkish = ['network','service-not-allowed'].includes(event.error);
      if (networkish && voiceConnected && window.MediaRecorder) {
        if (modeAtStart === 'tap') { fallbackNeeded = true; setVoiceState('Live transcription unavailable · keep speaking, then tap the mic'); }
        else { cloudFallback = true; setVoiceState('Using ElevenLabs transcription…'); startCloudContinuous().catch(error => setError(error.message, false)); }
      } else {
        setError(event.error === 'not-allowed' ? 'Microphone permission is blocked.' : 'Live transcription failed. Connect ElevenLabs for fallback.', false);
        if (modeAtStart === 'tap') { stopMicAll(); }
      }
    };
    rec.onend = () => {
      if(recognition!==rec)return;recognitionStarting=false;recognitionRunning = false;
      if(modeAtStart==='tap'&&!hadFinal&&!recorder){const text=tapCompleting?tapTranscript:'';stopMicAll();tapCompleting=false;if(text)processVoiceText(text,'tap').catch(error=>setError(error.message,false));else setVoiceState('No speech detected. Tap the mic to try again.');return;}
      if (modeAtStart === 'tap' && !hadFinal && recorder && !fallbackNeeded) { fallbackNeeded = true; setVoiceState(voiceConnected ? 'Tap the mic to finish and transcribe' : 'No speech was detected'); }
      if (modeAtStart !== 'tap' && voiceMode === modeAtStart && !busy && !speaking && !cloudFallback) restartContinuousSoon();
    };
    return rec;
  }
  function startRecognition(mode) {
    if (!recognitionSupported()) return false;if(recognitionRunning||recognitionStarting)return true;disposeRecognition();recognitionStarting=true;
    recognition = makeRecognition(mode); try { recognition.start(); return true; } catch { disposeRecognition();return false; }
  }
  async function startTap() {
    await ensureMic();tapTranscript=''; setVoiceMode('tap'); fallbackNeeded = false;
    if (voiceConnected) startPassiveRecorder();
    if (!startRecognition('tap')) {
      if (!voiceConnected) throw new Error('Live transcription is unavailable. Connect ElevenLabs or use current Chrome/Edge.');
      fallbackNeeded = true; setVoiceState('Recording · tap the mic when finished');
    }
  }
  function escapeRegex(value) { return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'); }
  async function processVoiceText(raw, modeAtStart) {
    let text = String(raw || '').trim(); if (!text) return;
    if (modeAtStart === 'handsfree') {
      const phrase = (prefs.wakePhrase || 'Hey Bacy').trim();
      const re = new RegExp('^' + escapeRegex(phrase) + '[,!. ]*(.*)$','i');
      const match = text.match(re);
      if(match){wakeUntil=Date.now()+10000;text=match[1].trim();if(!text){setVoiceState('Yes, Boss. I’m listening…');restartContinuousSoon();return;}}else if(Date.now()>wakeUntil){setVoiceState('Say “'+phrase+'”…');restartContinuousSoon();return;}wakeUntil=0;
    }
    setVoiceState('');
    if(voiceTurnPending)return;voiceTurnPending=true;try{await handleInput(text);}finally{voiceTurnPending=false;restartContinuousSoon();}
  }
  function pauseContinuousInput() {
    if (!['handsfree','live'].includes(voiceMode)) return;
    disposeRecognition();transcriptionController?.abort();const fallback=cloudFallback;stopCloud();cloudFallback=fallback;
  }
  function restartContinuousSoon() {
    clearTimeout(restartTimer);restartTimer=null;
    if (!['handsfree','live'].includes(voiceMode) || busy || speaking || voiceTurnPending || !micStream) return;
    restartTimer=setTimeout(() => {
      restartTimer=null;if (!['handsfree','live'].includes(voiceMode) || busy || speaking || voiceTurnPending || !micStream) return;
      if (cloudFallback) startCloudContinuous().catch(error => setError(error.message, false));
      else if (!recognitionRunning && !startRecognition(voiceMode)) {
        if (voiceConnected) { cloudFallback = true; startCloudContinuous().catch(error => setError(error.message,false)); }
      }
    }, 220);
  }
  async function finishCloudSegment(active, chunks, modeAtStart, send) {
    if (!active) return;const generation=micGeneration;
    if (active.state !== 'inactive') await new Promise(resolve => { active.addEventListener('stop', resolve, {once:true}); try { active.stop(); } catch { resolve(); } });
    if (!send || !chunks.length || voiceMode !== modeAtStart) return restartContinuousSoon();
    cloudTranscribing = true;
    try { const text = await transcribeChunks(active, chunks); if (text&&generation===micGeneration) await processVoiceText(text, modeAtStart); }
    catch (error) { if (error.name!=='AbortError'&&!/short|no speech|could not hear/i.test(error.message)) setError(error.message, false); }
    finally { cloudTranscribing = false; restartContinuousSoon(); }
  }
  async function startCloudContinuous() {
    if (!['handsfree','live'].includes(voiceMode) || busy || speaking || cloudRecorder || cloudTranscribing) return;
    await ensureMic(); if (!voiceConnected || !window.MediaRecorder) throw new Error('ElevenLabs is required for cloud transcription fallback.');
    const modeAtStart = voiceMode, type = preferredMime();const chunks=[]; cloudChunks=chunks;
    const active = new MediaRecorder(micStream, type ? {mimeType:type} : undefined); cloudRecorder = active;
    active.ondataavailable = event => { if (event.data?.size) chunks.push(event.data); }; active.start(150);
    const started = performance.now(); let speechStart = 0, lastVoice = 0;
    const monitor = () => {
      if (cloudRecorder !== active || active.state === 'inactive') return;
      if (busy || speaking || voiceMode !== modeAtStart) { cloudRecorder = null; const chunks = cloudChunks; cloudChunks=[]; finishCloudSegment(active,chunks,modeAtStart,false); return; }
      const now = performance.now(); if (audioLevel > 0.02) { if (!speechStart) speechStart = now; lastVoice = now; setVoiceState('Listening…'); }
      if (speechStart && now-lastVoice > 520 && now-speechStart > 280) { cloudRecorder=null; const chunks=cloudChunks; cloudChunks=[]; finishCloudSegment(active,chunks,modeAtStart,true); return; }
      if (speechStart && now-speechStart > 8000) { cloudRecorder=null; const chunks=cloudChunks; cloudChunks=[]; finishCloudSegment(active,chunks,modeAtStart,true); return; }
      if (!speechStart && now-started > 7000) { cloudRecorder=null; const chunks=cloudChunks; cloudChunks=[]; finishCloudSegment(active,chunks,modeAtStart,false); return; }
      cloudTimer = setTimeout(monitor, 75);
    };
    cloudTimer = setTimeout(monitor, 75);
  }
  async function startContinuous(mode) {
    if (voiceMode === mode) { stopMicAll(); return; }
    stopMicAll(); await ensureMic(); setVoiceMode(mode); cloudFallback = false;
    if (!startRecognition(mode)) {
      if (!voiceConnected) throw new Error('Hands-free/Live talk needs Chrome/Edge speech recognition or ElevenLabs.');
      cloudFallback = true; await startCloudContinuous();
    }
  }

  micButton.addEventListener('click', async () => {
    if(micChanging)return;micChanging=true;clearError();
    try {
      if (voiceMode === 'tap' && recorder && (fallbackNeeded || !recognitionRunning)) return await finishTapFallback();
      if(voiceMode==='tap'){tapCompleting=true;const current=recognition;const generation=micGeneration;try{current?.stop();}catch{}await new Promise(resolve=>setTimeout(resolve,350));if(voiceMode==='tap'&&generation===micGeneration){const text=tapTranscript;stopMicAll();if(text)await processVoiceText(text,'tap');else setVoiceState('No speech detected. Tap the mic to try again.');}return;}
      if (voiceMode !== 'off') stopMicAll();
      await startTap();
    } catch (error) { stopMicAll(); setError(error.message, false); }finally{micChanging=false;}
  });
  $('handsfree-button').addEventListener('click', () => startContinuous('handsfree').catch(error => { stopMicAll(); setError(error.message,false); }));
  $('live-button').addEventListener('click', () => startContinuous('live').catch(error => { stopMicAll(); setError(error.message,false); }));

  // ---------- Local Windows commands + chat ----------
  function localReply(userText, reply) {
    appendMessage('user', userText); appendMessage('assistant', reply); speak(reply); input.value=''; autosize(); updateSend();
  }
  async function performLocal(userText, fn) {
    try { const reply = await fn(); localReply(userText, reply); switchView('chat'); }
    catch (error) { lastFailed = userText; setError(error.message, true); }
  }
  async function openApp(id, label) {
    const data = await postJson('/api/apps/open', { id }); return data.message || ('Opened ' + label + '.');
  }
  async function handleInput(raw) {
    const message = String(raw || '').trim(); if (!message || busy) return;
    clearError();
    const command = message.toLowerCase().replace(/[.!?]+$/,'').replace(/^(?:hey )?bacy[, ]+/,'').replace(/^please /,'').trim();
    if (/^(stop|stop speaking|be quiet|quiet)$/.test(command)) { stopSpeaking(); return localReply(message,'Voice stopped.'); }
    const approval=command.match(/^(confirm|cancel) ([a-f0-9]{12})$/);
    if(approval)return performLocal(message,async()=>{const data=await postJson('/api/actions/'+approval[1],{id:approval[2]});return data.message;});
    const remember=message.match(/^remember(?: that)? (.{1,64}?)\s*(?:=|:| is )\s*(.+)$/i);
    if(remember)return performLocal(message,async()=>(await postJson('/api/memory/remember',{key:remember[1],value:remember[2]})).message);
    const forget=message.match(/^forget (.{1,64})$/i);
    if(forget)return performLocal(message,async()=>(await postJson('/api/memory/forget',{key:forget[1]})).message);
    const recall=message.match(/^(?:recall|what do you remember|show memory)(?: (.*))?$/i);
    if(recall)return performLocal(message,async()=>{const data=await postJson('/api/memory',{query:recall[1]||''});return data.facts.length?data.facts.map(item=>item.key+': '+item.value).join('\n'):'No matching saved facts.';});
    const systemMap = { 'show desktop':'show-desktop','show my desktop':'show-desktop','volume up':'volume-up','turn up the volume':'volume-up','volume down':'volume-down','turn down the volume':'volume-down','mute':'mute','unmute':'mute' };
    if (systemMap[command]) return performLocal(message, async () => (await postJson('/api/computer',{action:systemMap[command]})).message);
    const search = command.match(/^(?:search(?: the web)?(?: for)?|look up) (.+)$/); if (search) return performLocal(message, async()=> (await postJson('/api/search',{query:search[1]})).message);
    if (/^(open|show)(?: my)? files$/.test(command)) return performLocal(message, async()=>{ const chosen=await postJson('/api/files/choose'); if(!chosen.selected)return 'No folder was selected.'; return 'Folder access enabled for '+(chosen.name||'the selected folder')+'.'; });
    const app = command.match(/^(?:open|launch|start) (.+)$/);
    if (app) {
      const map={browser:'browser',chrome:'chrome','google chrome':'chrome',edge:'edge','microsoft edge':'edge',firefox:'firefox',word:'word','microsoft word':'word',excel:'excel',outlook:'outlook','vs code':'vscode',notepad:'notepad',calculator:'calculator','file explorer':'explorer',settings:'settings','windows settings':'settings'};
      const id=map[app[1]];
      if (id) return performLocal(message,()=>openApp(id,app[1]));
    }
    if (/\b(gmail|email|emails|inbox|mailbox)\b/i.test(message) && !gmailConnected) {
      switchView('connections'); lastFailed = message; return setError('Connect Gmail MCP in Connections first. Gemini can still answer normal questions.', true);
    }
    if (!aiConfigured) { $('first-run').hidden=false; return; }
    await submitMessage(message);
  }

  function conversationForApi() {
    const items = history.slice(-19);
    while (items.length && items[0].role !== 'user') items.shift();
    const clean = [];
    for (const item of items) {
      if (!clean.length || clean.at(-1).role !== item.role) clean.push({...item,content:String(item.content||'').slice(0,8000)});
    }
    while (clean.length && clean.at(-1).role !== 'user') clean.pop();
    while(clean.length>1&&clean.reduce((total,item)=>total+item.content.length,0)>24000)clean.splice(0,2);
    return clean;
  }

  async function submitMessage(message) {
    if (busy) return;
    busy = true; lastFailed = message; updateSend(); pauseContinuousInput(); clearError();
    const userEl = appendMessage('user', message); lastFailedEl = userEl; input.value=''; autosize(); showThinking();
    const started = performance.now(); chatController = new AbortController();let requestTimedOut=false;const timer=setTimeout(()=>{requestTimedOut=true;chatController?.abort();},40000);
    try {
      const response = await fetch('/api/chat', { method:'POST', signal:chatController.signal, headers:{'Content-Type':'application/json','X-Bacy-Token':token}, body:JSON.stringify({mode:'professional',messages:conversationForApi()}) });
      const data = await response.json().catch(()=>({})); if(!response.ok) throw new Error(data.error || 'Bacy could not get an AI response.');
      const reply=String(data.reply||'').trim(); if(!reply) throw new Error('Gemini returned an empty reply.');
      hideThinking(); const seconds=((performance.now()-started)/1000).toFixed(1); appendMessage('assistant',reply,{latency:seconds+'s'}); $('latency-label').textContent=seconds+'s'; lastFailed=''; lastFailedEl=null;
      busy=false; chatController=null; updateSend(); speak(reply); restartContinuousSoon();
    } catch (error) {
      hideThinking();
      if (error.name === 'AbortError') setError(requestTimedOut?'The AI did not answer within 40 seconds. Check the connection, then retry.':'Response stopped.',requestTimedOut); else setError(error.message, true);
      if (history.at(-1)?.role==='user' && history.at(-1)?.content===message) { history.pop(); saveHistory(); }
      userEl.classList.add('failed'); busy=false; chatController=null; updateSend(); restartContinuousSoon();
    }finally{clearTimeout(timer);}
  }

  form.addEventListener('submit', event => {
    event.preventDefault();
    if (busy) { chatController?.abort(); return; }
    handleInput(input.value);
  });
  input.addEventListener('keydown', event => { if (event.key==='Enter' && !event.shiftKey) { event.preventDefault(); if (busy) chatController?.abort(); else handleInput(input.value); } });
  $('retry-button').addEventListener('click', () => { if (!lastFailed) return; lastFailedEl?.remove(); lastFailedEl=null; clearError(); handleInput(lastFailed); });
  $('dismiss-error').addEventListener('click', clearError);
  document.querySelectorAll('[data-prompt]').forEach(button => button.addEventListener('click', () => handleInput(button.dataset.prompt)));
  document.querySelectorAll('[data-local]').forEach(button => button.addEventListener('click', async () => {
    const [type,value] = button.dataset.local.split(':');
    if (type==='app') await performLocal('Open '+value,()=>openApp(value,value));
    else await performLocal(value.replaceAll('-',' '),async()=> (await postJson('/api/computer',{action:value})).message);
  }));

  window.addEventListener('beforeunload', () => { stopMicAll(); stopSpeaking(); });
  document.addEventListener('keydown', event => { if (event.key==='Escape') { $('mobile-sheet').hidden=true; if (voiceMode!=='off') stopMicAll(); } });

  async function init() {
    renderHistory(); autosize(); updateSend(); updateVoiceOutputLabel();
    const params = new URLSearchParams(location.search);
    if (params.get('gmail') === 'connected') { toast('Gmail MCP connected.'); }
    if (params.get('gmail') === 'error') setError(params.get('message') || 'Gmail connection failed.', false);
    if (params.has('gmail')) window.history.replaceState?.(null,'',location.pathname);
    await refreshConnections();
    if (aiConfigured) { $('first-run').hidden=true; input.focus(); }
  }
  init().catch(error => setError(error.message,false));
})();
