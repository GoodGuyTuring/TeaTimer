package app.teatimer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.teatimer.brew.BrewController
import app.teatimer.model.BrewState
import app.teatimer.model.BrewingSequence
import app.teatimer.model.TeaLibrary
import app.teatimer.model.WidgetBackground
import app.teatimer.model.formatRemaining
import app.teatimer.model.formatSteep
import app.teatimer.ui.theme.TeaTimerTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /** Opened from the widget to choose a tea: the main button says "Select" and closes the app. */
    private var fromWidget by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val controller = BrewController.get(this)
        fromWidget = intent.getBooleanExtra(EXTRA_FROM_WIDGET, false)
        setContent {
            TeaTimerTheme {
                TeaTimerApp(controller, fromWidget = fromWidget, onSelect = { finish() })
            }
        }
    }

    // singleTask: a widget tap while the app is still open arrives here instead of in onCreate.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        fromWidget = intent.getBooleanExtra(EXTRA_FROM_WIDGET, false)
    }

    companion object {
        const val EXTRA_FROM_WIDGET = "from_widget"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeaTimerApp(controller: BrewController, fromWidget: Boolean, onSelect: () -> Unit) {
    val state by controller.state.collectAsState()
    val library by controller.libraryFlow.collectAsState()
    val ringSeconds by controller.ringSeconds.collectAsState()
    val widgetBackground by controller.widgetBackground.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var updating by remember { mutableStateOf(false) }
    RequestNotificationPermission()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    if (updating) {
                        CircularProgressIndicator(Modifier.padding(end = 16.dp).size(24.dp), strokeWidth = 2.dp)
                    } else {
                        TextButton(
                            onClick = {
                                updating = true
                                scope.launch {
                                    val result = controller.updateTeas()
                                    updating = false
                                    snackbar.showSnackbar(
                                        result.fold(
                                            onSuccess = { "Tea list updated: ${describe(it)}" },
                                            onFailure = { "Update failed: ${it.message ?: it.javaClass.simpleName}" },
                                        ),
                                    )
                                }
                            },
                        ) { Text("Update teas") }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            state.sequenceId?.let { library[it] }?.let { sequence ->
                BrewCard(
                    sequence = sequence,
                    state = state,
                    selectMode = fromWidget && state.phase == BrewState.Phase.READY,
                    onSelect = onSelect,
                    onToggle = { scope.launch { controller.toggle() } },
                    onRestart = { scope.launch { controller.resetInfusion() } },
                    onStop = { scope.launch { controller.stop() } },
                )
            }
            TeaBrowser(
                library = library,
                selectedId = state.sequenceId,
                modifier = Modifier.weight(1f),
                onChoose = { sequence -> scope.launch { controller.select(sequence.id) } },
            )
            RingDurationSetting(ringSeconds, onChange = { scope.launch { controller.setRingSeconds(it) } })
            WidgetBackgroundSetting(widgetBackground, onChange = { scope.launch { controller.setWidgetBackground(it) } })
        }
    }
}

private fun describe(library: TeaLibrary): String =
    "${library.teaCount} teas" + (library.updated?.let { ", as of $it" } ?: "")

@Composable
private fun BrewCard(
    sequence: BrewingSequence,
    state: BrewState,
    selectMode: Boolean,
    onSelect: () -> Unit,
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
    val step = sequence.steps[state.step]
    var noteExpanded by rememberSaveable(sequence.id) { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(sequence.tea, style = MaterialTheme.typography.titleLarge)
            Text(
                listOfNotNull(sequence.detail, sequence.amount).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                buildString {
                    append("Infusion ${state.step + 1} of ${sequence.steps.size}")
                    step.temp?.let { append(" · $it") }
                    if (step.optional) append(" · optional")
                },
            )
            Text(
                formatRemaining(state.remainingAt(now, step.seconds * 1000L)),
                style = MaterialTheme.typography.displayMedium,
            )
            Text(
                buildAnnotatedString {
                    sequence.steps.forEachIndexed { index, s ->
                        if (index > 0) append("  ")
                        val time = if (s.optional) "(${formatSteep(s.seconds)})" else formatSteep(s.seconds)
                        if (index == state.step) {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)) {
                                append(time)
                            }
                        } else {
                            append(time)
                        }
                    }
                },
                style = MaterialTheme.typography.bodySmall,
            )
            sequence.note?.let { note ->
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (noteExpanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { noteExpanded = !noteExpanded },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (selectMode) {
                    // Chosen from the widget: keep it ready there and go back to the home screen.
                    Button(onClick = onSelect) { Text("Select") }
                } else {
                    Button(onClick = onToggle) {
                        Text(
                            when (state.phase) {
                                BrewState.Phase.READY -> "Start"
                                BrewState.Phase.RUNNING -> "Pause"
                                BrewState.Phase.PAUSED -> "Resume"
                            },
                        )
                    }
                }
                OutlinedButton(onClick = onRestart, enabled = state.phase != BrewState.Phase.READY) {
                    Text("Restart")
                }
                TextButton(onClick = onStop) { Text("Stop") }
            }
        }
    }
}

/** One tab per tea type; each tea has a row per method with its strengths as chips. */
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
    val selectedTab = tab.coerceIn(types.indices)

    Column(modifier) {
        PrimaryScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 16.dp) {
            types.forEachIndexed { index, name ->
                Tab(selected = index == selectedTab, onClick = { tab = index }, text = { Text(name) })
            }
        }
        LazyColumn(Modifier.fillMaxWidth()) {
            items(library.teasOf(types[selectedTab]).entries.toList(), key = { it.key }) { (tea, sequences) ->
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(tea, style = MaterialTheme.typography.titleMedium)
                    sequences.groupBy { it.method }.forEach { (method, strengths) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(method, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(116.dp))
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                strengths.forEach { sequence ->
                                    FilterChip(
                                        selected = sequence.id == selectedId,
                                        onClick = { onChoose(sequence) },
                                        label = { Text(sequence.strength.label) },
                                    )
                                }
                            }
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

/** Background colour of the home screen widget; each swatch previews its text colour. */
@Composable
private fun WidgetBackgroundSetting(selected: WidgetBackground, onChange: (WidgetBackground) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column {
            Text("Widget")
            Text(
                selected.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WidgetBackground.entries.forEach { background ->
                val isSelected = background == selected
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(background.background?.let { Color(it) } ?: MaterialTheme.colorScheme.surfaceContainerHighest)
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape,
                        )
                        .clickable { onChange(background) }
                        .semantics { contentDescription = background.label },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (background == WidgetBackground.WALLPAPER) "A" else "Aa",
                        style = MaterialTheme.typography.labelSmall,
                        color = background.text?.let { Color(it) } ?: MaterialTheme.colorScheme.onSurface,
                    )
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
