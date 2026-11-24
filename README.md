# Skylark Wallet

すかいらーくご優待券の **9桁のご優待番号** を登録すると、残高・有効期限・13桁のバーコード番号を取得して、端末内に暗号化して保存する Android アプリです。お店ではアプリが描いたバーコードを提示できます。

- 外部サーバー・有料 API は使いません。通信・解析・暗号化保存はすべて端末内で行います（照会時の reCAPTCHA はサイトが読み込む Google のものです）。
- サイト（`https://www.skylarkyutaiken.com/`）へのアクセスは、照会の操作をしたときだけ行います。バックグラウンドでの定期照会はしません。
- UI は Jetpack Compose + Material 3 Expressive（`MaterialExpressiveTheme` / `MotionScheme.expressive()` / Floating Toolbar / Loading Indicator）です。

## 主な機能

### ホーム

- 総残高（ロール表示）と最終更新日時を表示します。
- 券は「未使用（期限の近い順）」と「使用済み（残高0円、登録の新しい順）」に分けて並べます。
- バッジ: 期限まで30日以内は「あと N 日」、期限切れ、最終照会から7日以上は「要更新」、照会エラー、使用済み。
- 券ごとに、照会・バーコード表示・メモの編集（40文字まで）・削除ができます。ご優待番号の表示には生体認証または画面ロックでの本人確認が必要です。
- 端末の暗号鍵が無効になって読み込めなくなった券は、まとめて削除できるよう案内します。

### 登録と照会

- 「追加」から9桁の英数字を入力します（ハイフン・空白は自動で除去、クリップボードから貼り付け可）。登録済みの番号は弾きます。
- 「更新」は、使用済み・期限切れを除いた券を期限の近い順に1枚ずつ照会します（進み具合を `n / N` で表示、スキップ・中止可）。
- 照会はサイトの入力画面をアプリ内の WebView で開いて行います。9桁の入力・規約への同意・送信・結果の保存・次の券への移動はアプリが行い、**人がするのは reCAPTCHA の「私はロボットではありません」のチェックだけ**です（画像の選択を求められたときはそれも人が解きます）。チェックが済むと自動で送信します。
- 一括更新では、送信してから次の券の入力画面を開くまで 2 秒あけます。
- 認証が通らなかったときは自動ではやり直さず、入力画面を開き直してもう一度チェックしてもらいます。
- 結果の画面ごとに、認証の失敗・無効なご優待番号・サイトのメンテナンス・通信エラー（再試行可）・想定外の画面を区別して知らせます。
- サイトの利用規約に同意するまでは照会できません（初回起動時に確認し、設定から取り消しもできます）。

### バーコード

- 取得した13桁の番号から EAN-13 のバーコードをアプリ内で描画します。複数の券は横に送って切り替えられます。
- 表示中は画面を最大輝度にします（設定で OFF 可、バーコードのタップで切り替え）。
- 「公式画面で表示」から、前回照会したときの保存済み画面、またはその場で照会した最新の公式画面を WebView で表示できます。

> サイトの Q&A には「画面上のバーコードを撮影したものを掲示しての利用はできません」とあります。アプリで描画したバーコードがレジで受け付けられるかは、実店舗で確認してください。受け付けられない場合は「公式画面で表示 → 最新を表示」を使ってください。

### 設定

| 項目 | 内容 |
|---|---|
| 起動時にロック | 生体認証または画面ロックで解除する（有効化時にも認証を確認） |
| バーコード表示時に明るさを最大にする | 既定 ON |
| 有効期限の通知 | 期限の30日前と7日前に通知する。WorkManager で1日1回、保存済みの期限だけを見る（サイトにはアクセスしない） |
| テーマ | システム既定 / ライト / ダーク |
| サイトの利用規約 | 同意状態の確認・同意・取り消し |
| バックアップ | 「バックアップを作成」「バックアップから復元」（機種変更用、下記） |

### 裏設定（詳細設定・試験的機能）

ホームの設定アイコンを**長押し**すると開きます。既定は OFF です。

- **デバッグログを出力**: アプリ全体の動作を logcat（タグ `SkylarkWallet`）に出力します。出すのは起動・画面遷移・設定・券の操作（ID のみ）・バックアップと通知の結果・認証の結果・未処理の例外・通信のパスと状態コードと Cookie 名・照会の段階（入力画面の読み込み・チェック完了・送信・結果）・サイトのエラー文言・結果画面の各項目がセレクタと本文のどちらで取れたかです。ご優待番号・バーコード番号・残高・Cookie の値・reCAPTCHA のトークン・メモ・パスワードは出しません。

以前あった「CAPTCHA を自動で入力」（端末内の OCR で画像の数字を読む機能）は、サイトが reCAPTCHA に移行したため廃止しました。経緯は [docs/auto-captcha-investigation.md](docs/auto-captcha-investigation.md) に残しています。

## 構成

```text
app/src/main/
 └── java/app/skylark/wallet/
      ├── data/
      │    ├── backup/           # 端末移行用バックアップ（BackupFormat / BackupPayload / BackupPassword / BackupManager）
      │    ├── local/crypto/     # Tink + Android Keystore（AES-256-GCM, AAD = 券の ID）
      │    ├── local/db/         # Room（VoucherEntity / VoucherDao）
      │    ├── local/prefs/      # DataStore（規約同意・ロック・輝度・通知・テーマ・裏設定）
      │    ├── remote/           # ResultParser / BarcodeImageInliner / LookupLogInterceptor
      │    └── repository/       # VoucherRepositoryImpl
      ├── di/                    # Hilt モジュール（OkHttp・Room・DataStore など）
      ├── diagnostics/           # DebugLog（裏設定のデバッグログ）
      ├── domain/                # Voucher・VoucherSections・CodeNormalizer・MemoNormalizer・Ean13、Repository、UseCase
      ├── notification/          # 期限通知（WorkManager）
      └── ui/                    # home / lookup（照会の WebView）/ barcode / webview / settings / onboarding / lock / common / theme
```

主なライブラリ: Hilt / Room / DataStore / WorkManager / Biometric / OkHttp / jsoup / ZXing / Tink / kotlinx.serialization

## サイトの仕様（2026-11-24 に再確認）

`GET https://www.skylarkyutaiken.com/input` の実 HTML（`app/src/test/resources/skylark/input.html`）から確認しました。照会はこの画面を WebView で開き、`ui/lookup/LookupScript.kt` の JavaScript で番号の入力と送信を行います。

| 項目 | 内容 |
|---|---|
| フォーム | `POST /input`（`name="codeform"`）。送信はサイトの「送信」ボタン（`input[type=submit]`）を押す |
| ご優待番号 | `code[0]` / `code[1]` / `code[2]`（3文字ずつ） |
| 認証 | reCAPTCHA Enterprise のチェックボックス（`div#charge-recaptcha.g-recaptcha`、`data-action="INPUT"`）。トークンはウィジェットの textarea `#g-recaptcha-response` に入り、サイトの hidden `#g-recaptcha-response-input`（`name="g-recaptcha-response"`）にも写して送る |
| 規約同意 | `agree=1`（アプリで同意済みのときだけチェックを入れる） |
| hidden / CSRF | `csrf_test_name`（`csrf_cookie_name` Cookie と対応）。WebView がそのまま送る |
| メンテナンス | 実際のメンテナンス画面（`maintenance_actual.html`）で確認。`#gh.mainte`、タイトルや本文の「メンテナンス」「Site Maintenance」で検知する |

結果画面・エラー画面はまだ実物を保存していません。`/css/public.css` の `body.inp` 向けスタイルから次の構造を推定し、`ResultParser` の先頭に定数としてまとめています。セレクタで取れない場合は、本文テキストの正規表現で読み取ります。

| 項目 | 推定セレクタ |
|---|---|
| 残高 | `hgroup.ticket p.val` |
| 残高確認日時 | `hgroup.ticket p.date` |
| バーコード番号 / 画像 | `.bcode span` / `.bcode img` |
| 有効期限 | `.limit` |
| エラー | `p.err`（番号のエラーなら無効なコード、それ以外は認証の失敗として扱う） |

`result_sample.html` などは合成データです。デバッグログの `result balance=… barcode=…` で、実際の結果画面の各項目がセレクタ（`selector`）と本文の正規表現（`text`）のどちらで取れたかを確認できます。実際の結果画面を取得できたら、番号をマスクして差し替え、セレクタを確定してください。

## ビルド

- JDK 17 以上、Android SDK（compileSdk 37 / targetSdk 35 / minSdk 28）
- AGP 9.4 / Gradle 9.8 / Kotlin 2.4 / Compose BOM 2026.09 / Material3 1.5.0-beta01

```sh
./gradlew :app:testDebugUnitTest   # 単体テスト
./gradlew :app:lintDebug
./gradlew :app:assembleDebug       # デバッグ APK
```

- テストのメソッド名は ASCII にしてください（日本語名だと、ロケールによってはクラスファイルのパスでビルドが失敗します）。
- リリースビルドは R8 で縮小します。リフレクションで使うクラスは `app/proguard-rules.pro` で保持しています。
- ネイティブライブラリを含む依存を追加・更新したら、lint の `Aligned16KB` 警告が出ていないこと（16KB ページの端末で読み込めること）を確認してください。

## 開発の流れ

- 作業ブランチの PR は、まず **`dev`** に向けて作ります（`main` に直接向けない）。
- `dev` に入った変更でベータ版をビルドし、確認できたら `dev` から `main` にマージします。
- PR タイトルはそのままリリースノートの What's Changed に載るので、`feat:` / `fix:` などの接頭辞を付け、内容が分かる日本語にします。

## リリース

GitHub でリリースを手動で公開すると、`.github/workflows/release.yml` が次の処理を行います。

1. **APK のビルドと添付**: リリースのタグが指すコミットから署名付き APK をビルドし、`skylark-wallet_<タグ>.apk` としてリリースに添付します。versionName はタグから先頭の `v` を除いた値（例 `v1.0.0-beta1` → `1.0.0-beta1`）、versionCode はワークフローの実行番号です。タグは、「Set as a pre-release」にチェックしたリリース（ベータ）なら `dev`、正式版なら `main` に入っているコミットに打ってください（リリース作成画面の Target をそのブランチにする）。違うブランチのコミットだとビルドを中止します。
2. **リリースノートの自動生成**: 前回のリリース（ベータは直前のリリース、正式版は直前の正式版）からの PR 一覧で What's Changed と Full Changelog を作ります。さらに Claude が利用者向けの要約（新機能／改善・修正）を作り、本文の先頭に付けます。

アプリの変更点ではない PR（紹介ページなど）は、`site` ラベルを付けるとリリースノートから除外されます（`.github/release.yml`）。

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

## セキュリティ

- 9桁・13桁・結果 HTML は平文で保存しません（Tink AEAD、associated data に券の ID）。
- バーコード画面・公式画面・ご優待番号の表示・バックアップのパスワード画面は `FLAG_SECURE` でスクリーンショットと「最近使ったアプリ」への映り込みを防ぎます。
- `allowBackup="false"`、バックアップ・端末間転送ルールで DB と Keyset を除外しています。機種変更には設定の「バックアップを作成」「バックアップから復元」を使います（下記）。
- 通信は HTTPS のみ（`network_security_config` で cleartext 禁止）。OkHttp の通信ログはデバッグビルドのみで、本文と Cookie は出しません。
- WebView はファイル・コンテンツへのアクセス、新しいウィンドウ、位置情報、フォームの保存を無効にしています。DOM ストレージは照会の WebView だけで有効にし（reCAPTCHA に必要なため）、照会が終わったら Cookie と DOM ストレージを消します。
- 照会の WebView はサイト（`https://www.skylarkyutaiken.com`）以外へ画面遷移させず、JavaScript の注入もサイトのページでだけ行います。JS ブリッジ（`addJavascriptInterface`）は使いません。User-Agent は変えません。
- 照会の画面も `FLAG_SECURE` でスクリーンショットを防ぎます。
- 起動時の生体認証ロック（設定で ON/OFF）。

### 端末移行用のバックアップ

Keystore の鍵は端末の外に持ち出せないため、バックアップでは券を復号し、移行用の鍵で暗号化し直したファイル（`skylark-wallet-backup-YYYYMMDD.skwb`）を書き出します。保存先・読み込み元はファイル選択画面で選ぶので、ストレージの権限は使いません。

| 項目 | 内容 |
|---|---|
| パスワード | アプリが生成する Crockford Base32 の20文字（約100bit、`XXXX-XXXX-XXXX-XXXX-XXXX`）。書き出し後に1度だけ表示し、ファイルには含めない |
| 鍵導出 | PBKDF2-HMAC-SHA256（20万回、salt 16バイト） |
| 暗号 | AES-256-GCM（nonce 12バイト）。ヘッダー（magic `SKWB`・バージョン・反復回数・salt・nonce）を associated data にして改ざんを検知 |
| 中身 | gzip した JSON（9桁・13桁・結果 HTML・残高・期限・メモなど）。券の ID は移行先で振り直す |
| 読み込み | 同じ9桁が登録済みの券は飛ばして追加する。ファイルサイズ・反復回数・展開後のサイズに上限を設け、細工したファイルを拒否する |

- 書き出しの前には、アプリのロック設定に関わらず毎回、生体認証または画面ロックでの本人確認を求めます（画面ロックが無い端末では作成できません）。
- パスワードの表示・入力画面は `FLAG_SECURE` でスクリーンショットを禁止します（写真がクラウドに同期され、ファイルと同じ場所に置かれるのを避けるため）。
- ファイルとパスワードの両方が他人に渡ると券を使われるおそれがあるので、別々に保管し、移行後は削除するよう画面で案内します。

## ライセンス

[MIT License](LICENSE)
