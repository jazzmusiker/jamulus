/*
 * Copyright (c) 2026 Thomas Mueller
 * Author(s): Thomas Mueller
 * Licensed under AGPL 3.0 or any later version. See COPYING for details.
 */
package org.jamulus.multiplatform

import kotlinx.serialization.Serializable

@Serializable
data class ClientProfile(
    val alias: String = "",
    val instrument: String = "",
    val instrumentId: Int = 0,
    val country: String = "",
    val countryId: Int = 0,
    val city: String = "",
    val skill: String = "",
    val skillId: Int = 0
)

data class ClientPreferences(
    val profile: ClientProfile = ClientProfile(),
    val directoryAddress: String = "",
    val directoryAddressDraft: String = "",
    val serverSearchFilter: String = "",
    val lastServerAddress: String = "",
    val recentServerAddresses: List<String> = emptyList(),
    val smallNetworkBuffers: Boolean = false,
    val audioFormat: Int = 1,
    val audioChannels: Int = 0,
    val audioDevice: String = "",
    val audioAlerts: Boolean = true,
    val bufferMode: String = "Standard",
    val automaticJitterBuffer: Boolean = true,
    val jitterBufferFrames: Int = 10,
    val serverJitterBufferFrames: Int = 10
)

fun ClientPreferences.rememberServerAddress(address: String): ClientPreferences {
    val normalizedAddress = address.trim().take(255)
    if (normalizedAddress.isBlank()) return this

    return copy(
        lastServerAddress = normalizedAddress,
        recentServerAddresses = (listOf(normalizedAddress) + recentServerAddresses)
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinctBy(String::lowercase)
            .take(10)
    )
}

interface ClientSettingsRepository {
    fun load(): ClientPreferences
    fun save(preferences: ClientPreferences)
}

expect fun createClientSettingsRepository(): ClientSettingsRepository
