package app.teatimer.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.compose
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.teatimer.MainActivity
import app.teatimer.R
import app.teatimer.brew.BrewController
import app.teatimer.model.BrewState
import app.teatimer.model.BrewingSequence
import app.teatimer.model.RingDuration
import app.teatimer.model.WidgetBackground
import app.teatimer.model.formatRemaining
import app.teatimer.model.formatSteep

/**
 * Home screen widget.
 *
 * - No tea chosen: a tap opens the app to choose one.
 * - Tea chosen: a tap starts, pauses or resumes the infusion; a double tap restarts it.
 *   The ✕ goes back to choosing.
 * - Bottom row: previous / next infusion, and the bell chip that cycles the ring
 *   duration (Auto → 3 → 5 → 10 → 15 s), as in the app.
 * - The background colour is chosen in the app.
 */
class TeaTimerWidget : GlanceAppWidget() {
    // Exact: the layout knows its size and drops the hint line when the widget is short.
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val controller = BrewController.get(context)
        provideContent {
            val state by controller.state.collectAsState()
            val library by controller.libraryFlow.collectAsState()
            val ringSetting by controller.ringSetting.collectAsState()
            val background by controller.widgetBackground.collectAsState()
            GlanceTheme {
                CompositionLocalProvider(LocalWidgetColors provides widgetColors(background)) {
                    val sequence = state.sequenceId?.let { library[it] }
                    if (sequence == null) ChooseContent(ringSetting) else BrewContent(sequence, state, ringSetting)
                }
            }
        }
    }
}

/** The widget's colours: the wallpaper-based theme, or a fixed background picked in the app. */
private class WidgetColors(
    val background: ColorProvider,
    val text: ColorProvider,
    val subtext: ColorProvider,
    val chip: ColorProvider,
    val chipText: ColorProvider,
    /** For the embedded countdown; null keeps its theme colour from the layout. */
    val countdownArgb: Int?,
)

private val LocalWidgetColors = staticCompositionLocalOf<WidgetColors> { error("No widget colours") }

@Composable
private fun widgetColors(background: WidgetBackground): WidgetColors {
    if (background.background == null || background.text == null) {
        return WidgetColors(
            background = GlanceTheme.colors.widgetBackground,
            text = GlanceTheme.colors.onSurface,
            subtext = GlanceTheme.colors.onSurfaceVariant,
            chip = GlanceTheme.colors.secondaryContainer,
            chipText = GlanceTheme.colors.onSecondaryContainer,
            countdownArgb = null,
        )
    }
    val text = Color(background.text)
    return WidgetColors(
        background = ColorProvider(Color(background.background)),
        text = ColorProvider(text),
        subtext = ColorProvider(text.copy(alpha = 0.75f)),
        chip = ColorProvider(text.copy(alpha = 0.16f)),
        chipText = ColorProvider(text),
        countdownArgb = text.toArgb(),
    )
}

private val widgetModifier: GlanceModifier
    @Composable get() = GlanceModifier
        .fillMaxSize()
        .background(LocalWidgetColors.current.background)
        .cornerRadius(16.dp)
        .padding(12.dp)

@Composable
private fun ChooseContent(ringSetting: Int) {
    val context = LocalContext.current
    val colors = LocalWidgetColors.current
    Column(
        modifier = widgetModifier.clickable(
            actionStartActivity<MainActivity>(actionParametersOf(FromWidgetKey to true)),
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(GlanceModifier.defaultWeight())
        Text(
            text = context.getString(R.string.app_name),
            style = TextStyle(color = colors.text, fontWeight = FontWeight.Bold, fontSize = 16.sp),
        )
        Text(
            text = context.getString(R.string.widget_choose_hint),
            style = TextStyle(color = colors.subtext),
        )
        Spacer(GlanceModifier.defaultWeight())
        RingChip(ringSetting)
    }
}

@Composable
private fun BrewContent(sequence: BrewingSequence, state: BrewState, ringSetting: Int) {
    val context = LocalContext.current
    val colors = LocalWidgetColors.current
    val step = sequence.steps[state.step]
    Column(
        modifier = widgetModifier.clickable(actionRunCallback<TapAction>()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = sequence.tea,
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(color = colors.text, fontWeight = FontWeight.Bold),
            )
            Box(
                modifier = GlanceModifier.padding(start = 8.dp).clickable(actionRunCallback<ResetAction>()),
            ) {
                Text("✕", style = TextStyle(color = colors.subtext, fontSize = 18.sp))
            }
        }
        Text(
            text = sequence.detail,
            maxLines = 1,
            style = TextStyle(color = colors.subtext, fontSize = 12.sp),
        )
        Text(
            text = buildString {
                append("Infusion ${state.step + 1} of ${sequence.steps.size}")
                step.temp?.let { append(" · $it") }
                if (step.optional) append(" · optional")
                if (state.phase == BrewState.Phase.PAUSED) append(" · paused")
            },
            maxLines = 1,
            style = TextStyle(color = colors.subtext, fontSize = 12.sp),
        )
        // Leaf amount, when it matters: on the first infusion. Same for every strength.
        // The line keeps its space (blank) on later infusions, so the time doesn't shift.
        val hasLeafLine = sequence.amount != null
        if (hasLeafLine) {
            Text(
                text = if (state.step == 0) "Leaf: ${sequence.amount}" else " ",
                maxLines = 1,
                style = TextStyle(color = colors.subtext, fontSize = 12.sp),
            )
        }
        // The time gets all the free space and sits in its centre, the same box in every phase,
        // so the running countdown doesn't move away from where the ready/paused time was.
        val showHint = LocalSize.current.height >= HINT_MIN_HEIGHT
        val timeSize = timeSizeDp(
            widthDp = LocalSize.current.width.value,
            heightDp = LocalSize.current.height.value,
            showHint = showHint,
            extraLines = if (hasLeafLine) 1 else 0,
        )
        Box(
            modifier = GlanceModifier.defaultWeight().fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            when (state.phase) {
                BrewState.Phase.RUNNING -> AndroidRemoteViews(
                    remoteViews = RemoteViews(context.packageName, R.layout.widget_countdown).apply {
                        setChronometer(R.id.countdown, state.endsAt, null, true)
                        setChronometerCountDown(R.id.countdown, true)
                        setTextViewTextSize(R.id.countdown, TypedValue.COMPLEX_UNIT_DIP, timeSize)
                        colors.countdownArgb?.let { setTextColor(R.id.countdown, it) }
                    },
                    modifier = GlanceModifier.fillMaxSize(),
                )
                BrewState.Phase.READY -> BigTime(formatSteep(step.seconds), timeSize)
                BrewState.Phase.PAUSED -> BigTime(formatRemaining(state.remainingMs), timeSize)
            }
        }
        if (showHint) {
            Text(
                text = when (state.phase) {
                    BrewState.Phase.READY -> "Tap to start"
                    BrewState.Phase.RUNNING -> "Tap to pause · double-tap to restart"
                    BrewState.Phase.PAUSED -> "Paused · tap to resume"
                },
                maxLines = 1,
                style = TextStyle(color = colors.subtext, fontSize = 12.sp),
            )
            Spacer(GlanceModifier.height(4.dp))
        }
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Chip(
                icon = R.drawable.ic_prev,
                label = "Prev",
                description = "Previous infusion",
                action = actionRunCallback<PreviousAction>(),
                enabled = state.step > 0,
            )
            Spacer(GlanceModifier.width(6.dp))
            Chip(
                icon = R.drawable.ic_next,
                label = "Next",
                description = "Next infusion",
                action = actionRunCallback<NextAction>(),
                enabled = state.step < sequence.steps.lastIndex,
                iconAfter = true,
            )
            Spacer(GlanceModifier.defaultWeight())
            RingChip(ringSetting)
        }
    }
}

/** The ready/paused time, at [sizeDp] like the running countdown (whatever the phone's font scale). */
@Composable
private fun BigTime(text: String, sizeDp: Float) {
    val fontScale = LocalContext.current.resources.configuration.fontScale
    Text(
        text = text,
        maxLines = 1,
        style = TextStyle(
            color = LocalWidgetColors.current.text,
            fontWeight = FontWeight.Bold,
            fontSize = (sizeDp / fontScale).sp,
        ),
    )
}

/**
 * Font size (dp) for the big time: as wide as the widget allows with a small gap at each
 * side, but never so tall that it crowds out the lines above and the button row below.
 * Sized for five characters ("00:25", the running countdown's format) so ready, paused
 * and running all use the same size.
 */
internal fun timeSizeDp(widthDp: Float, heightDp: Float, showHint: Boolean, extraLines: Int = 0): Float {
    val byWidth = (widthDp - 2 * WIDGET_PADDING_DP - 2 * TIME_GAP_DP) / TIME_WIDTH_EM
    val reserved = 2 * WIDGET_PADDING_DP + TOP_LINES_DP + extraLines * INFO_LINE_DP + BUTTON_ROW_DP +
        if (showHint) HINT_DP else 0f
    val byHeight = (heightDp - reserved) / TIME_LINE_HEIGHT_EM
    return minOf(byWidth, byHeight).coerceIn(TIME_MIN_DP, TIME_MAX_DP)
}

private const val WIDGET_PADDING_DP = 12f
private const val TIME_GAP_DP = 8f
/** Width of "00:25" in bold digits, in font sizes (digits ≈ 0.6, colon ≈ 0.3, plus some slack). */
private const val TIME_WIDTH_EM = 2.8f
/** Text line height in font sizes, including the font's built-in padding. */
private const val TIME_LINE_HEIGHT_EM = 1.25f
/** Title row and the two info lines above the time. */
private const val TOP_LINES_DP = 62f
/** One more small info line, e.g. the leaf amount on the first infusion. */
private const val INFO_LINE_DP = 18f
private const val BUTTON_ROW_DP = 32f
private const val HINT_DP = 22f
private const val TIME_MIN_DP = 28f
private const val TIME_MAX_DP = 220f

/** Shows the ring duration; a tap switches to the next one. */
@Composable
private fun RingChip(ringSetting: Int) {
    Chip(
        icon = R.drawable.ic_ring,
        label = RingDuration.label(ringSetting),
        description = "Ring duration",
        action = actionRunCallback<CycleRingAction>(),
    )
}

/**
 * A small tappable pill with an icon and a label, dimmed when [enabled] is false. It stays
 * clickable then (its action does nothing at that point), so the tap doesn't fall through
 * to the widget behind it and start or pause the timer.
 */
@Composable
private fun Chip(
    icon: Int,
    label: String,
    description: String,
    action: Action,
    enabled: Boolean = true,
    iconAfter: Boolean = false,
) {
    val colors = LocalWidgetColors.current
    val content = if (enabled) colors.chipText else colors.subtext
    val modifier = GlanceModifier
        .cornerRadius(12.dp)
        .background(colors.chip)
        .padding(horizontal = 10.dp, vertical = 6.dp)
        .clickable(action)
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        val image = @Composable {
            Image(
                provider = ImageProvider(icon),
                contentDescription = description,
                colorFilter = ColorFilter.tint(content),
                modifier = GlanceModifier.size(16.dp),
            )
        }
        if (!iconAfter) image()
        Text(
            text = if (iconAfter) "$label " else " $label",
            style = TextStyle(color = content, fontSize = 13.sp),
        )
        if (iconAfter) image()
    }
}

/** Below this height the "Tap to start" hint line is left out, to keep room for the time. */
private val HINT_MIN_HEIGHT = 200.dp

/** Extra the app reads to know it was opened from the widget to choose a tea. */
val FromWidgetKey = ActionParameters.Key<Boolean>(MainActivity.EXTRA_FROM_WIDGET)

class TapAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        BrewController.get(context).tap()
    }
}

class PreviousAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        BrewController.get(context).previousInfusion()
    }
}

class NextAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        BrewController.get(context).nextInfusion()
    }
}

class CycleRingAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        BrewController.get(context).cycleRingSetting()
    }
}

class ResetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        BrewController.get(context).stop()
    }
}

/**
 * Redraws every Tea Timer widget right now. Glance's own updates run as background work,
 * which Android holds back while the phone sleeps: when an infusion ended with the screen
 * off, the old countdown kept running below zero until the phone was unlocked. The alarm
 * receiver calls this to render and hand over the new state immediately.
 */
@OptIn(ExperimentalGlanceApi::class)
suspend fun redrawWidgetsNow(context: Context) {
    val glanceManager = GlanceAppWidgetManager(context)
    val appWidgetManager = AppWidgetManager.getInstance(context)
    val provider = ComponentName(context, TeaTimerWidgetReceiver::class.java)
    appWidgetManager.getAppWidgetIds(provider).forEach { appWidgetId ->
        val glanceId = glanceManager.getGlanceIdBy(appWidgetId)
        val views = TeaTimerWidget().compose(context, glanceId, options = appWidgetManager.getAppWidgetOptions(appWidgetId))
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}

class TeaTimerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TeaTimerWidget()
}
