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

## リリース

GitHub でリリースを手動で公開すると、`.github/workflows/release.yml` が次の処理を行います。

1. **APK のビルドと添付**: 「Set as a pre-release」にチェックしたリリース（ベータ）は `dev` の最新、正式版は `main` の最新から署名付き APK をビルドし、`skylark-wallet_<タグ>.apk` としてリリースに添付します。versionName はタグから先頭の `v` を除いた値（例 `v1.0.0-beta1` → `1.0.0-beta1`）、versionCode はワークフローの実行番号です。
2. **リリースノートの自動生成**: 前回のリリース（ベータは直前のリリース、正式版は直前の正式版）からの PR 一覧で What's Changed と Full Changelog を作ります。さらに Claude が利用者向けの要約（新機能／改善・修正）を作り、本文の先頭に付けます。

### 必要な Secret（Settings → Secrets and variables → Actions）

| Secret | 内容 |
|---|---|
| `KEYSTORE_BASE64` | 署名用 keystore を Base64 にしたもの（必須） |
| `KEYSTORE_PASSWORD` | keystore のパスワード（必須） |
| `KEY_ALIAS` | 鍵のエイリアス（必須） |
| `KEY_PASSWORD` | 鍵のパスワード（必須） |
| `CLAUDE_CODE_OAUTH_TOKEN` | 要約に使う Claude のトークン（任意。`claude setup-token` で発行）。未設定なら要約なしで What's Changed だけを出します |

署名用の4つの Secret は、`scripts/setup-release-secrets.ps1` で keystore の用意から登録までまとめて行えます（Windows PowerShell 5.1 / PowerShell 7）。

```powershell
# 前提: GitHub CLI でログイン済み（gh auth login）、JDK または Android Studio（keytool）が入っている
powershell -ExecutionPolicy Bypass -File scripts\setup-release-secrets.ps1
```

- keystore が無ければ、このアプリ専用のもの（PKCS12、RSA 4096）を `%USERPROFILE%\keystores\skylark-wallet-release.jks` に作ります。パスワードは実行中に入力します（半角の英数字と記号のみ、6文字以上）。
- 作成済みの keystore を使うときは `-KeystorePath D:\keys\skylark.jks -KeyAlias skylark` のように指定します。
- 登録済みの Secret があると上書きしてよいか確認します（`-Force` で確認を省略）。`-WhatIf` を付けると、keystore の確認・作成だけを行い、登録はしません。
- 値はコマンドラインや一時ファイルに残さず、`gh secret set` の標準入力に直接渡します。

keystore のファイルとパスワードは、必ず安全な場所にバックアップしてください。同じ鍵で署名し続けないと、アプリをアップデートできなくなります。

アプリの変更点ではない PR（紹介ページなど）は、`site` ラベルを付けるとリリースノートから除外されます（`.github/release.yml`）。

## セキュリティ

- 9桁・13桁・結果 HTML は平文で保存しません（Tink AEAD、associated data に券の ID）。
- バーコード画面・公式画面は `FLAG_SECURE` でスクリーンショットと「最近使ったアプリ」への映り込みを防ぎます。
- `allowBackup="false"`、バックアップ・端末間転送ルールで DB と Keyset を除外しています。
- 通信は HTTPS のみ（`network_security_config` で cleartext 禁止）。通信ログはデバッグビルドのみで、本文と Cookie は出しません。
- 起動時の生体認証ロック（設定で ON/OFF）。
