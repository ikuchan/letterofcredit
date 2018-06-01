package eloc.state

import eloc.LetterOfCreditDataStructures.Company
import eloc.LetterOfCreditDataStructures.PricedGood
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import java.time.LocalDate

data class InvoiceState(
        val owner: Party,
        val buyer: Party,
        val assigned: Boolean,
        val isConsumeable: Boolean,
        val props: InvoiceProperties) : LinearState {
    override val linearId = UniqueIdentifier()
    override val participants = listOf(owner, buyer)
}

@CordaSerializable
data class InvoiceProperties(
        val invoiceID: String,
        val seller: Company,
        val buyer: Company,
        val invoiceDate: LocalDate,
        val attachmentHash: SecureHash,
        val term: Long,
        val goods: List<PricedGood>) {
    init {
        require(term > 0) { "the term must be a positive number" }
        require(goods.isNotEmpty()) { "there must be goods assigned to the invoice"}
    }

    // add term to invoice date to determine the payDate
    val payDate = invoiceDate.plusDays(term)
}