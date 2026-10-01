package dev.gatsaeng.app.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.gatsaeng.app.model.Routine
import java.time.DayOfWeek

@Composable
fun RoutineEditor(routine: Routine?, onDismiss: () -> Unit, onSave: (Routine) -> Unit) {
    val context = LocalContext.current
    var title by rememberSaveable(routine?.id) { mutableStateOf(routine?.title.orEmpty()) }
    var note by rememberSaveable(routine?.id) { mutableStateOf(routine?.note.orEmpty()) }
    var dayMask by rememberSaveable(routine?.id) { mutableIntStateOf(routine?.days?.sumOf { 1 shl (it.value - 1) } ?: 127) }
    var hour by rememberSaveable(routine?.id) { mutableIntStateOf(routine?.hour ?: 8) }
    var minute by rememberSaveable(routine?.id) { mutableIntStateOf(routine?.minute ?: 0) }
    var reminder by rememberSaveable(routine?.id) { mutableStateOf(routine?.reminderEnabled ?: true) }
    val validTitle = title.trim().isNotBlank() && title.trim().length <= 80
    val valid = validTitle && note.length <= 500 && dayMask != 0

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().padding(16.dp).widthIn(max = 560.dp), shape = MaterialTheme.shapes.extraLarge) {
            Column(Modifier.padding(22.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(if (routine == null) "새 루틴 만들기" else "루틴 수정", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("작게 시작해도 좋아요. 꾸준히 할 수 있는 만큼.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = title, onValueChange = { title = it.take(80) }, label = { Text("루틴 이름") }, placeholder = { Text("예: 20분 산책하기") }, singleLine = true, modifier = Modifier.fillMaxWidth(), supportingText = { Text("${title.length}/80") })
                OutlinedTextField(value = note, onValueChange = { note = it.take(500) }, label = { Text("메모 (선택)") }, placeholder = { Text("나만의 방법이나 목표를 적어주세요") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4)
                Text("반복 요일", style = MaterialTheme.typography.titleSmall)
                // Two rows stay usable with larger system fonts and narrow screens.
                listOf(DayOfWeek.entries.take(4), DayOfWeek.entries.drop(4)).forEach { days ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        days.forEach { day ->
                            val bit = 1 shl (day.value - 1)
                            FilterChip(selected = dayMask and bit != 0, onClick = { dayMask = dayMask xor bit }, label = { Text(listOf("월", "화", "수", "목", "금", "토", "일")[day.value - 1]) })
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { dayMask = 127 }) { Text("매일") }
                    TextButton(onClick = { dayMask = 31 }) { Text("평일") }
                    TextButton(onClick = { dayMask = 96 }) { Text("주말") }
                }
                if (dayMask == 0) Text("요일을 하나 이상 선택해주세요.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("리마인더 알림", fontWeight = FontWeight.SemiBold); Text("선택한 요일에 알려드려요", style = MaterialTheme.typography.bodySmall) }
                    Switch(checked = reminder, onCheckedChange = { reminder = it })
                }
                if (reminder) OutlinedButton(onClick = {
                    TimePickerDialog(context, { _, h, m -> hour = h; minute = m }, hour, minute, true).show()
                }, modifier = Modifier.fillMaxWidth()) { Text("알림 시간  %02d:%02d".format(hour, minute)) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("취소") }
                    Button(enabled = valid, onClick = {
                        val days = DayOfWeek.entries.filter { dayMask and (1 shl (it.value - 1)) != 0 }.toSet()
                        val updated = if (routine == null) Routine(title = title.trim(), note = note.trim(), days = days, hour = hour, minute = minute, reminderEnabled = reminder)
                        else routine.copy(title = title.trim(), note = note.trim(), days = days, hour = hour, minute = minute, reminderEnabled = reminder)
                        onSave(updated)
                    }) { Text("저장") }
                }
            }
        }
    }
}
