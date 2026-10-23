package com.chethan616.clearpdf.ui.screen

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.data.repository.AppSettingsManager
import com.chethan616.clearpdf.ui.components.GlassBackButton
import com.chethan616.clearpdf.ui.components.GlassScreenScaffold
import com.chethan616.clearpdf.ui.components.GlassTitlePill
import com.chethan616.clearpdf.ui.components.LiquidIconButton
import com.chethan616.clearpdf.ui.components.LocalBackToLibraryAction
import com.chethan616.clearpdf.ui.components.rememberAdaptiveInk
import com.chethan616.clearpdf.ui.components.rememberScreenBackdrop
import com.chethan616.clearpdf.ui.theme.LiquidGlassColors
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.chethan616.clearpdf.ui.viewmodel.MarkdownViewerViewModel
import com.chethan616.clearpdf.utils.PdfPrint
import com.chethan616.clearpdf.utils.markdown.MdBlock
import com.kyant.backdrop.backdrops.LayerBackdrop
import kotlinx.coroutines.launch

/**
 * Read-only Markdown viewer: the file drawn natively (so the glass header and buttons refract the
 * real page, as in every other viewer), wide tables and code scrolling sideways inside their own
 * boxes, text selectable. Print, "Open as PDF" and Share sit bottom right, as in the Word viewer;
 * the PDF is laid out from the same blocks for paper.
 *
 * Like the Word viewer, its light or dark look follows the page's own reader mode, not the app theme.
 */
@Composable
fun MarkdownViewerScreen(
    backdrop: LayerBackdrop,
    viewModel: MarkdownViewerViewModel,
    onBack: () -> Unit,
    onOpenPdf: (Uri) -> Unit
) {
    val appDark = LocalIsDarkMode.current
    val context = LocalContext.current
    val autoDarkPaper = appDark && remember { AppSettingsManager.getAutoDarkPages(context) }
    var paperDarkOverride by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val paperDark = paperDarkOverride ?: autoDarkPaper

    val view = LocalView.current
    DisposableEffect(view, paperDark) {
        val window = view.context.findActivity()?.window
        val ctrl = window?.let { WindowCompat.getInsetsController(it, view) }
        ctrl?.isAppearanceLightStatusBars = !paperDark
        onDispose { ctrl?.isAppearanceLightStatusBars = !appDark }
    }

    CompositionLocalProvider(LocalIsDarkMode provides paperDark) {
        MarkdownViewerContent(backdrop, viewModel, onBack, onOpenPdf, paperDark) { paperDarkOverride = !paperDark }
    }
}

@Composable
private fun MarkdownViewerContent(
    backdrop: LayerBackdrop,
    viewModel: MarkdownViewerViewModel,
    onBack: () -> Unit,
    onOpenPdf: (Uri) -> Unit,
    paperDark: Boolean,
    onTogglePaper: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val text = LiquidGlassColors.text(paperDark)
    val sub = LiquidGlassColors.secondary(paperDark)
    val accent = LiquidGlassColors.Blue
    val colors = remember(paperDark, text, sub) { MdColors.of(paperDark, text, sub, accent) }
    val state = viewModel.state
    val doc = state.doc
    val listState = rememberLazyListState()
    val screenBackdrop = rememberScreenBackdrop(backdrop)
    val imageLabel = stringResource(R.string.md_image)
    val linkUnavailable = stringResource(R.string.md_link_unavailable)

    val anchors = remember(doc) {
        doc?.blocks?.withIndex()?.mapNotNull { (i, b) -> (b as? MdBlock.Heading)?.let { it.anchor to i } }?.toMap().orEmpty()
    }
    val onLink: (String) -> Unit = { url ->
        when {
            url.startsWith("#") -> {
                val key = runCatching { java.net.URLDecoder.decode(url.drop(1).replace("+", "%2B"), "UTF-8") }.getOrDefault(url.drop(1)).lowercase()
                anchors[key]?.let { scope.launch { listState.animateScrollToItem(it) } }
            }
            Regex("^(https?|mailto|tel):", RegexOption.IGNORE_CASE).containsMatchIn(url) ->
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure { Toast.makeText(context, linkUnavailable, Toast.LENGTH_SHORT).show() }
            // A link to another file next to this one: the viewer has no access to its folder.
            else -> Toast.makeText(context, linkUnavailable, Toast.LENGTH_SHORT).show()
        }
    }

    fun openAsPdf() {
        viewModel.exportToPdf(context) { pdf ->
            if (pdf != null) onOpenPdf(pdf) else Toast.makeText(context, R.string.md_read_failed, Toast.LENGTH_SHORT).show()
        }
    }
    fun print() {
        Toast.makeText(context, R.string.docx_preparing_print, Toast.LENGTH_SHORT).show()
        viewModel.exportToPdf(context) { pdf ->
            if (pdf == null || !PdfPrint.print(context, pdf, state.fileName.substringBeforeLast('.')))
                Toast.makeText(context, R.string.docx_print_failed, Toast.LENGTH_SHORT).show()
        }
    }

    Box(Modifier.fillMaxSize()) {
        GlassScreenScaffold(
            backdrop = backdrop,
            screenBackdrop = screenBackdrop,
            contentHorizontalPadding = 0.dp,
            headerHorizontalPadding = 12.dp,
            header = { headerBackdrop ->
                val backInk = rememberAdaptiveInk(text)
                val titleInk = rememberAdaptiveInk(text)
                val paperInk = rememberAdaptiveInk(text)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    GlassBackButton(
                        onBack = onBack,
                        backdrop = headerBackdrop,
                        modifier = backInk.modifier,
                        foreground = backInk.color,
                        onLongPressBack = LocalBackToLibraryAction.current
                    )
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        GlassTitlePill(
                            text = state.fileName.ifBlank { "Markdown" },
                            backdrop = headerBackdrop,
                            modifier = titleInk.modifier,
                            surfaceColor = Color.Transparent,
                            contentColor = titleInk.color
                        )
                    }
                    LiquidIconButton(onClick = onTogglePaper, backdrop = headerBackdrop, modifier = paperInk.modifier) {
                        Icon(
                            if (paperDark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                            stringResource(if (paperDark) R.string.viewer_page_colors_original else R.string.viewer_page_dark_mode),
                            Modifier.size(19.dp), paperInk.color
                        )
                    }
                }
            }
        ) { contentPadding ->
            when {
                state.isLoading -> Box(Modifier.fillMaxSize().background(colors.paper), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = accent, strokeWidth = 2.5.dp)
                }
                doc == null -> Box(Modifier.fillMaxSize().background(colors.paper).padding(24.dp), contentAlignment = Alignment.Center) {
                    BasicText(stringResource(R.string.md_read_failed), style = TextStyle(sub, 15.sp, textAlign = TextAlign.Center))
                }
                else -> {
                    val dir = LocalLayoutDirection.current
                    SelectionContainer(Modifier.fillMaxSize().background(colors.paper)) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = contentPadding.calculateStartPadding(dir) + 20.dp,
                                end = contentPadding.calculateEndPadding(dir) + 20.dp,
                                top = contentPadding.calculateTopPadding() + 8.dp,
                                // Room for the print / PDF / share buttons over the last lines.
                                bottom = contentPadding.calculateBottomPadding() + 84.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            itemsIndexed(doc.blocks) { _, block -> MdBlockView(block, colors, imageLabel, onLink) }
                            if (doc.blocks.isEmpty()) item {
                                BasicText(stringResource(R.string.md_empty), style = TextStyle(sub, 15.sp))
                            }
                        }
                    }
                }
            }
        }

        // ── reading chrome: print, PDF layout and share, as in the Word viewer ──
        if (doc != null) {
            val printInk = rememberAdaptiveInk(text)
            val pdfInk = rememberAdaptiveInk(text)
            val shareInk = rememberAdaptiveInk(text)
            Row(
                Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 12.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LiquidIconButton(onClick = { print() }, backdrop = screenBackdrop.glass, modifier = printInk.modifier.size(44.dp)) {
                    Icon(Icons.Rounded.Print, stringResource(R.string.docx_print), Modifier.size(20.dp), printInk.color)
                }
                LiquidIconButton(onClick = { openAsPdf() }, backdrop = screenBackdrop.glass, modifier = pdfInk.modifier.size(44.dp)) {
                    Icon(Icons.Rounded.PictureAsPdf, stringResource(R.string.docx_open_as_pdf), Modifier.size(20.dp), pdfInk.color)
                }
                LiquidIconButton(
                    onClick = { (state.localUri ?: state.sourceUri)?.let { shareFile(context, it) } },
                    backdrop = screenBackdrop.glass,
                    modifier = shareInk.modifier.size(44.dp)
                ) {
                    Icon(Icons.Rounded.IosShare, stringResource(R.string.viewer_share_document), Modifier.size(20.dp), shareInk.color)
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
