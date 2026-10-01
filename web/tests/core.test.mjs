import test from 'node:test';
import assert from 'node:assert/strict';
import { dateKey, nextReminder, streak, encodeBackup, decodeBackup, applyCommand, makePlans, deliverDue } from '../core.js';
const now = (hour = 7, minute = 0) => new Date(2026, 9, 1, hour, minute);
const base = (extra = {}) => ({ id: '3dc15c68-8f1d-4089-b9b7-dc2192b2778a', title: '산책', note: '', days: [1, 2, 3, 4, 5, 6, 7], hour: 8, minute: 0, reminderEnabled: true, active: true, completedDates: [], lastRemindedDate: null, createdOn: '2026-09-01', ...extra });
test('future alarm is today and elapsed alarm is tomorrow', () => {
  assert.equal(dateKey(nextReminder(base(), now())), '2026-10-01');
  assert.equal(dateKey(nextReminder(base(), now(8))), '2026-10-02');
});
test('completed, notified and paused reminders are skipped', () => {
  assert.equal(dateKey(nextReminder(base({ completedDates: ['2026-10-01'] }), now())), '2026-10-02');
  assert.equal(dateKey(nextReminder(base({ lastRemindedDate: '2026-10-01' }), now())), '2026-10-02');
  assert.equal(nextReminder(base({ active: false }), now()), null);
});
test('weekly recurrence selects Monday', () => assert.equal(dateKey(nextReminder(base({ days: [1] }), now())), '2026-10-05'));
test('streak keeps yesterday while today unfinished', () => assert.equal(streak(base({ completedDates: ['2026-09-29', '2026-09-30'] }), now()), 2));
test('backup schema matches Android fields and preserves Korean', () => {
  const r = base({ title: 'god life zzz', note: '책 📖\n10쪽' });
  assert.deepEqual(decodeBackup(encodeBackup([r])), [r]);
});
test('bad schema and duplicate IDs are rejected', () => {
  assert.throws(() => decodeBackup('{"version":2,"routines":[]}'));
  assert.throws(() => decodeBackup(encodeBackup([base(), base()])));
  assert.throws(() => encodeBackup([base({ days: [8] })]));
  assert.throws(() => encodeBackup([base({ createdOn: '2026-02-30' })]));
});
test('editing preserves freshest completion history', () => {
  const current = base({ completedDates: ['2026-10-01'], lastRemindedDate: '2026-10-01' });
  const [saved] = applyCommand([current], 'save', base({ title: '새 제목' }), now());
  assert.deepEqual(saved.completedDates, current.completedDates);
  assert.equal(saved.lastRemindedDate, current.lastRemindedDate);
});
test('changing time permits a new reminder', () => {
  const [saved] = applyCommand([base({ lastRemindedDate: '2026-10-01' })], 'save', base({ hour: 9 }), now());
  assert.equal(saved.lastRemindedDate, null);
});
test('import merges completion without replacing settings', () => {
  const [merged] = applyCommand([base({ title: '현재 제목', completedDates: ['2026-09-30'] })], 'merge', [base({ completedDates: ['2026-10-01'] })], now());
  assert.equal(merged.title, '현재 제목');
  assert.deepEqual(merged.completedDates, ['2026-09-30', '2026-10-01']);
});
test('completion toggle is reversible and cannot complete future', () => {
  const checked = applyCommand([base()], 'toggleComplete', { id: base().id }, now());
  assert.deepEqual(checked[0].completedDates, ['2026-10-01']);
  assert.deepEqual(applyCommand(checked, 'toggleComplete', { id: base().id }, now())[0].completedDates, []);
  assert.throws(() => applyCommand(checked, 'complete', { id: base().id, date: '2026-10-02' }, now()));
});
test('worker restart retains an overdue scheduled reminder', () => {
  const plan = makePlans([base()], {}, now());
  assert.equal(makePlans([base()], plan, now(8, 2))[base().id].when, plan[base().id].when);
});
test('alarm delivers once and schedules tomorrow', async () => {
  const plan = makePlans([base()], {}, now()); let called = 0;
  const first = await deliverDue([base()], plan, async () => { called++; return true; }, now(8, 2));
  const second = await deliverDue(first.routines, first.plans, async () => { called++; return true; }, now(8, 3));
  assert.equal(called, 1); assert.equal(first.routines[0].lastRemindedDate, '2026-10-01');
  assert.equal(second.plans[base().id].date, '2026-10-02');
});
test('old-day alarms are not shown after waking', async () => {
  const plan = makePlans([base()], {}, now()); let called = 0;
  await deliverDue([base()], plan, async () => { called++; return true; }, new Date(2026, 9, 2, 7));
  assert.equal(called, 0);
});
test('completed routines cancel pending reminders', () => {
  const plan = makePlans([base()], {}, now());
  const next = makePlans([base({ completedDates: ['2026-10-01'] })], plan, now());
  assert.equal(next[base().id].date, '2026-10-02');
});
test('permission failure keeps today reminder pending for retry', async () => {
  const plan = makePlans([base()], {}, now());
  const result = await deliverDue([base()], plan, async () => false, now(8, 2));
  assert.equal(result.routines[0].lastRemindedDate, null);
  assert.equal(result.plans[base().id].date, '2026-10-01');
});
