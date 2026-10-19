package app.skylark.wallet.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VoucherDao {

    @Query("SELECT * FROM vouchers ORDER BY createdAt")
    fun observeAll(): Flow<List<VoucherEntity>>

    @Query("SELECT * FROM vouchers ORDER BY createdAt")
    suspend fun getAll(): List<VoucherEntity>

    @Query("SELECT * FROM vouchers WHERE id = :id")
    suspend fun getById(id: String): VoucherEntity?

    @Insert
    suspend fun insert(entity: VoucherEntity)

    @Update
    suspend fun update(entity: VoucherEntity)

    @Query("UPDATE vouchers SET memo = :memo WHERE id = :id")
    suspend fun updateMemo(id: String, memo: String)

    @Query("UPDATE vouchers SET lastError = :error WHERE id = :id")
    suspend fun updateError(id: String, error: String?)

    @Query("DELETE FROM vouchers WHERE id = :id")
    suspend fun delete(id: String)
}
