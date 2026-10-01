import { DAYS, dateKey, due, completed, streak, timeText, daysText, nextReminder, decodeBackup, encodeBackup, applyCommand, makePlans, deliverDue } from './core.js';
import { SyncEngine } from './sync-engine.js';

const extension = Boolean(globalThis.chrome?.runtime?.id && chrome.storage);
const STORAGE = 'gatsaeng-web-v1';
const PLAN_KEY = 'gatsaeng-web-plans-v1';
const SYNC_KEY = 'gatsaeng-web-sync-v1';
const $ = selector => document.querySelector(selector);
const escape = value => String(value).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
let routines = [];
let tab = ['today', 'routines', 'settings'].includes(location.hash.slice(1)) ? location.hash.slice(1) : 'today';
let editing = null;
let selectedDays = new Set([1, 2, 3, 4, 5, 6, 7]);
let readable = true;
let notificationPermission = 'default';
let installedPrompt = null;
let toastTimer;
let pendingConfirmation = null;
let sync = {};
const engine = extension ? null : new SyncEngine(
  async () => browserRead(),
  async next => withLock(async () => {
    const previous = JSON.parse(localStorage.getItem(PLAN_KEY) || '{}');
    localStorage.setItem(STORAGE, encodeBackup(next));
    localStorage.setItem(PLAN_KEY, JSON.stringify(makePlans(next, previous)));
  }),
  async () => JSON.parse(localStorage.getItem(SYNC_KEY) || '{}'),
  async state => localStorage.setItem(SYNC_KEY, JSON.stringify(state)),
);

function toast(message) {
  clearTimeout(toastTimer);
  $('#toast').textContent = message;
  $('#toast').hidden = false;
  toastTimer = setTimeout(() => { $('#toast').hidden = true; }, 5000);
}
async function send(message) {
  const result = await chrome.runtime.sendMessage(message);
  if (!result?.ok) throw new Error(result?.error || '확장 프로그램과 연결하지 못했어요.');
  return result;
}
function browserRead() {
  const text = localStorage.getItem(STORAGE);
  return text == null ? [] : decodeBackup(text);
}
async function withLock(task) {
  return navigator.locks ? navigator.locks.request('gatsaeng-data-write', task) : task();
}
async function load() {
  try {
    routines = extension ? (await send({ type: 'read' })).routines : browserRead();
    sync = extension ? (await send({ type: 'syncStatus' })).sync : await engine.status();
    notificationPermission = extension ? (await send({ type: 'permission' })).permission : ('Notification' in window ? Notification.permission : 'unsupported');
    readable = true;
  } catch (error) { readable = false; toast(`기록을 읽지 못했어요. 기존 데이터는 보존됩니다. ${error.message}`); }
  render();
}
async function mutate(type, payload) {
  if (!readable) { toast('기록을 읽을 수 없어 변경을 멈췄어요.'); return false; }
  try {
    const result = extension ? await send({ type, payload }) : await engine.mutate(type, payload);
    routines = result.routines;
    sync = result.sync;
    render();
    return true;
  } catch (error) { render(); toast(error.message); return false; }
}
async function syncAction(type, values = {}) {
  const result = extension ? await send({ type, ...values }) : type === 'syncPull'
    ? await engine.pull() : type === 'syncShare' ? await engine.share(values.id)
      : await engine.connect(values.kind, values.name, values.code);
  routines = result.routines;
  sync = result.sync;
  render();
}
function shared(id) { return (sync.sharedIds ?? []).includes(id); }
function friendStatus(routine) {
  if (!shared(routine.id)) return '';
  const friend = sync.snapshot?.members?.find(member => member.personId !== sync.personId);
  if (!friend) return '<span class="small">공유 루틴 · 친구 초대 대기 중</span>';
  const done = sync.snapshot.completions?.some(item => item.routineId === routine.id && item.personId === friend.personId && item.date === dateKey());
  return `<span class="small">${escape(friend.displayName)}: ${done ? '오늘 완료 ✓' : '아직 미완료'}</span>`;
}

function render() {
  $('#date-label').textContent = new Intl.DateTimeFormat('ko-KR', { month: 'long', day: 'numeric', weekday: 'long' }).format(new Date());
  $('#mode-badge').textContent = extension ? '크롬 확장 버전' : '크롬 웹 버전';
  $('#top-add').hidden = tab === 'settings';
  $('#top-add').disabled = !readable;
  document.querySelectorAll('[data-tab]').forEach(button => {
    button.classList.toggle('active', button.dataset.tab === tab);
    if (button.dataset.tab === tab) button.setAttribute('aria-current', 'page'); else button.removeAttribute('aria-current');
  });
  if (!readable) {
    $('#main').innerHTML = '<div class="page-intro"><div><h1>기록을 확인해주세요.</h1><p>기존 데이터는 보존됩니다. 브라우저 저장 공간이나 파일 형식을 확인하고 다시 열어주세요.</p></div></div>';
    return;
  }
  $('#main').innerHTML = tab === 'today' ? todayView() : tab === 'routines' ? routinesView() : settingsView();
}

function emptyView(title, body) {
  return `<div class="empty"><div class="empty-symbol" aria-hidden="true">＋</div><h3>${title}</h3><p>${body}</p><button class="button primary" data-action="add">루틴 만들기</button></div>`;
}
function todayView() {
  const now = new Date();
  const today = routines.filter(r => due(r, now)).sort((a, b) => a.hour - b.hour || a.minute - b.minute);
  const done = today.filter(r => completed(r, now)).length;
  const percentage = today.length ? Math.round(done / today.length * 100) : 0;
  const allDone = today.length > 0 && done === today.length;
  const next = routines.map(r => ({ r, at: nextReminder(r, now) })).filter(x => x.at).sort((a, b) => a.at - b.at)[0];
  return `<div class="page-intro"><div><span class="eyebrow">A LITTLE BETTER, EVERY DAY</span><h1>오늘도, 하나씩.</h1><p>나만의 속도로 만드는 좋은 하루</p></div></div>
    <section class="hero" aria-label="오늘의 달성 현황"><div><span class="eyebrow">TODAY'S LITTLE WINS</span><h2>${allDone ? '오늘 루틴 모두 완료!' : '오늘의 작은 성취'}</h2><p>${allDone ? '수고했어요. 내일도 god life zzz' : today.length ? '한 번에 하나면 충분해요. 오늘도 가볍게 시작!' : '첫 루틴을 추가해서 나만의 하루를 만들어보세요.'}</p><div class="hero-count"><strong>${done}</strong><span>/ ${today.length} 완료</span></div><div class="progress-track" role="progressbar" aria-label="오늘 완료율" aria-valuemin="0" aria-valuemax="100" aria-valuenow="${percentage}"><div class="progress-fill" style="width:${percentage}%"></div></div></div><div class="hero-art" aria-hidden="true"><div class="orbit"></div><div class="orbit two"></div><div class="hero-mark">✓</div><span class="art-spark">✳</span></div></section>
    ${notificationPermission !== 'granted' && routines.some(r => r.active && r.reminderEnabled) ? '<div class="permission-note"><span>루틴을 놓치지 않도록 알림을 켜주세요.</span><button data-action="settings">알림 설정 →</button></div>' : ''}
    <div class="dashboard-grid"><section><div class="section-head"><h3>오늘의 루틴</h3><small>${today.length}개의 작은 약속</small></div><div class="routine-list">${today.length ? today.map(r => {
      const isDone = completed(r, now); const count = streak(r, now);
      return `<article class="routine-card ${isDone ? 'done' : ''}"><button class="check" data-action="complete" data-id="${r.id}" aria-label="${escape(r.title)} ${isDone ? '완료 취소' : '완료 체크'}" aria-pressed="${isDone}">✓</button><div class="routine-copy"><h3>${escape(r.title)}</h3>${r.note ? `<p>${escape(r.note)}</p>` : ''}<div class="meta-line"><span class="time-pill">${r.reminderEnabled ? timeText(r) + ' 알림' : '알림 없이 실천'}</span>${count ? `<span class="streak-pill">↗ 연속 ${count}회</span>` : ''}</div>${friendStatus(r)}</div>${isDone ? '<span class="status-done">완료!</span>' : `<button class="text-button" data-action="edit" data-id="${r.id}" aria-label="${escape(r.title)} 수정">수정</button>`}</article>`;
    }).join('') : emptyView(routines.length ? '오늘은 예정된 루틴이 없어요' : '좋은 하루의 첫 단추', routines.length ? '반복 요일은 ‘내 루틴’에서 바꿀 수 있어요.' : '물 마시기, 독서, 산책… 원하는 루틴을 직접 만들어보세요.')}</div></section>
    <aside class="dashboard-aside"><div class="side-card"><span class="eyebrow">MY PACE</span><div class="big-number">${percentage}<span class="small"> %</span></div><p>오늘의 루틴 달성률</p><div class="week-dots">${DAYS.map((day, i) => `<div class="week-dot ${i + 1 === (now.getDay() || 7) ? 'today' : ''}">${day}<span>${i + 1 === (now.getDay() || 7) ? '●' : '·'}</span></div>`).join('')}</div></div><div class="side-card note-card"><span class="seed" aria-hidden="true">✳</span><h3>작은 실천도 실천이니까.</h3><p>완벽한 하루보다<br>계속 이어가는 하루를 만들어요.</p></div><div class="side-card"><h3>다음 리마인더</h3><p style="margin-top:10px">${next ? `${escape(next.r.title)}<br><strong>${next.at.getMonth() + 1}/${next.at.getDate()} ${timeText(next.r)}</strong>` : '알림이 있는 루틴을 추가해보세요.'}</p></div></aside></div>`;
}
function routinesView() {
  return `<div class="page-intro"><div><span class="eyebrow">MADE FOR YOU</span><h1>내 루틴</h1><p>전체 ${routines.length}개 · 사용 중 ${routines.filter(r => r.active).length}개</p></div></div>
    ${routines.length ? `<div class="all-routines">${routines.map(r => `<article class="manage-card"><div class="manage-head"><h3>${escape(r.title)}</h3><input class="switch" type="checkbox" data-action="active" data-id="${r.id}" aria-label="${escape(r.title)} 사용" ${r.active ? 'checked' : ''}></div><div class="meta-line"><span class="time-pill">${r.reminderEnabled ? timeText(r) + ' 알림' : '알림 꺼짐'}</span><span>${daysText(r)}</span></div>${r.note ? `<p>${escape(r.note)}</p>` : ''}${!r.active ? '<p class="small">잠시 쉬는 중 · 기록은 그대로 보관돼요.</p>' : ''}<p class="small">${shared(r.id) ? '친구와 공유 중' : '이 기기에만 저장'}</p><div class="manage-actions">${sync.personId && !shared(r.id) ? `<button class="text-button" data-action="share" data-id="${r.id}">친구와 공유</button>` : ''}<button class="text-button danger" data-action="delete" data-id="${r.id}">삭제</button><button class="text-button" data-action="edit" data-id="${r.id}">수정</button></div></article>`).join('')}</div>` : emptyView('나에게 맞는 하루를 만들어봐요', '반복 요일과 알림 시간을 직접 정해보세요.')}`;
}
function settingsView() {
  const granted = notificationPermission === 'granted';
  return `<div class="page-intro"><div><span class="eyebrow">A GOOD ROUTINE STARTS HERE</span><h1>설정</h1><p>루틴이 제때 찾아올 수 있도록</p></div></div><div class="settings-grid">
    <section class="settings-card"><h2>알림</h2><p class="permission">${granted ? '● 알림 허용됨' : '○ 알림을 허용해주세요'}</p><p>${extension ? '앱 탭을 닫아도 크롬이 실행 중이면 백그라운드에서 예약을 확인합니다. 약 1분 간격으로 확인하며 크롬과 Windows 상태에 따라 늦어질 수 있어요.' : '웹 버전 알림은 이 페이지를 열어둔 동안 확인합니다. 탭을 닫은 뒤에도 알림을 받으려면 크롬 확장 버전을 사용해주세요.'}</p>${!extension && !granted ? '<button class="button primary" data-action="permission">알림 허용하기</button>' : ''}<button class="button outline" data-action="test">테스트 알림</button><p class="small">컴퓨터가 꺼져 있거나 크롬이 완전히 종료된 동안에는 알림을 보낼 수 없어요. 절전 중 알림은 복귀 후 늦게 도착할 수 있습니다. 소리는 Windows 알림·방해금지 설정을 따라요.</p></section>
    <section class="settings-card"><h2>친구·기기 동기화</h2>${sync.personId ? `<p>연결됨 · ${escape(sync.snapshot?.members?.map(m => m.displayName).join(' + ') || '공유 공간')}</p><p class="small">새 루틴은 자동 공유됩니다. 기존 루틴은 ‘내 루틴’에서 직접 공유할 수 있어요. 완료 기록은 각자 따로 저장돼요.</p><button class="button outline" data-action="sync-pull">지금 동기화</button>${sync.inviteCode ? `<p>친구 초대 코드</p><code class="pair-code">${escape(sync.inviteCode)}</code><button class="button outline" data-action="copy-code" data-code="invite">초대 코드 복사</button>` : ''}<p>내 복구 코드 (새 기기 연결)</p><code class="pair-code">${escape(sync.recoveryCode || '복구 코드는 처음 만든 기기에 표시됩니다.')}</code>${sync.recoveryCode ? '<button class="button outline" data-action="copy-code" data-code="recovery">복구 코드 복사</button>' : ''}<p class="small">복구 코드를 잃어버리면 이 사람으로 새 기기를 연결할 수 없어요. 안전한 곳에 보관하고 친구에게 보내지 마세요.</p><p class="small">마지막 동기화: ${escape(sync.lastSyncAt ? new Date(sync.lastSyncAt).toLocaleString('ko-KR') : '대기 중')}</p>` : `<p>두 사람이 같은 루틴을 보면서 완료는 각각 체크할 수 있어요. 각 기기는 인터넷이 필요합니다.</p><label>내 이름 <input id="sync-name" class="sync-input" maxlength="40" placeholder="예: 나"></label><button class="button primary" data-action="sync-create">공유 공간 만들기</button><label>친구의 초대 코드 <input id="sync-invite" class="sync-input" placeholder="gz1_..."></label><button class="button outline" data-action="sync-join">친구 공간 참여</button><label>내 복구 코드 <input id="sync-recovery" class="sync-input" placeholder="gz1_..."></label><button class="button outline" data-action="sync-restore">다른 기기 연결</button>`}</section>
    <section class="settings-card"><h2>내 기록</h2><p>백업 파일은 이 기기의 루틴과 내 완료 기록을 저장해요. 동기화 중인 공유 루틴을 가져온 경우 서버의 기록이 우선합니다.</p><button class="button outline" data-action="export">백업 파일 저장</button><button class="button outline" data-action="import">백업 파일 가져오기</button><p class="small">${extension ? '이 크롬 프로필' : '이 브라우저의 현재 사이트'}의 저장 공간을 지우기 전에 백업과 복구 코드를 따로 보관해주세요.</p></section>
    ${!extension ? `<section class="settings-card wide"><h2>PC에서 앱처럼 사용하기</h2><p>예약 알림이 필요하면 소스의 <code>web</code> 폴더를 크롬 확장 프로그램으로 설치해주세요.</p><ol><li>크롬 주소창에 <code>chrome://extensions</code> 입력</li><li>‘개발자 모드’ 켜기</li><li>‘압축해제된 확장 프로그램을 로드합니다’ → <code>web</code> 폴더 선택</li><li>확장 프로그램 목록에서 ‘god life zzz’ 고정 후 아이콘 클릭</li></ol>${installedPrompt ? '<button class="button outline" data-action="install">웹 앱 설치하기</button>' : '<p class="small">일반 웹 앱은 크롬 메뉴 → 전송, 저장 및 공유 → 페이지를 앱으로 설치하기로 설치할 수도 있어요. 웹 앱을 설치해도 닫힌 상태에서 예약 알림은 실행되지 않습니다.</p>'}</section>` : ''}
    <section class="settings-card wide"><h2>알림이 오지 않을 때</h2><p>Windows 설정 → 시스템 → 알림에서 Chrome 알림을 확인해주세요. 절전 모드, 방해금지, 조직의 브라우저 정책 때문에 알림이 제한될 수 있습니다.${extension ? ' 크롬을 완전히 종료한 뒤에는 다시 실행해주세요.' : ' 웹 페이지를 열어두거나 크롬 확장 버전을 사용해주세요.'}</p></section>
    </div><div class="app-about">god life zzz · v0.2.0<br>오픈소스 · MIT License · ${extension ? '크롬 확장' : '크롬 웹'} 버전</div>`;
}

function navigate(nextTab) {
  tab = nextTab;
  history.replaceState(null, '', '#' + tab);
  render();
}
function paintDays() {
  $('#days').innerHTML = DAYS.map((label, i) => `<button type="button" class="day-chip" data-day="${i + 1}" aria-pressed="${selectedDays.has(i + 1)}">${label}</button>`).join('');
}
function openEditor(id = null) {
  if (!readable) return;
  editing = routines.find(r => r.id === id) ?? null;
  $('#routine-form').reset();
  $('#editor-title').textContent = editing ? '루틴 수정' : '새 루틴 만들기';
  $('#title').value = editing?.title ?? '';
  $('#note').value = editing?.note ?? '';
  $('#time').value = editing ? timeText(editing) : '08:00';
  $('#reminder').checked = editing?.reminderEnabled ?? true;
  $('#time-field').hidden = !$('#reminder').checked;
  $('#form-error').textContent = '';
  selectedDays = new Set(editing?.days ?? [1, 2, 3, 4, 5, 6, 7]);
  paintDays();
  $('#editor').showModal();
  $('#title').focus();
}
function confirmAction(title, message, action, label = '확인') {
  pendingConfirmation = action;
  $('#confirm-title').textContent = title;
  $('#confirm-text').textContent = message;
  $('#confirm-ok').textContent = label;
  $('#confirm').showModal();
}
document.querySelectorAll('[data-tab]').forEach(button => button.addEventListener('click', () => navigate(button.dataset.tab)));
$('.brand').addEventListener('click', event => { event.preventDefault(); navigate('today'); });
$('#top-add').addEventListener('click', () => openEditor());
for (const selector of ['#close-editor', '#cancel-editor']) $(selector).addEventListener('click', () => $('#editor').close());
$('#days').addEventListener('click', event => {
  const button = event.target.closest('[data-day]');
  if (!button) return;
  const day = Number(button.dataset.day);
  if (selectedDays.has(day)) selectedDays.delete(day); else selectedDays.add(day);
  paintDays();
});
document.querySelectorAll('[data-preset]').forEach(button => button.addEventListener('click', () => {
  selectedDays = new Set(button.dataset.preset === 'daily' ? [1, 2, 3, 4, 5, 6, 7] : button.dataset.preset === 'weekdays' ? [1, 2, 3, 4, 5] : [6, 7]); paintDays();
}));
$('#reminder').addEventListener('change', () => { $('#time-field').hidden = !$('#reminder').checked; });
$('#routine-form').addEventListener('submit', async event => {
  event.preventDefault();
  if (!selectedDays.size) { $('#form-error').textContent = '요일을 하나 이상 선택해주세요.'; return; }
  const [hour, minute] = $('#time').value.split(':').map(Number);
  const routine = {
    id: editing?.id ?? crypto.randomUUID(), title: $('#title').value.trim(), note: $('#note').value.trim(),
    days: [...selectedDays].sort((a, b) => a - b), hour, minute, reminderEnabled: $('#reminder').checked,
    active: editing?.active ?? true, completedDates: editing?.completedDates ?? [],
    lastRemindedDate: editing?.lastRemindedDate ?? null, createdOn: editing?.createdOn ?? dateKey(),
  };
  if (await mutate('save', routine)) { $('#editor').close(); toast(editing ? '루틴을 수정했어요.' : '새 루틴을 만들었어요.'); }
});
$('#confirm-cancel').addEventListener('click', () => { pendingConfirmation = null; $('#confirm').close(); });
$('#confirm').addEventListener('cancel', () => { pendingConfirmation = null; });
$('#confirm-ok').addEventListener('click', async () => {
  const action = pendingConfirmation; pendingConfirmation = null;
  $('#confirm').close();
  if (action) await action();
});
$('#main').addEventListener('click', async event => {
  const control = event.target.closest('[data-action]');
  if (!control) return;
  const { action, id } = control.dataset;
  try {
    if (action === 'add') openEditor();
    else if (action === 'edit') openEditor(id);
    else if (action === 'settings') navigate('settings');
    else if (action === 'complete') await mutate('toggleComplete', { id });
    else if (action === 'active') await mutate('toggleActive', { id });
    else if (action === 'share') confirmAction('친구와 공유할까요?', '이 루틴의 설정과 내 완료 기록이 공유 공간으로 올라갑니다. 친구의 완료는 별도로 기록돼요.', async () => { await syncAction('syncShare', { id }); toast('친구와 공유했어요.'); }, '공유');
    else if (action === 'sync-pull') { await syncAction('syncPull'); toast('동기화했어요.'); }
    else if (action === 'sync-create' || action === 'sync-join' || action === 'sync-restore') {
      const kind = action.slice(5);
      const name = $('#sync-name')?.value.trim() || '';
      const code = (kind === 'join' ? $('#sync-invite') : $('#sync-recovery'))?.value.trim();
      if (kind !== 'restore' && !name) throw new Error('내 이름을 입력해주세요.');
      await syncAction('syncConnect', { kind, name, code });
      toast('공유 공간에 연결됐어요. 복구 코드를 안전하게 보관해주세요.');
    } else if (action === 'copy-code') {
      const code = control.dataset.code === 'invite' ? sync.inviteCode : sync.recoveryCode;
      if (!code) throw new Error('코드를 찾지 못했어요.');
      await navigator.clipboard.writeText(code); toast('코드를 복사했어요.');
    }
    else if (action === 'delete') {
      const r = routines.find(x => x.id === id);
      confirmAction('루틴을 삭제할까요?', `‘${r.title}’의 완료 기록도 삭제돼요. 필요하면 먼저 백업해주세요.`, async () => { if (await mutate('delete', { id })) toast('루틴을 삭제했어요.'); }, '삭제');
    } else if (action === 'permission') {
      if (!('Notification' in window)) throw new Error('이 환경은 알림을 지원하지 않아요.');
      if (Notification.permission === 'denied') { toast('크롬 주소창의 사이트 설정에서 알림을 허용해주세요.'); return; }
      notificationPermission = await Notification.requestPermission(); render();
    } else if (action === 'test') {
      if (extension) await send({ type: 'test' });
      else if (!await webNotify({ id: 'test', title: 'god life zzz', note: '알림이 도착했어요. 오늘도 하나씩!' }, dateKey())) throw new Error('알림 권한을 먼저 허용해주세요.');
      toast('테스트 알림을 보냈어요.');
    } else if (action === 'export') {
      const fresh = extension ? (await send({ type: 'read' })).routines : browserRead();
      const blob = new Blob([encodeBackup(fresh)], { type: 'application/json' });
      const url = URL.createObjectURL(blob); const link = document.createElement('a');
      link.href = url; link.download = `gatsaeng-${dateKey()}.json`; link.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000); toast('백업 파일을 저장했어요.');
    } else if (action === 'import') $('#import-file').click();
    else if (action === 'install' && installedPrompt) {
      await installedPrompt.prompt(); installedPrompt = null; render();
    }
  } catch (error) { toast(error.message); }
});
$('#import-file').addEventListener('change', async () => {
  const file = $('#import-file').files[0]; $('#import-file').value = '';
  if (!file) return;
  try {
    if (file.size > 4_000_000) throw new Error('백업 파일이 너무 큽니다.');
    const imported = decodeBackup(await file.text());
    confirmAction('백업을 가져올까요?', `${imported.length}개 루틴을 현재 기록과 합칩니다. 같은 루틴의 설정은 현재 값을 유지하고 완료 기록을 합쳐요. 기존 기록은 삭제하지 않습니다.`, async () => { if (await mutate('merge', imported)) toast('백업을 가져왔어요.'); }, '가져오기');
  } catch (error) { toast(`백업을 읽지 못했어요: ${error.message}`); }
});
async function webNotify(routine, date) {
  if (!('Notification' in window) || Notification.permission !== 'granted') return false;
  const options = { body: routine.note || '작은 실천 하나, 오늘도 god life zzz', icon: 'icons/icon128.png', tag: `${routine.id}|${date}` };
  const registration = await navigator.serviceWorker?.getRegistration();
  if (registration) await registration.showNotification(routine.title, options);
  else new Notification(routine.title, options);
  return true;
}
async function webTick() {
  if (extension || !readable) return;
  try {
    await withLock(async () => {
      const current = browserRead();
      const previous = JSON.parse(localStorage.getItem(PLAN_KEY) || '{}');
      const result = await deliverDue(current, makePlans(current, previous), webNotify);
      localStorage.setItem(STORAGE, encodeBackup(result.routines));
      localStorage.setItem(PLAN_KEY, JSON.stringify(result.plans));
      routines = result.routines;
    });
    render();
  } catch (error) { readable = false; toast(error.message); }
}
window.addEventListener('storage', event => { if (event.key === STORAGE) load(); });
document.addEventListener('visibilitychange', () => { if (!document.hidden) { load().then(() => { if (!extension) webTick(); if (sync.personId) syncAction('syncPull').catch(error => toast(`동기화: ${error.message}`)); }); } });
if (extension) chrome.storage.onChanged.addListener(changes => { if (changes.data) load(); });
else {
  if ('serviceWorker' in navigator) navigator.serviceWorker.register('./sw.js').catch(error => console.warn('오프라인 캐시:', error.message));
  window.addEventListener('beforeinstallprompt', event => { event.preventDefault(); installedPrompt = event; if (tab === 'settings') render(); });
  setInterval(webTick, 30_000);
  setInterval(() => { if (sync.personId) syncAction('syncPull').catch(error => toast(`동기화: ${error.message}`)); }, 60_000);
}
await load();
if (!extension && readable) await webTick();
if (sync.personId) syncAction('syncPull').catch(error => toast(`동기화: ${error.message}`));
