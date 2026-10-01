package dev.gatsaeng.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.gatsaeng.app.data.RoutineCodec
import dev.gatsaeng.app.data.RoutineRepository
import dev.gatsaeng.app.data.SyncInfo
import dev.gatsaeng.app.data.SyncRepository
import dev.gatsaeng.app.model.Routine
import dev.gatsaeng.app.notifications.AlarmScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class RoutineState(
    val routines: List<Routine> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    val notificationsAllowed: Boolean = false,
    val exactAlarmsAllowed: Boolean = false,
    val storageReadable: Boolean = true,
    val error: String? = null,
    val sync: SyncInfo = SyncInfo(),
)

class RoutineViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = RoutineRepository(application)
    private val scheduler = AlarmScheduler(application)
    private val syncRepository = SyncRepository(application)
    private val mutableState = MutableStateFlow(RoutineState())
    private var scheduleInitialized = false
    val state = mutableState.asStateFlow()

    init { refresh(); syncNow() }

    fun refresh() {
        try {
            val routines = repository.load()
            val notificationsAllowed = scheduler.notificationsAllowed()
            val exactAllowed = scheduler.hasExactPermission()
            // A UI refresh must not cancel an alarm that is about to be delivered.
            if (!scheduleInitialized || notificationsAllowed != mutableState.value.notificationsAllowed ||
                exactAllowed != mutableState.value.exactAlarmsAllowed) {
                scheduler.scheduleAll(routines)
                scheduleInitialized = true
            }
            mutableState.value = RoutineState(routines, LocalDate.now(), notificationsAllowed, exactAllowed,
                sync = syncRepository.info())
        } catch (error: Exception) {
            mutableState.value = mutableState.value.copy(storageReadable = false,
                error = "기록을 불러오지 못했어요. 기존 데이터는 보존됩니다. ${error.message.orEmpty()}")
        }
    }

    private fun change(transform: (List<Routine>) -> List<Routine>): Boolean {
        return try {
            // Reload before each write so notification actions are not overwritten by stale UI state.
            val previous = repository.load()
            val next = transform(previous)
            repository.save(next)
            (previous.map { it.id }.toSet() - next.map { it.id }.toSet()).forEach {
                scheduler.cancel(it)
                scheduler.clearNotification(it)
            }
            next.filter { !it.active || !it.reminderEnabled || it.isCompleted(LocalDate.now()) }
                .forEach { scheduler.clearNotification(it.id) }
            next.filter { updated -> previous.find { it.id == updated.id } != updated }.forEach(scheduler::schedule)
            mutableState.value = mutableState.value.copy(routines = next, today = LocalDate.now(), storageReadable = true)
            true
        } catch (error: Exception) {
            mutableState.value = mutableState.value.copy(error = error.message ?: "저장하지 못했어요.")
            false
        }
    }

    private fun remote(action: () -> Unit) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { action() }
                syncNow()
            } catch (error: Exception) { reportError(error.message ?: "동기화하지 못했어요.") }
        }
    }

    fun syncNow() {
        if (syncRepository.info().personId.isBlank()) return
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { syncRepository.pull(repository.load()) }
                change { result.first.routines }
                mutableState.value = mutableState.value.copy(sync = result.second)
            } catch (error: Exception) { reportError("동기화: ${error.message ?: "서버에 연결하지 못했어요."}") }
        }
    }

    fun connect(kind: String, name: String, code: String) = remote {
        syncRepository.connect(kind, name, code)
    }

    fun share(id: String) = remote {
        val routine = repository.load().find { it.id == id } ?: error("루틴을 찾지 못했어요.")
        syncRepository.share(routine)
    }

    fun save(routine: Routine): Boolean {
        val info = syncRepository.info()
        val existing = mutableState.value.routines.find { it.id == routine.id }
        if (info.personId.isNotBlank() && (existing == null || routine.id in info.sharedIds)) {
            remote {
                if (existing == null) syncRepository.share(routine)
                else syncRepository.put(routine.copy(completedDates = existing.completedDates,
                    createdOn = existing.createdOn), info.revisions[routine.id] ?: error("루틴 버전을 읽지 못했어요."))
            }
            return true
        }
        return change { list ->
        val existing = list.find { it.id == routine.id }
        if (existing == null) {
            require(list.size < 500) { "루틴은 최대 500개까지 추가할 수 있어요." }
            list + routine
        } else list.map { if (it.id == routine.id) routine.copy(
            completedDates = existing.completedDates,
            lastRemindedDate = if (existing.hour != routine.hour || existing.minute != routine.minute) null else existing.lastRemindedDate,
            createdOn = existing.createdOn,
        ) else it }
        }
    }

    fun delete(id: String): Boolean {
        val info = syncRepository.info()
        if (id in info.sharedIds) {
            remote { syncRepository.remove(id, info.revisions[id] ?: error("루틴 버전을 읽지 못했어요.")) }
            return true
        }
        return change { list -> list.filterNot { it.id == id } }
    }
    fun toggleActive(id: String): Boolean {
        val info = syncRepository.info()
        if (id in info.sharedIds) {
            val routine = mutableState.value.routines.find { it.id == id } ?: return false
            remote { syncRepository.put(routine.copy(active = !routine.active),
                info.revisions[id] ?: error("루틴 버전을 읽지 못했어요.")) }
            return true
        }
        return change { list -> list.map { if (it.id == id) it.copy(active = !it.active) else it } }
    }
    fun toggleComplete(id: String): Boolean {
        val info = syncRepository.info()
        if (id in info.sharedIds) {
            val routine = mutableState.value.routines.find { it.id == id } ?: return false
            if (!routine.isDue(LocalDate.now())) return false
            remote { syncRepository.complete(id, LocalDate.now(), !routine.isCompleted(LocalDate.now())) }
            return true
        }
        return change { list ->
        val date = LocalDate.now()
        list.map { r ->
            if (r.id != id || !r.isDue(date)) r else r.copy(completedDates =
                if (r.isCompleted(date)) r.completedDates - date else r.completedDates + date)
        }
        }
    }

    fun export(): String = RoutineCodec.encode(repository.load())
    fun parseBackup(text: String): List<Routine> = RoutineCodec.decode(text)
    fun mergeBackup(imported: List<Routine>): Boolean = change { current ->
        // Same-ID routines keep current settings and merge completion history; nothing is deleted.
        val merged = current.map { existing ->
            val incoming = imported.find { it.id == existing.id }
            if (incoming == null) existing else existing.copy(completedDates = existing.completedDates + incoming.completedDates)
        } + imported.filter { incoming -> current.none { it.id == incoming.id } }
        require(merged.size <= 500) { "합친 루틴 수가 500개를 넘습니다." }
        merged
    }

    fun testNotification(): Boolean {
        if (!scheduler.notificationsAllowed()) {
            reportError("알림 권한을 먼저 허용해주세요.")
            return false
        }
        val sample = Routine(id = "00000000-0000-0000-0000-000000000001", title = "god life zzz 알림 테스트", note = "알림이 도착했어요! 소리와 진동은 휴대폰 알림 설정을 따라요.")
        return scheduler.notify(sample, LocalDate.now())
    }

    fun reportError(message: String) { mutableState.value = mutableState.value.copy(error = message) }
    fun clearError() { mutableState.value = mutableState.value.copy(error = null) }
}
