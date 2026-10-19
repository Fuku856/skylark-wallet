package com.example.skylarkwallet.ui.common

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Format {
    private val zone: ZoneId = ZoneId.of("Asia/Tokyo")
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.JAPAN)
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm", Locale.JAPAN)

    fun yen(amount: Int): String = "%,d".format(Locale.JAPAN, amount)

    fun date(epochMillis: Long): String = dateFormatter.format(Instant.ofEpochMilli(epochMillis).atZone(zone))

    fun dateTime(epochMillis: Long): String = dateTimeFormatter.format(Instant.ofEpochMilli(epochMillis).atZone(zone))
}
