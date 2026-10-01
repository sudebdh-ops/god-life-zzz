import { decodeBackup, encodeBackup, applyCommand, makePlans, deliverDue, dateKey } from './core.js';
import { SyncEngine } from './sync-engine.js';

let queue = Promise.resolve();
function serial(task) {
  const next = queue.then(task);
  queue = next.catch(error => console.error('갓생:', error.message));
  return next;
}
async function read() {
  const data = await chrome.storage.local.get(['data', 'plans']);
  return { routines: data.data == null ? [] : decodeBackup(data.data), plans: data.plans ?? {} };
}
async function write(routines, plans) {
  await chrome.storage.local.set({ data: encodeBackup(routines), plans });
}
const engine = new SyncEngine(
  async () => (await read()).routines,
  async routines => {
    const current = await read();
    await write(routines, makePlans(routines, current.plans));
  },
  async () => (await chrome.storage.local.get('syncState')).syncState ?? {},
  async state => chrome.storage.local.set({ syncState: state }),
);
async function notify(r, date) {
  if (await chrome.notifications.getPermissionLevel() !== 'granted') return false;
  await chrome.notifications.create(`${r.id}|${date}`, {
    type: 'basic', iconUrl: 'icons/icon128.png', title: r.title,
    message: r.note || '작은 실천 하나, 오늘도 god life zzz',
    buttons: [{ title: '완료했어요' }], priority: 1,
  });
  return true;
}
async function tick() {
  if ((await engine.status()).personId) {
    try { await engine.pull(); } catch (error) { console.warn('동기화:', error.message); }
  }
  const data = await read();
  const plans = makePlans(data.routines, data.plans);
  const next = await deliverDue(data.routines, plans, notify);
  await write(next.routines, next.plans);
}
async function initialize() {
  await tick();
  if (!await chrome.alarms.get('routine-check')) await chrome.alarms.create('routine-check', { periodInMinutes: 1 });
}
chrome.runtime.onInstalled.addListener(() => serial(initialize));
chrome.runtime.onStartup.addListener(() => serial(initialize));
chrome.alarms.onAlarm.addListener(alarm => { if (alarm.name === 'routine-check') serial(tick); });
chrome.action.onClicked.addListener(() => chrome.tabs.create({ url: chrome.runtime.getURL('index.html') }));
chrome.notifications.onClicked.addListener(async id => {
  await chrome.tabs.create({ url: chrome.runtime.getURL('index.html') });
  await chrome.notifications.clear(id);
});
chrome.notifications.onButtonClicked.addListener((id, index) => {
  if (index !== 0 || !id.includes('|')) return;
  serial(async () => {
    const [routineId, date] = id.split('|');
    await engine.mutate('complete', { id: routineId, date });
    await chrome.notifications.clear(id);
  });
});
chrome.runtime.onMessage.addListener((message, sender, respond) => {
  if (sender.id !== chrome.runtime.id) return false;
  serial(async () => {
    if (message.type === 'read') return { routines: (await read()).routines };
    if (message.type === 'syncStatus') return { sync: await engine.status() };
    if (message.type === 'syncPull') return engine.pull();
    if (message.type === 'syncConnect') return engine.connect(message.kind, message.name, message.code);
    if (message.type === 'syncShare') return engine.share(message.id);
    if (message.type === 'permission') return { permission: await chrome.notifications.getPermissionLevel() };
    if (message.type === 'test') {
      if (await chrome.notifications.getPermissionLevel() !== 'granted') throw new Error('Windows 또는 Chrome 알림 설정을 확인해주세요.');
      await chrome.notifications.create('gatsaeng-test', { type: 'basic', iconUrl: 'icons/icon128.png', title: 'god life zzz', message: '알림이 도착했어요. 오늘도 하나씩!' });
      return {};
    }
    const data = await read();
    const result = await engine.mutate(message.type, message.payload);
    const routines = result.routines;
    for (const previous of data.routines) {
      const current = routines.find(r => r.id === previous.id);
      if (!current || !current.active || !current.reminderEnabled || current.completedDates.includes(dateKey())) await chrome.notifications.clear(`${previous.id}|${dateKey()}`);
    }
    return result;
  }).then(result => respond({ ok: true, ...result }), error => respond({ ok: false, error: error.message }));
  return true;
});
// Restore the heartbeat after a service-worker restart without resetting an existing alarm.
serial(initialize);
