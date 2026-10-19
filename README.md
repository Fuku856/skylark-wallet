# Skylark Wallet

すかいらーくご優待券の **9桁のご優待番号** を登録すると、残高・有効期限・13桁のバーコード番号を取得して、端末内に暗号化して保存する Android アプリです。

- 入力するのは、9桁のコード（登録時のみ）と画像認証（CAPTCHA）の数字だけです。CAPTCHA は人が読んで入力します（OCR などで自動解読はしません）。
- 外部サーバー・有料 API は使いません。通信・解析・暗号化保存はすべて端末内で行います。
- サイトへのアクセスは、照会の操作をしたときだけ行います。バックグラウンドでの定期照会はしません（期限通知は保存済みの期限だけを見ます）。
- UI は Material 3 Expressive（`MaterialExpressiveTheme` / `MotionScheme.expressive()` / Floating Toolbar / Loading Indicator）です。

## 構成

```text
app/src/main/java/com/example/skylarkwallet/
 ├── data/
 │    ├── local/crypto/     # Tink + Android Keystore（AES-256-GCM, AAD = 券の ID）
 │    ├── local/db/         # Room（VoucherEntity / VoucherDao）
 │    ├── local/prefs/      # DataStore（規約同意・ロック・輝度・通知）
 │    ├── remote/           # SkylarkSession / SkylarkScraper / InputForm / ResultParser
 │    └── repository/       # VoucherRepositoryImpl / SkylarkLookupRepository
 ├── domain/                # Voucher・CodeNormalizer・Ean13、Repository、UseCase
 ├── notification/          # 期限通知（WorkManager）
 └── ui/                    # home / captcha / barcode / webview / settings / onboarding / lock / theme
```

## サイトの仕様（Phase 1 で確認した内容）

`GET https://www.skylarkyutaiken.com/input` の実 HTML（`app/src/test/resources/skylark/input.html`）から確認しました。

| 項目 | 内容 |
|---|---|
| フォーム | `POST /input`（`name="codeform"`） |
| ご優待番号 | `code[0]` / `code[1]` / `code[2]`（3文字ずつ） |
| 画像の番号 | `captchakey` |
| 規約同意 | `agree=1`（同意済みのときだけ送る） |
| hidden / CSRF | なし（あれば自動で送る） |
| CAPTCHA 画像 | `/input/captcha?<乱数>`。`ci_session` Cookie に紐づく。「画像交換」はクエリを変えて再取得するだけ |

結果画面・エラー画面は、POST に有効なコードと CAPTCHA の手入力が必要なため未取得です。`/css/public.css` の `body.inp` 向けスタイルから次の構造を推定し、`ResultParser` の先頭に定数としてまとめています。セレクタで取れない場合は、本文テキストの正規表現で読み取ります。

| 項目 | 推定セレクタ |
|---|---|
| 残高 | `hgroup.ticket p.val` |
| 残高確認日時 | `hgroup.ticket p.date` |
| バーコード番号 / 画像 | `.bcode span` / `.bcode img` |
| 有効期限 | `.limit` |
| エラー | `p.err`（「画像」を含めば CAPTCHA 不一致、それ以外は無効なコード） |
| メンテナンス | `#gh.mainte` |

`result_sample.html` などは合成データです。実際の結果画面を取得できたら、番号をマスクして差し替え、セレクタを確定してください。

> サイトの Q&A には「画面上のバーコードを撮影したものを掲示しての利用はできません」とあります。アプリで描画したバーコードがレジで受け付けられるかは、実店舗で確認してください。受け付けられない場合は「公式画面で表示 → 最新を表示」を使ってください。

## ビルド

- JDK 17 以上、Android SDK（compileSdk 37 / targetSdk 35 / minSdk 28）
- AGP 9.4 / Gradle 9.8 / Kotlin 2.4 / Material3 1.5.0-beta01

```sh
./gradlew :app:testDebugUnitTest   # 単体テスト
./gradlew :app:assembleDebug       # デバッグ APK
./gradlew :app:lintDebug
```

## セキュリティ

- 9桁・13桁・結果 HTML は平文で保存しません（Tink AEAD、associated data に券の ID）。
- バーコード画面・公式画面は `FLAG_SECURE` でスクリーンショットと「最近使ったアプリ」への映り込みを防ぎます。
- `allowBackup="false"`、バックアップ・端末間転送ルールで DB と Keyset を除外しています。
- 通信は HTTPS のみ（`network_security_config` で cleartext 禁止）。通信ログはデバッグビルドのみで、本文と Cookie は出しません。
- 起動時の生体認証ロック（設定で ON/OFF）。
