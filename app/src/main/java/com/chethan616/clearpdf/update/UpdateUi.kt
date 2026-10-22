package com.chethan616.clearpdf.update

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.ConfigurationCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.ui.components.AccentIconTile
import com.chethan616.clearpdf.ui.components.GlassProgressBar
import com.chethan616.clearpdf.ui.components.LiquidButton
import com.chethan616.clearpdf.ui.components.liquidGlassPanel
import com.chethan616.clearpdf.ui.components.liquidRowClick
import com.chethan616.clearpdf.ui.theme.LiquidGlassColors
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.chethan616.clearpdf.ui.utils.UISensor
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.delay

/*
 * Docsy's updates in one place: Settings → About. Everything else only says a version is out and
 * leads there — the Home row, and a short pill over a document (Docsy is mostly opened straight
 * into a document, where neither Home nor About is ever seen).
 */

/** Routes where the Home row or About already shows the update; the pill is for everything else. */
private val NoPillRoutes = setOf("home", "tools", "settings")

/**
 * App-wide: looks for an update when Docsy comes to the front (once a day), opens About when the
 * row or the pill asks for it, and shows the pill over documents once per version.
 */
@Composable
fun UpdateHost(currentRoute: String?, backdrop: Backdrop, openSettings: () -> Unit) {
    if (!Updates.supported) return
    val context = LocalContext.current
    LifecycleResumeEffect(Unit) {
        Updates.onAppResume(context)
        onPauseOrDispose {}
    }
    val openAbout by Updates.openAbout.collectAsState()
    LaunchedEffect(openAbout) { if (openAbout) openSettings() }

    val state by Updates.state.collectAsState()
    val info = state.newVersion
    val inDocument = currentRoute != null && currentRoute !in NoPillRoutes && !currentRoute.startsWith("onboarding")
    var pillVisible by remember { mutableStateOf(false) }
    LaunchedEffect(info?.versionCode, inDocument) {
        if (info == null || !inDocument || Updates.pillShown(context, info)) return@LaunchedEffect
        Updates.markPillShown(context, info)
        delay(1500) // let the document open first
        pillVisible = true
        delay(5000)
        pillVisible = false
    }
    // Its own see-through layer over the app: nothing here takes touches except the pill itself.
    Box(Modifier.fillMaxSize()) {
    AnimatedVisibility(
        visible = pillVisible && inDocument && info != null,
        enter = fadeIn(tween(220)),
        exit = fadeOut(tween(220)),
        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp)
    ) {
        LiquidButton(
            onClick = { pillVisible = false; Updates.openAbout.value = true },
            backdrop = backdrop,
            tint = LiquidGlassColors.Blue
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Rounded.SystemUpdate, null, Modifier.size(17.dp), Color.White)
                BasicText(
                    stringResource(R.string.update_pill, info?.version.orEmpty()),
                    style = TextStyle(Color.White, 14.sp, FontWeight.SemiBold)
                )
            }
        }
    }
    }
}

/** Home: one row when a version is out (or downloading). Tapping it opens About. */
@Composable
fun UpdateRow(backdrop: Backdrop, uiSensor: UISensor, modifier: Modifier = Modifier) {
    val state by Updates.state.collectAsState()
    val info = state.newVersion ?: return
    val isDark = LocalIsDarkMode.current
    val ink = LiquidGlassColors.text(isDark)
    val sub = LiquidGlassColors.secondary(isDark)
    val downloading = state as? UpdateState.Downloading
    Row(
        modifier
            .fillMaxWidth()
            .liquidGlassPanel(backdrop, uiSensor, withShadow = false)
            .liquidRowClick(corner = 28.dp) { Updates.openAbout.value = true }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.SystemUpdate, null, Modifier.size(20.dp), LiquidGlassColors.Blue)
        BasicText(
            if (downloading != null) stringResource(R.string.update_downloading, downloading.percent)
            else stringResource(R.string.update_title, info.version),
            style = TextStyle(ink, 16.sp, FontWeight.SemiBold),
            modifier = Modifier.weight(1f)
        )
        Icon(Icons.Rounded.ChevronRight, null, Modifier.size(20.dp), sub)
    }
}

/**
 * About → «Updates» panel, first row: "Check for updates" in the Settings-index row language (icon
 * tile, title, the current standing as the subtitle, chevron). Tapping checks again; while the
 * check runs the chevron gives way to a spinner. About checks on its own when it opens.
 */
@Composable
fun UpdateCheckRow(ink: Color, sub: Color, modifier: Modifier = Modifier) {
    if (!Updates.supported) return
    val context = LocalContext.current
    LaunchedEffect(Unit) { Updates.checkNow(context) }
    val state by Updates.state.collectAsState()
    val checking = state == UpdateState.Checking
    val status = when (val s = state) {
        UpdateState.Unknown -> stringResource(R.string.update_check_tap)
        UpdateState.Checking -> stringResource(R.string.update_checking)
        UpdateState.UpToDate -> stringResource(R.string.update_latest)
        UpdateState.CheckFailed -> stringResource(R.string.update_check_failed)
        else -> stringResource(R.string.update_title, s.newVersion?.version.orEmpty())
    }
    Row(
        modifier
            .fillMaxWidth()
            .liquidRowClick(enabled = !checking, corner = 18.dp) { Updates.checkNow(context) }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        AccentIconTile(Icons.Rounded.SystemUpdate, LiquidGlassColors.Blue, size = 34, iconSize = 18)
        Column(Modifier.weight(1f)) {
            BasicText(stringResource(R.string.update_check_now), style = TextStyle(ink, 16.sp, FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText(status, style = TextStyle(sub, 13.sp))
        }
        if (checking) {
            CircularProgressIndicator(Modifier.size(18.dp), LiquidGlassColors.Blue, strokeWidth = 2.dp)
        } else {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(20.dp), sub.copy(alpha = 0.7f))
        }
    }
}

/**
 * Under the row, only when a version is out: what's new, the download progress, the install
 * permission note, and the one button that moves things along.
 */
@Composable
fun UpdateActionBlock(backdrop: Backdrop, sub: Color, modifier: Modifier = Modifier) {
    if (!Updates.supported) return
    val context = LocalContext.current
    val state by Updates.state.collectAsState()
    val s = state
    val info = s.newVersion ?: return
    val centered = TextStyle(sub, 13.sp, textAlign = TextAlign.Center, lineHeight = 18.sp)
    val language = ConfigurationCompat.getLocales(LocalConfiguration.current)[0]?.language.orEmpty()
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        info.notes(language).takeIf { it.isNotEmpty() }?.let { BasicText(it, style = centered) }
        when (s) {
            is UpdateState.Downloading -> {
                GlassProgressBar(progress = s.percent / 100f, modifier = Modifier.fillMaxWidth(), backdrop = backdrop)
                BasicText(stringResource(R.string.update_downloading, s.percent), style = centered)
            }
            else -> {
                if (s is UpdateState.ReadyToInstall && s.needsPermission) {
                    BasicText(stringResource(R.string.update_allow_install), style = centered)
                }
                if (s is UpdateState.DownloadFailed) {
                    BasicText(
                        stringResource(if (s.badFile) R.string.update_bad_file else R.string.update_no_network),
                        style = centered.copy(color = LiquidGlassColors.Red)
                    )
                }
                LiquidButton(
                    onClick = { Updates.startUpdate(context) },
                    backdrop = backdrop,
                    tint = LiquidGlassColors.Blue,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    BasicText(
                        stringResource(if (s is UpdateState.ReadyToInstall) R.string.update_allow_button else R.string.update_button),
                        style = TextStyle(Color.White, 15.sp, FontWeight.SemiBold)
                    )
                }
            }
        }
    }
}
