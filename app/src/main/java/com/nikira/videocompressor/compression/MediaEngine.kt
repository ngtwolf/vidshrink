package com.nikira.videocompressor.compression

import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaMetadataRetriever
import android.media.MediaFormat
import android.net.Uri
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.*
import com.nikira.videocompressor.domain.BitrateCalculator
import com.nikira.videocompressor.domain.CompressionSettings
import com.nikira.videocompressor.domain.VideoCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@androidx.media3.common.util.UnstableApi
class MediaEngine(private val context: Context) {
    data class SourceInfo(val durationSeconds: Double, val width: Int, val height: Int)
    fun inspect(uri: Uri): SourceInfo = MediaMetadataRetriever().use { retriever -> retriever.setDataSource(context, uri); SourceInfo(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toDoubleOrNull()?.div(1000.0) ?: 0.0, retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0, retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0) }
    fun supportsHardwareHevc(): Boolean = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.any { info -> info.isEncoder && info.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_VIDEO_HEVC, true) } && isHardware(info) }
    private fun isHardware(info: MediaCodecInfo): Boolean = if (android.os.Build.VERSION.SDK_INT >= 29) info.isHardwareAccelerated else !info.name.startsWith("OMX.google") && !info.name.startsWith("c2.android")
    suspend fun compress(input: Uri, settings: CompressionSettings, output: File, onProgress: (Int) -> Unit) {
        val source = inspect(input); val maximumBytes = (settings.targetMegabytes * 1024 * 1024).toLong()
        var bitrate = BitrateCalculator.videoBitrate(settings.targetMegabytes * INITIAL_BUDGET, source.durationSeconds, settings.audioBitrate)
        require(bitrate > 0) { "This target size cannot hold the audio track. Choose a larger size." }
        repeat(MAX_ATTEMPTS) { attempt ->
            output.delete()
            try { export(input, settings, source, bitrate, output, onProgress) } catch (failure: Throwable) { if (settings.codec != VideoCodec.HEVC) throw failure; output.delete(); export(input, settings.copy(codec = VideoCodec.AVC), source, bitrate, output, onProgress) }
            if (output.length() <= maximumBytes) return
            val ratio = maximumBytes.toDouble() / output.length().toDouble()
            bitrate = (bitrate * ratio * RETRY_HEADROOM).toInt()
            require(bitrate > 0 && attempt < MAX_ATTEMPTS - 1) { "Could not reach the requested maximum file size after $MAX_ATTEMPTS attempts." }
        }
    }
    private suspend fun export(input: Uri, settings: CompressionSettings, source: SourceInfo, bitrate: Int, output: File, onProgress: (Int) -> Unit) = suspendCancellableCoroutine { continuation ->
        val maxDimension = BitrateCalculator.suggestedMaxDimension(bitrate, settings.resolutionLimit)
        val scale = maxDimension?.let { limit -> if (source.width > 0 && source.height > 0) minOf(1f, limit.toFloat() / maxOf(source.width, source.height)) else 1f } ?: 1f
        val effects: List<Effect> = if (scale < 1f) listOf(ScaleAndRotateTransformation.Builder().setScale(scale, scale).build()) else emptyList()
        val edited = EditedMediaItem.Builder(MediaItem.fromUri(input)).setEffects(Effects(emptyList(), effects)).build()
        val factory = DefaultEncoderFactory.Builder(context).setRequestedAudioEncoderSettings(AudioEncoderSettings.Builder().setBitrate(settings.audioBitrate).build()).setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(bitrate).build()).setEnableFallback(true).build()
        lateinit var transformer: Transformer
        transformer = Transformer.Builder(context).setEncoderFactory(factory).setVideoMimeType(if (settings.codec == VideoCodec.HEVC) MimeTypes.VIDEO_H265 else MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC).addListener(object : Transformer.Listener {
            override fun onCompleted(composition: Composition, result: ExportResult) { if (continuation.isActive) continuation.resume(Unit) }
            override fun onError(composition: Composition, result: ExportResult, exception: ExportException) { if (continuation.isActive) continuation.resumeWithException(exception) }
        }).build()
        transformer.start(edited, output.absolutePath)
        val reporter = CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch { val holder = ProgressHolder(); while (continuation.isActive) { if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress); delay(400) } }
        continuation.invokeOnCancellation { reporter.cancel(); transformer.cancel() }
    }
    companion object { private const val INITIAL_BUDGET = 0.90f; private const val RETRY_HEADROOM = 0.90; private const val MAX_ATTEMPTS = 3 }
}