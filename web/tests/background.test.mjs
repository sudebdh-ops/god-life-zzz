import test from 'node:test';
import assert from 'node:assert/strict';
import { encodeBackup, decodeBackup } from '../core.js';

test('Chrome worker persists reminders, serializes edits and handles notification completion', async () => {
  const RealDate = Date;
  const fixed = new RealDate(2026, 9, 1, 8, 1).getTime();
  globalThis.Date = class extends RealDate { constructor(...args) { super(...(args.length ? args : [fixed])); } static now() { return fixed; } };
  const id = '3dc15c68-8f1d-4089-b9b7-dc2192b2778a';
  const routine = { id, title: '산책', note: '', days: [1, 2, 3, 4, 5, 6, 7], hour: 8, minute: 0, reminderEnabled: true, active: true, completedDates: [], lastRemindedDate: null, createdOn: '2026-09-01' };
  const events = {};
  const listen = name => ({ addListener(fn) { events[name] = fn; } });
  const storage = { data: encodeBackup([routine]), plans: { [id]: { when: fixed - 60_000, date: '2026-10-01', signature: JSON.stringify([routine.days, 8, 0, routine.createdOn]) } } };
  const alarmMap = new Map(); const notifications = []; const cleared = [];
  globalThis.chrome = {
    storage: { local: { async get() { return structuredClone(storage); }, async set(value) { Object.assign(storage, structuredClone(value)); } } },
    alarms: { async get(name) { return alarmMap.get(name); }, async create(name, value) { alarmMap.set(name, value); }, onAlarm: listen('alarm') },
    notifications: { async getPermissionLevel() { return 'granted'; }, async create(id, value) { notifications.push({ id, value }); }, async clear(id) { cleared.push(id); }, onClicked: listen('click'), onButtonClicked: listen('button') },
    runtime: { id: 'test-extension', getURL(file) { return `chrome-extension://test-extension/${file}`; }, onInstalled: listen('installed'), onStartup: listen('startup'), onMessage: listen('message') },
    action: { onClicked: listen('action') }, tabs: { async create() {} },
  };
  const message = input => new Promise(resolve => events.message(input, { id: 'test-extension' }, resolve));
  try {
    await import('../background.js');
    await message({ type: 'read' });
    assert.equal(alarmMap.get('routine-check').periodInMinutes, 1);
    assert.equal(notifications.length, 1);
    assert.equal(decodeBackup(storage.data)[0].lastRemindedDate, '2026-10-01');
    events.alarm({ name: 'routine-check' });
    await message({ type: 'read' });
    assert.equal(notifications.length, 1, 'already delivered reminder does not duplicate');
    events.button(`${id}|2026-10-01`, 0);
    await message({ type: 'read' });
    assert.deepEqual(decodeBackup(storage.data)[0].completedDates, ['2026-10-01']);
    assert.ok(cleared.includes(`${id}|2026-10-01`));
    const edit = await message({ type: 'save', payload: { ...routine, title: '수정된 산책' } });
    assert.equal(edit.ok, true);
    assert.deepEqual(edit.routines[0].completedDates, ['2026-10-01'], 'stale UI edit preserves notification completion');
    storage.data = 'broken-json';
    const broken = await message({ type: 'read' });
    assert.equal(broken.ok, false);
    assert.equal(storage.data, 'broken-json', 'unreadable storage is never silently overwritten');
  } finally { globalThis.Date = RealDate; delete globalThis.chrome; }
});
