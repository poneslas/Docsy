package com.chethan616.clearpdf.utils

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Turns the bytes of a text file into a string without knowing its encoding up front: a BOM
 * decides when present, then UTF-16 without a BOM (every other byte zero), then strict UTF-8,
 * and finally windows-1251, the usual legacy encoding of Russian text files. A file that is
 * neither still opens: the last step can decode any byte.
 */
object TextDecoding {

    fun decode(bytes: ByteArray): String {
        bomCharset(bytes)?.let { (cs, skip) -> return String(bytes, skip, bytes.size - skip, cs) }
        // UTF-16 first: its zero bytes are valid UTF-8, so the strict check would accept it.
        utf16WithoutBom(bytes)?.let { return String(bytes, it) }
        strictUtf8(bytes)?.let { return it }
        return String(bytes, Charset.forName("windows-1251"))
    }

    private fun bomCharset(b: ByteArray): Pair<Charset, Int>? = when {
        b.size >= 3 && b[0] == 0xEF.toByte() && b[1] == 0xBB.toByte() && b[2] == 0xBF.toByte() -> Charsets.UTF_8 to 3
        b.size >= 2 && b[0] == 0xFF.toByte() && b[1] == 0xFE.toByte() -> Charsets.UTF_16LE to 2
        b.size >= 2 && b[0] == 0xFE.toByte() && b[1] == 0xFF.toByte() -> Charsets.UTF_16BE to 2
        else -> null
    }

    private fun strictUtf8(b: ByteArray): String? = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(b)).toString()
    } catch (_: CharacterCodingException) {
        null
    }

    /** ASCII-heavy UTF-16 has a zero in every other byte; which half holds them gives the order. */
    private fun utf16WithoutBom(b: ByteArray): Charset? {
        val n = minOf(b.size, 4096) and 1.inv()
        if (n < 8) return null
        var evenZeros = 0
        var oddZeros = 0
        for (i in 0 until n step 2) {
            if (b[i] == 0.toByte()) evenZeros++
            if (b[i + 1] == 0.toByte()) oddZeros++
        }
        val pairs = n / 2
        return when {
            oddZeros > pairs * 0.4 && evenZeros < pairs * 0.05 -> Charsets.UTF_16LE
            evenZeros > pairs * 0.4 && oddZeros < pairs * 0.05 -> Charsets.UTF_16BE
            else -> null
        }
    }
}
