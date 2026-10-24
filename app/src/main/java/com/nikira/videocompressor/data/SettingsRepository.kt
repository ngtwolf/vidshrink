package com.nikira.videocompressor.data

import android.content.Context
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.nikira.videocompressor.domain.CompressionSettings
import com.nikira.videocompressor.domain.ResolutionLimit
import com.nikira.videocompressor.domain.VideoCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore("compression_settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val targetMb = floatPreferencesKey("target_mb")
        val codec = stringPreferencesKey("codec")
        val audioBitrate = intPreferencesKey("audio_bitrate")
        val resolution = stringPreferencesKey("resolution")
    }
    val settings: Flow<CompressionSettings> = context.settingsDataStore.data.map { p ->
        CompressionSettings(
            targetMegabytes = p[Keys.targetMb] ?: 20f,
            codec = p[Keys.codec]?.let { runCatching { VideoCodec.valueOf(it) }.getOrNull() } ?: VideoCodec.AVC,
            audioBitrate = p[Keys.audioBitrate] ?: 128_000,
            resolutionLimit = p[Keys.resolution]?.let { runCatching { ResolutionLimit.valueOf(it) }.getOrNull() } ?: ResolutionLimit.AUTO,
        )
    }
    suspend fun save(value: CompressionSettings) = context.settingsDataStore.edit { p ->
        p[Keys.targetMb] = value.targetMegabytes
        p[Keys.codec] = value.codec.name
        p[Keys.audioBitrate] = value.audioBitrate
        p[Keys.resolution] = value.resolutionLimit.name
    }
}