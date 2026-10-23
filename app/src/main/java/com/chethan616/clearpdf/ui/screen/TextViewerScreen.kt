package com.chethan616.clearpdf.ui.screen

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.WrapText
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DataObject
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.data.repository.AppSettingsManager
import com.chethan616.clearpdf.ui.components.GlassBackButton
import com.chethan616.clearpdf.ui.components.GlassDialog
import com.chethan616.clearpdf.ui.components.GlassDialogAction
import com.chethan616.clearpdf.ui.components.GlassScreenScaffold
import com.chethan616.clearpdf.ui.components.GlassTitlePill
import com.chethan616.clearpdf.ui.components.LiquidIconButton
import com.chethan616.clearpdf.ui.components.LocalBackToLibraryAction
import com.chethan616.clearpdf.ui.components.liquidRowClick
import com.chethan616.clearpdf.ui.components.rememberAdaptiveInk
import com.chethan616.clearpdf.ui.components.rememberScreenBackdrop
import com.chethan616.clearpdf.ui.components.viewerGlass
import com.chethan616.clearpdf.ui.theme.LiquidGlassColors
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.chethan616.clearpdf.ui.viewmodel.TextViewerViewModel
import com.chethan616.clearpdf.utils.PdfPrint
import com.chethan616.clearpdf.utils.text.Grammar
import com.chethan616.clearpdf.utils.text.Grammars
import com.chethan616.clearpdf.utils.text.LineState
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.shapes.Capsule
import java.text.NumberFormat

/**
 * Read-only viewer for text files: logs, JSON, XML, YAML, INI, CSV, source code and plain text.
 * Monospace with line numbers, highlighted by the file's type (TextMate-style rules, see
 * [Grammars]); long lines wrap or, with wrapping off, the page scrolls sideways. JSON and XML can
 * be re-indented. Print, "Open as PDF" and Share as in the other viewers.
 *
 * The page's light or dark look follows its own reader mode, as in the Word and Markdown viewers.
 */
@Composable
fun TextViewerScreen(
    backdrop: LayerBackdrop,
    viewModel: TextViewerViewModel,
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
        TextViewerContent(backdrop, viewModel, onBack, onOpenPdf, paperDark) { paperDarkOverride = !paperDark }
    }
}

private val Mono = FontFamily.Monospace
private val TextSize = 12.5.sp
private val TextLine = 18.sp

@Composable
private fun TextViewerContent(
    backdrop: LayerBackdrop,
    viewModel: TextViewerViewModel,
    onBack: () -> Unit,
    onOpenPdf: (Uri) -> Unit,
    paperDark: Boolean,
    onTogglePaper: () -> Unit
) {
    val context = LocalContext.current
    val text = LiquidGlassColors.text(paperDark)
    val sub = LiquidGlassColors.secondary(paperDark)
    val accent = LiquidGlassColors.Blue
    val paper = if (paperDark) Color(0xFF111114) else Color.White
    val state = viewModel.state
    val lines = state.lines
    val screenBackdrop = rememberScreenBackdrop(backdrop)
    var wrap by rememberSaveable { mutableStateOf(true) }
    var showGrammar by remember { mutableStateOf(false) }

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
                val wrapInk = rememberAdaptiveInk(text)
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
                            text = state.fileName.ifBlank { "Text" },
                            backdrop = headerBackdrop,
                            modifier = titleInk.modifier,
                            surfaceColor = Color.Transparent,
                            contentColor = titleInk.color
                        )
                    }
                    LiquidIconButton(
                        onClick = { wrap = !wrap },
                        backdrop = headerBackdrop,
                        modifier = wrapInk.modifier,
                        surfaceColor = if (wrap) accent.copy(0.22f) else Color.Unspecified
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.WrapText, stringResource(R.string.tv_wrap), Modifier.size(20.dp), if (wrap) accent else wrapInk.color)
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
                state.isLoading -> Box(Modifier.fillMaxSize().background(paper), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = accent, strokeWidth = 2.5.dp)
                }
                state.failed || lines == null -> Box(Modifier.fillMaxSize().background(paper).padding(24.dp), contentAlignment = Alignment.Center) {
                    BasicText(stringResource(R.string.md_read_failed), style = TextStyle(sub, 15.sp, textAlign = TextAlign.Center))
                }
                else -> TextLines(lines, state.grammar, wrap, paperDark, paper, text, sub, contentPadding, state.truncated)
            }
        }

        // ── reading chrome: highlighting + line count (left); format, print, PDF, share (right) ──
        if (lines != null) {
            val modeInk = rememberAdaptiveInk(text)
            val formatInk = rememberAdaptiveInk(text)
            val printInk = rememberAdaptiveInk(text)
            val pdfInk = rememberAdaptiveInk(text)
            val shareInk = rememberAdaptiveInk(text)
            val count = remember(lines.count) { NumberFormat.getIntegerInstance().format(lines.count) }
            Row(
                Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(start = 12.dp, bottom = 10.dp)
                    .height(44.dp)
                    .then(modeInk.modifier)
                    .viewerGlass(screenBackdrop.glass, Color.Transparent, shape = { Capsule })
                    .clip(Capsule)
                    .liquidRowClick(corner = 22.dp) { showGrammar = true }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicText(
                    grammarLabel(state.grammar) + "  ·  " + stringResource(R.string.tv_line_count, count),
                    style = TextStyle(modeInk.color, 12.sp, FontWeight.SemiBold),
                    maxLines = 1
                )
            }
            Row(
                Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 12.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (state.canFormat) LiquidIconButton(
                    onClick = { viewModel.toggleFormat() },
                    backdrop = screenBackdrop.glass,
                    modifier = formatInk.modifier.size(44.dp),
                    surfaceColor = if (state.formatted) accent.copy(0.22f) else Color.Unspecified
                ) {
                    Icon(Icons.Rounded.DataObject, stringResource(R.string.tv_format), Modifier.size(20.dp), if (state.formatted) accent else formatInk.color)
                }
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

        GrammarDialog(
            visible = showGrammar,
            current = state.grammar,
            backdrop = screenBackdrop.glass,
            ink = text,
            accent = accent,
            onPick = { viewModel.setGrammar(it); showGrammar = false },
            onDismiss = { showGrammar = false }
        )
    }
}

/** The lines, numbered, highlighted as they scroll into view. */
@Composable
private fun TextLines(
    lines: TextViewerViewModel.Lines,
    grammar: Grammar?,
    wrap: Boolean,
    dark: Boolean,
    paper: Color,
    ink: Color,
    sub: Color,
    contentPadding: PaddingValues,
    truncated: Boolean
) {
    val dir = LocalLayoutDirection.current
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    val measurer = rememberTextMeasurer()
    val style = remember(ink) { TextStyle(ink, TextSize, fontFamily = Mono, lineHeight = TextLine) }
    val numberStyle = remember(sub) { TextStyle(sub.copy(alpha = 0.7f), 11.sp, fontFamily = Mono, lineHeight = TextLine, textAlign = TextAlign.End) }
    // Monospace: one glyph's width sizes the gutter and, unwrapped, the page.
    val charPx = remember(style) { measurer.measure("0", style).size.width.toFloat() }
    val gutterDigits = lines.count.toString().length.coerceAtLeast(2)
    val gutter = with(density) { (measurer.measure("0".repeat(gutterDigits), numberStyle).size.width).toDp() } + 12.dp
    val padStart = contentPadding.calculateStartPadding(dir) + 10.dp
    val padEnd = contentPadding.calculateEndPadding(dir) + 16.dp
    val pageWidth = with(density) { (charPx * (lines.longest + 1)).toDp() } + gutter + padStart + padEnd

    val list: @Composable (Modifier) -> Unit = { modifier ->
        LazyColumn(
            state = listState,
            modifier = modifier,
            contentPadding = PaddingValues(
                start = padStart,
                end = padEnd,
                top = contentPadding.calculateTopPadding() + 6.dp,
                bottom = contentPadding.calculateBottomPadding() + 80.dp
            )
        ) {
            items(lines.text.size) { i ->
                val line = lines.text[i]
                val lineState = lines.states?.getOrNull(i) ?: LineState.Initial
                val annotated = remember(line, grammar, dark, lineState) { highlightLine(line, grammar, lineState, dark) }
                Row(Modifier.fillMaxWidth()) {
                    BasicText(
                        if (lines.numbers[i] > 0) lines.numbers[i].toString() else "",
                        Modifier.width(gutter).padding(end = 12.dp),
                        style = numberStyle
                    )
                    BasicText(annotated, Modifier.weight(1f), style = style, softWrap = wrap)
                }
            }
            if (truncated) item {
                BasicText(
                    stringResource(R.string.tv_truncated, TextViewerViewModel.MaxBytes / (1024 * 1024)),
                    Modifier.padding(start = gutter, top = 12.dp),
                    style = TextStyle(sub, 13.sp, FontWeight.Medium)
                )
            }
        }
    }

    SelectionContainer(Modifier.fillMaxSize().background(paper)) {
        if (wrap) list(Modifier.fillMaxSize())
        else Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
            list(Modifier.width(pageWidth).fillMaxHeight())
        }
    }
}

@Composable
private fun grammarLabel(g: Grammar?): String = when (g) {
    null -> stringResource(R.string.tv_lang_plain)
    Grammars.Log -> stringResource(R.string.tv_lang_log)
    Grammars.Code -> stringResource(R.string.tv_lang_code)
    Grammars.Script -> stringResource(R.string.tv_lang_script)
    else -> g.id.uppercase()
}

/** Pick the highlighting: the file's own guess is just the starting point. */
@Composable
private fun GrammarDialog(
    visible: Boolean,
    current: Grammar?,
    backdrop: com.kyant.backdrop.Backdrop,
    ink: Color,
    accent: Color,
    onPick: (Grammar?) -> Unit,
    onDismiss: () -> Unit
) {
    GlassDialog(
        visible = visible,
        onDismiss = onDismiss,
        backdrop = backdrop,
        title = stringResource(R.string.tv_highlight_title),
        actions = { GlassDialogAction(stringResource(R.string.cancel), onDismiss) }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            (listOf<Grammar?>(null) + Grammars.all).forEach { g ->
                val selected = g == current
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(if (selected) accent.copy(0.14f) else Color.Transparent)
                        .liquidRowClick(corner = 14.dp) { onPick(g) }
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicText(grammarLabel(g), style = TextStyle(if (selected) accent else ink, 15.sp, if (selected) FontWeight.SemiBold else FontWeight.Normal))
                    Spacer(Modifier.weight(1f))
                    if (selected) Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), accent)
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
