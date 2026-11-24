# 自動CAPTCHA入力が失敗する件　調査まとめ・解決報告

> **廃止の注記（2026-11-24）**: サイトの入力画面が画像 CAPTCHA から reCAPTCHA Enterprise（チェックボックス型）に移行したため、自動CAPTCHA（ONNX / ddddocr）は撤去しました。現在の照会は、サイトの入力画面を WebView で開き、reCAPTCHA のチェックだけを人が行う方式です（README の「登録と照会」を参照）。この文書は経緯として残しています。

## 現象

- 裏設定の「CAPTCHAを自動で入力」を ON にしても自動認識が3回失敗し、手動入力シートになる。
- 手動入力に切り替えると残高照会は成功する。
- 一方、別パッケージの `app.ocr.skylark.wallet`（「Skylark Wallet」OCR版）では自動 CAPTCHA 認識自体は動作していた。

---

## 原因の全体像

調査の結果、原因は以下の **3点** に切り分けられた。

### 1. リリースビルドの R8 による ML Kit コンストラクタ削除（beta1〜beta3）

- `v0.2.0-beta1`〜`beta3` のリリース APK（`isMinifyEnabled = true`）では、R8 が ML Kit の `CommonComponentRegistrar`・`VisionCommonRegistrar`・`TextRegistrar` の引数なしコンストラクタ `<init>()` を削除していた。
- そのため `TextRecognition.getClient()` 呼び出し時に `NullPointerException`（初期化時は `NoSuchMethodException`）が発生し、OCR エンジンが一度も起動していなかった。
- `v0.2.0-beta4`（PR #32）にて `proguard-rules.pro` に保持ルールを追加したことで、エンジン起動前の例外は解消された。

### 2. `CaptchaPreprocessor.binarize` の実装バグと ML Kit の構造的不適合（beta4）

`v0.2.0-beta4` で ML Kit が起動するようになった後も、実サイトの CAPTCHA 画像に対して `result=NotDigits ocr=''`（空文字）で失敗していた原因は以下の通り。

1. **大津の二値化の閾値境界バグ（`< threshold` と `<= threshold` の誤り）**
   - `CaptchaPreprocessor.binarize` で `wB += histogram[t]`（`0..t`）から求めた `threshold` に対し、`grays[i] < threshold`（狭義の不等号）で黒画素判定していたため、2階調画像や最小輝度側にピークがある画像で全ピクセルが `false`（100% 純白の空白画像）になっていた。
2. **背景・文字の明暗極性の決め打ちによる白黒反転**
   - 実サイトの CAPTCHA は背景色・文字色がランダムであり、背景より文字の方が輝度が高い組み合わせ（例：背景 `gray=121`、文字 `gray=126`）では、背景全体が黒・文字が白に反転していた。
3. **単一輝度変換による色相差の消失とアンチエイリアス破壊**
   - RGB を単一輝度 $Y = (77R + 150G + 29B) \gg 8$ に潰してから2値に切り捨てるため、色相が異なっても輝度が近い色の組み合わせでコントラストが消滅し、さらにアンチエイリアスが失われて文字の細線断線やジャギーが発生していた。
4. **Google ML Kit Text Recognition と波状変形 CAPTCHA の不適合**
   - ML Kit (`com.google.mlkit:text-recognition`) は「水平に整列した一般文書・風景内のテキスト行」を矩形検出（Text Detector）してから行認識にかける2段構成である。
   - すかいらーくの CAPTCHA（PHP Securimage 系）は文字ごとに強いサイン波変形がかかっており、前段の Text Detector がテキスト行として検出できず空文字 `ocr=''` を返していた。

### 3. 「以前は動いていた」理由の解明（2つのアプリの切り分け）

- PR #26（`v0.2.0-beta1`）が作成された 2026-10-23 13:49 時点で実サイトはすでにメンテナンス中であり、`v0.2.0-beta2`（10/23 15:19）はメンテナンス画面の検知修正版であったため、`app.skylark.wallet` の ML Kit 実装がメンテナンス前に実サイトで動作した事実はなかった。
- メンテナンス前に自動認識が動作していたのは、端末に共存していた別パッケージ **`app.ocr.skylark.wallet`** であり、そちらは CAPTCHA 専用の CRNN + CTC モデル **`ddddocr.onnx`（ONNX Runtime）** を使用していた。
- `app.ocr.skylark.wallet` は現在も WebView（公式画面表示）では自動ログインに成功しており、一括更新（`SkylarkScraper`）だけがメンテナンス後に失敗するようになったのは、サイト側で追加された CSRF トークン（`csrf_test_name`）の未送信と、画像取得時の `Set-Cookie: ci_session` で `csrf_cookie_name` を上書き消去する `ClearableMemoryCookieJar` の不具合が原因であった（本リポジトリ `app.skylark.wallet` の通信層は CSRF・複数 Cookie ともに正しく対応済み）。

---

## 解決策（今回の修正内容）

1. **OCR エンジンを ML Kit から `ddddocr.onnx` + ONNX Runtime（`OnnxCaptchaSolver`）へ移行**
   - `com.google.mlkit:text-recognition` を削除し、`com.microsoft.onnxruntime:onnxruntime-android:1.20.0` と `ddddocr.onnx` / `charsets_old.json` に置き換え。
   - 二値化（`binarize`）を廃止し、高さ 64px にリサイズした白背景合成グレースケール連続階調（`0.0f`〜`1.0f`）をそのままモデルに入力。
   - 出力ロジットから数字（`'0'`〜`'9'`）と Blank（`0`）のみを比較する Restricted CTC Greedy Decode により、4〜5桁の数字を抽出。
   - モデルとセッションの読み込みは初回 `solve()` 時に `Dispatchers.Default` 上で遅延初期化（Lazy Load）し、アプリ起動速度への影響をゼロに抑えた。
2. **R8 保持ルールの更新**
   - `app/proguard-rules.pro` に `-keep class ai.onnxruntime.** { *; }` を設定し、リリースビルド（`isMinifyEnabled = true`）でも JNI リフレクション呼び出しが削除されないように対応。
