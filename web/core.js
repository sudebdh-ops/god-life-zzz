export const DAYS = ['월', '화', '수', '목', '금', '토', '일'];
export const MAX_ROUTINES = 500;
export function dateKey(date = new Date()) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}
export function dateFromKey(key) {
  if (typeof key !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(key)) throw new Error('날짜 형식을 확인해주세요.');
  const [y, m, d] = key.split('-').map(Number);
  const result = new Date(y, m - 1, d, 12);
  if (dateKey(result) !== key) throw new Error('올바르지 않은 날짜입니다.');
  return result;
}
export function weekday(date) { return date.getDay() === 0 ? 7 : date.getDay(); }
export function due(routine, date = new Date()) {
  return routine.active && dateKey(date) >= routine.createdOn && routine.days.includes(weekday(date));
}
export function completed(routine, date = new Date()) { return routine.completedDates.includes(dateKey(date)); }
export function timeText(r) { return `${String(r.hour).padStart(2, '0')}:${String(r.minute).padStart(2, '0')}`; }
export function daysText(r) {
  const days = [...r.days].sort((a, b) => a - b);
  return days.length === 7 ? '매일' : days.join(',') === '1,2,3,4,5' ? '평일' : days.map(d => DAYS[d - 1]).join(' · ');
}
export function nextReminder(r, now = new Date()) {
  if (!r.active || !r.reminderEnabled) return null;
  for (let offset = 0; offset <= 14; offset++) {
    const date = new Date(now.getFullYear(), now.getMonth(), now.getDate() + offset, r.hour, r.minute);
    const key = dateKey(date);
    if (due(r, date) && !completed(r, date) && r.lastRemindedDate !== key && date > now) return date;
  }
  return null;
}
export function streak(r, today = new Date()) {
  const date = new Date(today.getFullYear(), today.getMonth(), today.getDate(), 12);
  if (!due(r, date) || !completed(r, date)) date.setDate(date.getDate() - 1);
  let count = 0;
  // Only completion-count + one missed occurrence need to be visited.
  for (let visited = 0; dateKey(date) >= r.createdOn && visited <= (r.completedDates.length + 1) * 7; visited++) {
    if (r.days.includes(weekday(date))) {
      if (!completed(r, date)) break;
      count++;
    }
    date.setDate(date.getDate() - 1);
  }
  return count;
}
export function validateRoutine(input) {
  if (!input || typeof input !== 'object') throw new Error('루틴 형식을 확인해주세요.');
  const r = { ...input };
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(r.id)) throw new Error('잘못된 루틴 ID입니다.');
  if (typeof r.title !== 'string' || !r.title.trim() || r.title.length > 80) throw new Error('루틴 이름은 1~80자로 입력해주세요.');
  if (typeof r.note !== 'string' || r.note.length > 500) throw new Error('메모는 500자까지 입력할 수 있어요.');
  if (!Array.isArray(r.days) || !r.days.length || r.days.some(d => !Number.isInteger(d) || d < 1 || d > 7)) throw new Error('반복 요일을 하나 이상 선택해주세요.');
  if (!Number.isInteger(r.hour) || r.hour < 0 || r.hour > 23 || !Number.isInteger(r.minute) || r.minute < 0 || r.minute > 59) throw new Error('알림 시간을 확인해주세요.');
  if (typeof r.active !== 'boolean' || typeof r.reminderEnabled !== 'boolean') throw new Error('루틴 설정 형식을 확인해주세요.');
  dateFromKey(r.createdOn);
  if (!Array.isArray(r.completedDates)) throw new Error('완료 기록을 확인해주세요.');
  r.completedDates.forEach(dateFromKey);
  if (r.lastRemindedDate != null) dateFromKey(r.lastRemindedDate);
  return {
    id: r.id, title: r.title.trim(), note: r.note, days: [...new Set(r.days)].sort((a, b) => a - b),
    hour: r.hour, minute: r.minute, reminderEnabled: r.reminderEnabled, active: r.active,
    completedDates: [...new Set(r.completedDates)].sort(), lastRemindedDate: r.lastRemindedDate ?? null, createdOn: r.createdOn,
  };
}
export function decodeBackup(text) {
  if (typeof text !== 'string' || text.length > 2_000_000) throw new Error('백업 파일이 너무 큽니다.');
  const data = JSON.parse(text);
  if (data.version !== 1 || !Array.isArray(data.routines)) throw new Error('지원하지 않는 백업 형식입니다.');
  if (data.routines.length > MAX_ROUTINES) throw new Error('루틴은 500개까지 사용할 수 있어요.');
  const routines = data.routines.map(r => validateRoutine({ note: '', ...r }));
  if (new Set(routines.map(r => r.id)).size !== routines.length) throw new Error('중복된 루틴 ID가 있어요.');
  return routines;
}
export function encodeBackup(routines) { return JSON.stringify({ version: 1, routines: routines.map(validateRoutine) }, null, 2); }
export function applyCommand(current, type, payload, now = new Date()) {
  let result;
  if (type === 'save') {
    const r = validateRoutine(payload);
    const existing = current.find(x => x.id === r.id);
    if (existing) {
      r.createdOn = existing.createdOn;
      r.completedDates = existing.completedDates;
      r.lastRemindedDate = existing.hour === r.hour && existing.minute === r.minute ? existing.lastRemindedDate : null;
      result = current.map(x => x.id === r.id ? r : x);
    } else result = [...current, r];
  } else if (type === 'delete') result = current.filter(r => r.id !== payload.id);
  else if (type === 'toggleActive') result = current.map(r => r.id === payload.id ? { ...r, active: !r.active } : r);
  else if (type === 'toggleComplete' || type === 'complete') {
    const key = payload.date ?? dateKey(now);
    const date = dateFromKey(key);
    if (key > dateKey(now)) throw new Error('미래 기록은 완료할 수 없어요.');
    result = current.map(r => {
      if (r.id !== payload.id || !due(r, date)) return r;
      const set = new Set(r.completedDates);
      if (type === 'toggleComplete' && set.has(key)) set.delete(key); else set.add(key);
      return { ...r, completedDates: [...set].sort() };
    });
  } else if (type === 'merge') {
    const imported = decodeBackup(encodeBackup(payload));
    result = current.map(existing => {
      const incoming = imported.find(r => r.id === existing.id);
      return incoming ? { ...existing, completedDates: [...new Set([...existing.completedDates, ...incoming.completedDates])].sort() } : existing;
    }).concat(imported.filter(r => !current.some(x => x.id === r.id)));
  } else throw new Error('지원하지 않는 작업입니다.');
  if (result.length > MAX_ROUTINES) throw new Error('루틴은 500개까지 사용할 수 있어요.');
  return result;
}
export function makePlans(routines, previous = {}, now = new Date()) {
  const plans = {};
  for (const r of routines) {
    if (!r.active || !r.reminderEnabled) continue;
    const signature = JSON.stringify([r.days, r.hour, r.minute, r.createdOn]);
    const old = previous[r.id];
    // Preserve an overdue event until the worker can deliver it, unless settings changed.
    if (old && old.signature === signature && old.date >= dateKey(now) && r.lastRemindedDate !== old.date && !r.completedDates.includes(old.date)) {
      plans[r.id] = old;
    } else {
      const next = nextReminder(r, now);
      if (next) plans[r.id] = { when: next.getTime(), date: dateKey(next), signature };
    }
  }
  return plans;
}
export async function deliverDue(routines, plans, notify, now = new Date()) {
  let updated = [...routines];
  for (const r of routines) {
    const plan = plans[r.id];
    if (plan && plan.when <= now.getTime() && plan.date === dateKey(now) && due(r, now) && r.reminderEnabled && !completed(r, now) && r.lastRemindedDate !== plan.date) {
      if (await notify(r, plan.date)) updated = updated.map(x => x.id === r.id ? { ...x, lastRemindedDate: plan.date } : x);
    }
  }
  return { routines: updated, plans: makePlans(updated, plans, now) };
}
