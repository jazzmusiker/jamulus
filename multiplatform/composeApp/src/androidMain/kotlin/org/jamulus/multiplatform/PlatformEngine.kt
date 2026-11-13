/*
 * Copyright (c) 2026 Thomas Mueller
 * Author(s): Thomas Mueller
 * Licensed under AGPL 3.0 or any later version. See COPYING for details.
 */
package org.jamulus.multiplatform

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

private val engineJson = Json { ignoreUnknownKeys = true }

actual fun createJamulusEngine(): JamulusEngine {
    val application = JamulusApplication.instance
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
        application.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
    ) {
        return UnavailableJamulusEngine("Jamulus benötigt die Mikrofonberechtigung für Audioverbindungen.")
    }
    return AndroidJamulusEngine(File(application.filesDir, "Jamulus.ini").absolutePath)
}

private class AndroidJamulusEngine(settingsFile: String) : JamulusEngine {
    private val mutableSnapshot = MutableStateFlow(
        EngineSnapshot(status = JamulusStatus.ENGINE_UNAVAILABLE)
    )
    override val snapshot: StateFlow<EngineSnapshot> = mutableSnapshot

    @Volatile
    private var nativeHandle: Long = 0L

    private val nativeListener: (String) -> Unit = { payload ->
        runCatching { engineJson.decodeFromString<EngineSnapshot>(payload) }
            .onSuccess { mutableSnapshot.value = it }
            .onFailure { error ->
                mutableSnapshot.value = mutableSnapshot.value.copy(
                    status = JamulusStatus.FAILED,
                    detail = "Die native Engine lieferte einen ungültigen Status: ${error.message.orEmpty()}"
                )
            }
    }

    init {
        try {
            System.loadLibrary("jamuluscompose")
            nativeHandle = DesktopNativeBridge.nativeCreate(nativeListener, settingsFile)
            if (nativeHandle == 0L) {
                mutableSnapshot.value = EngineSnapshot(
                    status = JamulusStatus.FAILED,
                    detail = "Die native Jamulus-Engine konnte nicht gestartet werden."
                )
            }
        } catch (error: Throwable) {
            mutableSnapshot.value = EngineSnapshot(
                status = JamulusStatus.ENGINE_UNAVAILABLE,
                detail = error.message ?: "Native Bibliothek jamuluscompose konnte nicht geladen werden."
            )
        }
    }

    override suspend fun refreshServerList(directory: String) {
        if (nativeHandle != 0L) DesktopNativeBridge.nativeRefreshServerList(nativeHandle, directory.take(255))
    }

    override suspend fun setShowServerMusicians(enabled: Boolean) {
        if (nativeHandle != 0L) DesktopNativeBridge.nativeSetShowServerMusicians(nativeHandle, enabled)
    }

    override suspend fun connect(address: String) {
        if (nativeHandle != 0L) DesktopNativeBridge.nativeConnect(nativeHandle, address.trim().take(255))
    }

    override suspend fun disconnect() {
        if (nativeHandle != 0L) DesktopNativeBridge.nativeDisconnect(nativeHandle)
    }

    override suspend fun setSendMuted(muted: Boolean) {
        if (nativeHandle != 0L) DesktopNativeBridge.nativeSetSendMuted(nativeHandle, muted)
    }

    override suspend fun setChannelGain(channelId: Int, gain: Float) {
        if (nativeHandle != 0L) DesktopNativeBridge.nativeSetChannelGain(nativeHandle, channelId, gain.coerceIn(0f, 1f))
    }

    override suspend fun setChannelPan(channelId: Int, pan: Float) {
        if (nativeHandle != 0L) DesktopNativeBridge.nativeSetChannelPan(nativeHandle, channelId, pan.coerceIn(-1f, 1f))
    }

    override suspend fun setChannelMuted(channelId: Int, muted: Boolean) {
        if (nativeHandle != 0L) DesktopNativeBridge.nativeSetChannelMuted(nativeHandle, channelId, muted)
    }

    override suspend fun setChannelSolo(channelId: Int, solo: Boolean) {
        if (nativeHandle != 0L) DesktopNativeBridge.nativeSetChannelSolo(nativeHandle, channelId, solo)
    }

    override suspend fun sendChat(text: String) {
        if (nativeHandle != 0L) DesktopNativeBridge.nativeSendChat(nativeHandle, text.trim().take(512))
    }

    override suspend fun setClientProfile(profile: ClientProfile) {
        if (nativeHandle != 0L) {
            DesktopNativeBridge.nativeSetClientProfile(
                nativeHandle,
                profile.alias.take(16),
                profile.instrumentId,
                profile.countryId,
                profile.city.take(20),
                profile.skillId.coerceIn(0, 3)
            )
        }
    }

    override suspend fun setAudioDevice(device: String) {
        if (nativeHandle != 0L) DesktopNativeBridge.nativeSetAudioDevice(nativeHandle, device.take(255))
    }

    override suspend fun setAudioFormat(format: Int) {
        if (nativeHandle != 0L) DesktopNativeBridge.nativeSetAudioFormat(nativeHandle, format.coerceIn(0, 3))
    }

    override suspend fun setAudioChannels(channels: Int) {
        if (nativeHandle != 0L) DesktopNativeBridge.nativeSetAudioChannels(nativeHandle, channels.coerceIn(0, 2))
    }

    override suspend fun setSmallNetworkBuffers(enabled: Boolean) {
        if (nativeHandle != 0L) DesktopNativeBridge.nativeSetSmallNetworkBuffers(nativeHandle, enabled)
    }

    override suspend fun setAudioBufferFactor(factor: Int) {
        if (nativeHandle != 0L) DesktopNativeBridge.nativeSetAudioBufferFactor(nativeHandle, factor)
    }

    override suspend fun setAutomaticJitterBuffer(enabled: Boolean) {
        if (nativeHandle != 0L) DesktopNativeBridge.nativeSetAutomaticJitterBuffer(nativeHandle, enabled)
    }

    override suspend fun setJitterBufferFrames(frames: Int) {
        if (nativeHandle != 0L) DesktopNativeBridge.nativeSetJitterBufferFrames(nativeHandle, frames.coerceIn(1, 20))
    }

    override suspend fun setServerJitterBufferFrames(frames: Int) {
        if (nativeHandle != 0L) DesktopNativeBridge.nativeSetServerJitterBufferFrames(nativeHandle, frames.coerceIn(1, 20))
    }

    override fun close() {
        val handle = nativeHandle
        nativeHandle = 0L
        if (handle != 0L) DesktopNativeBridge.nativeClose(handle)
    }
}

private object DesktopNativeBridge {
    external fun nativeCreate(listener: (String) -> Unit, settingsFile: String): Long
    external fun nativeClose(handle: Long)
    external fun nativeConnect(handle: Long, address: String)
    external fun nativeDisconnect(handle: Long)
    external fun nativeRefreshServerList(handle: Long, directory: String)
    external fun nativeSetShowServerMusicians(handle: Long, enabled: Boolean)
    external fun nativeSetSendMuted(handle: Long, muted: Boolean)
    external fun nativeSetChannelGain(handle: Long, channelId: Int, gain: Float)
    external fun nativeSetChannelPan(handle: Long, channelId: Int, pan: Float)
    external fun nativeSetChannelMuted(handle: Long, channelId: Int, muted: Boolean)
    external fun nativeSetChannelSolo(handle: Long, channelId: Int, solo: Boolean)
    external fun nativeSendChat(handle: Long, message: String)
    external fun nativeSetClientProfile(
        handle: Long,
        alias: String,
        instrumentId: Int,
        countryId: Int,
        city: String,
        skillId: Int
    )

    external fun nativeSetAudioDevice(handle: Long, device: String)
    external fun nativeSetAudioFormat(handle: Long, format: Int)
    external fun nativeSetAudioChannels(handle: Long, channels: Int)
    external fun nativeSetSmallNetworkBuffers(handle: Long, enabled: Boolean)
    external fun nativeSetAudioBufferFactor(handle: Long, factor: Int)
    external fun nativeSetAutomaticJitterBuffer(handle: Long, enabled: Boolean)
    external fun nativeSetJitterBufferFrames(handle: Long, frames: Int)
    external fun nativeSetServerJitterBufferFrames(handle: Long, frames: Int)
}
