package dev.gatsaeng.app.data

import android.content.Context
import android.annotation.SuppressLint
import dev.gatsaeng.app.model.Routine

class RoutineRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("gatsaeng_routines_v1", Context.MODE_PRIVATE)

    // Do not silently replace unreadable data with an empty list.
    fun load(): List<Routine> {
        val raw = preferences.getString("data", null) ?: return emptyList()
        return RoutineCodec.decode(raw)
    }

    // KTX edit returns Unit; use commit directly so storage failures are observable.
    @SuppressLint("UseKtx")
    fun save(routines: List<Routine>) {
        check(preferences.edit().putString("data", RoutineCodec.encode(routines)).commit()) {
            "저장하지 못했어요. 휴대폰 저장 공간을 확인해주세요."
        }
    }
}
