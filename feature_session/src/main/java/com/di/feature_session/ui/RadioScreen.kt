package com.di.feature_session.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.di.feature_audio.RadioViewModel
import com.di.feature_audio.presetStations

/**
 * Radio screen optimised for landscape tablets / Chromebooks.
 * ------------------------------------------------------------
 * • Station list (40 % width) on the left with name + one‑line description.
 * • Divider in the middle.
 * • Large playback panel (60 %) on the right.
 * • Portrait mode falls back to the simpler column UI.
 * • Top app‑bar switched to SmallTopAppBar to reclaim vertical space.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioScreen(
    onBack: () -> Unit,
    vm: RadioViewModel = hiltViewModel()
) {
    val isPlaying by vm.isPlaying.collectAsState()

    // Persist selected station so it survives config changes.
    var selectedIndex by rememberSaveable { mutableStateOf(0) }
    val currentStation = presetStations[selectedIndex]

    val isLandscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Scaffold(
        topBar = {
            /* Use the smaller M3 variant to free up content area. */
            SmallTopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Radio") }
            )
        }
    ) { pad ->
        if (isLandscape) {
            /* ────────────────────────── LANDSCAPE ────────────────────────── */
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(pad)
            ) {
                /* ── Station list panel ── */
                Surface(
                    tonalElevation = 2.dp,
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(0.4f)
                ) {
                    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                        itemsIndexed(presetStations) { index, station ->
                            val selected = index == selectedIndex
                            ListItem(
                                headlineContent = { Text(station.name) },
                                supportingContent = { Text(station.description) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedIndex = index
                                        if (isPlaying) vm.toggle(station)
                                    }
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.secondaryContainer
                                        else Color.Transparent
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                /* Divider */
                Divider(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                )

                /* ── Playback panel ── */
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(0.6f)
                        .padding(horizontal = 48.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        currentStation.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(Modifier.height(12.dp))

                    FilledTonalButton(
                        onClick = { vm.toggle(currentStation) },
                        modifier = Modifier
                            .width(260.dp)
                            .height(80.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isPlaying)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Stop" else "Play",
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.width(24.dp))
                        Text(
                            if (isPlaying) "STOP" else "PLAY",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(24.dp))

                    Text(
                        currentStation.description,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(Modifier.height(16.dp))

                    Text(
                        if (isPlaying) "Streaming…" else "Radio is stopped.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isPlaying)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        } else {
            /* ────────────────────────── PORTRAIT ────────────────────────── */
            PortraitRadioScreen(
                pad = pad,
                isPlaying = isPlaying,
                selectedIndex = selectedIndex,
                onStationSelected = { selectedIndex = it },
                currentStation = currentStation,
                onToggle = { vm.toggle(currentStation) }
            )
        }
    }
}

/* -------------------------------- Portrait helper -------------------------------- */
@Composable
private fun PortraitRadioScreen(
    pad: PaddingValues,
    isPlaying: Boolean,
    selectedIndex: Int,
    onStationSelected: (Int) -> Unit,
    currentStation: com.di.feature_audio.RadioStation,
    onToggle: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(pad),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedButton(onClick = { menuOpen = true }) {
            Text(currentStation.name)
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false }
        ) {
            presetStations.forEachIndexed { i, station ->
                DropdownMenuItem(
                    text = { Text(station.name) },
                    onClick = {
                        onStationSelected(i)
                        menuOpen = false
                    }
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        FilledTonalButton(
            onClick = onToggle,
            modifier = Modifier
                .width(320.dp)
                .height(110.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isPlaying)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Stop" else "Play",
                modifier = Modifier.size(42.dp)
            )
            Spacer(Modifier.width(18.dp))
            Text(
                if (isPlaying)
                    "STOP ${currentStation.name.uppercase()}"
                else
                    "PLAY ${currentStation.name.uppercase()}",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(28.dp))

        Text(
            if (isPlaying) "Streaming ${currentStation.name}…" else "Radio is stopped.",
            style = MaterialTheme.typography.bodyLarge,
            color = if (isPlaying)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.onSurface
        )
    }
}
