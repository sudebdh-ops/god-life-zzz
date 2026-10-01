package dev.gatsaeng.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import dev.gatsaeng.app.ui.GatsaengApp
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private val viewModel: RoutineViewModel by viewModels()

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.refresh()
    }

    private val exportFile = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching {
            val json = viewModel.export()
            val output = contentResolver.openOutputStream(uri) ?: error("파일을 열 수 없어요.")
            output.bufferedWriter(Charsets.UTF_8).use { it.write(json) }
            android.widget.Toast.makeText(this, "백업을 저장했어요.", android.widget.Toast.LENGTH_SHORT).show()
        }.onFailure { viewModel.reportError("백업 저장 실패: ${it.message.orEmpty()}") }
    }

    private var pendingImport by androidx.compose.runtime.mutableStateOf<List<dev.gatsaeng.app.model.Routine>?>(null)
    private val importFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            val stream = contentResolver.openInputStream(uri) ?: error("파일을 열 수 없어요.")
            val text = stream.bufferedReader(Charsets.UTF_8).use { reader ->
                val buffer = CharArray(4096)
                val result = StringBuilder()
                while (true) {
                    val length = reader.read(buffer)
                    if (length < 0) break
                    require(result.length + length <= 2_000_000) { "백업 파일이 너무 큽니다." }
                    result.append(buffer, 0, length)
                }
                result.toString()
            }
            pendingImport = viewModel.parseBackup(text)
        }.onFailure { viewModel.reportError("백업을 읽지 못했어요: ${it.message.orEmpty()}") }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GatsaengApp(
                viewModel = viewModel,
                onNotificationPermission = {
                    if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else openNotificationSettings()
                },
                onExactAlarmPermission = {
                    if (Build.VERSION.SDK_INT >= 31) openSettings(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:$packageName".toUri()))
                },
                onNotificationSettings = ::openNotificationSettings,
                onExport = { exportFile.launch("gatsaeng-${LocalDate.now()}.json") },
                onImport = { importFile.launch(arrayOf("application/json", "text/plain")) },
                pendingImport = pendingImport,
                onConfirmImport = { if (viewModel.mergeBackup(pendingImport.orEmpty())) pendingImport = null },
                onCancelImport = { pendingImport = null },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    private fun openNotificationSettings() = openSettings(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    })

    private fun openSettings(intent: Intent) {
        runCatching { startActivity(intent) }.onFailure { viewModel.reportError("설정을 열 수 없어요. 휴대폰 설정에서 앱 권한을 확인해주세요.") }
    }
}
