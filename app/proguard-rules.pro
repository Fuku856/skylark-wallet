# Tink はリフレクションを使わないが、protobuf-lite の生成コードを保持する
-keep class com.google.crypto.tink.proto.** { *; }
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**

# Jsoup の re2j はオプション依存
-dontwarn com.google.re2j.**

# ML Kit
# ComponentRegistrar の引数なしコンストラクタが R8 で削除されるのを防ぐ
# （ライブラリ側のルールにコンストラクタ指定がなく、リフレクション生成時に NoSuchMethodException / NullPointerException になるため）
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }

# OkHttp
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
