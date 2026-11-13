/*
 * Copyright (c) 2026 Thomas Mueller
 * Author(s): Thomas Mueller
 * Licensed under AGPL 3.0 or any later version. See COPYING for details.
 */
package org.jamulus.multiplatform

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.Typography
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class AppScreen(val title: String) {
    CONNECT("Verbinden"),
    MIX("Mixer"),
    CHAT("Chat"),
    SETTINGS("Einstellungen")
}

@Composable
private fun ConnectNavigationIcon() {
    val iconColor = LocalContentColor.current
    Canvas(Modifier.size(24.dp)) {
        val scale = size.width / 24f
        val lineWidth = 1.8f * scale

        drawRoundRect(
            color = iconColor,
            topLeft = Offset(0.8f * scale, 1f * scale),
            size = Size(9.5f * scale, 7.2f * scale),
            cornerRadius = CornerRadius(1f * scale),
            style = Stroke(lineWidth)
        )
        drawLine(
            color = iconColor,
            start = Offset(2.2f * scale, 6.3f * scale),
            end = Offset(8.9f * scale, 6.3f * scale),
            strokeWidth = lineWidth
        )
        drawLine(
            color = iconColor,
            start = Offset(5.5f * scale, 8.3f * scale),
            end = Offset(5.5f * scale, 9.8f * scale),
            strokeWidth = lineWidth
        )
        drawLine(
            color = iconColor,
            start = Offset(3.5f * scale, 9.8f * scale),
            end = Offset(7.5f * scale, 9.8f * scale),
            strokeWidth = lineWidth,
            cap = StrokeCap.Round
        )

        val connectionDots = listOf(
            Offset(11f, 3.7f), Offset(13f, 3.7f), Offset(15f, 3.7f),
            Offset(16f, 5.3f), Offset(16f, 7.3f), Offset(16f, 9.3f),
            Offset(16f, 11.3f), Offset(16f, 13.3f), Offset(14.8f, 15f),
            Offset(12.8f, 15f)
        )
        connectionDots.forEach { point ->
            drawCircle(
                color = iconColor,
                radius = 0.85f * scale,
                center = Offset(point.x * scale, point.y * scale)
            )
        }

        drawRoundRect(
            color = iconColor,
            topLeft = Offset(14f * scale, 15.2f * scale),
            size = Size(9.2f * scale, 7.2f * scale),
            cornerRadius = CornerRadius(1f * scale),
            style = Stroke(lineWidth)
        )
        drawLine(
            color = iconColor,
            start = Offset(15.4f * scale, 20.5f * scale),
            end = Offset(21.8f * scale, 20.5f * scale),
            strokeWidth = lineWidth
        )
    }
}

@Composable
private fun MixerNavigationIcon() {
    val iconColor = LocalContentColor.current
    Canvas(Modifier.size(24.dp)) {
        val scale = size.width / 24f
        val lineWidth = 1.8f * scale
        listOf(5.5f to 7f, 12f to 14f, 18.5f to 9.5f).forEach { (x, handleY) ->
            drawLine(
                color = iconColor,
                start = Offset(x * scale, 3f * scale),
                end = Offset(x * scale, 21f * scale),
                strokeWidth = lineWidth,
                cap = StrokeCap.Round
            )
            drawRoundRect(
                color = iconColor,
                topLeft = Offset((x - 2.3f) * scale, (handleY - 1.5f) * scale),
                size = Size(4.6f * scale, 3f * scale),
                cornerRadius = CornerRadius(1f * scale),
                style = Stroke(lineWidth)
            )
        }
    }
}

@Composable
private fun ChatNavigationIcon() {
    val iconColor = LocalContentColor.current
    Canvas(Modifier.size(24.dp)) {
        val scale = size.width / 24f
        val lineWidth = 1.8f * scale
        drawRoundRect(
            color = iconColor,
            topLeft = Offset(2.5f * scale, 3f * scale),
            size = Size(19f * scale, 14f * scale),
            cornerRadius = CornerRadius(3f * scale),
            style = Stroke(lineWidth)
        )
        drawLine(
            color = iconColor,
            start = Offset(7f * scale, 16.5f * scale),
            end = Offset(5f * scale, 20f * scale),
            strokeWidth = lineWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = iconColor,
            start = Offset(5f * scale, 20f * scale),
            end = Offset(11f * scale, 16.5f * scale),
            strokeWidth = lineWidth,
            cap = StrokeCap.Round
        )
        listOf(7.5f, 12f, 16.5f).forEach { x ->
            drawCircle(
                color = iconColor,
                radius = 1f * scale,
                center = Offset(x * scale, 10f * scale)
            )
        }
    }
}

@Composable
private fun SettingsNavigationIcon() {
    val iconColor = LocalContentColor.current
    Canvas(Modifier.size(24.dp)) {
        val scale = size.width / 24f
        val center = Offset(12f * scale, 12f * scale)
        val lineWidth = 1.8f * scale
        for (tooth in 0 until 8) {
            val angle = tooth * PI / 4.0
            val directionX = cos(angle).toFloat()
            val directionY = sin(angle).toFloat()
            drawLine(
                color = iconColor,
                start = Offset(center.x + directionX * 7f * scale, center.y + directionY * 7f * scale),
                end = Offset(center.x + directionX * 10f * scale, center.y + directionY * 10f * scale),
                strokeWidth = 2.2f * scale,
                cap = StrokeCap.Round
            )
        }
        drawCircle(iconColor, radius = 6.5f * scale, center = center, style = Stroke(lineWidth))
        drawCircle(iconColor, radius = 2f * scale, center = center)
    }
}

@Composable
private fun NavigationScreenIcon(screen: AppScreen) {
    when (screen) {
        AppScreen.CONNECT -> ConnectNavigationIcon()
        AppScreen.MIX -> MixerNavigationIcon()
        AppScreen.CHAT -> ChatNavigationIcon()
        AppScreen.SETTINGS -> SettingsNavigationIcon()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NavigationTooltipIcon(screen: AppScreen) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(screen.title) } },
        state = rememberTooltipState()
    ) {
        Box(Modifier.semantics { contentDescription = screen.title }) {
            NavigationScreenIcon(screen)
        }
    }
}

private val chatUrlPattern = Regex("(https?://|www\\.)[^\\s<>]+", RegexOption.IGNORE_CASE)

private val compactTypography = Typography().let { base ->
    fun smaller(style: TextStyle) = style.copy(
        fontSize = style.fontSize * 0.9f,
        lineHeight = if (style.lineHeight == TextUnit.Unspecified) {
            style.lineHeight
        } else {
            style.lineHeight * 0.9f
        }
    )

    base.copy(
        displayLarge = smaller(base.displayLarge),
        displayMedium = smaller(base.displayMedium),
        displaySmall = smaller(base.displaySmall),
        headlineLarge = smaller(base.headlineLarge),
        headlineMedium = smaller(base.headlineMedium),
        headlineSmall = smaller(base.headlineSmall),
        titleLarge = smaller(base.titleLarge),
        titleMedium = smaller(base.titleMedium),
        titleSmall = smaller(base.titleSmall),
        bodyLarge = smaller(base.bodyLarge),
        bodyMedium = smaller(base.bodyMedium),
        bodySmall = smaller(base.bodySmall),
        labelLarge = smaller(base.labelLarge),
        labelMedium = smaller(base.labelMedium),
        labelSmall = smaller(base.labelSmall)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JamulusApp(isAndroid: Boolean) {
    val engine = remember { createJamulusEngine() }
    val settingsRepository = remember { createClientSettingsRepository() }
    var preferences by remember { mutableStateOf(settingsRepository.load()) }
    val snapshot by engine.snapshot.collectAsState()
    val scope = rememberCoroutineScope()
    var selectedScreen by remember { mutableStateOf(AppScreen.CONNECT) }
    var nativeSettingsInitialized by remember { mutableStateOf(isAndroid) }
    val screens = AppScreen.entries
    val savePreferences: (ClientPreferences) -> Unit = { updated ->
        preferences = updated
        settingsRepository.save(updated)
    }

    LaunchedEffect(snapshot.status) {
        if (snapshot.status != JamulusStatus.CONNECTED && selectedScreen in setOf(AppScreen.MIX, AppScreen.CHAT)) {
            selectedScreen = AppScreen.CONNECT
        }
    }

    LaunchedEffect(snapshot.status) {
        if (!nativeSettingsInitialized && snapshot.status != JamulusStatus.ENGINE_UNAVAILABLE && snapshot.status != JamulusStatus.FAILED) {
            val nativeProfile = snapshot.profile.copy(skill = skillLabel(snapshot.profile.skillId))
            val audio = snapshot.audio
            val nativePreferences = preferences.copy(
                profile = nativeProfile,
                smallNetworkBuffers = audio.smallNetworkBuffers,
                audioFormat = audio.format,
                audioChannels = audio.channels,
                audioDevice = audio.device,
                bufferMode = bufferFactorLabel(audio.bufferFactor),
                automaticJitterBuffer = audio.automaticJitterBuffer,
                jitterBufferFrames = audio.jitterBufferFrames,
                serverJitterBufferFrames = audio.serverJitterBufferFrames
            )
            if (nativePreferences != preferences) savePreferences(nativePreferences)
            nativeSettingsInitialized = true
        }
    }

    DisposableEffect(engine, isAndroid) {
        onDispose {
            if (!isAndroid) engine.close()
        }
    }

    MaterialTheme(typography = compactTypography) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Jamulus", fontWeight = FontWeight.SemiBold)
                            Text(
                                statusLabel(snapshot.status),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    },
                    actions = {
                        if (snapshot.status == JamulusStatus.CONNECTED) {
                            OutlinedButton(onClick = {
                                selectedScreen = AppScreen.CONNECT
                                scope.launch { engine.disconnect() }
                            }) {
                                Text("Trennen")
                            }
                        }
                    }
                )
            },
            bottomBar = {
                if (isAndroid) {
                    NavigationBar {
                        screens.forEach { screen ->
                            NavigationBarItem(
                                selected = selectedScreen == screen,
                                enabled = screen !in setOf(AppScreen.MIX, AppScreen.CHAT) || snapshot.status == JamulusStatus.CONNECTED,
                                onClick = { selectedScreen = screen },
                                icon = { NavigationTooltipIcon(screen) },
                                label = null
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Row(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!isAndroid) {
                    NavigationRail {
                        screens.forEach { screen ->
                            NavigationRailItem(
                                selected = selectedScreen == screen,
                                enabled = screen !in setOf(AppScreen.MIX, AppScreen.CHAT) || snapshot.status == JamulusStatus.CONNECTED,
                                onClick = { selectedScreen = screen },
                                icon = { NavigationTooltipIcon(screen) },
                                label = null
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    EngineNotice(snapshot = snapshot)
                    when (selectedScreen) {
                        AppScreen.CONNECT -> ConnectScreen(snapshot, engine, preferences, savePreferences)
                        AppScreen.MIX -> MixerScreen(snapshot, engine)
                        AppScreen.CHAT -> ChatScreen(snapshot, engine)
                        AppScreen.SETTINGS -> SettingsScreen(isAndroid, snapshot, engine, preferences, savePreferences)
                    }
                }
            }
        }
    }
}

@Composable
private fun EngineNotice(snapshot: EngineSnapshot) {
    if (snapshot.status != JamulusStatus.ENGINE_UNAVAILABLE && snapshot.status != JamulusStatus.FAILED) {
        return
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Audio-Engine nicht verfügbar", fontWeight = FontWeight.SemiBold)
            Text(snapshot.detail, style = MaterialTheme.typography.bodyMedium)
            Text(
                "Die Oberfläche zeigt deshalb keine simulierte Verbindung und keine erfundenen Pegel an.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun ConnectScreen(
    snapshot: EngineSnapshot,
    engine: JamulusEngine,
    preferences: ClientPreferences,
    savePreferences: (ClientPreferences) -> Unit
) {
    val scope = rememberCoroutineScope()
    var address by remember(preferences.lastServerAddress) { mutableStateOf(preferences.lastServerAddress) }
    var directory by remember(preferences.directoryAddressDraft, preferences.directoryAddress) {
        mutableStateOf(preferences.directoryAddressDraft.ifBlank { preferences.directoryAddress })
    }
    var directoryMenuExpanded by remember { mutableStateOf(false) }
    var requestedDirectory by remember { mutableStateOf("") }
    var filter by remember(preferences.serverSearchFilter) { mutableStateOf(preferences.serverSearchFilter) }
    val ready = snapshot.status != JamulusStatus.ENGINE_UNAVAILABLE && snapshot.status != JamulusStatus.FAILED

    LaunchedEffect(preferences.directoryAddress, snapshot.directoryAddress, snapshot.status) {
        val selectedAddress = preferences.directoryAddress
            .ifBlank { preferences.directoryAddressDraft }
            .ifBlank { snapshot.directoryAddress }
        if (selectedAddress.isNotBlank()) {
            if (ready && requestedDirectory != selectedAddress) {
                requestedDirectory = selectedAddress
                if (preferences.directoryAddress != selectedAddress || preferences.directoryAddressDraft != selectedAddress) {
                    savePreferences(
                        preferences.copy(
                            directoryAddress = selectedAddress,
                            directoryAddressDraft = selectedAddress
                        )
                    )
                }
                engine.refreshServerList(selectedAddress)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("Mit einem Jamulus-Server verbinden", style = MaterialTheme.typography.headlineSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                OutlinedButton(
                    enabled = ready && snapshot.directories.isNotEmpty(),
                    onClick = { directoryMenuExpanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val selectedName = snapshot.directories.firstOrNull { it.address == directory }?.let(::directoryLabel)
                    Text(selectedName ?: directory.ifBlank { "Verzeichnis auswählen" })
                }
                DropdownMenu(
                    expanded = directoryMenuExpanded,
                    onDismissRequest = { directoryMenuExpanded = false }
                ) {
                    snapshot.directories.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(directoryLabel(option)) },
                            onClick = {
                                directoryMenuExpanded = false
                                directory = option.address
                                requestedDirectory = option.address
                                savePreferences(
                                    preferences.copy(
                                        directoryAddress = option.address,
                                        directoryAddressDraft = option.address
                                    )
                                )
                                scope.launch { engine.refreshServerList(option.address) }
                            }
                        )
                    }
                }
            }
            Button(
                enabled = ready && directory.isNotBlank(),
                onClick = {
                    val selectedAddress = directory.trim().take(255)
                    requestedDirectory = selectedAddress
                    directory = selectedAddress
                    savePreferences(
                        preferences.copy(
                            directoryAddress = selectedAddress,
                            directoryAddressDraft = selectedAddress
                        )
                    )
                    scope.launch { engine.refreshServerList(selectedAddress) }
                }
            ) {
                Text("Aktualisieren")
            }
        }
        if (snapshot.serverListLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        OutlinedTextField(
            value = directory,
            onValueChange = {
                directory = it.take(255)
                savePreferences(preferences.copy(directoryAddressDraft = it.take(255)))
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Verzeichnisadresse") },
            singleLine = true
        )

        OutlinedTextField(
            value = filter,
            onValueChange = {
                filter = it.take(255)
                savePreferences(preferences.copy(serverSearchFilter = it.take(255)))
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Server suchen") },
            placeholder = { Text("Text oder # für belegte Server") },
            singleLine = true
        )

        val query = filter.trim().lowercase()
        val filteredServers = snapshot.servers
            .sortedWith(compareBy<ServerInfo> { it.pingMs ?: Int.MAX_VALUE }.thenBy { it.name.lowercase() })
            .filter { server ->
                when {
                    query.isEmpty() -> true
                    query == "#" -> server.musicianCount > 0
                    else -> listOf(server.name, server.location, server.genre, server.address)
                        .any { value -> value.lowercase().contains(query) }
                }
            }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (snapshot.servers.isEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Text(
                            when {
                                !ready -> snapshot.detail
                                snapshot.serverListLoading -> "Serverliste wird geladen …"
                                snapshot.serverListError.isNotBlank() -> snapshot.serverListError
                                else -> "Keine Server im gewählten Verzeichnis."
                            },
                            Modifier.padding(16.dp)
                        )
                    }
                }
            } else if (filteredServers.isEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Text("Keine passenden Server gefunden.", Modifier.padding(16.dp))
                    }
                }
            } else {
                item {
                    Text(
                        if (filteredServers.size == 1) "1 Server" else "${filteredServers.size} Server",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                items(filteredServers, key = { it.address }) { server ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                val serverTitle = if (server.location.isBlank()) {
                                    server.name
                                } else {
                                    "${server.name} (${server.location})"
                                }
                                Text(serverTitle, fontWeight = FontWeight.SemiBold)
                                if (server.genre.isNotBlank()) Text(server.genre)
                                Text("${server.musicianCount} Musiker · ${server.pingMs?.let { "$it ms" } ?: "Ping ausstehend"}")
                            }
                            Button(enabled = ready, onClick = {
                                savePreferences(preferences.rememberServerAddress(server.address))
                                scope.launch { engine.connect(server.address) }
                            }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                                Text("Verbinden")
                            }
                        }
                    }
                }
            }

            item {
                HorizontalDivider()
                Text("Direkt verbinden", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("Adresse, z. B. jam.example.org:22124") },
                        singleLine = true
                    )
                    Button(
                        enabled = ready && address.isNotBlank(),
                        onClick = {
                            val selectedAddress = address.trim()
                            savePreferences(preferences.rememberServerAddress(selectedAddress))
                            scope.launch { engine.connect(selectedAddress) }
                        }
                    ) {
                        Text("Verbinden")
                    }
                }
            }
        }
    }
}

private fun directoryLabel(directory: ServerDirectory): String = when (directory.key) {
    "0" -> "Alle Genres 1"
    "1" -> "Alle Genres 2"
    "2" -> "Alle Genres (Asien)"
    "3" -> "Genre Rock"
    "4" -> "Genre Jazz"
    "5" -> "Genre Klassik / Folk"
    "6" -> "Genre Chor / Barbershop"
    else -> directory.name
}

@Composable
private fun MixerScreen(snapshot: EngineSnapshot, engine: JamulusEngine) {
    val scope = rememberCoroutineScope()
    val connected = snapshot.status == JamulusStatus.CONNECTED
    val engineReady = snapshot.status != JamulusStatus.ENGINE_UNAVAILABLE && snapshot.status != JamulusStatus.FAILED
    var sendMuted by remember { mutableStateOf(snapshot.sendMuted) }

    LaunchedEffect(snapshot.sendMuted) {
        sendMuted = snapshot.sendMuted
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("Dein Mix", style = MaterialTheme.typography.headlineSmall)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Eingangspegel")
                    Text("Ping ${snapshot.pingMs?.let { "$it ms" } ?: "–"} · Delay ${snapshot.delayMs?.let { "$it ms" } ?: "–"}")
                }
                Text("Links")
                LinearProgressIndicator(
                    progress = { snapshot.inputLevel.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Rechts")
                LinearProgressIndicator(
                    progress = { snapshot.inputLevelRight.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    enabled = engineReady,
                    onClick = {
                        sendMuted = !sendMuted
                        scope.launch { engine.setSendMuted(sendMuted) }
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text(if (sendMuted) "Eigenen Sendestream einschalten" else "Eigenen Sendestream stummschalten")
                }
                Text(
                    "Du kannst den Sendestream schon vor dem Verbinden stummschalten, um Rückkopplungen zu vermeiden.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        if (snapshot.channels.isEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Text("Die Kanäle des persönlichen Mixes erscheinen nach der Verbindung.", Modifier.padding(16.dp))
            }
        }

        snapshot.channels.forEach { channel ->
            MusicianCard(
                channel = channel,
                instrumentIcon = snapshot.instruments.firstOrNull { it.id == channel.instrumentId }?.icon.orEmpty(),
                countryIcon = snapshot.countries.firstOrNull { it.id == channel.countryId }?.icon.orEmpty(),
                enabled = connected,
                engine = engine
            )
        }
    }
}

@Composable
private fun MusicianCard(
    channel: MusicianChannel,
    instrumentIcon: String,
    countryIcon: String,
    enabled: Boolean,
    engine: JamulusEngine
) {
    val scope = rememberCoroutineScope()
    var gain by remember(channel.id) { mutableStateOf(channel.gain) }
    var pan by remember(channel.id) { mutableStateOf(channel.pan) }
    var gainDragging by remember(channel.id) { mutableStateOf(false) }
    var panDragging by remember(channel.id) { mutableStateOf(false) }
    var muted by remember(channel.id) { mutableStateOf(channel.muted) }
    var solo by remember(channel.id) { mutableStateOf(channel.solo) }
    var showPan by remember(channel.id) { mutableStateOf(false) }

    LaunchedEffect(channel.gain) {
        if (!gainDragging) gain = channel.gain
    }
    LaunchedEffect(channel.pan) {
        if (!panDragging) pan = channel.pan
    }
    LaunchedEffect(channel.muted) { muted = channel.muted }
    LaunchedEffect(channel.solo) { solo = channel.solo }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        channel.name,
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (instrumentIcon.isNotBlank()) JamulusIcon(instrumentIcon, Modifier.size(24.dp))
                    if (channel.instrument.isNotBlank()) {
                        Text(channel.instrument, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                    if (countryIcon.isNotBlank()) JamulusIcon(countryIcon, Modifier.size(28.dp))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(
                    onClick = { showPan = !showPan },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (showPan) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
                    )
                ) {
                    Text("L/R")
                }
                OutlinedButton(
                    enabled = enabled,
                    onClick = {
                        muted = !muted
                        scope.launch { engine.setChannelMuted(channel.id, muted) }
                    },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (muted) Color(0xFFD32F2F) else Color.Transparent,
                        contentColor = if (muted) Color.White else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(if (muted) "Stumm" else "Mute")
                }
                OutlinedButton(
                    enabled = enabled,
                    onClick = {
                        solo = !solo
                        scope.launch { engine.setChannelSolo(channel.id, solo) }
                    },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (solo) Color(0xFFD32F2F) else Color.Transparent,
                        contentColor = if (solo) Color.White else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Solo")
                }
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    val trackColor = MaterialTheme.colorScheme.surfaceVariant
                    val meterGreen = Color(0xFF2E7D32)
                    Canvas(Modifier.fillMaxWidth().height(48.dp)) {
                        val inset = 10.dp.toPx()
                        val startX = inset
                        val endX = size.width - inset
                        val centerY = size.height / 2f
                        val strokeWidth = 4.dp.toPx()
                        val meterEndX = startX + (endX - startX) * channel.level.coerceIn(0f, 1f)
                        drawLine(
                            color = trackColor,
                            start = Offset(startX, centerY),
                            end = Offset(endX, centerY),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = meterGreen,
                            start = Offset(startX, centerY),
                            end = Offset(meterEndX, centerY),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round
                        )
                    }
                    Slider(
                        value = gain.coerceIn(0f, 1f),
                        onValueChange = {
                            gain = it
                            gainDragging = true
                        },
                        onValueChangeFinished = {
                            gainDragging = false
                            scope.launch { engine.setChannelGain(channel.id, gain) }
                        },
                        enabled = enabled,
                        colors = SliderDefaults.colors(
                            activeTrackColor = Color.Transparent,
                            inactiveTrackColor = Color.Transparent
                        )
                    )
                }
            }
            if (showPan) {
                Text("Panorama")
                Slider(
                    value = (pan.coerceIn(-1f, 1f) + 1f) / 2f,
                    onValueChange = {
                        pan = it * 2f - 1f
                        panDragging = true
                    },
                    onValueChangeFinished = {
                        panDragging = false
                        scope.launch { engine.setChannelPan(channel.id, pan) }
                    },
                    enabled = enabled
                )
            }
        }
    }
}

@Composable
private fun ChatScreen(snapshot: EngineSnapshot, engine: JamulusEngine) {
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf("") }
    val connected = snapshot.status == JamulusStatus.CONNECTED

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Session-Chat", style = MaterialTheme.typography.headlineSmall)
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(snapshot.chat) { item ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(6.dp)) {
                        Text("${item.sender} · ${item.timestamp}", fontWeight = FontWeight.SemiBold)
                        ChatMessageText(item.text)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = message,
                onValueChange = { if (it.length <= 512) message = it },
                modifier = Modifier.weight(1f),
                label = { Text("Nachricht") },
                enabled = connected,
                singleLine = true
            )
            Button(
                enabled = connected && message.isNotBlank(),
                onClick = {
                    val text = message.trim()
                    message = ""
                    scope.launch { engine.sendChat(text) }
                }
            ) {
                Text("Senden")
            }
        }
    }
}

@Composable
private fun ChatMessageText(text: String) {
    val linkColor = MaterialTheme.colorScheme.primary
    val annotatedText = remember(text, linkColor) {
        buildAnnotatedString {
            var cursor = 0
            chatUrlPattern.findAll(text).forEach { match ->
                val visibleUrl = match.value.trimEnd('.', ',', ')', ']', '}', '!', '?', ':', ';')
                if (visibleUrl.isNotEmpty()) {
                    val start = match.range.first
                    append(text.substring(cursor, start))
                    val url = if (visibleUrl.startsWith("www.", ignoreCase = true)) {
                        "https://$visibleUrl"
                    } else {
                        visibleUrl
                    }
                    withLink(
                        LinkAnnotation.Url(
                            url = url,
                            styles = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
                        )
                    ) {
                        append(visibleUrl)
                    }
                    cursor = start + visibleUrl.length
                }
            }
            append(text.substring(cursor))
        }
    }

    Text(annotatedText, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun SettingsScreen(
    isAndroid: Boolean,
    snapshot: EngineSnapshot,
    engine: JamulusEngine,
    preferences: ClientPreferences,
    savePreferences: (ClientPreferences) -> Unit
) {
    val scope = rememberCoroutineScope()
    val profile = preferences.profile
    val nativeReady = snapshot.status != JamulusStatus.ENGINE_UNAVAILABLE && snapshot.status != JamulusStatus.FAILED
    val skillOptions = listOf(
        JamulusOption(0, "Nicht"),
        JamulusOption(1, "Anfänger"),
        JamulusOption(2, "Mittlere Spielstärke"),
        JamulusOption(3, "Experte")
    )
    val audioFormatOptions = listOf(
        JamulusOption(0, "Niedrig"),
        JamulusOption(1, "Normal"),
        JamulusOption(2, "Hoch"),
        JamulusOption(3, "Maximal (unkomprimiert)")
    )
    val audioChannelOptions = listOf(
        JamulusOption(0, "Mono"),
        JamulusOption(1, "Mono-Eingang / Stereo-Ausgang"),
        JamulusOption(2, "Stereo")
    )
    val audioDeviceOptions = snapshot.audio.devices.mapIndexed { index, device -> JamulusOption(index, device) }
    val instrumentOptions = snapshot.instruments.sortedBy { it.label.lowercase() }
    val countryOptions = snapshot.countries.sortedBy { if (it.id == 0) "" else it.label.lowercase() }
    val selectedInstrument = instrumentOptions.firstOrNull { it.id == profile.instrumentId }
    val selectedCountry = countryOptions.firstOrNull { it.id == profile.countryId }

    val updateProfile: (ClientProfile) -> Unit = { changed ->
        val bounded = changed.copy(alias = changed.alias.take(16), city = changed.city.take(20))
        savePreferences(preferences.copy(profile = bounded))
        scope.launch { engine.setClientProfile(bounded) }
    }

    var jitterBufferFrames by remember(snapshot.audio.jitterBufferFrames) {
        mutableStateOf(snapshot.audio.jitterBufferFrames.toFloat())
    }
    var serverJitterBufferFrames by remember(snapshot.audio.serverJitterBufferFrames) {
        mutableStateOf(snapshot.audio.serverJitterBufferFrames.toFloat())
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Profil und Audio", style = MaterialTheme.typography.headlineSmall)
        if (!nativeReady) {
            Card(Modifier.fillMaxWidth()) {
                Text(
                    if (isAndroid) "Die Android-Oberfläche ist vorhanden, aber die native Jamulus-Client-Engine ist auf diesem Ziel noch nicht angebunden. Diese Einstellungen können daher noch nicht auf eine Jamulus-Sitzung wirken."
                    else "Die native Jamulus-Engine ist nicht verfügbar. Einstellungen können im Moment nicht übernommen werden.",
                    Modifier.padding(16.dp)
                )
            }
        }

        Text("Musikerprofil", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = profile.alias,
            onValueChange = { updateProfile(profile.copy(alias = it)) },
            enabled = nativeReady,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Name / Alias") },
            singleLine = true
        )
        SettingsDropdown(
            label = "Instrument",
            selectedLabel = selectedInstrument?.label ?: profile.instrument,
            selectedIcon = selectedInstrument?.icon.orEmpty(),
            options = instrumentOptions,
            enabled = nativeReady,
            onSelected = { selected ->
                updateProfile(profile.copy(instrumentId = selected.id, instrument = selected.label))
            }
        )
        SettingsDropdown(
            label = "Land / Region",
            selectedLabel = selectedCountry?.label ?: profile.country,
            selectedIcon = selectedCountry?.icon.orEmpty(),
            options = countryOptions,
            enabled = nativeReady,
            onSelected = { selected ->
                updateProfile(profile.copy(countryId = selected.id, country = selected.label))
            }
        )
        OutlinedTextField(
            value = profile.city,
            onValueChange = { updateProfile(profile.copy(city = it)) },
            enabled = nativeReady,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Stadt") },
            singleLine = true
        )
        SettingsDropdown(
            label = "Kenntnisstand",
            selectedLabel = skillOptions.first { it.id == profile.skillId }.label,
            options = skillOptions,
            enabled = nativeReady,
            onSelected = { selected ->
                updateProfile(profile.copy(skillId = selected.id, skill = selected.label))
            }
        )

        HorizontalDivider()
        Text("Audio", style = MaterialTheme.typography.titleMedium)
        SettingsDropdown(
            label = "Audiogerät",
            selectedLabel = snapshot.audio.device.ifBlank { snapshot.audio.devices.firstOrNull().orEmpty() },
            options = audioDeviceOptions,
            enabled = nativeReady,
            onSelected = { selected ->
                val device = snapshot.audio.devices.getOrNull(selected.id).orEmpty()
                savePreferences(preferences.copy(audioDevice = device))
                scope.launch { engine.setAudioDevice(device) }
            }
        )
        if (snapshot.audio.audioError.isNotBlank()) {
            Card(Modifier.fillMaxWidth()) {
                Text(snapshot.audio.audioError, Modifier.padding(16.dp))
            }
        }
        SettingsDropdown(
            label = "Audioqualität",
            selectedLabel = audioFormatOptions.firstOrNull { it.id == snapshot.audio.format }?.label.orEmpty(),
            options = audioFormatOptions,
            enabled = nativeReady,
            onSelected = { selected ->
                savePreferences(preferences.copy(audioFormat = selected.id))
                scope.launch { engine.setAudioFormat(selected.id) }
            }
        )
        SettingsDropdown(
            label = "Audiokanäle",
            selectedLabel = audioChannelOptions.firstOrNull { it.id == snapshot.audio.channels }?.label.orEmpty(),
            options = audioChannelOptions,
            enabled = nativeReady,
            onSelected = { selected ->
                savePreferences(preferences.copy(audioChannels = selected.id))
                scope.launch { engine.setAudioChannels(selected.id) }
            }
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Kleine Netzwerkpuffer (OPUS64)")
            Switch(
                checked = snapshot.audio.smallNetworkBuffers,
                enabled = nativeReady,
                onCheckedChange = { enabled ->
                    savePreferences(preferences.copy(smallNetworkBuffers = enabled))
                    scope.launch { engine.setSmallNetworkBuffers(enabled) }
                }
            )
        }

        SettingsDropdown(
            label = "Soundkartenpuffer",
            selectedLabel = bufferFactorLabel(snapshot.audio.bufferFactor),
            options = listOf(
                JamulusOption(1, "Bevorzugt (64 Samples)"),
                JamulusOption(2, "Standard (128 Samples)"),
                JamulusOption(4, "Sicher (256 Samples)")
            ).filter { it.id in snapshot.audio.supportedBufferFactors },
            enabled = nativeReady,
            onSelected = { selected ->
                savePreferences(preferences.copy(bufferMode = bufferFactorLabel(selected.id)))
                scope.launch { engine.setAudioBufferFactor(selected.id) }
            }
        )
        if (snapshot.audio.actualBufferSamples > 0) {
            Text("Aktive Puffergröße: ${snapshot.audio.actualBufferSamples} Samples")
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Automatischer Jitterpuffer")
            Switch(
                checked = snapshot.audio.automaticJitterBuffer,
                enabled = nativeReady,
                onCheckedChange = { enabled ->
                    savePreferences(preferences.copy(automaticJitterBuffer = enabled))
                    scope.launch { engine.setAutomaticJitterBuffer(enabled) }
                }
            )
        }

        Text("Lokaler Jitterpuffer: ${jitterBufferFrames.roundToInt()} Blöcke")
        Slider(
            value = jitterBufferFrames.coerceIn(1f, 20f),
            onValueChange = { jitterBufferFrames = it },
            onValueChangeFinished = {
                val frames = jitterBufferFrames.roundToInt().coerceIn(1, 20)
                savePreferences(preferences.copy(jitterBufferFrames = frames))
                scope.launch { engine.setJitterBufferFrames(frames) }
            },
            valueRange = 1f..20f,
            steps = 18,
            enabled = nativeReady && !snapshot.audio.automaticJitterBuffer
        )
        Text("Server-Jitterpuffer: ${serverJitterBufferFrames.roundToInt()} Blöcke")
        Slider(
            value = serverJitterBufferFrames.coerceIn(1f, 20f),
            onValueChange = { serverJitterBufferFrames = it },
            onValueChangeFinished = {
                val frames = serverJitterBufferFrames.roundToInt().coerceIn(1, 20)
                savePreferences(preferences.copy(serverJitterBufferFrames = frames))
                scope.launch { engine.setServerJitterBufferFrames(frames) }
            },
            valueRange = 1f..20f,
            steps = 18,
            enabled = nativeReady && !snapshot.audio.automaticJitterBuffer
        )

        HorizontalDivider()
        Text(
            "Audiobenachrichtigungen und MIDI sind noch nicht mit der nativen Compose-Engine verbunden.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun SettingsDropdown(
    label: String,
    selectedLabel: String,
    selectedIcon: String = "",
    options: List<JamulusOption>,
    enabled: Boolean,
    onSelected: (JamulusOption) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(
            enabled = enabled && options.isNotEmpty(),
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.fillMaxWidth()) {
                Text(label, style = MaterialTheme.typography.labelSmall)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (selectedIcon.isNotBlank()) JamulusIcon(selectedIcon, Modifier.size(24.dp))
                    Text(selectedLabel.ifBlank { "Auswählen" })
                }
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (option.icon.isNotBlank()) JamulusIcon(option.icon, Modifier.size(24.dp))
                            Text(option.label)
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelected(option)
                    }
                )
            }
        }
    }
}

private fun skillLabel(skillId: Int): String = when (skillId) {
    1 -> "Anfänger"
    2 -> "Mittlere Spielstärke"
    3 -> "Experte"
    else -> "Nicht"
}

private fun bufferFactorLabel(factor: Int): String = when (factor) {
    1 -> "Bevorzugt"
    4 -> "Sicher"
    else -> "Standard"
}

private fun statusLabel(status: JamulusStatus): String = when (status) {
    JamulusStatus.ENGINE_UNAVAILABLE -> "Engine nicht verfügbar"
    JamulusStatus.DISCONNECTED -> "Nicht verbunden"
    JamulusStatus.CONNECTING -> "Verbindung wird aufgebaut"
    JamulusStatus.CONNECTED -> "Verbunden"
    JamulusStatus.RECONNECTING -> "Verbindung wird wiederhergestellt"
    JamulusStatus.FAILED -> "Fehler"
}
