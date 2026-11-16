// 프런트 원본 없이 제공된 번들의 실제 추출 핸들러를 실행하는 회귀 테스트.
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import assert from 'node:assert/strict';

const bundle = readFileSync(new URL('../../main/resources/static/assets/index-CqeESGX6.js', import.meta.url), 'utf8');
const start = bundle.indexOf('O=async G=>{if(G.preventDefault(),!!R.trim())');
assert.ok(start >= 0, 'URL 추출 핸들러를 찾을 수 있어야 한다');
const end = bundle.indexOf(',D=()=>', start);
assert.ok(end > start);
const handlerSource = bundle.slice(start + 2, end);

function setup(fetchImage, imageUrl = 'https://cdn.example.com/shirt.png') {
  const events = [];
  let image = { name: 'previous-image.png' };
  const factory = new Function('Fj', 'w', 'C', 'N', 'g', 'Qe', 'fetch', 'p', 'T', 'f', 'R', 'window', 'console',
    `return (${handlerSource});`);
  const handler = factory(async () => ({ name: '셔츠', imageUrl }),
    value => events.push(['draft', value]), { name: '', type: 'TOP', attributes: [] },
    () => { image = null; }, value => { image = value; },
    { success: text => events.push(['success', text]), warning: text => events.push(['warning', text]), error: text => events.push(['error', text]) },
    fetchImage, value => events.push(['loading', value]), () => {}, () => {},
    'https://www.musinsa.com/products/1', { location: { href: 'https://otboo.example/' } }, { error() {} });
  return { run: () => handler({ preventDefault() {} }), events, get image() { return image; } };
}

test('이미지 다운로드 완료 후 성공을 알리고 MIME 타입을 보존한다', async () => {
  let resolve;
  const context = setup(() => new Promise(done => { resolve = done; }));
  const pending = context.run();
  await Promise.resolve();
  assert.equal(context.events.some(e => e[0] === 'success'), false);
  resolve({ ok: true, blob: async () => new Blob(['png'], { type: 'image/png' }) });
  await pending;
  assert.equal(context.image.type, 'image/png');
  assert.equal(context.image.name, 'shirt.png');
  assert.equal(context.events.filter(e => e[0] === 'success').length, 1);
  assert.deepEqual(context.events.at(-1), ['loading', false]);
});

for (const [name, fetchImage] of [
  ['CORS/네트워크 실패', async () => { throw new TypeError('Failed to fetch'); }],
  ['HTTP 오류', async () => ({ ok: false, status: 403 })],
  ['이미지가 아닌 응답', async () => ({ ok: true, blob: async () => new Blob(['error'], { type: 'text/html' }) })],
  ['빈 이미지', async () => ({ ok: true, blob: async () => new Blob([], { type: 'image/png' }) })],
  ['5MiB 초과 이미지', async () => ({ ok: true, blob: async () => new Blob([new Uint8Array(5 * 1024 * 1024 + 1)], { type: 'image/png' }) })]
]) {
  test(`${name} 시 초기값은 유지하고 기존 이미지를 지운 뒤 수동 선택을 안내한다`, async () => {
    const context = setup(fetchImage);
    await context.run();
    assert.equal(context.image, null);
    assert.equal(context.events.find(e => e[0] === 'draft')[1].name, '셔츠');
    assert.equal(context.events.some(e => e[0] === 'success'), false);
    assert.equal(context.events.filter(e => e[0] === 'warning').length, 1);
    assert.deepEqual(context.events.at(-1), ['loading', false]);
  });
}

test('이미지 URL이 없으면 요청하지 않고 수동 선택을 안내한다', async () => {
  const context = setup(() => { assert.fail('이미지 요청 금지'); }, null);
  await context.run();
  assert.equal(context.image, null);
  assert.equal(context.events.filter(e => e[0] === 'warning').length, 1);
});
