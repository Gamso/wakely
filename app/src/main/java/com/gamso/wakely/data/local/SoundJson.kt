package com.gamso.wakely.data.local

import com.gamso.wakely.domain.model.AlarmSound
import com.gamso.wakely.domain.model.SoundSource
import org.json.JSONObject

internal fun AlarmSound.toJson(): JSONObject = JSONObject().put("uri", uri).put("name", name).put("source", source.name)

internal fun JSONObject.toAlarmSound(): AlarmSound? = runCatching {
    AlarmSound(
        uri = getString("uri"),
        name = optString("name"),
        source = SoundSource.valueOf(getString("source")),
    )
}.getOrNull()
