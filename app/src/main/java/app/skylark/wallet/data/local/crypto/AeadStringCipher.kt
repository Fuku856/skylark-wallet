package app.skylark.wallet.data.local.crypto

import com.google.crypto.tink.Aead
import java.util.Base64

/**
 * Tink の [Aead] で暗号化し、結果を Base64 文字列にする。
 * 暗号文には Tink の出力プレフィックスと IV（nonce）が含まれる。
 */
class AeadStringCipher(private val aeadProvider: () -> Aead) : StringCipher {

    private val aead by lazy(aeadProvider)

    override fun encrypt(plainText: String, associatedData: String): String {
        val cipher = aead.encrypt(plainText.toByteArray(Charsets.UTF_8), associatedData.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(cipher)
    }

    override fun decrypt(cipherText: String, associatedData: String): String {
        val plain = aead.decrypt(Base64.getDecoder().decode(cipherText), associatedData.toByteArray(Charsets.UTF_8))
        return String(plain, Charsets.UTF_8)
    }
}
