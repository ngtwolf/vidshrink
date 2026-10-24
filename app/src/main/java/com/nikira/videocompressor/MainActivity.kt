package com.nikira.videocompressor

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nikira.videocompressor.compression.CompressionProgress
import com.nikira.videocompressor.compression.CompressionService
import com.nikira.videocompressor.compression.CompressionStatus
import com.nikira.videocompressor.compression.MediaEngine
import com.nikira.videocompressor.data.SettingsRepository
import com.nikira.videocompressor.domain.*
import com.nikira.videocompressor.ui.theme.VideoCompressorTheme
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    private var launchIntent by mutableStateOf<Intent?>(null)
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); launchIntent = intent; enableEdgeToEdge(); setContent { VideoCompressorTheme { CompressorApp(launchIntent) } } }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); launchIntent = intent }
}
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SettingsRepository(application)
    val settings = repository.settings
    val supportsHevc = MediaEngine(application).supportsHardwareHevc()
    fun save(settings: CompressionSettings) = viewModelScope.launch { repository.save(settings) }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun CompressorApp(launchIntent: Intent?, viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current; val defaults by viewModel.settings.collectAsStateWithLifecycle(CompressionSettings()); val progress by CompressionStatus.state.collectAsState()
    var showSettings by remember { mutableStateOf(false) }; var selected by remember { mutableStateOf<Uri?>(null) }; var videoSettings by remember(selected) { mutableStateOf(defaults) }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("video/mp4")) { destination ->
        val completed = CompressionStatus.state.value as? CompressionProgress.Completed
        if (destination != null && completed != null) runCatching { copyCompletedVideo(context, completed.file, destination) }.onFailure { CompressionStatus.state.value = CompressionProgress.Failed(it.message ?: "Could not save the video.") }.onSuccess { CompressionStatus.state.value = CompressionProgress.Idle }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) { selected = uri; videoSettings = defaults } }
    val incoming = launchIntent?.takeIf { it.action == Intent.ACTION_SEND }?.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
    LaunchedEffect(incoming) { incoming?.let { startCompression(it, context, defaults, true) } }
    Scaffold(topBar = { TopAppBar(title = { Text(if (showSettings) "Defaults" else "VidShrink", maxLines = 1) }, actions = { if (!showSettings) TextButton({ showSettings = true }) { Text("Defaults") } }) }) { padding ->
        if (showSettings) SettingsScreen(defaults, viewModel.supportsHevc, viewModel::save, { showSettings = false }, Modifier.padding(padding)) else HomeScreen(selected, videoSettings, viewModel.supportsHevc, { picker.launch(arrayOf("video/*")) }, { videoSettings = it }, { selected?.let { startCompression(it, context, videoSettings, false) } }, Modifier.padding(padding))
    }
    when (val current = progress) {
        is CompressionProgress.Completed -> AlertDialog(onDismissRequest = {}, title = { Text("Compressed video ready") }, text = { Text("Choose where to save it or share it now.") }, confirmButton = { TextButton({ saveLauncher.launch("compressed-video.mp4") }) { Text("Save") } }, dismissButton = { TextButton({ shareCompleted(context, current.file); CompressionStatus.state.value = CompressionProgress.Idle }) { Text("Share") } })
        is CompressionProgress.Running -> AlertDialog(onDismissRequest = {}, title = { Text("Processing video") }, text = { Column { Text(current.message); LinearProgressIndicator(progress = { current.percent / 100f }, modifier = Modifier.fillMaxWidth()) } }, confirmButton = {})
        is CompressionProgress.Failed -> AlertDialog(onDismissRequest = { CompressionStatus.state.value = CompressionProgress.Idle }, title = { Text("Compression failed") }, text = { Text(current.message) }, confirmButton = { TextButton({ CompressionStatus.state.value = CompressionProgress.Idle }) { Text("OK") } })
        CompressionProgress.Idle -> Unit
    }
}
@Composable private fun HomeScreen(selected: Uri?, settings: CompressionSettings, hevc: Boolean, pick: () -> Unit, updateSettings: (CompressionSettings) -> Unit, compress: () -> Unit, modifier: Modifier) {
    Column(modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) { Text("Make a smaller MP4 for sharing.", style = MaterialTheme.typography.titleMedium); Button(pick, Modifier.fillMaxWidth()) { Text(if (selected == null) "Choose video" else "Choose another video") }; if (selected != null) { CompressionControls(settings, hevc, updateSettings); Button(compress, Modifier.fillMaxWidth()) { Text("Compress") } } }
}
@Composable private fun CompressionControls(settings: CompressionSettings, hevc: Boolean, update: (CompressionSettings) -> Unit) {
    Text("Maximum final size: " + "%.0f".format(settings.targetMegabytes) + " MB"); Slider(settings.targetMegabytes, { update(settings.copy(targetMegabytes = it)) }, valueRange = 5f..100f)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(10f, 20f, 50f).forEach { value -> AssistChip({ update(settings.copy(targetMegabytes = value)) }, { Text(value.toInt().toString() + " MB") }) } }
    Text("Codec"); Row { VideoCodec.entries.forEach { codec -> FilterChip(codec == settings.codec, { if (codec == VideoCodec.AVC || hevc) update(settings.copy(codec = codec)) }, { Text(if (codec == VideoCodec.AVC) "H.264" else "H.265") }, enabled = codec == VideoCodec.AVC || hevc) } }
    Text("Maximum output resolution"); Row { ResolutionLimit.entries.forEach { limit -> FilterChip(limit == settings.resolutionLimit, { update(settings.copy(resolutionLimit = limit)) }, { Text(limit.label) }) } }; Text("Size and resolution are maximums; videos are never upscaled.", style = MaterialTheme.typography.bodySmall)
}
@Composable private fun SettingsScreen(settings: CompressionSettings, hevc: Boolean, save: (CompressionSettings) -> Unit, done: () -> Unit, modifier: Modifier) { Column(modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) { Text("Defaults apply to videos sent from another app too."); CompressionControls(settings, hevc, save); Button(done) { Text("Done") } } }
private fun copyCompletedVideo(context: Context, file: File, destination: Uri) { context.contentResolver.openOutputStream(destination)?.use { output -> file.inputStream().use { it.copyTo(output) } } ?: error("Could not write the selected location.") }
private fun shareCompleted(context: Context, file: File) { val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".files", file); context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("video/mp4").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share compressed video").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
private fun startCompression(uri: Uri, context: Context, settings: CompressionSettings, shareAfter: Boolean) { ContextCompat.startForegroundService(context, Intent(context, CompressionService::class.java).setData(uri).putExtra(CompressionService.EXTRA_SHARE, shareAfter).putExtra(CompressionService.EXTRA_TARGET_MB, settings.targetMegabytes).putExtra(CompressionService.EXTRA_CODEC, settings.codec.name).putExtra(CompressionService.EXTRA_RESOLUTION, settings.resolutionLimit.name)) }