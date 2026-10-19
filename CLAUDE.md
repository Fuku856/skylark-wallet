# Skylark Wallet 開発メモ

## ブランチ運用

- 作業ブランチの PR は、まず **`dev`** に向けて作成する（`main` に直接向けない）。
- `dev` に入った変更でベータ版をビルドし、確認できたら `dev` から `main` にマージする。
- 細かい単位で PR を分けてよい。

## ビルド・確認

```sh
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
```

- テストのメソッド名は ASCII にする（日本語名だと、ロケールによってはクラスファイルのパスでビルドが失敗する）。
