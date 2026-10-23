package app.skylark.wallet.domain.usecase

import app.skylark.wallet.domain.repository.VoucherRepository
import javax.inject.Inject

class DeleteVoucherUseCase @Inject constructor(
    private val repository: VoucherRepository,
) {
    suspend operator fun invoke(id: String) = repository.delete(id)
}
