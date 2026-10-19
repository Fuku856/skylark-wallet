# Skylark Wallet 開発メモ

## ブランチ運用

- 作業ブランチの PR は、まず **`dev`** に向けて作成する（`main` に直接向けない）。
- `dev` に入った変更でベータ版をビルドし、確認できたら `dev` から `main` にマージする。
- 細かい単位で PR を分けてよい。

## リリース

- GitHub でリリースを公開すると `.github/workflows/release.yml` が動く。プレリリースは `dev`、正式版は `main` の最新から署名付き APK をビルドしてリリースに添付し、リリースノート（Claude の要約 + What's Changed）で本文を上書きする。
- PR タイトルはそのまま What's Changed に載るので、`feat:` / `fix:` などの接頭辞を付け、内容が分かる日本語にする。

## ビルド・確認

```sh
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
```

- テストのメソッド名は ASCII にする（日本語名だと、ロケールによってはクラスファイルのパスでビルドが失敗する）。
