import { applyCommand, dateKey, due } from './core.js';
import { SyncClient } from './sync-client.js';
import { SYNC_CONFIG } from './sync-config.js';

export class SyncEngine {
  constructor(readRoutines, writeRoutines, readSync, writeSync, fetcher = fetch) {
    this.readRoutines = readRoutines;
    this.writeRoutines = writeRoutines;
    this.readSync = readSync;
    this.writeSync = writeSync;
    this.client = new SyncClient(SYNC_CONFIG, readSync, writeSync, fetcher);
  }

  async status() { return await this.readSync() ?? {}; }

  async pull() {
    const state = await this.status();
    if (!state.personId) return { routines: await this.readRoutines(), sync: state };
    const local = await this.readRoutines();
    const result = await this.client.pull(local, state.sharedIds ?? []);
    await this.writeRoutines(result.routines);
    const sync = { ...(await this.status()), sharedIds: result.sharedIds,
      snapshot: result.snapshot, lastSyncAt: new Date().toISOString() };
    await this.writeSync(sync);
    return { routines: result.routines, sync };
  }

  async connect(kind, name, code) {
    if (kind === 'create') await this.client.create(name);
    else if (kind === 'join') await this.client.join(name, code);
    else if (kind === 'restore') await this.client.restore(code);
    else throw new Error('지원하지 않는 연결 방식입니다.');
    return this.pull();
  }

  async share(id) {
    const state = await this.status();
    if (!state.personId) throw new Error('먼저 공유 공간에 연결해주세요.');
    if ((state.sharedIds ?? []).includes(id)) return this.pull();
    const routine = (await this.readRoutines()).find(r => r.id === id);
    if (!routine) throw new Error('루틴을 찾지 못했어요.');
    await this.client.share(routine);
    return this.pull();
  }

  async mutate(type, payload) {
    const sync = await this.status();
    const local = await this.readRoutines();
    if (!sync.personId || type === 'merge') {
      const next = applyCommand(local, type, payload);
      await this.writeRoutines(next);
      return { routines: next, sync };
    }
    const id = type === 'save' ? payload.id : payload?.id;
    const shared = (sync.sharedIds ?? []).includes(id);
    const existing = local.find(r => r.id === id);
    if (type === 'save' && (!existing || shared)) {
      const next = applyCommand(local, type, payload);
      const routine = next.find(r => r.id === id);
      if (shared) await this.client.put(routine, this.revision(sync, id));
      else await this.client.share(routine);
      return this.pull();
    }
    if (shared && type === 'toggleActive') {
      const next = applyCommand(local, type, payload);
      await this.client.put(next.find(r => r.id === id), this.revision(sync, id));
      return this.pull();
    }
    if (shared && type === 'delete') {
      await this.client.remove(id, this.revision(sync, id));
      return this.pull();
    }
    if (shared && (type === 'toggleComplete' || type === 'complete')) {
      const date = payload.date ?? dateKey();
      const routine = existing;
      if (!routine || !due(routine, new Date(`${date}T12:00:00`))) return { routines: local, sync };
      const done = type === 'complete' || !routine.completedDates.includes(date);
      await this.client.complete(id, date, done);
      return this.pull();
    }
    const next = applyCommand(local, type, payload);
    await this.writeRoutines(next);
    return { routines: next, sync };
  }

  revision(sync, id) {
    const revision = sync.snapshot?.routines?.find(r => r.id === id)?.revision;
    if (!Number.isInteger(revision)) throw new Error('공유 루틴 버전을 읽지 못했어요. 먼저 새로고침해주세요.');
    return revision;
  }
}
