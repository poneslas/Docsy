package com.chethan616.clearpdf.ui.screen.signature

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.imageeditor.engine.ImageIo
import com.chethan616.clearpdf.imageeditor.engine.SignatureExtractor
import com.chethan616.clearpdf.imageeditor.thirdparty.cropper.ImageCropper
import com.chethan616.clearpdf.imageeditor.thirdparty.cropper.model.AspectRatio
import com.chethan616.clearpdf.imageeditor.thirdparty.cropper.rememberImageCropperState
import com.chethan616.clearpdf.imageeditor.thirdparty.cropper.settings.CropDefaults
import com.chethan616.clearpdf.ui.components.LiquidButton
import com.chethan616.clearpdf.ui.components.LiquidIconButton
import com.chethan616.clearpdf.ui.components.LiquidSlider
import com.chethan616.clearpdf.ui.screen.imageeditor.CropMasks
import com.chethan616.clearpdf.ui.theme.LiquidGlassColors
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

private enum class ImportStep { Pick, Crop, Tune }

/**
 * The signature pad's "Photo" mode: pick a photo of a signature (camera or gallery), frame it, and
 * tune how the paper is lifted off ([SignatureExtractor]). Hands back the same kind of bitmap the
 * pad draws — ink on transparency, trimmed — so naming, saving and placing stay the pad's own.
 *
 * It fills the space the pad's ink tray, canvas and actions take in "Draw" mode and reuses their
 * look: the light paper card, the light tray with ink beads and a glass slider, and the bottom
 * pill row. The camera is the app's document scanner (Google's on play / foss, with its own edge
 * detection; the camera app on fdroid); the frame step is the image editor's cropper.
 */
@Composable
fun ColumnScope.SignatureImportStage(
    backdrop: Backdrop,
    inkColors: List<Color>,
    chrome: Color,
    trayColor: Color,
    cardBorder: Color,
    onExtracted: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf(ImportStep.Pick) }
    var source by remember { mutableStateOf<Bitmap?>(null) }
    var cropped by remember { mutableStateOf<Bitmap?>(null) }
    var cropTrigger by remember { mutableStateOf(false) }
    // Framed by the user (not the whole photo): strokes may then touch the frame.
    var framed by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    // Tune settings: null strength = automatic; null ink = the pen's own colour.
    var auto by remember { mutableStateOf(true) }
    var strength by remember { mutableFloatStateOf(0.5f) }
    var solidInk by remember { mutableStateOf<Color?>(null) }
    var originalInk by remember { mutableStateOf(Color(0xFF1565C0)) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var previewEmpty by remember { mutableStateOf(false) }
    var finishing by remember { mutableStateOf(false) }

    fun load(uri: Uri) {
        scope.launch {
            val bmp = withContext(Dispatchers.IO) { runCatching { ImageIo.decode(context, uri, maxDim = SourceMaxDim) }.getOrNull() }
            failed = bmp == null
            if (bmp != null) { source = bmp; cropped = null; step = ImportStep.Crop }
        }
    }

    val startScanner = com.chethan616.clearpdf.scan.rememberDocumentScanner(
        onPages = { pages -> pages.firstOrNull()?.let(::load) },
        onFinished = {},
        onError = { failed = true }
    )
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) load(uri)
    }

    fun params(): SignatureExtractor.Params = SignatureExtractor.Params(
        strength = if (auto) null else strength,
        ink = solidInk?.let { SignatureExtractor.Ink.Solid(it.toArgb()) } ?: SignatureExtractor.Ink.Original,
        dropEdgeTouching = !framed
    )

    // Live preview on a small copy; recomputed whenever a setting changes.
    LaunchedEffect(cropped, framed, auto, strength, solidInk) {
        val img = cropped ?: return@LaunchedEffect
        val result = withContext(Dispatchers.Default) { extract(scaledDown(img, PreviewMaxDim), params()) }
        previewEmpty = result == null
        preview = result?.first
        if (solidInk == null && result != null) originalInk = Color(0xFF000000.toInt() or result.second)
    }

    val paperCard = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(20.dp))
        .background(Color(0xFFF7F7F3))
        .border(1.dp, Color.Black.copy(0.10f), RoundedCornerShape(20.dp))
    val hintStyle = TextStyle(Color.Black.copy(0.45f), 14.sp, FontWeight.Medium, textAlign = TextAlign.Center)

    Crossfade(targetState = step, label = "importStep", modifier = Modifier.weight(1f).fillMaxWidth()) { current ->
        when (current) {
            ImportStep.Pick -> Box(paperCard.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(Icons.Rounded.PhotoCamera, null, Modifier.size(44.dp), Color.Black.copy(0.22f))
                    BasicText(stringResource(R.string.sig_photo_hint), style = hintStyle)
                    if (failed) {
                        BasicText(
                            stringResource(R.string.sig_photo_failed),
                            style = hintStyle.copy(color = LiquidGlassColors.Red)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LiquidButton(onClick = { failed = false; startScanner() }, backdrop = backdrop, tint = LiquidGlassColors.Blue) {
                            IconLabel(Icons.Rounded.PhotoCamera, stringResource(R.string.sig_photo_camera), Color.White)
                        }
                        LiquidButton(onClick = { failed = false; pickImage.launch("image/*") }, backdrop = backdrop, surfaceColor = Color.Black.copy(0.06f)) {
                            IconLabel(Icons.Rounded.PhotoLibrary, stringResource(R.string.sig_photo_gallery), Color.Black.copy(0.8f))
                        }
                    }
                }
            }

            ImportStep.Crop -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicText(
                    stringResource(R.string.sig_photo_crop_hint),
                    style = TextStyle(chrome.copy(0.6f), 12.sp, FontWeight.SemiBold, letterSpacing = 0.3.sp)
                )
                val img = source
                Box(paperCard.weight(1f).background(Color.Black.copy(0.85f))) {
                    if (img != null) {
                        val image = remember(img) { img.asImageBitmap() }
                        ImageCropper(
                            modifier = Modifier.fillMaxSize(),
                            imageBitmap = image,
                            cropStyle = CropDefaults.style(
                                overlayColor = Color.White.copy(0.55f),
                                handleColor = LiquidGlassColors.Blue,
                                backgroundColor = Color.Black.copy(0.45f)
                            ),
                            cropProperties = CropDefaults.properties(
                                cropOutlineProperty = CropMasks[0].property,
                                aspectRatio = AspectRatio.Original,
                                fixedAspectRatio = false,
                                overlayRatio = 0.9f,
                                maxZoom = 8f
                            ),
                            state = rememberImageCropperState(),
                            crop = cropTrigger,
                            onCropStart = {},
                            onZoomChange = {},
                            onCropSuccess = { r ->
                                cropTrigger = false
                                val x = (r.left * img.width).roundToInt().coerceIn(0, img.width - 1)
                                val y = (r.top * img.height).roundToInt().coerceIn(0, img.height - 1)
                                val w = ((r.right - r.left) * img.width).roundToInt().coerceIn(1, img.width - x)
                                val h = ((r.bottom - r.top) * img.height).roundToInt().coerceIn(1, img.height - y)
                                framed = w * h < img.width * img.height * 0.9f
                                cropped = Bitmap.createBitmap(img, x, y, w, h)
                                step = ImportStep.Tune
                            }
                        )
                    }
                }
            }

            ImportStep.Tune -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Ink beads (the pen's own colour first) and the clean-up strength.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(trayColor)
                        .border(1.dp, cardBorder, RoundedCornerShape(22.dp))
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        InkBead(originalInk, solidInk == null, backdrop, original = true) { solidInk = null }
                        inkColors.forEach { c -> InkBead(c, solidInk == c, backdrop) { solidInk = c } }
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Color.Black.copy(0.07f)))
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        LiquidButton(
                            onClick = { auto = !auto },
                            backdrop = backdrop,
                            tint = if (auto) LiquidGlassColors.Blue else Color.Unspecified,
                            surfaceColor = if (auto) Color.Unspecified else Color.Black.copy(0.06f),
                            horizontalContentPadding = 4.dp
                        ) {
                            IconLabel(
                                Icons.Rounded.AutoFixHigh, stringResource(R.string.sig_photo_auto),
                                if (auto) Color.White else Color.Black.copy(0.75f), small = true
                            )
                        }
                        LiquidSlider(
                            value = { strength },
                            onValueChange = { strength = it.coerceIn(0f, 1f); auto = false },
                            valueRange = 0f..1f,
                            visibilityThreshold = 0.01f,
                            backdrop = backdrop,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                // The lifted signature on a checkerboard, so what is transparent shows as such.
                Box(paperCard.weight(1f), contentAlignment = Alignment.Center) {
                    Checkerboard(Modifier.fillMaxSize())
                    val shown = preview
                    if (shown != null) {
                        val image = remember(shown) { shown.asImageBitmap() }
                        Image(image, null, Modifier.fillMaxSize().padding(20.dp), contentScale = ContentScale.Fit)
                    } else if (previewEmpty) {
                        BasicText(stringResource(R.string.sig_photo_none), style = hintStyle, modifier = Modifier.padding(24.dp))
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(14.dp))

    // Bottom actions, in the pad's own row: back a step · next / use.
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val canGoBack = step != ImportStep.Pick
        LiquidButton(
            onClick = {
                step = when (step) {
                    ImportStep.Tune -> ImportStep.Crop
                    else -> ImportStep.Pick
                }
            },
            backdrop = backdrop,
            surfaceColor = chrome.copy(if (canGoBack) 0.10f else 0.04f),
            isInteractive = canGoBack,
            modifier = Modifier.weight(1f)
        ) {
            BasicText(
                stringResource(R.string.back),
                style = TextStyle(chrome.copy(if (canGoBack) 0.85f else 0.32f), 15.sp, FontWeight.Medium),
                modifier = Modifier.padding(vertical = 9.dp)
            )
        }
        val ready = when (step) {
            ImportStep.Pick -> false
            ImportStep.Crop -> true
            ImportStep.Tune -> preview != null && !finishing
        }
        LiquidButton(
            onClick = {
                when (step) {
                    ImportStep.Pick -> Unit
                    ImportStep.Crop -> cropTrigger = true
                    ImportStep.Tune -> {
                        val img = cropped ?: return@LiquidButton
                        if (preview == null || finishing) return@LiquidButton
                        finishing = true
                        scope.launch {
                            // Full resolution for the saved signature; the preview is a small copy.
                            val result = withContext(Dispatchers.Default) { extract(img, params()) }
                            finishing = false
                            result?.first?.let(onExtracted)
                        }
                    }
                }
            },
            backdrop = backdrop,
            tint = if (ready) Color(0xFF00C853) else Color(0xFF2E7D32).copy(0.55f),
            isInteractive = ready,
            modifier = Modifier.weight(1.7f)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 9.dp)
            ) {
                if (step == ImportStep.Tune) Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), Color.White)
                BasicText(
                    stringResource(if (step == ImportStep.Tune) R.string.sig_use else R.string.next),
                    style = TextStyle(Color.White, 15.sp, FontWeight.SemiBold)
                )
            }
        }
    }
}

/**
 * Icon and label as one centred unit inside a [LiquidButton] — the viewer's own button recipe
 * (Done / Delete in the image tools), so the label sits next to its icon, not across the pill.
 */
@Composable
private fun IconLabel(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, ink: Color, small: Boolean = false) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(if (small) 6.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = if (small) 10.dp else 14.dp, vertical = 8.dp)
    ) {
        Icon(icon, null, Modifier.size(if (small) 16.dp else 18.dp), ink)
        BasicText(label, style = TextStyle(ink, if (small) 13.sp else 15.sp, FontWeight.SemiBold))
    }
}

/** An ink bead like the pad's; the "original" one carries a small wand so it reads as "as photographed". */
@Composable
private fun InkBead(color: Color, selected: Boolean, backdrop: Backdrop, original: Boolean = false, onClick: () -> Unit) {
    val size by animateDpAsState(
        if (selected) 38.dp else 28.dp,
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "inkBead"
    )
    LiquidIconButton(onClick = onClick, backdrop = backdrop, surfaceColor = color, modifier = Modifier.size(size)) {
        when {
            selected -> Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), Color.White)
            original -> Icon(Icons.Rounded.AutoFixHigh, stringResource(R.string.sig_photo_original_ink), Modifier.size(13.dp), Color.White.copy(0.9f))
        }
    }
}

@Composable
private fun Checkerboard(modifier: Modifier) {
    Canvas(modifier) {
        val cell = 12.dp.toPx()
        val light = Color(0xFFF7F7F3); val dark = Color(0xFFE6E6E1)
        drawRect(light)
        var y = 0f; var row = 0
        while (y < size.height) {
            var x = if (row % 2 == 0) 0f else cell
            while (x < size.width) {
                drawRect(dark, Offset(x, y), Size(cell, cell))
                x += cell * 2
            }
            y += cell; row++
        }
    }
}

/** Runs the extractor on [img]; the transparent signature bitmap and the ink colour it used. */
private fun extract(img: Bitmap, params: SignatureExtractor.Params): Pair<Bitmap, Int>? {
    val w = img.width; val h = img.height
    val px = IntArray(w * h)
    img.getPixels(px, 0, w, 0, 0, w, h)
    val r = SignatureExtractor.extract(px, w, h, params) ?: return null
    return Bitmap.createBitmap(r.pixels, r.width, r.height, Bitmap.Config.ARGB_8888) to r.inkRgb
}

private fun scaledDown(img: Bitmap, maxDim: Int): Bitmap {
    val scale = maxDim.toFloat() / max(img.width, img.height)
    if (scale >= 1f) return img
    return Bitmap.createScaledBitmap(img, (img.width * scale).roundToInt().coerceAtLeast(1), (img.height * scale).roundToInt().coerceAtLeast(1), true)
}

private const val SourceMaxDim = 2048
private const val PreviewMaxDim = 720
