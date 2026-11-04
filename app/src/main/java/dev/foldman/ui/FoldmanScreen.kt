package dev.foldman.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowLayoutInfo
import dev.foldman.deck.DeckEvent
import dev.foldman.deck.DeckUi
import dev.foldman.deck.DeckViewModel
import dev.foldman.deck.OrientationMode
import dev.foldman.deck.RepeatMode
import dev.foldman.deck.Source
import dev.foldman.deck.SourceStatus
import dev.foldman.deck.Transport
import kotlinx.coroutines.launch

private enum class Posture { Cover, Unfolded, Tabletop }

/** Body enamel: the blue of the first portable cassette players. */
private val Enamel = Color(0xFF2F6FB5)

/**
 * Picks a layout from the hinge:
 *  - folded (cover screen): the portable player stood upright, tape on end behind its window,
 *    keys and volume down a silver side strip;
 *  - unfolded: the same player laid down as a deck, with the library above and now-playing below;
 *  - half-open on a table (Flex mode): the deck on the upper half, the library on the lower.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoldmanScreen(
    vm: DeckViewModel,
    windowLayout: WindowLayoutInfo?,
    onPickFolder: () -> Unit,
    onInstallApp: (Source) -> Unit,
    onAppleSignIn: () -> Unit,
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val anim = rememberDeckAnimations(ui.transport, ui.progress)
    val sounds = remember { MechSounds() }
    DisposableEffect(sounds) { onDispose { sounds.release() } }
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var shelfOpen by rememberSaveable { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val feel by rememberUpdatedState(ui)

    // A new tape drops into the deck whenever the source changes (and when the app opens).
    LaunchedEffect(ui.source) {
        anim.insert.snapTo(1f)
        anim.insert.animateTo(0f, spring(dampingRatio = 0.62f, stiffness = 220f))
        if (feel.keySounds) sounds.autoStop()
    }
    LaunchedEffect(vm) {
        vm.events.collect { e ->
            if (feel.keySounds) sounds.autoStop()
            when (e) {
                DeckEvent.TapeEnd -> if (feel.haptics) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                DeckEvent.Eject -> {
                    if (feel.haptics) view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    scope.launch {
                        anim.pop.animateTo(1f, tween(90))
                        anim.pop.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = 500f))
                    }
                    shelfOpen = true
                    settingsOpen = false
                }
            }
        }
    }

    val appleActions = remember(vm) {
        AppleActions(
            signIn = onAppleSignIn,
            signOut = vm::signOutOfApple,
            setDeveloperToken = vm::setAppleDeveloperToken,
            refresh = vm::loadApplePlaylists,
            openPlaylist = vm::openApplePlaylist,
            closePlaylist = vm::closeApplePlaylist,
            play = vm::playApplePlaylist,
        )
    }
    val shelfActions = remember(vm) {
        ShelfActions(
            selectSource = vm::selectSource,
            pickFolder = onPickFolder,
            playTrack = vm::playTrack,
            openAccess = vm::openAccessSettings,
            openApp = vm::openSourceApp,
            installApp = onInstallApp,
            apple = appleActions,
        )
    }
    val deck = remember(vm) { DeckControls(vm) }
    val fold = windowLayout?.displayFeatures?.filterIsInstance<FoldingFeature>()?.firstOrNull()

    CompositionLocalProvider(
        LocalMechSounds provides sounds.takeIf { ui.keySounds },
        LocalHaptics provides ui.haptics,
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(Brush.radialGradient(listOf(Color(0xFF2A2420), DeckColors.Backdrop), radius = 1800f)),
        ) {
            val density = LocalDensity.current
            val sheetHeight = maxHeight * 0.8f
            val posture = when {
                fold != null && fold.state == FoldingFeature.State.HALF_OPENED && fold.orientation == FoldingFeature.Orientation.HORIZONTAL -> Posture.Tabletop
                fold != null && fold.orientation == FoldingFeature.Orientation.VERTICAL -> Posture.Unfolded
                maxWidth >= 600.dp && maxWidth > maxHeight * 0.7f -> Posture.Unfolded
                else -> Posture.Cover
            }
            LaunchedEffect(posture) { view.showSystemBars(posture != Posture.Cover) }
            when {
                expanded -> ExpandedDeck(
                    ui, anim, deck,
                    onContract = { expanded = false },
                    modifier = Modifier.fillMaxSize(),
                )
                posture == Posture.Cover -> CoverDeck(
                    ui, anim, deck,
                    onTapes = { shelfOpen = true; settingsOpen = false },
                    onSettings = { settingsOpen = true; shelfOpen = false },
                    onExpand = { expanded = true },
                    modifier = Modifier.fillMaxSize(),
                )
                posture == Posture.Unfolded ->
                    if (maxWidth > maxHeight) {
                        LandscapeDeck(
                            ui, anim, deck,
                            onTapes = { shelfOpen = true; settingsOpen = false },
                            onSettings = { settingsOpen = true; shelfOpen = false },
                            onExpand = { expanded = true },
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        // Unfolded and upright: the component stack, the listening room view.
                        StackDeck(
                            ui, anim, vm,
                            onTapes = { shelfOpen = true; settingsOpen = false },
                            onSettings = { settingsOpen = true; shelfOpen = false },
                            onExpand = { expanded = true },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                else -> {
                    val (topH, gap) = with(density) {
                        fold!!.bounds.top.toDp() to (fold.bounds.bottom - fold.bounds.top).toDp()
                    }
                    Column(Modifier.fillMaxSize()) {
                        LandscapeDeck(
                            ui, anim, deck,
                            onTapes = { shelfOpen = true; settingsOpen = false },
                            onSettings = { settingsOpen = true; shelfOpen = false },
                            onExpand = { expanded = true },
                            modifier = Modifier.height(topH).fillMaxWidth(),
                            tabletop = true,
                        )
                        Spacer(Modifier.height(gap))
                        LibraryBand(
                            ui, shelfActions, vm::playQueueItem,
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
                        )
                    }
                }
            }
            if (shelfOpen) {
                ModalBottomSheet(
                    onDismissRequest = { shelfOpen = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = Color(0xFF1C1510),
                ) {
                    TapeShelf(
                        ui,
                        ShelfActions(
                            selectSource = { vm.selectSource(it) },
                            pickFolder = onPickFolder,
                            playTrack = { vm.playTrack(it); shelfOpen = false },
                            openAccess = vm::openAccessSettings,
                            openApp = vm::openSourceApp,
                            installApp = onInstallApp,
                            apple = appleActions,
                        ),
                        Modifier.fillMaxWidth().height(sheetHeight),
                    )
                }
            }
            if (settingsOpen) {
                ModalBottomSheet(
                    onDismissRequest = { settingsOpen = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = Color(0xFF1C1510),
                ) {
                    CoverSettingsSheet(ui, vm, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp))
                    Spacer(Modifier.height(28.dp))
                }
            }
        }
    }
}

/** The deck's settings sheet: the CLICK and HAPTIC switches on a piece of the silver chassis,
 * the ORIENTATION hold below them, plus the notification access row when a remote source is
 * waiting for it, and the maker's badge at the foot. The orientation pick is persisted by the
 * view model and applied to the hosting activity, at selection time and again at startup;
 * AUTO leaves the activity unspecified, exactly the deck's behaviour before the setting
 * existed. */
@Composable
private fun CoverSettingsSheet(ui: DeckUi, vm: DeckViewModel, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text("SETTINGS", style = EngravedCaps.copy(color = Color(0xFFD8D2C8), fontSize = 11.sp))
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().chassis(corner = 16.dp, screws = false).padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            SlideSwitch(ui.keySounds, vm::setKeySounds, "CLICK", height = 54.dp)
            SlideSwitch(ui.haptics, vm::setHaptics, "HAPTIC", height = 54.dp)
        }
        Spacer(Modifier.height(14.dp))
        Text("ORIENTATION", style = EngravedCaps.copy(color = Color(0xFFD8D2C8), fontSize = 11.sp))
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().chassis(corner = 16.dp, screws = false).padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OrientationMode.entries.forEach { mode ->
                val selected = ui.orientationMode == mode
                Text(
                    mode.name,
                    style = EngravedCaps.copy(
                        fontSize = 11.sp,
                        color = if (selected) Color(0xFF1E1916) else Color(0xFFD8D2C8),
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) DeckColors.Accent else Color.Transparent)
                        .clickable { vm.setOrientationMode(mode) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        if (ui.status == SourceStatus.NeedsAccess) {
            Spacer(Modifier.height(14.dp))
            Text(
                "GRANT NOTIFICATION ACCESS",
                style = EngravedCaps.copy(color = DeckColors.Accent, fontSize = 11.sp),
                modifier = Modifier.clickable { vm.openAccessSettings() }.padding(vertical = 6.dp),
            )
        }
        Spacer(Modifier.height(22.dp))
        // The maker's badge at the foot of the sheet: the house mark on a small light
        // plate, the way a builder's plate rides the back panel of the real thing, with
        // the house's name set under it in the sheet's own engraved caps.
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White)
                    .padding(7.dp),
            ) {
                Image(
                    painter = painterResource(dev.foldman.R.drawable.protech_prime_logo),
                    contentDescription = "Protech Prime",
                    modifier = Modifier.width(118.dp),
                )
            }
            Spacer(Modifier.height(9.dp))
            Text(
                "BY PROTECH PRIME CONSULTING",
                style = EngravedCaps.copy(color = Color(0xFFD8D2C8), fontSize = 9.sp),
            )
        }
    }
}

/** Hides the status and navigation bars (a swipe from the edge brings them back briefly), or restores them. */
private fun android.view.View.showSystemBars(show: Boolean) {
    var ctx = context
    while (ctx is android.content.ContextWrapper && ctx !is android.app.Activity) ctx = ctx.baseContext
    val window = (ctx as? android.app.Activity)?.window ?: return
    WindowCompat.getInsetsController(window, this).run {
        if (show) {
            show(WindowInsetsCompat.Type.systemBars())
        } else {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}

/** Key actions, bundled so the layouts don't each need the view model. */
private class DeckControls(vm: DeckViewModel) {
    val play = vm::play
    val pause = vm::pauseKey
    val stop = vm::stop
    val rewind = vm::rewind
    val fastForward = vm::fastForward
    val next = vm::next
    val previous = vm::previous
    val volume = vm::setVolume
    val shuffle = vm::toggleShuffle
    val repeat = vm::cycleRepeat
    val seek = vm::seekToFraction
}

private val Serif = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black, color = Color(0xFFF1F3F6), letterSpacing = 2.sp)
private val OnEnamel = EngravedCaps.copy(color = Color(0xFFDCE6F2))
private val OnDarkMetal = EngravedCaps.copy(color = Color(0xFFB9BCC2))

/**
 * The folded phone: the full deck stacked upright on dark brushed metal. Meters and volume up
 * top, the whole cassette in the middle, track info and round transport buttons below.
 * Full bleed: the brushed surface is drawn before the insets padding, so it runs edge to edge
 * behind the status bar and the gesture bar (both transparent in this posture); the top and
 * side safe drawing insets land inside the deck as padding, keeping the header and volume
 * strip clear of the status icons while every zone spans the width. The bottom inset is NOT
 * padded here: the track info panel is the deck's terminal element and runs flush to the
 * bottom edge of the window, taking the bottom safe inset as internal padding inside its
 * own recess surface so its buttons stay clear of the gesture area.
 */
@Composable
private fun CoverDeck(
    ui: DeckUi,
    anim: DeckAnimations,
    deck: DeckControls,
    onTapes: () -> Unit,
    onSettings: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier,
) {
    BoxWithConstraints(modifier) {
        // Short windows shrink the meter zone first; the cassette keeps what is left.
        val short = maxHeight < 640.dp
        val meterH = if (short) 104.dp else 128.dp
        val levels = rememberNeedles(ui.transport, ui.source)
        // Casse proportions: on a tall window the info panel claims whatever height the
        // cassette does not need, starting right under the cassette. The claim is made only
        // when the grown panel's minimum content provably fits what is left, computed from
        // the real insets, the fixed top stack and the cassette's width-driven height.
        // Every other window keeps the weighted middle zone, which absorbs the slack and
        // shrinks the cassette by height rather than cropping it.
        val density = LocalDensity.current
        val topInset = with(density) { WindowInsets.safeDrawing.getTop(density).toDp() }
        val bottomInset = with(density) { WindowInsets.safeDrawing.getBottom(density).toDp() }
        val cassetteH = (maxWidth - 8.dp) * (63.8f / 100.4f)
        val fixedTop = topInset + 10.dp + 30.dp + 40.dp + 48.dp + meterH
        val fillPanel = !short && (maxHeight - fixedTop - cassetteH - bottomInset) >= 330.dp
        Column(
            Modifier
                .fillMaxSize()
                .darkBrushed()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(start = 4.dp, end = 4.dp, top = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Wordmark("FERRIC PLAYER", 22.sp)
                Spacer(Modifier.weight(1f))
                Text("STEREO CASSETTE DECK", style = OnDarkMetal.copy(fontSize = 8.sp))
            }
            VolumeSliderHorizontal(ui.volume, deck.volume, Modifier.fillMaxWidth().height(48.dp))
            Row(
                Modifier.fillMaxWidth().height(meterH).bezel().padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                VuDial(levels, ui.transport, Modifier.weight(1f).fillMaxHeight(), channel = 0)
                PeakLampColumn(levels, Modifier.width(24.dp).fillMaxHeight())
                VuDial(levels, ui.transport, Modifier.weight(1f).fillMaxHeight(), channel = 1)
            }
            Box(
                if (fillPanel) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.TopCenter,
            ) {
                FullCassetteFace(ui, anim, Modifier.fillMaxWidth())
            }
            // The expand cap sits on the player, never on the tape: it overlays the bottom
            // right corner of the now playing panel, in the corner padding zone beside the
            // control strip. The strip's repeat button is centered in its third of the row,
            // which leaves the panel's corner clear of every control, and the seat lifts by
            // the bottom safe inset just like the panel's own content does.
            Box(
                if (fillPanel) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth(),
            ) {
                TrackInfoPanel(
                    ui,
                    onPrevious = deck.previous,
                    onPlay = deck.play,
                    onPause = deck.pause,
                    onNext = deck.next,
                    onQueue = onTapes,
                    onSettings = onSettings,
                    onShuffle = deck.shuffle,
                    onRepeat = deck.repeat,
                    onSeek = deck.seek,
                    compact = short,
                    fill = fillPanel,
                    modifier = if (fillPanel) Modifier.fillMaxSize() else Modifier.fillMaxWidth(),
                )
                ExpandToggleButton(
                    expanded = false,
                    onPress = onExpand,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                        .padding(end = 10.dp, bottom = 10.dp),
                    diameter = 36.dp,
                )
            }
        }
    }
}

/**
 * The expanded deck: the cassette alone, letterboxed at its true aspect on the dark brushed
 * surface, with only the transport trio floating over the bottom edge. The contract cap
 * belongs to the screen rather than the tape: it is pinned to the bottom right corner
 * inside the safe inset, wherever the letterboxed cassette happens to end.
 */
@Composable
private fun ExpandedDeck(
    ui: DeckUi,
    anim: DeckAnimations,
    deck: DeckControls,
    onContract: () -> Unit,
    modifier: Modifier,
) {
    val playing = ui.transport == Transport.PLAYING
    Box(modifier.darkBrushed()) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(14.dp),
            contentAlignment = Alignment.Center,
        ) {
            val cassetteWidth = minOf(maxWidth, maxHeight * (100.4f / 63.8f))
            FullCassetteFace(ui, anim, Modifier.width(cassetteWidth))
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundTransportButton("Previous", deck.previous, symbol = KeySymbol.PREV, diameter = 56.dp, enabled = ui.hasPrevious)
                RoundTransportButton(
                    if (playing) "Pause" else "Play",
                    if (playing) deck.pause else deck.play,
                    symbol = if (playing) KeySymbol.PAUSE else KeySymbol.PLAY,
                    diameter = 72.dp, ring = true, active = playing,
                )
                RoundTransportButton("Next", deck.next, symbol = KeySymbol.NEXT, diameter = 56.dp, enabled = ui.hasNext)
            }
        }
        ExpandToggleButton(
            expanded = true,
            onPress = onContract,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(end = 10.dp, bottom = 10.dp),
            diameter = 40.dp,
        )
    }
}

/**
 * The landscape deck: a slim personal player at hand scale, not a picture of one. The plate
 * is the whole window: the dark brushed body runs edge to edge with no page background
 * framing it, and the safe drawing insets land inside the plate as furniture padding.
 * Furniture is kept slim so the tape gets the room: a 16 dp engraved wordmark strip, the
 * 2 dp orange line over the fixed 54 dp key rail, one 20 dp info row, and the 3 dp progress
 * hairline at the plate's bottom edge. The window well is a shallow recess that hugs the
 * cassette, the tape plus about 6 dp of well wall on every side, centered in the plate;
 * the cassette starts from a fit by whichever of the well zone's width or height binds
 * first, grows by a tenth into the plate's breathing room, and is clamped to leave at
 * least 16 dp of zone on the binding axis, always at its true aspect and never cropped,
 * and the textured plate body shows around the well instead of a black letterbox void.
 * The rail carries the portrait deck's furniture in
 * player order: utilities, transport, the control strip trio, volume, and the expand key
 * at the far right.
 */
@Composable
private fun LandscapeDeck(
    ui: DeckUi,
    anim: DeckAnimations,
    deck: DeckControls,
    onTapes: () -> Unit,
    onSettings: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier,
    tabletop: Boolean = false,
) {
    val playing = ui.transport == Transport.PLAYING
    // While a scrub drag is live on the bottom hairline, the info row's elapsed time
    // shows where the finger is headed instead of where the tape is.
    var scrubTargetMs by remember { mutableStateOf<Long?>(null) }
    val insetModifier = if (tabletop) {
        Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
    } else {
        Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
    }
    Column(
        modifier
            .darkBrushed()
            .then(insetModifier),
    ) {
        Row(
            Modifier.fillMaxWidth().height(16.dp).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Wordmark("FERRIC PLAYER", 11.sp)
        }
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Jerome's 1.3.2 adjustment: the tape runs about a tenth bigger than the
            // plain fit, eating into the plate's breathing room around the well. Start
            // from the 1.3.1 fit (zone minus the old 10 dp margin on every side), scale
            // it by 1.10, then clamp so the cassette always leaves at least 16 dp of
            // zone on whichever axis binds, at true aspect and never cropped. The well
            // margin drops to 6 dp so more of the gain is tape, not wall.
            val wellMargin = 6.dp
            val fittedWidth = minOf(
                maxWidth - 20.dp,
                (maxHeight - 20.dp) * (100.4f / 63.8f),
            )
            val cassetteWidth = minOf(
                fittedWidth * 1.10f,
                maxWidth - 16.dp,
                (maxHeight - 16.dp) * (100.4f / 63.8f),
            )
            val cassetteHeight = cassetteWidth * (63.8f / 100.4f)
            Box(
                Modifier
                    .size(cassetteWidth + wellMargin * 2, cassetteHeight + wellMargin * 2)
                    .recess(corner = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                FullCassetteFace(ui, anim, Modifier.width(cassetteWidth))
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(DeckColors.Accent.copy(alpha = 0.8f)),
        )
        Row(
            Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundTransportButton("Settings", onSettings, roundSymbol = RoundSymbol.GEAR, diameter = 44.dp)
            Spacer(Modifier.width(8.dp))
            RoundTransportButton("Tapes and queue", onTapes, roundSymbol = RoundSymbol.LIST, diameter = 44.dp)
            Spacer(Modifier.width(10.dp))
            RoundTransportButton("Previous", deck.previous, symbol = KeySymbol.PREV, diameter = 48.dp, enabled = ui.hasPrevious)
            Spacer(Modifier.width(8.dp))
            RoundTransportButton(
                if (playing) "Pause" else "Play",
                if (playing) deck.pause else deck.play,
                symbol = if (playing) KeySymbol.PAUSE else KeySymbol.PLAY,
                diameter = 52.dp, ring = true, active = playing,
            )
            Spacer(Modifier.width(8.dp))
            RoundTransportButton("Next", deck.next, symbol = KeySymbol.NEXT, diameter = 48.dp, enabled = ui.hasNext)
            Spacer(Modifier.width(14.dp))
            EqualizerButton()
            Spacer(Modifier.width(8.dp))
            DeckStripButton(
                description = "Shuffle",
                onPress = deck.shuffle,
                symbol = StripSymbol.SHUFFLE,
                lit = ui.shuffleOn,
                enabled = ui.canShuffle,
                stateText = if (ui.shuffleOn) "On" else "Off",
            )
            Spacer(Modifier.width(8.dp))
            DeckStripButton(
                description = "Repeat",
                onPress = deck.repeat,
                symbol = if (ui.repeatMode == RepeatMode.ONE) StripSymbol.REPEAT_ONE else StripSymbol.REPEAT,
                lit = ui.repeatMode != RepeatMode.NONE,
                enabled = ui.canRepeat,
                stateText = when (ui.repeatMode) {
                    RepeatMode.NONE -> "Off"
                    RepeatMode.ALL -> "All"
                    RepeatMode.ONE -> "One"
                },
            )
            Spacer(Modifier.width(12.dp))
            VolumeSliderHorizontal(ui.volume, deck.volume, Modifier.weight(1f).height(48.dp))
            Spacer(Modifier.width(8.dp))
            ExpandToggleButton(expanded = false, onPress = onExpand, diameter = 38.dp)
        }
        Row(
            Modifier.fillMaxWidth().height(20.dp).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val headline = buildString {
                append(ui.title.ifBlank { "NO TAPE" })
                if (ui.artist.isNotBlank()) append(" · ").append(ui.artist)
            }
            Text(
                headline,
                style = TextStyle(color = Color(0xFFF4EFE9), fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(16.dp))
            Text(
                "${formatTime(scrubTargetMs ?: ui.positionMs)} / ${formatTime(ui.durationMs)}",
                style = TextStyle(color = Color(0xFFA79C92), fontSize = 11.sp),
                maxLines = 1,
            )
        }
        // The progress hairline at the plate's bottom edge: the shared scrubber with its
        // bar pinned to the bottom of a 20 dp invisible touch target, so the line still
        // sits flush on the edge while a finger can actually land on it.
        SeekScrubber(
            progress = ui.progress,
            canSeek = ui.canSeek,
            durationMs = ui.durationMs,
            onSeek = deck.seek,
            modifier = Modifier.fillMaxWidth(),
            barHeight = 3.dp,
            touchHeight = 20.dp,
            corner = 0.dp,
            barAlignment = Alignment.BottomCenter,
            onDragTimeChange = { scrubTargetMs = it },
        )
    }
}

/** The silver strip down the side of the case: mic, LED, skip, keys, volume, phones. */
@Composable
private fun SideStrip(ui: DeckUi, deck: DeckControls, modifier: Modifier) {
    BoxWithConstraints(modifier) {
        val keyH = ((maxHeight - 400.dp) / 5 - 18.dp).coerceIn(34.dp, 64.dp)
        // Runs the full height of the screen; the padding keeps controls clear of the rounded corners.
        Column(
            Modifier
                .fillMaxSize()
                .chassis(corner = 0.dp, screws = false)
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Top + WindowInsetsSides.End))
                .padding(start = 10.dp, end = 12.dp, top = 28.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MicGrille(Modifier.fillMaxWidth().height(26.dp))
            Text("MIC", style = EngravedCaps.copy(fontSize = 7.sp))
            Spacer(Modifier.height(8.dp))
            StatusLed(ui.transport)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                MiniButton("AMS", deck.previous, symbol = KeySymbol.PREV, diameter = 26.dp)
                MiniButton("AMS", deck.next, symbol = KeySymbol.NEXT, diameter = 26.dp, enabled = ui.hasNext)
            }
            Spacer(Modifier.height(10.dp))
            TransportKeys(ui, deck.stop, deck.rewind, deck.play, deck.fastForward, deck.pause, Modifier.fillMaxWidth(), keyHeight = keyH, vertical = true)
            Spacer(Modifier.height(12.dp))
            Text("VOLUME", style = EngravedCaps.copy(fontSize = 7.sp))
            VolumeSlider(ui.volume, deck.volume, Modifier.fillMaxWidth().weight(1f))
            Spacer(Modifier.height(8.dp))
            PhonesJack(Modifier.size(18.dp))
            Text("PHONES", style = EngravedCaps.copy(fontSize = 6.sp))
        }
    }
}

/** The unfolded phone: the player laid down as a deck. */
@Composable
private fun DeckBand(ui: DeckUi, anim: DeckAnimations, deck: DeckControls, vm: DeckViewModel, modifier: Modifier) {
    Row(modifier.paintedBody(Enamel, corner = 22.dp)) {
        Column(Modifier.weight(0.74f).fillMaxHeight().padding(horizontal = 20.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("STEREO", style = Serif.copy(fontSize = 24.sp))
                Spacer(Modifier.weight(1f))
                Text("FM-F8 · STEREO CASSETTE PLAYER", style = OnEnamel.copy(fontSize = 8.sp))
                Spacer(Modifier.width(16.dp))
                Wordmark("FERRIC PLAYER", 26.sp)
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(150.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
                    Row(Modifier.fillMaxWidth().weight(1f).bezel().padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        VuMeter(ui.transport, ui.source, Modifier.weight(1f).fillMaxHeight(), channel = 0)
                        VuMeter(ui.transport, ui.source, Modifier.weight(1f).fillMaxHeight(), channel = 1)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.bezel().padding(5.dp)) { TapeCounter(anim.reels, digitWidth = 16.dp) }
                        Spacer(Modifier.width(8.dp))
                        MiniButton("RESET", { anim.reels.resetCounter() }, diameter = 20.dp)
                        Spacer(Modifier.width(6.dp))
                        StatusLed(ui.transport)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    TapeWindow(ui, anim)
                }
                Spacer(Modifier.width(12.dp))
                EjectArrow({ deck.stop() }, Modifier.size(26.dp, 50.dp))
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                MiniButton("AMS", deck.previous, symbol = KeySymbol.PREV, diameter = 30.dp)
                Spacer(Modifier.width(10.dp))
                TransportKeys(ui, deck.stop, deck.rewind, deck.play, deck.fastForward, deck.pause, Modifier.weight(1f), keyHeight = 58.dp)
                Spacer(Modifier.width(10.dp))
                MiniButton("AMS", deck.next, symbol = KeySymbol.NEXT, diameter = 30.dp, enabled = ui.hasNext)
            }
        }
        // Silver end strip: mic, switches, volume and phones.
        Row(
            Modifier
                .weight(0.26f)
                .fillMaxHeight()
                .padding(6.dp)
                .chassis(corner = 18.dp, screws = false)
                .padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                MicGrille(Modifier.fillMaxWidth().height(30.dp))
                Text("MIC", style = EngravedCaps.copy(fontSize = 7.sp))
                Spacer(Modifier.weight(1f))
                SlideSwitch(ui.keySounds, vm::setKeySounds, "CLICK", height = 54.dp)
                Spacer(Modifier.height(8.dp))
                SlideSwitch(ui.haptics, vm::setHaptics, "HAPTIC", height = 54.dp)
                Spacer(Modifier.weight(1f))
                PhonesJack(Modifier.size(18.dp))
                Text("PHONES", style = EngravedCaps.copy(fontSize = 6.sp))
            }
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("VOLUME", style = EngravedCaps.copy(fontSize = 7.sp))
                VolumeSlider(ui.volume, deck.volume, Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
}
