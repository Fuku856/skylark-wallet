package com.example.skylarkwallet.data.local.crypto

import android.content.Context
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager

/**
 * Android Keystore のマスターキーで保護した Tink Keyset から AES-256-GCM の [Aead] を作る。
 * Keyset は SharedPreferences に暗号化された状態で保存される（バックアップ対象外）。
 */
object CryptoManager {

    private const val KEYSET_NAME = "skylark_keyset"
    private const val PREF_FILE_NAME = "skylark_keyset_prefs"
    private const val MASTER_KEY_URI = "android-keystore://skylark_master_key"

    fun createAead(context: Context): Aead {
        AeadConfig.register()
        val handle = AndroidKeysetManager.Builder()
            .withSharedPref(context.applicationContext, KEYSET_NAME, PREF_FILE_NAME)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle
        return handle.getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }
}
