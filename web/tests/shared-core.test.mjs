import test from 'node:test';
import assert from 'node:assert/strict';
import { mergeWorkspaceSnapshot, sharedSettings } from '../shared-core.js';

const routine = (id, title = '운동') => ({ id, title, note: '', days: [1, 2, 3, 4, 5, 6, 7], hour: 8,
  minute: 0, reminderEnabled: true, active: true, createdOn: '2026-01-01',
  completedDates: ['2026-09-30'], lastRemindedDate: '2026-10-01' });
const sharedId = '00000000-0000-4000-8000-000000000001';
const personalId = '00000000-0000-4000-8000-000000000002';

test('shared settings never upload personal completion or reminder history', () => {
  const result = sharedSettings(routine(sharedId));
  assert.equal('completedDates' in result, false);
  assert.equal('lastRemindedDate' in result, false);
  assert.equal(result.title, '운동');
});

test('workspace snapshot preserves private routines and uses only this person’s completions', () => {
  const result = mergeWorkspaceSnapshot([routine(sharedId), routine(personalId, '개인 루틴')], [sharedId], {
    routines: [sharedSettings(routine(sharedId))],
    completions: [
      { routineId: sharedId, personId: 'me', date: '2026-10-01' },
      { routineId: sharedId, personId: 'friend', date: '2026-09-29' },
    ],
  }, 'me');
  assert.equal(result.routines.length, 2);
  assert.deepEqual(result.routines.find(r => r.id === sharedId).completedDates, ['2026-10-01']);
  assert.equal(result.routines.find(r => r.id === sharedId).lastRemindedDate, '2026-10-01');
  assert.deepEqual(result.routines.find(r => r.id === personalId).completedDates, ['2026-09-30']);
});

test('remote deletion removes only formerly shared routines', () => {
  const result = mergeWorkspaceSnapshot([routine(sharedId), routine(personalId)], [sharedId],
    { routines: [], completions: [] }, 'me');
  assert.deepEqual(result.routines.map(r => r.id), [personalId]);
});

test('changed shared reminder resets only device notification marker', () => {
  const incoming = { ...sharedSettings(routine(sharedId)), hour: 9 };
  const result = mergeWorkspaceSnapshot([routine(sharedId)], [sharedId],
    { routines: [incoming], completions: [] }, 'me');
  assert.equal(result.routines[0].lastRemindedDate, null);
});
