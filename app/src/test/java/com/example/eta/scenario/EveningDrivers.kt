package com.example.eta.scenario

import com.example.eta.data.local.BlockWithItem
import com.example.eta.data.repository.SignResult
import com.example.eta.domain.model.Contract
import com.example.eta.domain.model.ContractEffort
import com.example.eta.domain.model.Item
import com.example.eta.domain.reevaluation.DailySettlement
import com.example.eta.ui.reevaluation.MakeUpOffer
import com.example.eta.ui.reevaluation.ReevaluationViewModel
import com.example.eta.ui.root.reevaluationViewModelFactory
import kotlinx.datetime.LocalDate

/**
 * The evening's screen for one day, as the screens drive it: discarding a block,
 * answering a contract, taking up a make-up, and settling the day.
 *
 * Every action is the ViewModel's own; the desk only waits for the state the
 * screen would show before pressing the button.
 */
class EveningDesk internal constructor(
    private val scenario: Scenario,
    private val vm: ReevaluationViewModel,
) {
    /** The block of [item] on this evening, once the screen has loaded it. */
    suspend fun blockOf(item: Item): BlockWithItem =
        scenario.awaitState(vm.uiState, "\"${item.name}\" on the evening of ${vm.date}") { state ->
            state.blocks.any { it.item.id == item.id }
        }.blocks.first { it.item.id == item.id }

    /** "Verwerfen": drops the block of [item] and waits until the screen shows it dropped. */
    suspend fun discard(item: Item) {
        vm.discard(blockOf(item))
        scenario.awaitState(vm.uiState, "\"${item.name}\" dropped") { state ->
            state.blocks.any { it.item.id == item.id && it.block.isDiscarded }
        }
    }

    /** "Nachholen": takes up the offer for [item] and returns the make-up card the screen shows. */
    suspend fun makeUp(item: Item): Item {
        val offer = scenario.awaitState(vm.uiState, "a make-up offer for \"${item.name}\"") { state ->
            state.makeUpOffers.any { it.entry.item.id == item.id }
        }.makeUpOffers.first { it.entry.item.id == item.id }
        vm.makeUp(offer)
        return scenario.awaitState(vm.uiState, "the make-up card of \"${item.name}\"") { state ->
            state.makeUpOffers.any { it.entry.item.id == item.id && it.createdItem != null }
        }.makeUpOffers.first { it.entry.item.id == item.id }.createdItem
            ?: error("the make-up card of \"${item.name}\" is missing")
    }

    /** The make-up offers the screen lists, once loaded. */
    suspend fun makeUpOffers(): List<MakeUpOffer> =
        scenario.awaitState(vm.uiState, "the evening of ${vm.date} loaded") { !it.loading }.makeUpOffers

    /** The contracts the evening asks about, once loaded. */
    suspend fun contractsAsked(): List<Contract> =
        scenario.awaitState(vm.uiState, "the evening of ${vm.date} loaded") { !it.loading }.contracts

    /** Answers a contract's question: kept, or broken and, for a breach, whether it is served on. */
    suspend fun answer(contract: Contract, kept: Boolean, keepServing: Boolean = false) {
        scenario.awaitState(vm.uiState, "contract \"${contract.title}\" asked") { state ->
            state.contracts.any { it.id == contract.id }
        }
        vm.setVerdict(contract, kept)
        if (!kept) vm.setContinuation(contract, keepServing)
    }

    /**
     * Settles the day with the answers given. Returns the settlement, or null when
     * the day was already booked before this call.
     */
    suspend fun settle(): DailySettlement? {
        scenario.awaitState(vm.uiState, "the evening of ${vm.date} loaded") { !it.loading }
        val state = vm.uiState.value
        if (state.settled != null || state.alreadySettled) return null
        vm.settle {}
        return scenario.awaitState(vm.uiState, "the settlement of ${vm.date}") {
            it.settled != null || it.alreadySettled
        }.settled
    }
}

/** The evening of [date] (today by default), for the duration of [body]. */
suspend fun <T> Scenario.evening(date: LocalDate = clock.today, body: suspend EveningDesk.() -> T): T {
    val vm = viewModel<ReevaluationViewModel>(
        reevaluationViewModelFactory(container, date),
        key = "evening-$date",
    )
    return subscribed(vm.uiState) { EveningDesk(this@evening, vm).body() }
}

/** Signs a contract in [slot] as the contracts screen does; returns the refusal instead of failing. */
suspend fun Scenario.trySignContract(slot: Int, title: String, endsOn: LocalDate): SignResult =
    container.contractRepository.sign(
        slot = slot,
        title = title,
        conditions = "Kein Zucker am Abend",
        breachDefinition = "Zucker gegessen",
        effort = ContractEffort.MITTEL,
        signature = "Lena",
        endsOn = endsOn,
    )

/** Signs a contract that must be accepted. */
suspend fun Scenario.signContract(slot: Int, title: String, endsOn: LocalDate): Contract =
    when (val result = trySignContract(slot, title, endsOn)) {
        is SignResult.Signed -> result.contract
        is SignResult.Refused -> error("the contract was refused: ${result.reason}")
    }
