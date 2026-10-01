import { decodeBackup, encodeBackup, applyCommand, makePlans, deliverDue, dateKey } from './core.js';

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
    const data = await read();
    const routines = applyCommand(data.routines, 'complete', { id: routineId, date });
    await write(routines, makePlans(routines, data.plans));
    await chrome.notifications.clear(id);
  });
});
chrome.runtime.onMessage.addListener((message, sender, respond) => {
  if (sender.id !== chrome.runtime.id) return false;
  serial(async () => {
    if (message.type === 'read') return { routines: (await read()).routines };
    if (message.type === 'permission') return { permission: await chrome.notifications.getPermissionLevel() };
    if (message.type === 'test') {
      if (await chrome.notifications.getPermissionLevel() !== 'granted') throw new Error('Windows 또는 Chrome 알림 설정을 확인해주세요.');
      await chrome.notifications.create('gatsaeng-test', { type: 'basic', iconUrl: 'icons/icon128.png', title: 'god life zzz', message: '알림이 도착했어요. 오늘도 하나씩!' });
      return {};
    }
    const data = await read();
    const routines = applyCommand(data.routines, message.type, message.payload);
    await write(routines, makePlans(routines, data.plans));
    for (const previous of data.routines) {
      const current = routines.find(r => r.id === previous.id);
      if (!current || !current.active || !current.reminderEnabled || current.completedDates.includes(dateKey())) await chrome.notifications.clear(`${previous.id}|${dateKey()}`);
    }
    return { routines };
  }).then(result => respond({ ok: true, ...result }), error => respond({ ok: false, error: error.message }));
  return true;
});
// Restore the heartbeat after a service-worker restart without resetting an existing alarm.
serial(initialize);
