package com.example.skylarkwallet.data.local.crypto

import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.security.GeneralSecurityException

/** Keystore を使わずに、同じ AES256_GCM の Aead で暗号化・復号を確かめる。 */
class AeadStringCipherTest {

    private val cipher = AeadStringCipher {
        AeadConfig.register()
        KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM"))
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    @Test
    fun `round trips`() {
        val encrypted = cipher.encrypt("ABC123XYZ", "id-1")

        assertFalse(encrypted.contains("ABC123XYZ"))
        assertEquals("ABC123XYZ", cipher.decrypt(encrypted, "id-1"))
    }

    @Test
    fun `same plaintext gives different ciphertext`() {
        assertNotEquals(cipher.encrypt("6674577439187", "id"), cipher.encrypt("6674577439187", "id"))
    }

    @Test
    fun `handles long japanese html`() {
        val html = "<p>残高</p>".repeat(1000)

        assertEquals(html, cipher.decrypt(cipher.encrypt(html, "id"), "id"))
    }

    @Test(expected = GeneralSecurityException::class)
    fun `fails with different record id`() {
        val encrypted = cipher.encrypt("ABC123XYZ", "id-1")
        cipher.decrypt(encrypted, "id-2")
    }
}
