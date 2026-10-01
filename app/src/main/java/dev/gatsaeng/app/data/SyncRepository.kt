package dev.gatsaeng.app.data

import android.content.Context
import dev.gatsaeng.app.model.Routine
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.time.LocalDate
import java.util.Base64

data class SyncInfo(
    val personId: String = "",
    val workspaceId: String = "",
    val inviteCode: String = "",
    val recoveryCode: String = "",
    val sharedIds: Set<String> = emptySet(),
    val revisions: Map<String, Int> = emptyMap(),
    val friendName: String = "",
    val friendDoneToday: Set<String> = emptySet(),
    val lastSyncAt: String = "",
)

class SyncRepository(context: Context) {
    private val prefs = context.getSharedPreferences("gatsaeng-sync-v1", Context.MODE_PRIVATE)
    private val origin = "https://nxloiezdoytbukmzftzv.supabase.co"
    // Publishable client key. Security relies on scoped RPCs, not secrecy of this key.
    private val publishableKey = "sb_publishable_74KOUr39taQJuL1K604Tgg_BoHhzeOa"
    private val random = SecureRandom()

    private fun code(): String = ByteArray(16).also(random::nextBytes).let {
        "gz1_" + Base64.getUrlEncoder().withoutPadding().encodeToString(it)
    }

    fun info(): SyncInfo {
        val revisions = JSONObject(prefs.getString("revisions", "{}") ?: "{}")
        return SyncInfo(
            personId = prefs.getString("personId", "") ?: "",
            workspaceId = prefs.getString("workspaceId", "") ?: "",
            inviteCode = prefs.getString("inviteCode", "") ?: "",
            recoveryCode = prefs.getString("recoveryCode", "") ?: "",
            sharedIds = prefs.getStringSet("sharedIds", emptySet())?.toSet() ?: emptySet(),
            revisions = revisions.keys().asSequence().associateWith { revisions.getInt(it) },
            friendName = prefs.getString("friendName", "") ?: "",
            friendDoneToday = prefs.getStringSet("friendDoneToday", emptySet())?.toSet() ?: emptySet(),
            lastSyncAt = prefs.getString("lastSyncAt", "") ?: "",
        )
    }

    private fun post(path: String, payload: JSONObject, token: String = publishableKey): JSONObject {
        val connection = (URL(origin + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("apikey", publishableKey)
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val content = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            if (connection.responseCode !in 200..299) {
                val error = runCatching { JSONObject(content) }.getOrNull()
                throw IllegalStateException(error?.optString("message")?.takeIf { it.isNotBlank() }
                    ?: error?.optString("msg")?.takeIf { it.isNotBlank() } ?: "동기화 서버 오류 (${connection.responseCode})")
            }
            return when {
                content.startsWith("{") -> JSONObject(content)
                content.startsWith("[") -> JSONObject().put("array", JSONArray(content))
                else -> JSONObject().put("value", content.trim().trim('"'))
            }
        } finally { connection.disconnect() }
    }

    private fun token(): String {
        val cached = prefs.getString("accessToken", "") ?: ""
        val expires = prefs.getLong("expiresAt", 0)
        if (cached.isNotBlank() && expires > System.currentTimeMillis() + 60_000) return cached
        val refresh = prefs.getString("refreshToken", "") ?: ""
        val auth = if (refresh.isBlank()) post("/auth/v1/signup", JSONObject())
            else post("/auth/v1/token?grant_type=refresh_token", JSONObject().put("refresh_token", refresh))
        val access = auth.getString("access_token")
        prefs.edit().putString("accessToken", access).putString("refreshToken", auth.getString("refresh_token"))
            .putLong("expiresAt", System.currentTimeMillis() + auth.getLong("expires_in") * 1000).apply()
        return access
    }

    private fun rpc(name: String, payload: JSONObject = JSONObject()): JSONObject =
        post("/rest/v1/rpc/gzz_$name", payload, token())

    fun connect(kind: String, name: String, inputCode: String): SyncInfo {
        require(name.trim().length in 1..40 || kind == "restore") { "내 이름을 1~40자로 입력해주세요." }
        require(kind == "create" || Regex("^gz1_[A-Za-z0-9_-]{22}$").matches(inputCode)) { "연결 코드 형식을 확인해주세요." }
        val recovery = if (kind == "restore") inputCode else code()
        val invite = if (kind == "create") code() else inputCode
        val identity = when (kind) {
            "create" -> rpc("create_workspace", JSONObject().put("p_name", name.trim())
                .put("p_invite_code", invite).put("p_recovery_code", recovery))
            "join" -> rpc("join_workspace", JSONObject().put("p_name", name.trim())
                .put("p_invite_code", invite).put("p_recovery_code", recovery))
            "restore" -> rpc("restore_person", JSONObject().put("p_recovery_code", recovery))
            else -> error("지원하지 않는 연결 방식입니다.")
        }
        prefs.edit().putString("personId", identity.getString("personId"))
            .putString("workspaceId", identity.getString("workspaceId"))
            .putString("recoveryCode", recovery)
            .putString("inviteCode", if (kind == "create") invite else "").apply()
        return info()
    }

    fun pull(local: List<Routine>): Pair<WorkspaceMerge, SyncInfo> {
        val before = info()
        require(before.personId.isNotBlank()) { "먼저 공유 공간에 연결해주세요." }
        val snapshot = rpc("snapshot")
        require(snapshot.getString("personId") == before.personId) { "공유 공간의 사용자 정보가 달라요." }
        val merged = WorkspaceSnapshot.merge(local, before.sharedIds, snapshot.toString(), before.personId)
        val revisions = JSONObject()
        val routines = snapshot.getJSONArray("routines")
        for (i in 0 until routines.length()) {
            val routine = routines.getJSONObject(i)
            revisions.put(routine.getString("id"), routine.getInt("revision"))
        }
        val members = snapshot.getJSONArray("members")
        var friendId = ""
        var friendName = ""
        for (i in 0 until members.length()) {
            val person = members.getJSONObject(i)
            if (person.getString("personId") != before.personId) {
                friendId = person.getString("personId")
                friendName = person.getString("displayName")
            }
        }
        val friendDone = mutableSetOf<String>()
        val completions = snapshot.getJSONArray("completions")
        for (i in 0 until completions.length()) {
            val done = completions.getJSONObject(i)
            if (done.getString("personId") == friendId && done.getString("date") == LocalDate.now().toString())
                friendDone += done.getString("routineId")
        }
        prefs.edit().putStringSet("sharedIds", merged.sharedIds).putString("revisions", revisions.toString())
            .putString("friendName", friendName).putStringSet("friendDoneToday", friendDone)
            .putString("lastSyncAt", java.time.LocalDateTime.now().toString()).apply()
        return merged to info()
    }

    fun share(routine: Routine) {
        rpc("share_routine", JSONObject().put("p_data", WorkspaceSnapshot.sharedSettings(routine))
            .put("p_dates", JSONArray(routine.completedDates.sorted().map { it.toString() })))
    }

    fun put(routine: Routine, revision: Int) {
        rpc("put_routine", JSONObject().put("p_data", WorkspaceSnapshot.sharedSettings(routine))
            .put("p_revision", revision))
    }

    fun remove(id: String, revision: Int) {
        rpc("delete_routine", JSONObject().put("p_id", id).put("p_revision", revision))
    }

    fun complete(id: String, date: LocalDate, done: Boolean) {
        rpc("set_completion", JSONObject().put("p_id", id).put("p_date", date.toString()).put("p_done", done))
    }
}
