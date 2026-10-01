package dev.gatsaeng.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.gatsaeng.app.RoutineState
import dev.gatsaeng.app.RoutineViewModel
import dev.gatsaeng.app.model.Routine
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayNames = listOf("월", "화", "수", "목", "금", "토", "일")
fun daysText(routine: Routine): String = when (routine.days) {
    DayOfWeek.entries.toSet() -> "매일"
    setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY) -> "평일"
    else -> routine.days.sortedBy { it.value }.joinToString(" · ") { dayNames[it.value - 1] }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GatsaengApp(
    viewModel: RoutineViewModel,
    onNotificationPermission: () -> Unit,
    onExactAlarmPermission: () -> Unit,
    onNotificationSettings: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    pendingImport: List<Routine>?,
    onConfirmImport: () -> Unit,
    onCancelImport: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editorVisible by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<Routine?>(null) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); viewModel.clearError() }
    }
    LaunchedEffect(Unit) {
        // Keep the Today tab correct if the app remains open across midnight.
        while (true) { delay(60_000); viewModel.refresh() }
    }

    GatsaengTheme {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("god life zzz", fontWeight = FontWeight.ExtraBold) },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    listOf("오늘" to "☀", "내 루틴" to "≡", "설정" to "⚙").forEachIndexed { index, (label, symbol) ->
                        NavigationBarItem(selected = tab == index, onClick = { tab = index },
                            icon = { Text(symbol, fontSize = 24.sp) }, label = { Text(label) })
                    }
                }
            },
            floatingActionButton = {
                if (tab != 2 && state.storageReadable) ExtendedFloatingActionButton(
                    onClick = { editingId = null; editorVisible = true },
                    icon = { Text("+", fontSize = 24.sp) }, text = { Text("루틴 추가") },
                )
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (tab) {
                    0 -> TodayScreen(state, onToggle = viewModel::toggleComplete, onAdd = { editingId = null; editorVisible = true }, onSettings = { tab = 2 })
                    1 -> RoutinesScreen(state,
                        onEdit = { editingId = it.id; editorVisible = true },
                        onDelete = { deleting = it }, onToggleActive = { viewModel.toggleActive(it.id) },
                        onAdd = { editingId = null; editorVisible = true })
                    else -> SettingsScreen(state, onNotificationPermission, onExactAlarmPermission,
                        onNotificationSettings, { viewModel.testNotification() }, onExport, onImport)
                }
            }
        }

        if (editorVisible) RoutineEditor(
            routine = state.routines.find { it.id == editingId },
            onDismiss = { editorVisible = false },
            onSave = { if (viewModel.save(it)) editorVisible = false },
        )
        deleting?.let { routine ->
            AlertDialog(onDismissRequest = { deleting = null }, title = { Text("루틴을 삭제할까요?") },
                text = { Text("‘${routine.title}’의 완료 기록도 삭제돼요. 필요하면 설정에서 먼저 백업해주세요.") },
                confirmButton = { TextButton(onClick = { if (viewModel.delete(routine.id)) deleting = null }) { Text("삭제", color = MaterialTheme.colorScheme.error) } },
                dismissButton = { TextButton(onClick = { deleting = null }) { Text("취소") } })
        }
        pendingImport?.let { routines ->
            AlertDialog(onDismissRequest = onCancelImport, title = { Text("백업을 가져올까요?") },
                text = { Text("${routines.size}개 루틴을 현재 기록과 합칩니다. 기존 루틴의 설정은 유지하고 완료 기록만 합쳐요. 기존 기록은 삭제하지 않습니다.") },
                confirmButton = { TextButton(onClick = onConfirmImport) { Text("가져오기") } },
                dismissButton = { TextButton(onClick = onCancelImport) { Text("취소") } })
        }
    }
}

@Composable
private fun TodayScreen(state: RoutineState, onToggle: (String) -> Unit, onAdd: () -> Unit, onSettings: () -> Unit) {
    val routines = state.routines.filter { it.isDue(state.today) }.sortedWith(compareBy<Routine> { it.hour }.thenBy { it.minute })
    val completed = routines.count { it.isCompleted(state.today) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Column {
                Text(state.today.format(DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN)), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text("오늘도, 하나씩.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text("나만의 속도로 만드는 좋은 하루", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(if (routines.isNotEmpty() && completed == routines.size) "오늘 루틴 모두 완료!" else "오늘의 작은 성취", fontWeight = FontWeight.SemiBold)
                        Text("$completed / ${routines.size}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    }
                    LinearProgressIndicator(progress = { if (routines.isEmpty()) 0f else completed.toFloat() / routines.size }, modifier = Modifier.fillMaxWidth().height(8.dp), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surface.copy(alpha = .6f))
                    Text(if (routines.isEmpty()) "첫 루틴을 추가해서 시작해보세요." else if (completed == routines.size) "수고했어요. 내일도 god life zzz" else "${routines.size - completed}개 남았어요. 한 번에 하나면 충분해요.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (!state.storageReadable) item { InfoCard("기록을 불러오지 못했어요", "기존 데이터를 보존하기 위해 편집을 멈췄습니다. 앱을 다시 열거나 저장 공간을 확인해주세요.") }
        if (!state.notificationsAllowed && state.routines.any { it.reminderEnabled && it.active }) item {
            OutlinedCard(onClick = onSettings) { Column(Modifier.padding(16.dp)) { Text("알림을 켜주세요", fontWeight = FontWeight.Bold); Text("설정에서 알림 권한을 허용하면 루틴을 알려드려요.", style = MaterialTheme.typography.bodySmall) } }
        }
        item { Text("오늘의 루틴", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        if (routines.isEmpty()) item {
            EmptyCard(if (state.routines.isEmpty()) "아직 루틴이 없어요" else "오늘은 예정된 루틴이 없어요",
                if (state.routines.isEmpty()) "물 마시기, 독서, 운동… 직접 원하는 루틴을 만들어보세요." else "반복 요일은 ‘내 루틴’에서 바꿀 수 있어요.", onAdd)
        }
        items(routines, key = { it.id }) { routine ->
            val done = routine.isCompleted(state.today)
            Card(onClick = { onToggle(routine.id) }, colors = CardDefaults.cardColors(containerColor = if (done) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(20.dp)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = done, onCheckedChange = { onToggle(routine.id) })
                    Column(Modifier.weight(1f).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(routine.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        if (routine.note.isNotBlank()) Text(routine.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(if (routine.reminderEnabled) "${routine.timeText} 알림" else "알림 없이 실천", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        val streak = routine.streak(state.today)
                        if (streak > 0) Text("연속 ${streak}회 달성", style = MaterialTheme.typography.labelSmall)
                    }
                    if (done) Text("완료", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(8.dp))
                }
            }
        }
    }
}

@Composable
private fun RoutinesScreen(state: RoutineState, onEdit: (Routine) -> Unit, onDelete: (Routine) -> Unit, onToggleActive: (Routine) -> Unit, onAdd: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Column {
                Text("내 루틴", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text("나에게 맞게 만들고, 필요하면 쉬어가요.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text("전체 ${state.routines.size}개 · 사용 중 ${state.routines.count { it.active }}개", style = MaterialTheme.typography.labelLarge)
            }
        }
        if (state.routines.isEmpty()) item { EmptyCard("좋은 하루의 첫 단추", "반복 요일과 알림 시간을 직접 정해보세요.", onAdd) }
        items(state.routines, key = { it.id }) { routine ->
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(routine.title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Switch(checked = routine.active, onCheckedChange = { onToggleActive(routine) }, enabled = state.storageReadable)
                    }
                    Text("${daysText(routine)} · ${if (routine.reminderEnabled) routine.timeText + " 알림" else "알림 꺼짐"}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    if (routine.note.isNotBlank()) Text(routine.note, style = MaterialTheme.typography.bodySmall)
                    if (!routine.active) Text("잠시 쉬는 중 · 기록은 그대로 보관돼요", style = MaterialTheme.typography.labelMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { onDelete(routine) }, enabled = state.storageReadable) { Text("삭제", color = MaterialTheme.colorScheme.error) }
                        TextButton(onClick = { onEdit(routine) }, enabled = state.storageReadable) { Text("수정") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(state: RoutineState, onPermission: () -> Unit, onExact: () -> Unit, onSettings: () -> Unit, onTest: () -> Unit, onExport: () -> Unit, onImport: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Column { Text("설정", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold); Text("루틴이 제때 찾아올 수 있도록", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        item {
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("알림", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(if (state.notificationsAllowed) "● 알림 허용됨" else "○ 알림 권한이 필요해요", color = MaterialTheme.colorScheme.primary)
                    if (!state.notificationsAllowed) Button(onClick = onPermission) { Text("알림 허용하기") }
                    Text(if (state.exactAlarmsAllowed) "● 정시 알림 사용 가능" else "○ 정확한 시간 알림은 추가 권한이 필요해요", style = MaterialTheme.typography.bodyMedium)
                    if (!state.exactAlarmsAllowed) OutlinedButton(onClick = onExact) { Text("알람 및 리마인더 허용") }
                    Text("정시 권한이 없으면 알림이 늦어질 수 있어요. 소리·진동은 휴대폰의 알림 및 방해금지 설정을 따릅니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onTest, enabled = state.notificationsAllowed) { Text("테스트 알림") }
                        TextButton(onClick = onSettings) { Text("알림 설정") }
                    }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("내 기록", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("루틴과 완료 기록은 이 휴대폰에만 저장돼요. 앱을 삭제하기 전에 백업해주세요. 친구와 자동으로 공유되지는 않아요.", style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = onExport, enabled = state.storageReadable) { Text("백업 파일 저장") }
                    OutlinedButton(onClick = onImport, enabled = state.storageReadable) { Text("백업 파일 가져오기") }
                }
            }
        }
        item { InfoCard("알림이 오지 않을 때", "알림 권한과 ‘알람 및 리마인더’를 확인해주세요. 기종에 따라 배터리 설정에서 이 앱의 백그라운드 사용을 허용해야 합니다. 앱을 강제 종료했다면 다시 열어주세요.") }
        item {
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("god life zzz", fontWeight = FontWeight.Bold)
                Text("v0.1.0 · 오픈소스 · MIT License", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("작은 실천을 쌓는, 우리만의 앱", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun EmptyCard(title: String, body: String, onAdd: () -> Unit) {
    OutlinedCard(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FilledTonalButton(onClick = onAdd) { Text("루틴 만들기") }
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    OutlinedCard(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
