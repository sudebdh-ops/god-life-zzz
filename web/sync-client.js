import { mergeWorkspaceSnapshot, sharedSettings } from './shared-core.js';

export function newPairCode(random = crypto.getRandomValues.bind(crypto)) {
  const bytes = random(new Uint8Array(16));
  const encoded = btoa(String.fromCharCode(...bytes)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `gz1_${encoded}`;
}

export function validPairCode(code) { return /^gz1_[A-Za-z0-9_-]{22}$/.test(code); }

export function validateSyncConfig(config) {
  if (!config || typeof config.url !== 'string' || typeof config.key !== 'string') throw new Error('동기화 설정이 없어요.');
  let url;
  try { url = new URL(config.url); } catch { throw new Error('동기화 주소가 올바르지 않아요.'); }
  if (url.protocol !== 'https:' || !url.hostname.endsWith('.supabase.co') || url.pathname !== '/' || url.search || url.hash) {
    throw new Error('Supabase HTTPS 프로젝트 주소를 확인해주세요.');
  }
  if (!config.key.trim()) throw new Error('Supabase 공개 키를 확인해주세요.');
  return { url: url.origin, key: config.key.trim() };
}

export class SyncClient {
  constructor(config, readState, writeState, fetcher = fetch) {
    this.config = validateSyncConfig(config);
    this.readState = readState;
    this.writeState = writeState;
    this.fetcher = fetcher;
  }

  async state() { return (await this.readState()) ?? {}; }

  async request(path, body, token) {
    const response = await this.fetcher(this.config.url + path, {
      method: 'POST', headers: { apikey: this.config.key, Authorization: `Bearer ${token ?? this.config.key}`,
        'Content-Type': 'application/json' }, body: JSON.stringify(body),
    });
    const result = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(result.message || result.msg || result.error_description || '동기화 서버에 연결하지 못했어요.');
    return result;
  }

  async accessToken() {
    const state = await this.state();
    if (state.accessToken && state.expiresAt > Date.now() + 60_000) return state.accessToken;
    const result = state.refreshToken
      ? await this.request('/auth/v1/token?grant_type=refresh_token', { refresh_token: state.refreshToken })
      : await this.request('/auth/v1/signup', {});
    if (!result.access_token || !result.refresh_token) throw new Error('익명 로그인에 실패했어요.');
    await this.writeState({ ...state, accessToken: result.access_token, refreshToken: result.refresh_token,
      expiresAt: Date.now() + result.expires_in * 1000 });
    return result.access_token;
  }

  async rpc(name, body = {}) { return this.request(`/rest/v1/rpc/gzz_${name}`, body, await this.accessToken()); }

  async create(name) {
    const inviteCode = newPairCode();
    const recoveryCode = newPairCode();
    const identity = await this.rpc('create_workspace', { p_name: name, p_invite_code: inviteCode,
      p_recovery_code: recoveryCode });
    await this.writeState({ ...(await this.state()), ...identity, inviteCode, recoveryCode });
    return { ...identity, inviteCode, recoveryCode };
  }

  async join(name, inviteCode) {
    if (!validPairCode(inviteCode)) throw new Error('초대 코드 형식을 확인해주세요.');
    const recoveryCode = newPairCode();
    const identity = await this.rpc('join_workspace', { p_name: name, p_invite_code: inviteCode,
      p_recovery_code: recoveryCode });
    await this.writeState({ ...(await this.state()), ...identity, recoveryCode });
    return { ...identity, recoveryCode };
  }

  async restore(recoveryCode) {
    if (!validPairCode(recoveryCode)) throw new Error('복구 코드 형식을 확인해주세요.');
    const identity = await this.rpc('restore_person', { p_recovery_code: recoveryCode });
    await this.writeState({ ...(await this.state()), ...identity, recoveryCode });
    return identity;
  }

  async pull(localRoutines, oldSharedIds) {
    const snapshot = await this.rpc('snapshot');
    const personId = (await this.state()).personId;
    if (!personId || snapshot.personId !== personId) throw new Error('공유 공간의 사용자 정보가 달라요.');
    return { ...mergeWorkspaceSnapshot(localRoutines, oldSharedIds, snapshot, personId), snapshot };
  }

  async share(routine) {
    return this.rpc('share_routine', { p_data: sharedSettings(routine), p_dates: routine.completedDates });
  }

  async put(routine, revision) {
    return this.rpc('put_routine', { p_data: sharedSettings(routine), p_revision: revision });
  }

  async remove(id, revision) { return this.rpc('delete_routine', { p_id: id, p_revision: revision }); }

  async complete(id, date, done) {
    return this.rpc('set_completion', { p_id: id, p_date: date, p_done: done });
  }
}
