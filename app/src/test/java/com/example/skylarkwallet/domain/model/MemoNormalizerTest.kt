package com.example.skylarkwallet.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MemoNormalizerTest {

    @Test
    fun `input replaces line breaks but keeps typed spaces`() {
        assertEquals("テスト 2027/03/10 ", MemoNormalizer.sanitizeInput("テスト\n2027/03/10\r"))
        assertEquals("a b", MemoNormalizer.sanitizeInput("a b"))
        assertEquals("家族 ", MemoNormalizer.sanitizeInput("家族 "))
    }

    @Test
    fun `input is limited to max length`() {
        assertEquals(MemoNormalizer.MAX_LENGTH, MemoNormalizer.sanitizeInput("あ".repeat(50)).length)
    }

    @Test
    fun `saved memo collapses spaces and trims`() {
        assertEquals("テスト 2027/03/10 3VPKGAFMW", MemoNormalizer.normalize("  テスト\n2027/03/10\t\t3VPKGAFMW\n"))
        assertEquals("", MemoNormalizer.normalize(" \n "))
    }
}
