package com.nikira.videocompressor.compression

import android.app.*
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.nikira.videocompressor.R
import com.nikira.videocompressor.data.SettingsRepository
import com.nikira.videocompressor.domain.CompressionSettings
import com.nikira.videocompressor.domain.ResolutionLimit
import com.nikira.videocompressor.domain.VideoCodec
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import java.io.File

sealed interface CompressionProgress { data object Idle: CompressionProgress; data class Running(val percent:Int, val message:String): CompressionProgress; data class Failed(val message:String): CompressionProgress; data class Completed(val file: File): CompressionProgress }
object CompressionStatus { val state = MutableStateFlow<CompressionProgress>(CompressionProgress.Idle) }

@androidx.media3.common.util.UnstableApi
class CompressionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val input = intent?.data ?: return START_NOT_STICKY
        val shareAfter = intent.getBooleanExtra(EXTRA_SHARE, true)
        createChannel(); startForeground(ID, notification(0, "Preparing video"))
        scope.launch {
            val folder = File(cacheDir, "compressed").apply { mkdirs() }
            val output = File(folder, "compressed-" + System.currentTimeMillis() + ".mp4")
            try {
                val settings = intent.settingsOrNull() ?: SettingsRepository(this@CompressionService).settings.first()
                MediaEngine(this@CompressionService).compress(input, settings, output) { percent ->
                    CompressionStatus.state.value = CompressionProgress.Running(percent, "Compressing video")
                    (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(ID, notification(percent, "Compressing video"))
                }
                if (shareAfter) { share(output); CompressionStatus.state.value = CompressionProgress.Idle }
                else CompressionStatus.state.value = CompressionProgress.Completed(output)
            } catch (error: Throwable) { CompressionStatus.state.value = CompressionProgress.Failed(error.message ?: "Compression failed") }
            finally { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(startId) }
        }
        return START_NOT_STICKY
    }
    private fun Intent?.settingsOrNull(): CompressionSettings? {
        if (this == null || !hasExtra(EXTRA_TARGET_MB)) return null
        val codec = getStringExtra(EXTRA_CODEC)?.let { runCatching { VideoCodec.valueOf(it) }.getOrNull() } ?: VideoCodec.AVC
        val resolution = getStringExtra(EXTRA_RESOLUTION)?.let { runCatching { ResolutionLimit.valueOf(it) }.getOrNull() } ?: ResolutionLimit.AUTO
        return CompressionSettings(targetMegabytes = getFloatExtra(EXTRA_TARGET_MB, 20f), codec = codec, resolutionLimit = resolution)
    }
    private fun share(file: File) { val uri = FileProvider.getUriForFile(this, packageName + ".files", file); startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("video/mp4").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), getString(R.string.share_compressed_video)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    private fun notification(percent: Int, text: String) = NotificationCompat.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_upload).setContentTitle(getString(R.string.app_name)).setContentText(text).setOnlyAlertOnce(true).setOngoing(true).setProgress(100, percent, percent == 0).build()
    private fun createChannel() { (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.compression_channel), NotificationManager.IMPORTANCE_LOW)) }
    override fun onBind(intent: Intent?): IBinder? = null
    companion object { const val EXTRA_SHARE = "share_after"; const val EXTRA_TARGET_MB = "target_megabytes"; const val EXTRA_CODEC = "video_codec"; const val EXTRA_RESOLUTION = "resolution_limit"; private const val CHANNEL = "compression"; private const val ID = 1001 }
}