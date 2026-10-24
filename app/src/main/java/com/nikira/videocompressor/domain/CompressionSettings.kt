package com.nikira.videocompressor.domain

import kotlin.math.max

enum class VideoCodec { AVC, HEVC }
enum class ResolutionLimit(val label: String) { AUTO("Auto"), P1080("1080p"), P720("720p"), P480("480p") }
data class CompressionSettings(val targetMegabytes: Float = 20f, val codec: VideoCodec = VideoCodec.AVC, val audioBitrate: Int = 128_000, val resolutionLimit: ResolutionLimit = ResolutionLimit.AUTO)
object BitrateCalculator {
    fun videoBitrate(targetMegabytes: Float, durationSeconds: Double, audioBitrate: Int = 128_000): Int { require(durationSeconds > 0) { "Video duration must be known." }; val targetBits = targetMegabytes * 1024f * 1024f * 8f; return max(0, ((targetBits - audioBitrate * durationSeconds) / durationSeconds).toInt()) }
    fun suggestedMaxDimension(videoBitrate: Int, preferred: ResolutionLimit): Int? = when (preferred) { ResolutionLimit.P1080 -> 1080; ResolutionLimit.P720 -> 720; ResolutionLimit.P480 -> 480; ResolutionLimit.AUTO -> when { videoBitrate >= 1_000_000 -> null; videoBitrate >= 500_000 -> 720; else -> 480 } }
}