/*
 * Copyright (c) 2026 Thomas Mueller
 * Author(s): Thomas Mueller
 * Licensed under AGPL 3.0 or any later version. See COPYING for details.
 */
package org.jamulus.multiplatform

import java.util.prefs.Preferences

actual fun createClientSettingsRepository(): ClientSettingsRepository = DesktopClientSettingsRepository()

private class DesktopClientSettingsRepository : ClientSettingsRepository {
    private val store = Preferences.userRoot().node("org/jamulus/multiplatform/client")

    override fun load(): ClientPreferences = ClientPreferences(
        profile = ClientProfile(
            alias = store.get("profile.alias", ""),
            instrument = store.get("profile.instrument", ""),
            instrumentId = store.getInt("profile.instrumentId", 0),
            country = store.get("profile.country", ""),
            countryId = store.getInt("profile.countryId", 0),
            city = store.get("profile.city", ""),
            skill = store.get("profile.skill", ""),
            skillId = store.getInt("profile.skillId", 0)
        ),
        directoryAddress = store.get("client.directoryAddress", ""),
        directoryAddressDraft = store.get(
            "client.directoryAddressDraft",
            store.get("client.directoryAddress", "")
        ),
        serverSearchFilter = store.get("client.serverSearchFilter", ""),
        showAdvancedServerCards = store.getBoolean("client.showAdvancedServerCards", false),
        lastServerAddress = store.get("client.lastServerAddress", ""),
        recentServerAddresses = store.get("client.recentServerAddresses", "")
            .lineSequence().filter(String::isNotBlank).take(10).toList(),
        smallNetworkBuffers = store.getBoolean(
            "audio.smallNetworkBuffers",
            store.get("audio.quality", "OPUS") == "OPUS64"
        ),
        audioFormat = store.getInt("audio.format", 1),
        audioChannels = store.getInt("audio.channels", 0),
        audioDevice = store.get("audio.device", ""),
        audioAlerts = store.getBoolean("audio.alerts", true),
        bufferMode = store.get("audio.bufferMode", "Standard"),
        automaticJitterBuffer = store.getBoolean("network.automaticJitterBuffer", true),
        jitterBufferFrames = store.getInt("network.jitterBufferFrames", 10),
        serverJitterBufferFrames = store.getInt("network.serverJitterBufferFrames", 10)
    )

    override fun save(preferences: ClientPreferences) {
        store.put("profile.alias", preferences.profile.alias.take(16))
        store.put("profile.instrument", preferences.profile.instrument.take(48))
        store.putInt("profile.instrumentId", preferences.profile.instrumentId)
        store.put("profile.country", preferences.profile.country.take(64))
        store.putInt("profile.countryId", preferences.profile.countryId)
        store.put("profile.city", preferences.profile.city.take(20))
        store.put("profile.skill", preferences.profile.skill.take(24))
        store.putInt("profile.skillId", preferences.profile.skillId)
        store.put("client.directoryAddress", preferences.directoryAddress.take(255))
        store.put("client.directoryAddressDraft", preferences.directoryAddressDraft.take(255))
        store.put("client.serverSearchFilter", preferences.serverSearchFilter.take(255))
        store.putBoolean("client.showAdvancedServerCards", preferences.showAdvancedServerCards)
        store.put("client.lastServerAddress", preferences.lastServerAddress.take(255))
        store.put("client.recentServerAddresses", preferences.recentServerAddresses
            .asSequence().map { it.trim().take(255) }.filter(String::isNotBlank).distinctBy(String::lowercase).take(10).joinToString("\n"))
        store.putBoolean("audio.smallNetworkBuffers", preferences.smallNetworkBuffers)
        store.put("audio.quality", if (preferences.smallNetworkBuffers) "OPUS64" else "OPUS")
        store.putInt("audio.format", preferences.audioFormat.coerceIn(0, 3))
        store.putInt("audio.channels", preferences.audioChannels.coerceIn(0, 2))
        store.put("audio.device", preferences.audioDevice.take(255))
        store.putBoolean("audio.alerts", preferences.audioAlerts)
        store.put("audio.bufferMode", preferences.bufferMode)
        store.putBoolean("network.automaticJitterBuffer", preferences.automaticJitterBuffer)
        store.putInt("network.jitterBufferFrames", preferences.jitterBufferFrames.coerceIn(1, 20))
        store.putInt("network.serverJitterBufferFrames", preferences.serverJitterBufferFrames.coerceIn(1, 20))
    }
}
