'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { createElevenLabs, cleanVoices } = require('../elevenlabs.cjs');

function memoryStore(saved = null) {
  let value = saved;
  return {
    async load() { return value; },
    async save(next) { value = next; },
    async clear() { value = null; },
    get value() { return value; }
  };
}

test('ElevenLabs configuration never exposes the key and loads usable voices', async () => {
  const store = memoryStore();
  const calls = [];
  const voice = createElevenLabs({
    env: {}, store,
    transport: async options => {
      calls.push(options);
      return { voices: [{ voice_id: 'voice_123456', name: 'Nova', labels: { age: 'young', gender: 'male', accent: 'South African' } }] };
    }
  });
  const key = 'sk_abcdefghijklmnopqrstuvwxyz123456';
  const configured = await voice.configure({ key, remember: true });
  assert.equal(configured.configured, true);
  assert.equal(JSON.stringify(configured).includes(key), false);
  const tested = await voice.test();
  assert.equal(tested.verified, true);
  assert.equal(tested.voiceId, 'voice_123456');
  assert.equal(tested.voices[0].name, 'Nova');
  assert.equal(calls[0].key, key);
  assert.equal(store.value.key, key);
});

test('ElevenLabs speech uses Flash v2.5 and returns binary audio', async () => {
  const audio = Buffer.from([1,2,3,4]);
  let request;
  const voice = createElevenLabs({
    env: {},
    store: memoryStore({ key: 'sk_abcdefghijklmnopqrstuvwxyz123456', voiceId: 'voice_123456' }),
    transport: async options => { request = options; return options.binary ? audio : { voices: [] }; }
  });
  const result = await voice.synthesize('Hello Boss');
  assert.deepEqual(result, audio);
  assert.match(request.url, /text-to-speech\/voice_123456/);
  assert.equal(request.body.model_id, 'eleven_flash_v2_5');
  assert.equal(request.body.text, 'Hello Boss');
});

test('voice metadata is bounded and invalid IDs are removed', () => {
  const list = cleanVoices({ voices: [{ voice_id: 'good_voice_123', name: 'A'.repeat(150), labels: { age: 'young' } }, { voice_id: 'bad voice id', name: 'Bad' }] });
  assert.equal(list.length, 1);
  assert.equal(list[0].name.length, 100);
});
