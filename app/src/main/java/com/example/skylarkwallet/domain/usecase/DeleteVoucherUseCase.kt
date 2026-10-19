package com.example.skylarkwallet.domain.usecase

import com.example.skylarkwallet.domain.repository.VoucherRepository
import javax.inject.Inject

class DeleteVoucherUseCase @Inject constructor(
    private val repository: VoucherRepository,
) {
    suspend operator fun invoke(id: String) = repository.delete(id)
}
