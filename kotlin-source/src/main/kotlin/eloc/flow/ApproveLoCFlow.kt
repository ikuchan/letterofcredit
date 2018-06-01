package eloc.flow

import co.paralleluniverse.fibers.Suspendable
import eloc.contract.InvoiceContract
import eloc.contract.LetterOfCreditApplicationContract
import eloc.contract.LetterOfCreditContract
import eloc.state.*
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.seconds
import java.time.LocalDate

@InitiatingFlow
@StartableByRPC
class ApproveLoCFlow(val reference: StateRef, val invoiceReference: StateRef) : FlowLogic<SignedTransaction>() {
    companion object {
        object GENERATING_APPROVAL_TRANSACTION : ProgressTracker.Step("Generating LOC transaction.")
        object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction.")
        object FINALIZING : ProgressTracker.Step("Recording and distributing transaction.")

        fun tracker() = ProgressTracker(
                GENERATING_APPROVAL_TRANSACTION,
                SIGNING_TRANSACTION,
                FINALIZING
        )
    }

    override val progressTracker = tracker()

    @Suspendable
    override fun call(): SignedTransaction {
        val applicationTxState = serviceHub.loadState(reference)
        val application = applicationTxState.data as LetterOfCreditApplicationState

        // Step 1. Generate transaction
        progressTracker.currentStep = GENERATING_APPROVAL_TRANSACTION
        val applicationProps = application.props
        val LOCProps = LetterOfCreditProperties(applicationProps, LocalDate.now())

        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        val loc = LetterOfCreditState(status = LetterOfCreditStatus.ISSUED, props = LOCProps)

        val builder = TransactionBuilder(notary = notary)
        val appStateAndRef = StateAndRef(state = applicationTxState, ref = reference)

        val invoiceTxState = serviceHub.loadState(invoiceReference)
        val invoiceStateAndRef = StateAndRef(state = invoiceTxState, ref = invoiceReference)

        //Terminate the invoice If the loc is approved to prevent double spend
        builder.addInputState(invoiceStateAndRef)
        builder.addInputState(appStateAndRef)
        builder.addOutputState(application.copy(status = LetterOfCreditApplicationStatus.APPROVED), LetterOfCreditApplicationContract.CONTRACT_ID)
        builder.addOutputState(loc, LetterOfCreditContract.CONTRACT_ID)
        builder.addCommand(LetterOfCreditApplicationContract.Commands.Approve(), ourIdentity.owningKey)
        builder.addCommand(LetterOfCreditContract.Commands.Issue(), ourIdentity.owningKey)
        builder.addCommand(InvoiceContract.Commands.Extinguish(), ourIdentity.owningKey)

        // Step 2. Add timestamp
        progressTracker.currentStep = SIGNING_TRANSACTION
        val currentTime = serviceHub.clock.instant()
        builder.setTimeWindow(currentTime, 30.seconds)

        // Step 3. Verify transaction
        builder.verify(serviceHub)

        // Step 4. Sign transaction
        progressTracker.currentStep = FINALIZING
        val stx = serviceHub.signInitialTransaction(builder)

        // Step 5. Assuming no exceptions, we can now finalise the transaction.
        return subFlow(FinalityFlow(stx))
    }
}