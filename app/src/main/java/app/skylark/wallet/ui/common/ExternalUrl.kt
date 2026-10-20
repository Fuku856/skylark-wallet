package app.skylark.wallet.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/** URL を外部ブラウザなどで開く。開けるアプリが無ければ何もしない。 */
fun openExternalUrl(context: Context, uri: Uri) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (_: ActivityNotFoundException) {
        // 開けるアプリが無ければ何もしない
    }
}
