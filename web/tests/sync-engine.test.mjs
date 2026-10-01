import test from 'node:test';
import assert from 'node:assert/strict';
import { SyncEngine } from '../sync-engine.js';

function fixture() {
  let routines = [];
  let sync = { personId: 'me', sharedIds: [], snapshot: { routines: [] } };
  const calls = [];
  const engine = new SyncEngine(async () => routines, async next => { routines = next; },
    async () => sync, async next => { sync = next; });
  engine.client = {
    share: async routine => { calls.push(['share', routine.id]); sync.sharedIds = [routine.id]; },
    put: async (routine, revision) => calls.push(['put', routine.id, revision]),
    complete: async (id, date, done) => calls.push(['complete', id, date, done]),
    remove: async (id, revision) => calls.push(['remove', id, revision]),
    pull: async local => ({ routines: local, sharedIds: sync.sharedIds,
      snapshot: { routines: local.filter(r => sync.sharedIds.includes(r.id)).map(r => ({ ...r, revision: 1 })),
        completions: [], members: [] } }),
  };
  return { engine, calls, setRoutines: next => { routines = next; }, getRoutines: () => routines };
}
const routine = { id: '123e4567-e89b-12d3-a456-426614174000', title: '걷기', note: '',
  days: [1, 2, 3, 4, 5, 6, 7], hour: 8, minute: 0, reminderEnabled: true,
  active: true, createdOn: '2026-01-01', completedDates: [], lastRemindedDate: null };

test('new routine is shared when connected', async () => {
  const { engine, calls } = fixture();
  const result = await engine.mutate('save', routine);
  assert.deepEqual(calls, [['share', routine.id]]);
  assert.deepEqual(result.sync.sharedIds, [routine.id]);
});

test('shared completion is sent remotely before local cache changes', async () => {
  const { engine, calls, setRoutines } = fixture();
  setRoutines([routine]);
  await engine.share(routine.id);
  await engine.mutate('toggleComplete', { id: routine.id });
  assert.equal(calls.at(-1)[0], 'complete');
  assert.equal(calls.at(-1)[3], true);
});

test('failed server write does not alter local routine', async () => {
  const { engine, setRoutines, getRoutines } = fixture();
  setRoutines([routine]);
  await engine.share(routine.id);
  engine.client.put = async () => { throw new Error('offline'); };
  await assert.rejects(() => engine.mutate('toggleActive', { id: routine.id }), /offline/);
  assert.equal(getRoutines()[0].active, true);
});
