package com.chethan616.clearpdf.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.ConfigurationCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.ui.components.GlassProgressBar
import com.chethan616.clearpdf.ui.components.LiquidButton
import com.chethan616.clearpdf.ui.components.liquidGlassPanel
import com.chethan616.clearpdf.ui.theme.LiquidGlassColors
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.chethan616.clearpdf.ui.utils.UISensor
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * "Version X is out" at the top of Home: what's new and one button. The button downloads the APK
 * and opens Android's installer; the first time Android asks to let Docsy install apps, and the
 * card says so. Shows nothing unless a newer version was found (GitHub build only).
 */
@Composable
fun UpdateCard(backdrop: Backdrop, uiSensor: UISensor, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf(Updates.pending(context)) }
    var progress by remember { mutableStateOf<Int?>(null) }
    var downloaded by remember { mutableStateOf<File?>(null) }
    var needsPermission by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf<String?>(null) }

    LifecycleResumeEffect(Unit) {
        // Back from the "install unknown apps" switch: carry on with the install.
        val file = downloaded
        if (needsPermission && file != null && Updates.canInstall(context)) {
            needsPermission = false
            Updates.install(context, file)
        }
        if (info == null && Updates.due(context)) {
            scope.launch { info = withContext(Dispatchers.IO) { Updates.check(context) } }
        }
        onPauseOrDispose {}
    }

    val update = info ?: return
    val isDark = LocalIsDarkMode.current
    val ink = LiquidGlassColors.text(isDark)
    val sub = LiquidGlassColors.secondary(isDark)
    val language = ConfigurationCompat.getLocales(LocalConfiguration.current)[0]?.language.orEmpty()
    val badFile = stringResource(R.string.update_bad_file)
    val noNetwork = stringResource(R.string.update_no_network)

    fun proceed(file: File) {
        if (Updates.canInstall(context)) Updates.install(context, file)
        else { needsPermission = true; Updates.openInstallPermission(context) }
    }

    Column(
        modifier
            .fillMaxWidth()
            .liquidGlassPanel(backdrop, uiSensor, withShadow = false)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.SystemUpdate, null, Modifier.size(20.dp), LiquidGlassColors.Blue)
            BasicText(stringResource(R.string.update_title, update.version), style = TextStyle(ink, 17.sp, FontWeight.SemiBold))
        }
        val notes = update.notes(language)
        if (notes.isNotEmpty()) BasicText(notes, style = TextStyle(sub, 14.sp, lineHeight = 20.sp))
        val p = progress
        when {
            p != null -> {
                GlassProgressBar(progress = p / 100f, modifier = Modifier.fillMaxWidth(), backdrop = backdrop)
                BasicText(stringResource(R.string.update_downloading, p), style = TextStyle(sub, 13.sp))
            }
            needsPermission -> BasicText(stringResource(R.string.update_allow_install), style = TextStyle(sub, 13.sp, lineHeight = 18.sp))
        }
        failed?.let { BasicText(it, style = TextStyle(LiquidGlassColors.Red, 13.sp)) }
        if (p == null) {
            LiquidButton(
                onClick = {
                    failed = null
                    downloaded?.takeIf { it.exists() }?.let { proceed(it); return@LiquidButton }
                    progress = 0
                    scope.launch {
                        try {
                            val f = withContext(Dispatchers.IO) { Updates.download(context, update) { progress = it } }
                            downloaded = f
                            progress = null
                            proceed(f)
                        } catch (e: Updates.BadUpdateFile) {
                            progress = null; failed = badFile
                        } catch (e: Exception) {
                            progress = null; failed = noNetwork
                        }
                    }
                },
                backdrop = backdrop,
                tint = LiquidGlassColors.Blue,
                modifier = Modifier.fillMaxWidth()
            ) {
                BasicText(
                    stringResource(if (needsPermission) R.string.update_allow_button else R.string.update_button),
                    style = TextStyle(Color.White, 15.sp, FontWeight.SemiBold)
                )
            }
        }
    }
}
