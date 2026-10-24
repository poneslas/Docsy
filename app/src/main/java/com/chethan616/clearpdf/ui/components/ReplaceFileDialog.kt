package com.chethan616.clearpdf.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.ui.theme.LiquidGlassColors
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.kyant.backdrop.Backdrop

/**
 * "Replace “name.pdf”?" — asked before Save writes over a file: the opened document itself, or a
 * file of the same name in the folder a copy is being saved to. Three outcomes, so the pills are
 * stacked full width (three in a row cut translated labels): Replace (accent) on top, then
 * [otherLabel] (save a copy instead) and Cancel; [body] explains what each one does.
 *
 * With [offerDontAsk] a "Don't ask again" switch is shown; [onReplace] receives its state. It is
 * offered only for replacing the opened document (the everyday Save), never for overwriting some
 * other file that happens to share the name.
 */
@Composable
fun ReplaceFileDialog(
    visible: Boolean,
    fileName: String,
    body: String,
    otherLabel: String,
    onReplace: (dontAskAgain: Boolean) -> Unit,
    onOther: () -> Unit,
    onCancel: () -> Unit,
    backdrop: Backdrop,
    offerDontAsk: Boolean = false
) {
    val isDark = LocalIsDarkMode.current
    var dontAsk by remember(visible) { mutableStateOf(false) }
    GlassDialog(
        visible = visible,
        onDismiss = onCancel,
        backdrop = backdrop,
        title = stringResource(R.string.replace_title, fileName),
        stackedActions = {
            GlassDialogAction(stringResource(R.string.replace_confirm), onClick = { onReplace(dontAsk) }, primary = true)
            GlassDialogAction(otherLabel, onClick = onOther)
            GlassDialogAction(stringResource(R.string.cancel), onClick = onCancel)
        }
    ) {
        BasicText(body, style = TextStyle(LiquidGlassColors.secondary(isDark), 15.sp))
        if (offerDontAsk) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicText(
                    stringResource(R.string.replace_dont_ask),
                    style = TextStyle(LiquidGlassColors.text(isDark), 15.sp, FontWeight.Medium),
                    modifier = Modifier.weight(1f)
                )
                LiquidToggle(selected = { dontAsk }, onSelect = { dontAsk = it }, backdrop = backdrop)
            }
        }
    }
}
