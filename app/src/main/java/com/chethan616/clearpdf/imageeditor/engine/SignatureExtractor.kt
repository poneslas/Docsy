package com.chethan616.clearpdf.imageeditor.engine

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Lifts a handwritten signature off a photo of paper: the ink, and only the ink, on a transparent
 * background, trimmed to its bounds — the same kind of bitmap the signature pad draws, so it is
 * placed, recoloured and exported exactly like a drawn signature.
 *
 * Pure pixel code on packed ARGB [IntArray]s (no android.graphics), so it runs on every flavour and
 * in plain JVM tests. The steps:
 *
 * 1. **Paper level.** The brightness of the paper around each pixel, estimated by a max-pool then a
 *    box blur of the darkest channel: strokes are thinner than the window, so they vanish from it,
 *    while shadows, a dim corner or cream paper stay. Dividing by it evens out the lighting.
 * 2. **Ink.** How much darker a pixel is than its paper. The darkest channel keeps blue ballpoint
 *    dark; warm pixels (wood, skin, a brown shadow) are not ink.
 * 3. **Threshold.** Otsu's split of that darkness separates paper from ink unless the user moves the
 *    strength slider; alpha ramps softly across it so thin strokes and antialiased edges survive.
 * 4. **Clean-up.** Specks, anything touching the photo's edge (the desk, a hand, the paper's own
 *    edge), anything not on bright paper (wood grain in a shadow, a plant against the wall) and
 *    filled patches far thicker than a pen stroke are dropped; of what is left, the largest cluster of nearby strokes is the signature. The result is
 *    trimmed to it with a small margin.
 */
object SignatureExtractor {

    sealed interface Ink {
        /** The pen's own colour, averaged over the strongest strokes. */
        data object Original : Ink
        data class Solid(val rgb: Int) : Ink
    }

    data class Params(
        /** null = automatic (Otsu); 0..1 otherwise, higher keeps fainter strokes. */
        val strength: Float? = null,
        val ink: Ink = Ink.Original,
        /**
         * Treat strokes touching the frame as background (a hand, the desk, the paper's edge). Right
         * for a whole photo; off once the user has framed the signature, since a tight frame may
         * touch the strokes themselves.
         */
        val dropEdgeTouching: Boolean = true
    )

    class Result(val pixels: IntArray, val width: Int, val height: Int, val inkRgb: Int)

    fun extract(argb: IntArray, width: Int, height: Int, params: Params = Params()): Result? {
        require(argb.size >= width * height)
        if (width < 8 || height < 8) return null
        val n = width * height

        // 1. Darkest channel and warmth.
        val gray = FloatArray(n)
        val warm = BooleanArray(n)
        for (i in 0 until n) {
            val c = argb[i]
            val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
            gray[i] = min(r, min(g, b)).toFloat()
            warm[i] = r - b > WarmDelta
        }
        val paper = paperLevel(gray, width, height)

        // 2. Darkness relative to the local paper, 0 = paper, 1 = black.
        val dark = FloatArray(n)
        for (i in 0 until n) {
            if (warm[i]) continue
            val p = max(paper[i], 1f)
            dark[i] = (1f - gray[i] / p).coerceIn(0f, 1f)
        }

        // 3. Threshold: Otsu between paper and ink, or the user's strength.
        val t = params.strength?.let { MaxThreshold - it.coerceIn(0f, 1f) * (MaxThreshold - MinThreshold) }
            ?: otsu(dark).coerceIn(MinThreshold, MaxThreshold)
        val lo = t * 0.6f
        val hi = min(1f, t * 1.4f)

        // 4. Keep strokes; drop specks and whatever touches the frame.
        val mask = BooleanArray(n) { dark[it] >= t }
        val keep = keepSignatureComponents(mask, paper, width, height, params.dropEdgeTouching)
        var left = width; var top = height; var right = -1; var bottom = -1
        for (y in 0 until height) for (x in 0 until width) {
            if (!keep[y * width + x]) continue
            if (x < left) left = x; if (x > right) right = x
            if (y < top) top = y; if (y > bottom) bottom = y
        }
        if (right < 0) return null

        // Soft alpha around the kept strokes: grow the kept mask a little so the antialiased rim of
        // each stroke (below the threshold itself) stays attached.
        val near = dilate(keep, width, height, EdgeRadius)
        val margin = max(4, ((right - left + bottom - top) * 0.02f).roundToInt())
        left = max(0, left - margin); top = max(0, top - margin)
        right = min(width - 1, right + margin); bottom = min(height - 1, bottom + margin)
        val ow = right - left + 1; val oh = bottom - top + 1

        var sr = 0L; var sg = 0L; var sb = 0L; var sc = 0L
        val alpha = FloatArray(ow * oh)
        for (y in 0 until oh) for (x in 0 until ow) {
            val i = (y + top) * width + (x + left)
            if (!near[i]) continue
            val a = smoothstep(lo, hi, dark[i])
            alpha[y * ow + x] = a
            if (dark[i] >= hi) {
                val c = argb[i]; sr += (c shr 16) and 0xFF; sg += (c shr 8) and 0xFF; sb += c and 0xFF; sc++
            }
        }
        val inkRgb = when (val ink = params.ink) {
            is Ink.Solid -> ink.rgb and 0xFFFFFF
            Ink.Original -> if (sc == 0L) 0x000000 else
                (((sr / sc).toInt() shl 16) or ((sg / sc).toInt() shl 8) or (sb / sc).toInt())
        }
        val out = IntArray(ow * oh) { (((alpha[it] * 255f).roundToInt() and 0xFF) shl 24) or inkRgb }
        return Result(out, ow, oh, inkRgb)
    }

    // ── Steps ────────────────────────────────────────────────────────────────────────────────

    /** Local paper brightness: max-pool by [Pool], box blur, sampled back at full size. */
    internal fun paperLevel(gray: FloatArray, width: Int, height: Int): FloatArray {
        val pw = (width + Pool - 1) / Pool; val ph = (height + Pool - 1) / Pool
        val pooled = FloatArray(pw * ph)
        for (y in 0 until height) for (x in 0 until width) {
            val j = (y / Pool) * pw + (x / Pool)
            if (gray[y * width + x] > pooled[j]) pooled[j] = gray[y * width + x]
        }
        // A second max pass over neighbours, so a stroke wider than one cell still drops out.
        val grown = FloatArray(pw * ph)
        for (y in 0 until ph) for (x in 0 until pw) {
            var m = 0f
            for (dy in -1..1) for (dx in -1..1) {
                val xx = x + dx; val yy = y + dy
                if (xx in 0 until pw && yy in 0 until ph) m = max(m, pooled[yy * pw + xx])
            }
            grown[y * pw + x] = m
        }
        val blurred = boxBlur(grown, pw, ph, BlurRadius)
        val out = FloatArray(width * height)
        for (y in 0 until height) {
            val fy = ((y + 0.5f) / Pool - 0.5f).coerceIn(0f, (ph - 1).toFloat())
            val y0 = fy.toInt(); val y1 = min(y0 + 1, ph - 1); val ty = fy - y0
            for (x in 0 until width) {
                val fx = ((x + 0.5f) / Pool - 0.5f).coerceIn(0f, (pw - 1).toFloat())
                val x0 = fx.toInt(); val x1 = min(x0 + 1, pw - 1); val tx = fx - x0
                val a = blurred[y0 * pw + x0] * (1 - tx) + blurred[y0 * pw + x1] * tx
                val b = blurred[y1 * pw + x0] * (1 - tx) + blurred[y1 * pw + x1] * tx
                out[y * width + x] = a * (1 - ty) + b * ty
            }
        }
        return out
    }

    /** Otsu's threshold over 0..1 values, ignoring exact zeros (paper and warm pixels). */
    internal fun otsu(values: FloatArray): Float {
        val bins = 256
        val hist = IntArray(bins)
        var total = 0
        for (v in values) if (v > 0f) { hist[min(bins - 1, (v * (bins - 1)).toInt())]++; total++ }
        if (total == 0) return MaxThreshold
        var sum = 0.0
        for (i in 0 until bins) sum += i.toDouble() * hist[i]
        var sumB = 0.0; var wB = 0; var best = 0.0; var bestT = 0
        for (i in 0 until bins) {
            wB += hist[i]; if (wB == 0) continue
            val wF = total - wB; if (wF == 0) break
            sumB += i.toDouble() * hist[i]
            val mB = sumB / wB; val mF = (sum - sumB) / wF
            val between = wB.toDouble() * wF * (mB - mF) * (mB - mF)
            if (between > best) { best = between; bestT = i }
        }
        return (bestT + 0.5f) / (bins - 1)
    }

    /**
     * The strokes that make up the signature. Connected components (8-connectivity) are dropped when
     * they are specks, touch the frame (a hand, the desk, the paper's edge or a shadow reach the
     * border; a signature written on the paper does not), lie on dim "paper" — real paper is among
     * the brightest broad surfaces in the photo — straddle a steep change in lighting (the paper's
     * edge, a shadow's border) or are filled patches rather than lines. The survivors are grouped by proximity and the largest
     * group is kept: the signature, not a stray mark somewhere else on the page.
     */
    internal fun keepSignatureComponents(
        mask: BooleanArray, paper: FloatArray, width: Int, height: Int, dropEdgeTouching: Boolean = true
    ): BooleanArray {
        val n = mask.size
        val minSize = max(MinSpeck, (width * height * SpeckFraction).toInt())
        val brightPaper = percentile(paper, 0.95f) * PaperFraction
        val maxStroke = max(MinStrokeLimit, max(width, height) * MaxStrokeFraction)
        val candidates = BooleanArray(n)
        val label = IntArray(n) { -1 }
        val queue = IntArray(n)
        var next = 0
        for (start in 0 until n) {
            if (!mask[start] || label[start] >= 0) continue
            var head = 0; var tail = 0
            queue[tail++] = start; label[start] = next
            var touches = false
            var paperSum = 0.0
            var paperMin = Float.MAX_VALUE; var paperMax = 0f
            var rim = 0
            while (head < tail) {
                val i = queue[head++]
                paperSum += paper[i]
                if (paper[i] < paperMin) paperMin = paper[i]
                if (paper[i] > paperMax) paperMax = paper[i]
                val x = i % width; val y = i / width
                if (x == 0 || y == 0 || x == width - 1 || y == height - 1) touches = true
                else if (!mask[i - 1] || !mask[i + 1] || !mask[i - width] || !mask[i + width]) rim++
                for (dy in -1..1) for (dx in -1..1) {
                    val xx = x + dx; val yy = y + dy
                    if (xx < 0 || yy < 0 || xx >= width || yy >= height) continue
                    val j = yy * width + xx
                    if (mask[j] && label[j] < 0) { label[j] = next; queue[tail++] = j }
                }
            }
            // Pen strokes are lines: area over half the rim is about their width. A filled patch
            // (a shadow, a dark corner of the desk) is far thicker than any pen.
            val thickness = 2f * tail / max(rim, 1)
            // Writing sits on evenly lit paper; a dark line along the paper's edge or a shadow's
            // border sits where the paper level drops steeply.
            val evenPaper = paperMin >= paperMax * EvenPaper
            if (!(touches && dropEdgeTouching) && tail >= minSize && paperSum / tail >= brightPaper && thickness <= maxStroke && evenPaper) {
                for (k in 0 until tail) candidates[queue[k]] = true
            }
            next++
        }

        // Group strokes that are close together (letters, the flourish under them) and keep the
        // group with the most ink.
        val reach = max(4, (max(width, height) * GroupReach).toInt())
        val grouped = dilate(candidates, width, height, reach)
        val group = IntArray(n) { -1 }
        var bestGroup = -1; var bestInk = 0
        var g = 0
        for (start in 0 until n) {
            if (!grouped[start] || group[start] >= 0) continue
            var head = 0; var tail = 0
            queue[tail++] = start; group[start] = g
            var ink = 0
            while (head < tail) {
                val i = queue[head++]
                if (candidates[i]) ink++
                val x = i % width; val y = i / width
                if (x > 0 && grouped[i - 1] && group[i - 1] < 0) { group[i - 1] = g; queue[tail++] = i - 1 }
                if (x < width - 1 && grouped[i + 1] && group[i + 1] < 0) { group[i + 1] = g; queue[tail++] = i + 1 }
                if (y > 0 && grouped[i - width] && group[i - width] < 0) { group[i - width] = g; queue[tail++] = i - width }
                if (y < height - 1 && grouped[i + width] && group[i + width] < 0) { group[i + width] = g; queue[tail++] = i + width }
            }
            if (ink > bestInk) { bestInk = ink; bestGroup = g }
            g++
        }
        return BooleanArray(n) { candidates[it] && group[it] == bestGroup }
    }

    private fun percentile(values: FloatArray, q: Float): Float {
        val hist = IntArray(256)
        for (v in values) hist[v.toInt().coerceIn(0, 255)]++
        val target = (values.size * q).toInt()
        var acc = 0
        for (i in 0 until 256) { acc += hist[i]; if (acc >= target) return i.toFloat() }
        return 255f
    }

    private fun dilate(mask: BooleanArray, width: Int, height: Int, r: Int): BooleanArray {
        val horiz = BooleanArray(mask.size)
        for (y in 0 until height) {
            var last = -r - 1
            for (x in 0 until width) { if (mask[y * width + x]) last = x; if (x - last <= r) horiz[y * width + x] = true }
            last = width + r + 1
            for (x in width - 1 downTo 0) { if (mask[y * width + x]) last = x; if (last - x <= r) horiz[y * width + x] = true }
        }
        val out = BooleanArray(mask.size)
        for (x in 0 until width) {
            var last = -r - 1
            for (y in 0 until height) { if (horiz[y * width + x]) last = y; if (y - last <= r) out[y * width + x] = true }
            last = height + r + 1
            for (y in height - 1 downTo 0) { if (horiz[y * width + x]) last = y; if (last - y <= r) out[y * width + x] = true }
        }
        return out
    }

    private fun boxBlur(src: FloatArray, w: Int, h: Int, r: Int): FloatArray {
        val tmp = FloatArray(src.size); val out = FloatArray(src.size)
        for (y in 0 until h) for (x in 0 until w) {
            var s = 0f; var c = 0
            for (k in -r..r) { val xx = x + k; if (xx in 0 until w) { s += src[y * w + xx]; c++ } }
            tmp[y * w + x] = s / c
        }
        for (y in 0 until h) for (x in 0 until w) {
            var s = 0f; var c = 0
            for (k in -r..r) { val yy = y + k; if (yy in 0 until h) { s += tmp[yy * w + x]; c++ } }
            out[y * w + x] = s / c
        }
        return out
    }

    private fun smoothstep(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3 - 2 * t)
    }

    private const val Pool = 8
    private const val BlurRadius = 2
    private const val WarmDelta = 28
    private const val MinThreshold = 0.12f
    private const val MaxThreshold = 0.45f
    private const val EdgeRadius = 2
    private const val MinSpeck = 12
    private const val SpeckFraction = 0.00002f
    /** Ink must sit on paper at least this bright, relative to the brightest paper in the photo. */
    private const val PaperFraction = 0.45f
    /** Darkest over brightest paper under one stroke: below this it straddles an edge or a shadow. */
    private const val EvenPaper = 0.75f
    /** Strokes this close (fraction of the photo's long side) belong to the same signature. */
    private const val GroupReach = 0.04f
    /** Widest pen stroke, as a fraction of the photo's long side (with a floor in pixels). */
    private const val MaxStrokeFraction = 0.012f
    private const val MinStrokeLimit = 6f
}
