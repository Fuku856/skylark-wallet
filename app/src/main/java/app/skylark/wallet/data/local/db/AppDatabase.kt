package app.skylark.wallet.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [VoucherEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun voucherDao(): VoucherDao
}
