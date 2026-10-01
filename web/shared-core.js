import { dateFromKey, validateRoutine } from './core.js';

// Group settings are shared; completion and reminder history belong to a person/device.
export function sharedSettings(routine) {
  const { id, title, note, days, hour, minute, reminderEnabled, active, createdOn } = validateRoutine(routine);
  return { id, title, note, days, hour, minute, reminderEnabled, active, createdOn };
}

export function mergeWorkspaceSnapshot(localRoutines, oldSharedIds, snapshot, personId) {
  if (!snapshot || !Array.isArray(snapshot.routines) || !Array.isArray(snapshot.completions)) {
    throw new Error('공유 공간의 응답 형식을 확인해주세요.');
  }
  if (snapshot.routines.length > 500) throw new Error('공유 루틴은 500개까지 사용할 수 있어요.');
  const settings = snapshot.routines.map(r => sharedSettings({ ...r, completedDates: [], lastRemindedDate: null }));
  const sharedIds = new Set(settings.map(r => r.id));
  if (sharedIds.size !== settings.length) throw new Error('공유 루틴 ID가 중복됐어요.');
  const completedById = new Map(settings.map(r => [r.id, new Set()]));
  for (const completion of snapshot.completions) {
    if (completion.personId !== personId || !completedById.has(completion.routineId)) continue;
    dateFromKey(completion.date);
    completedById.get(completion.routineId).add(completion.date);
  }
  const previousById = new Map(localRoutines.map(r => [r.id, r]));
  const shared = settings.map(r => {
    const previous = previousById.get(r.id);
    const sameReminder = previous && previous.hour === r.hour && previous.minute === r.minute &&
      previous.reminderEnabled === r.reminderEnabled && previous.active === r.active &&
      previous.days.join(',') === r.days.join(',');
    return validateRoutine({ ...r, completedDates: [...completedById.get(r.id)].sort(),
      lastRemindedDate: sameReminder ? previous.lastRemindedDate : null });
  });
  const formerlyShared = new Set(oldSharedIds);
  const personal = localRoutines.filter(r => !formerlyShared.has(r.id) && !sharedIds.has(r.id));
  if (personal.length + shared.length > 500) throw new Error('공유 후 루틴이 500개를 넘어요.');
  return { routines: [...personal, ...shared], sharedIds: [...sharedIds] };
}
