'use strict';
let prompt = '';
process.stdin.setEncoding('utf8');
process.stdin.on('data', value => { prompt += value; });
process.stdin.on('end', () => {
  const mode = process.argv[2];
  if (mode === 'hang') return setInterval(() => {}, 1000);
  const output = { type: 'item.completed', item: { id: 'answer', type: 'agent_message', text: 'A real protocol test: café 🤖. ' + (prompt.includes('$(do-not-execute)') ? 'Input preserved.' : '') } };
  if (mode === 'structured') output.item.text = JSON.stringify({ reply: 'A structured reply.', mood: 'happy', preview: { title: 'Example', kind: 'flow', items: [{ label: 'Start', detail: 'A first step.' }] } });
  if (mode === 'invalid-structured') output.item.text = '{}';
  const bytes = Buffer.from(JSON.stringify(output) + '\n');
  const split = Math.max(1, bytes.indexOf(Buffer.from('🤖')) + 1);
  process.stdout.write(bytes.subarray(0, split));
  setTimeout(() => {
    process.stdout.write(bytes.subarray(split));
    if (mode === 'fail') {
      process.stdout.write(JSON.stringify({ type: 'turn.failed', error: { message: '401 authentication failed' } }) + '\n');
      process.exitCode = 1;
    } else if (mode !== 'incomplete') process.stdout.write(JSON.stringify({ type: 'turn.completed', usage: { input_tokens: 1, output_tokens: 1 } }));
  }, 10);
});
