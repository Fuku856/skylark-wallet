package app.skylark.wallet.ui.lookup

import app.skylark.wallet.domain.model.CodeNormalizer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

/**
 * 照会の WebView に注入する JavaScript。サイトの入力画面（`form[name=codeform]`）に合わせている。
 *
 * reCAPTCHA のチェックは人が行い、ここでは触らない。トークンの有無だけを確かめ、トークン本体はネイティブへ返さない。
 * 戻り値は `evaluateJavascript` で JSON として受け取る。
 */
object LookupScript {

    const val RESULT_OK = "ok"
    const val RESULT_NO_FORM = "no_form"
    const val RESULT_NO_WIDGET = "no_widget"
    const val RESULT_NO_TOKEN = "no_token"

    /**
     * ご優待番号を3つの欄に入れ、[agree] なら規約に同意し、reCAPTCHA の枠を画面の中央に出す。
     * 9桁として正しくない番号は例外にする。
     */
    fun fill(code9: String, agree: Boolean): String {
        val parts = CodeNormalizer.split(code9).joinToString(",") { quote(it) }
        return """
            (function() {
              var f = document.forms['codeform'];
              if (!f) return '$RESULT_NO_FORM';
              var parts = [$parts];
              for (var i = 0; i < parts.length; i++) {
                var el = f.elements['code[' + i + ']'];
                if (!el) return '$RESULT_NO_FORM';
                el.value = parts[i];
                el.dispatchEvent(new Event('input', { bubbles: true }));
                el.dispatchEvent(new Event('change', { bubbles: true }));
              }
              var agree = f.elements['agree'];
              if (agree && $agree) {
                agree.checked = true;
                agree.dispatchEvent(new Event('change', { bubbles: true }));
              }
              var w = document.getElementById('charge-recaptcha') || f.querySelector('.g-recaptcha');
              if (!w) return '$RESULT_NO_WIDGET';
              w.scrollIntoView({ block: 'center', inline: 'center' });
              return '$RESULT_OK';
            })();
        """.trimIndent()
    }

    /** reCAPTCHA のトークンが入っているか（チェックが完了したか）を true / false で返す。 */
    val TOKEN_READY = """
        (function() {
          var t = document.getElementById('g-recaptcha-response');
          return !!(t && t.value);
        })();
    """.trimIndent()

    /**
     * サイトの「送信」ボタンを押す。`form.submit()` はサイトの submit ハンドラを飛ばすので使わない。
     * ウィジェットの textarea のトークンを、サイトが用意した hidden 欄にも写してから送る。
     */
    val SUBMIT = """
        (function() {
          var f = document.forms['codeform'];
          if (!f) return '$RESULT_NO_FORM';
          var t = document.getElementById('g-recaptcha-response');
          if (!t || !t.value) return '$RESULT_NO_TOKEN';
          var h = document.getElementById('g-recaptcha-response-input');
          if (h) h.value = t.value;
          var b = f.querySelector('input[type=submit], button[type=submit]');
          if (!b) return '$RESULT_NO_FORM';
          b.click();
          return '$RESULT_OK';
        })();
    """.trimIndent()

    const val OUTER_HTML = "document.documentElement.outerHTML"

    /** `evaluateJavascript` の戻り値（JSON）を文字列にする。文字列以外（null など）は null。 */
    fun decodeString(result: String?): String? {
        if (result.isNullOrBlank()) return null
        val element = runCatching { Json.parseToJsonElement(result) }.getOrNull() ?: return null
        return (element as? JsonPrimitive)?.takeIf { it.isString }?.content
    }

    private fun quote(value: String): String = JsonPrimitive(value).toString()
}
