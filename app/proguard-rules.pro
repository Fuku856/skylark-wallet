# Tink はリフレクションを使わないが、protobuf-lite の生成コードを保持する
-keep class com.google.crypto.tink.proto.** { *; }
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**

# Jsoup の re2j はオプション依存
-dontwarn com.google.re2j.**

# OkHttp
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
