package com.example.skylarkwallet.domain.usecase

import com.example.skylarkwallet.domain.model.CodeNormalizer
import com.example.skylarkwallet.domain.model.MemoNormalizer
import com.example.skylarkwallet.domain.model.Voucher
import com.example.skylarkwallet.domain.repository.VoucherRepository
import javax.inject.Inject

class RegisterVoucherUseCase @Inject constructor(
    private val repository: VoucherRepository,
) {
    sealed interface Result {
        data class Registered(val voucher: Voucher) : Result
        data object InvalidFormat : Result
        data class Duplicate(val existing: Voucher) : Result
    }

    suspend operator fun invoke(rawCode: String, memo: String): Result {
        val code = CodeNormalizer.normalize(rawCode)
        if (!CodeNormalizer.isValid(code)) return Result.InvalidFormat
        repository.findByCode(code)?.let { return Result.Duplicate(it) }
        return Result.Registered(repository.add(code, MemoNormalizer.normalize(memo)))
    }
}
