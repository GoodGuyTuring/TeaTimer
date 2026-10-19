package app.teatimer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.teatimer.brew.BrewController
import app.teatimer.model.BrewState
import app.teatimer.model.BrewingSequence
import app.teatimer.model.TeaLibrary
import app.teatimer.model.formatRemaining
import app.teatimer.model.formatSteep
import app.teatimer.ui.theme.TeaTimerTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val controller = BrewController.get(this)
        val fromWidget = intent.getBooleanExtra(EXTRA_FROM_WIDGET, false)
        setContent {
            TeaTimerTheme {
                // Opened from the widget to pick a tea: go straight back to the home screen once picked.
                TeaTimerApp(controller, onSequenceChosen = { if (fromWidget) finish() })
            }
        }
    }

    companion object {
        const val EXTRA_FROM_WIDGET = "from_widget"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeaTimerApp(controller: BrewController, onSequenceChosen: () -> Unit) {
    val state by controller.state.collectAsState()
    val ringSeconds by controller.ringSeconds.collectAsState()
    val scope = rememberCoroutineScope()
    RequestNotificationPermission()

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) }) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            controller.sequenceOf(state)?.let { sequence ->
                BrewCard(
                    sequence = sequence,
                    state = state,
                    onToggle = { scope.launch { controller.toggle() } },
                    onRestart = { scope.launch { controller.resetInfusion() } },
                    onStop = { scope.launch { controller.stop() } },
                )
            }
            TeaBrowser(
                library = controller.library,
                selectedId = state.sequenceId,
                modifier = Modifier.weight(1f),
                onChoose = { sequence ->
                    scope.launch {
                        controller.select(sequence.id)
                        onSequenceChosen()
                    }
                },
            )
            RingDurationSetting(ringSeconds, onChange = controller::setRingSeconds)
        }
    }
}

@Composable
private fun BrewCard(
    sequence: BrewingSequence,
    state: BrewState,
    onToggle: () -> Unit,
    onRestart: () -> Unit,
    onStop: () -> Unit,
) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(state.phase, state.endsAt) {
        while (state.phase == BrewState.Phase.RUNNING) {
            now = SystemClock.elapsedRealtime()
            delay(200)
        }
    }
    val stepMs = sequence.steepSeconds[state.step] * 1000L

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(sequence.name, style = MaterialTheme.typography.titleLarge)
            Text("Infusion ${state.step + 1} of ${sequence.steepSeconds.size}")
            Text(
                formatRemaining(state.remainingAt(now, stepMs)),
                style = MaterialTheme.typography.displayMedium,
            )
            Text(
                buildAnnotatedString {
                    sequence.steepSeconds.forEachIndexed { index, seconds ->
                        if (index > 0) append("  ")
                        if (index == state.step) {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)) {
                                append(formatSteep(seconds))
                            }
                        } else {
                            append(formatSteep(seconds))
                        }
                    }
                },
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onToggle) {
                    Text(
                        when (state.phase) {
                            BrewState.Phase.READY -> "Start"
                            BrewState.Phase.RUNNING -> "Pause"
                            BrewState.Phase.PAUSED -> "Resume"
                        },
                    )
                }
                OutlinedButton(onClick = onRestart, enabled = state.phase != BrewState.Phase.READY) {
                    Text("Restart")
                }
                TextButton(onClick = onStop) { Text("Stop") }
            }
        }
    }
}

/** One tab per tea type; each tea lists its intensities as chips. */
@Composable
private fun TeaBrowser(
    library: TeaLibrary,
    selectedId: String?,
    modifier: Modifier = Modifier,
    onChoose: (BrewingSequence) -> Unit,
) {
    val types = library.types
    if (types.isEmpty()) {
        Text("No teas yet.", modifier = modifier.padding(16.dp))
        return
    }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val type = types[tab.coerceIn(types.indices)]

    Column(modifier) {
        PrimaryScrollableTabRow(selectedTabIndex = tab.coerceIn(types.indices), edgePadding = 16.dp) {
            types.forEachIndexed { index, name ->
                Tab(selected = index == tab, onClick = { tab = index }, text = { Text(name) })
            }
        }
        LazyColumn(Modifier.fillMaxWidth()) {
            items(library.teasOf(type).entries.toList(), key = { it.key }) { (tea, intensities) ->
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(tea, style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        intensities.forEach { sequence ->
                            FilterChip(
                                selected = sequence.id == selectedId,
                                onClick = { onChoose(sequence) },
                                label = { Text("${sequence.intensity} · ${sequence.steepSeconds.size}×") },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RingDurationSetting(ringSeconds: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Ring for")
        val options = BrewController.RING_SECONDS_OPTIONS
        SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
            options.forEachIndexed { index, seconds ->
                SegmentedButton(
                    selected = seconds == ringSeconds,
                    onClick = { onChange(seconds) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                ) {
                    Text("$seconds s")
                }
            }
        }
    }
}

@Composable
private fun RequestNotificationPermission() {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
