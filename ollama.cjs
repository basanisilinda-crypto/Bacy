'use strict';
const ENDPOINT = 'http://127.0.0.1:11434';
const MODEL = process.env.BACY_OLLAMA_MODEL || 'llama3.2:1b';
async function request(route, options = {}) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 90000);
  const externalAbort = () => controller.abort();
  options.signal?.addEventListener('abort', externalAbort, { once: true });
  try {
    const response = await fetch(ENDPOINT + route, { ...options, signal: controller.signal });
    if (!response.ok) throw new Error('Ollama returned HTTP ' + response.status);
    return await response.json();
  } catch (error) {
    if (error.name === 'AbortError') throw new Error('Ollama took too long or the request was cancelled. Try a smaller model.');
    if (error.cause?.code === 'ECONNREFUSED' || error.message === 'fetch failed') throw new Error('Ollama is not running. Install Ollama, open it, then run: ollama pull ' + MODEL);
    throw error;
  } finally { clearTimeout(timeout); options.signal?.removeEventListener('abort', externalAbort); }
}
async function status() {
  try { const result = await request('/api/tags'); return { available: true, installed: result.models?.some(m => m.name === MODEL || m.model === MODEL), model: MODEL }; }
  catch (error) { return { available: false, installed: false, model: MODEL, error: error.message }; }
}
async function generate(prompt, {signal} = {}) {
  const result = await request('/api/generate', {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({model:MODEL,prompt,stream:false,format:'json',options:{num_predict:850}}), signal});
  return result.response || '';
}
module.exports = { status, generate };
