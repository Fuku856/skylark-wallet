package app.skylark.wallet.testutil

object Fixtures {
    fun read(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource("skylark/$name")) { "fixture not found: $name" }
            .readText(Charsets.UTF_8)
}
