package com.chethan616.clearpdf.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class TextDecodingTest {

    private val ru = "Привет, мир! Ёлка и щётка — 2024"
    private val bomUtf8 = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    private val bomLe = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
    private val bomBe = byteArrayOf(0xFE.toByte(), 0xFF.toByte())

    @Test
    fun utf8WithoutBom() {
        assertEquals(ru, TextDecoding.decode(ru.toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun utf8BomIsDroppedNotShownAsCharacter() {
        assertEquals(ru, TextDecoding.decode(bomUtf8 + ru.toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun utf16LittleEndianWithBom() {
        assertEquals(ru, TextDecoding.decode(bomLe + ru.toByteArray(Charsets.UTF_16LE)))
    }

    @Test
    fun utf16BigEndianWithBom() {
        assertEquals(ru, TextDecoding.decode(bomBe + ru.toByteArray(Charsets.UTF_16BE)))
    }

    @Test
    fun utf16WithoutBomIsRecognisedOnAsciiText() {
        val ascii = "Log line 1\nLog line 2\n"
        assertEquals(ascii, TextDecoding.decode(ascii.toByteArray(Charsets.UTF_16LE)))
        assertEquals(ascii, TextDecoding.decode(ascii.toByteArray(Charsets.UTF_16BE)))
    }

    @Test
    fun windows1251CyrillicFallsBackFromInvalidUtf8() {
        assertEquals(ru, TextDecoding.decode(ru.toByteArray(charset("windows-1251"))))
    }

    @Test
    fun plainAsciiAndEmptyInput() {
        assertEquals("hello", TextDecoding.decode("hello".toByteArray(Charsets.US_ASCII)))
        assertEquals("", TextDecoding.decode(ByteArray(0)))
        assertEquals("", TextDecoding.decode(bomUtf8))
    }
}
