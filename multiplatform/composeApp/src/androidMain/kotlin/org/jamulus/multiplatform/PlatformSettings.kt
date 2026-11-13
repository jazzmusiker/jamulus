/*
 * Copyright (c) 2026 Thomas Mueller
 * Author(s): Thomas Mueller
 * Licensed under AGPL 3.0 or any later version. See COPYING for details.
 */
package org.jamulus.multiplatform

import android.content.Context

actual fun createClientSettingsRepository(): ClientSettingsRepository = AndroidClientSettingsRepository(
    JamulusApplication.instance
)

private class AndroidClientSettingsRepository(context: Context) : ClientSettingsRepository {
    private val store = context.getSharedPreferences("jamulus-client", Context.MODE_PRIVATE)

    override fun load(): ClientPreferences = ClientPreferences(
        profile = ClientProfile(
            alias = store.getString("profile.alias", "").orEmpty(),
            instrument = store.getString("profile.instrument", "").orEmpty(),
            instrumentId = store.getInt("profile.instrumentId", 0),
            country = store.getString("profile.country", "").orEmpty(),
            countryId = store.getInt("profile.countryId", 0),
            city = store.getString("profile.city", "").orEmpty(),
            skill = store.getString("profile.skill", "").orEmpty(),
            skillId = store.getInt("profile.skillId", 0)
        ),
        directoryAddress = store.getString("client.directoryAddress", "").orEmpty(),
        directoryAddressDraft = store.getString(
            "client.directoryAddressDraft",
            store.getString("client.directoryAddress", "")
        ).orEmpty(),
        serverSearchFilter = store.getString("client.serverSearchFilter", "").orEmpty(),
        showAdvancedServerCards = store.getBoolean("client.showAdvancedServerCards", false),
        lastServerAddress = store.getString("client.lastServerAddress", "").orEmpty(),
        recentServerAddresses = store.getString("client.recentServerAddresses", "").orEmpty()
            .lineSequence().filter(String::isNotBlank).take(10).toList(),
        smallNetworkBuffers = store.getBoolean(
            "audio.smallNetworkBuffers",
            store.getString("audio.quality", "OPUS") == "OPUS64"
        ),
        audioFormat = store.getInt("audio.format", 1),
        audioChannels = store.getInt("audio.channels", 0),
        audioDevice = store.getString("audio.device", "").orEmpty(),
        audioAlerts = store.getBoolean("audio.alerts", true),
        bufferMode = store.getString("audio.bufferMode", "Standard").orEmpty(),
        automaticJitterBuffer = store.getBoolean("network.automaticJitterBuffer", true),
        jitterBufferFrames = store.getInt("network.jitterBufferFrames", 10),
        serverJitterBufferFrames = store.getInt("network.serverJitterBufferFrames", 10)
    )

    override fun save(preferences: ClientPreferences) {
        store.edit()
            .putString("profile.alias", preferences.profile.alias.take(16))
            .putString("profile.instrument", preferences.profile.instrument.take(48))
            .putInt("profile.instrumentId", preferences.profile.instrumentId)
            .putString("profile.country", preferences.profile.country.take(64))
            .putInt("profile.countryId", preferences.profile.countryId)
            .putString("profile.city", preferences.profile.city.take(20))
            .putString("profile.skill", preferences.profile.skill.take(24))
            .putInt("profile.skillId", preferences.profile.skillId)
            .putString("client.directoryAddress", preferences.directoryAddress.take(255))
            .putString("client.directoryAddressDraft", preferences.directoryAddressDraft.take(255))
            .putString("client.serverSearchFilter", preferences.serverSearchFilter.take(255))
            .putBoolean("client.showAdvancedServerCards", preferences.showAdvancedServerCards)
            .putString("client.lastServerAddress", preferences.lastServerAddress.take(255))
            .putString("client.recentServerAddresses", preferences.recentServerAddresses
                .asSequence().map { it.trim().take(255) }.filter(String::isNotBlank).distinctBy(String::lowercase).take(10).joinToString("\n"))
            .putBoolean("audio.smallNetworkBuffers", preferences.smallNetworkBuffers)
            .putString("audio.quality", if (preferences.smallNetworkBuffers) "OPUS64" else "OPUS")
            .putInt("audio.format", preferences.audioFormat.coerceIn(0, 3))
            .putInt("audio.channels", preferences.audioChannels.coerceIn(0, 2))
            .putString("audio.device", preferences.audioDevice.take(255))
            .putBoolean("audio.alerts", preferences.audioAlerts)
            .putString("audio.bufferMode", preferences.bufferMode)
            .putBoolean("network.automaticJitterBuffer", preferences.automaticJitterBuffer)
            .putInt("network.jitterBufferFrames", preferences.jitterBufferFrames.coerceIn(1, 20))
            .putInt("network.serverJitterBufferFrames", preferences.serverJitterBufferFrames.coerceIn(1, 20))
            .apply()
    }
}
