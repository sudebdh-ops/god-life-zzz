import test from 'node:test';
import assert from 'node:assert/strict';
import { newPairCode, validPairCode, validateSyncConfig, SyncClient } from '../sync-client.js';

test('pairing code has 128 random bits and cannot be mistaken for short input', () => {
  const code = newPairCode(bytes => bytes.fill(7));
  assert.equal(code.length, 26);
  assert.equal(validPairCode(code), true);
  assert.equal(validPairCode('123456'), false);
});

test('remote origin must be a Supabase HTTPS project', () => {
  assert.equal(validateSyncConfig({ url: 'https://abc.supabase.co', key: 'public-key' }).url,
    'https://abc.supabase.co');
  assert.throws(() => validateSyncConfig({ url: 'http://localhost', key: 'x' }));
  assert.throws(() => validateSyncConfig({ url: 'https://abc.supabase.co.evil.example', key: 'x' }));
});

test('expired session refreshes before authorized RPC and keeps identity', async () => {
  let state = { personId: 'member-one', refreshToken: 'refresh-old', expiresAt: 0 };
  const calls = [];
  const fetcher = async (url, options) => {
    calls.push({ url, options });
    return { ok: true, json: async () => url.includes('/auth/v1/token')
      ? { access_token: 'fresh-token', refresh_token: 'refresh-new', expires_in: 3600 }
      : { workspaceId: 'group-one', personId: 'member-one', routines: [], completions: [] } };
  };
  const client = new SyncClient({ url: 'https://abc.supabase.co', key: 'publishable' },
    async () => state, async value => { state = value; }, fetcher);
  const pulled = await client.pull([], []);
  assert.deepEqual(pulled.routines, []);
  assert.equal(calls.length, 2);
  assert.equal(calls[1].options.headers.Authorization, 'Bearer fresh-token');
  assert.equal(state.personId, 'member-one');
});
