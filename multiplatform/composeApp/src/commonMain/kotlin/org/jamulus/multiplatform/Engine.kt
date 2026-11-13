/*
 * Copyright (c) 2026 Thomas Mueller
 * Author(s): Thomas Mueller
 * Licensed under AGPL 3.0 or any later version. See COPYING for details.
 */
package org.jamulus.multiplatform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

@Serializable
enum class JamulusStatus {
    ENGINE_UNAVAILABLE,
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    FAILED
}

@Serializable
data class ServerInfo(
    val address: String,
    val name: String,
    val location: String = "",
    val genre: String = "",
    val musicianCount: Int = 0,
    val pingMs: Int? = null,
    val directoryKeys: List<String> = emptyList(),
    val musicians: List<ServerMusician> = emptyList()
)

@Serializable
data class ServerMusician(
    val name: String,
    val instrumentId: Int = 0,
    val countryId: Int = 0
)

@Serializable
data class ServerDirectory(
    val key: String,
    val name: String,
    val address: String
)

@Serializable
data class JamulusOption(val id: Int, val label: String, val icon: String = "")

@Serializable
data class ClientAudioSettings(
    val devices: List<String> = emptyList(),
    val device: String = "",
    val format: Int = 1,
    val channels: Int = 0,
    val smallNetworkBuffers: Boolean = false,
    val bufferFactor: Int = 2,
    val supportedBufferFactors: List<Int> = emptyList(),
    val actualBufferSamples: Int = 0,
    val automaticJitterBuffer: Boolean = true,
    val jitterBufferFrames: Int = 10,
    val serverJitterBufferFrames: Int = 10,
    val audioError: String = ""
)

@Serializable
data class MusicianChannel(
    val id: Int,
    val name: String,
    val instrument: String = "",
    val instrumentId: Int = 0,
    val countryId: Int = 0,
    val level: Float = 0f,
    val gain: Float = 0.7f,
    val pan: Float = 0f,
    val muted: Boolean = false,
    val solo: Boolean = false
)

@Serializable
data class ChatMessage(
    val sender: String,
    val text: String,
    val timestamp: String
)

@Serializable
data class EngineSnapshot(
    val status: JamulusStatus = JamulusStatus.ENGINE_UNAVAILABLE,
    val detail: String = "Die native Jamulus-Engine ist für dieses Ziel nicht verfügbar.",
    val serverAddress: String = "",
    val directoryAddress: String = "",
    val directories: List<ServerDirectory> = emptyList(),
    val profile: ClientProfile = ClientProfile(),
    val instruments: List<JamulusOption> = emptyList(),
    val countries: List<JamulusOption> = emptyList(),
    val audio: ClientAudioSettings = ClientAudioSettings(),
    val serverListLoading: Boolean = false,
    val serverListError: String = "",
    val pingMs: Int? = null,
    val delayMs: Int? = null,
    val inputLevel: Float = 0f,
    val inputLevelRight: Float = 0f,
    val sendMuted: Boolean = true,
    val servers: List<ServerInfo> = emptyList(),
    val channels: List<MusicianChannel> = emptyList(),
    val chat: List<ChatMessage> = emptyList()
)

interface JamulusEngine {
    val snapshot: StateFlow<EngineSnapshot>

    suspend fun refreshServerList(directory: String)
    suspend fun setShowServerMusicians(enabled: Boolean)
    suspend fun connect(address: String)
    suspend fun disconnect()
    suspend fun setSendMuted(muted: Boolean)
    suspend fun setChannelGain(channelId: Int, gain: Float)
    suspend fun setChannelPan(channelId: Int, pan: Float)
    suspend fun setChannelMuted(channelId: Int, muted: Boolean)
    suspend fun setChannelSolo(channelId: Int, solo: Boolean)
    suspend fun sendChat(text: String)
    suspend fun setClientProfile(profile: ClientProfile)
    suspend fun setAudioDevice(device: String)
    suspend fun setAudioFormat(format: Int)
    suspend fun setAudioChannels(channels: Int)
    suspend fun setSmallNetworkBuffers(enabled: Boolean)
    suspend fun setAudioBufferFactor(factor: Int)
    suspend fun setAutomaticJitterBuffer(enabled: Boolean)
    suspend fun setJitterBufferFrames(frames: Int)
    suspend fun setServerJitterBufferFrames(frames: Int)
    fun close()
}

/**
 * Fail-closed engine used until the native C++ facade is linked for this target.
 * It deliberately does not simulate a server connection or audio meters.
 */
class UnavailableJamulusEngine(reason: String) : JamulusEngine {
    private val mutableSnapshot = MutableStateFlow(
        EngineSnapshot(detail = reason)
    )

    override val snapshot: StateFlow<EngineSnapshot> = mutableSnapshot

    override suspend fun refreshServerList(directory: String) = Unit
    override suspend fun setShowServerMusicians(enabled: Boolean) = Unit
    override suspend fun connect(address: String) = Unit
    override suspend fun disconnect() = Unit
    override suspend fun setSendMuted(muted: Boolean) = Unit
    override suspend fun setChannelGain(channelId: Int, gain: Float) = Unit
    override suspend fun setChannelPan(channelId: Int, pan: Float) = Unit
    override suspend fun setChannelMuted(channelId: Int, muted: Boolean) = Unit
    override suspend fun setChannelSolo(channelId: Int, solo: Boolean) = Unit
    override suspend fun sendChat(text: String) = Unit
    override suspend fun setClientProfile(profile: ClientProfile) = Unit
    override suspend fun setAudioDevice(device: String) = Unit
    override suspend fun setAudioFormat(format: Int) = Unit
    override suspend fun setAudioChannels(channels: Int) = Unit
    override suspend fun setSmallNetworkBuffers(enabled: Boolean) = Unit
    override suspend fun setAudioBufferFactor(factor: Int) = Unit
    override suspend fun setAutomaticJitterBuffer(enabled: Boolean) = Unit
    override suspend fun setJitterBufferFrames(frames: Int) = Unit
    override suspend fun setServerJitterBufferFrames(frames: Int) = Unit
    override fun close() = Unit
}

expect fun createJamulusEngine(): JamulusEngine
