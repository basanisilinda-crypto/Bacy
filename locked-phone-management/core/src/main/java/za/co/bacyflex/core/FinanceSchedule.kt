package za.co.bacyflex.core

import java.time.LocalDate

/** Cents-only finance calculations; the server remains the source of truth. */
object FinanceSchedule {
    enum class Cadence { THIRTY_DAY, CALENDAR_MONTH }

    data class Instalment(val number: Int, val dueDate: LocalDate, val amountCents: Long)

    @JvmStatic
    fun initialSchedule(
        priceCents: Long, depositCents: Long, instalmentCents: Long,
        firstDueDate: LocalDate, cadence: Cadence
    ): List<Instalment> {
        require(priceCents > 0 && depositCents in 0 until priceCents)
        return remainingSchedule(priceCents - depositCents, priceCents - depositCents,
            instalmentCents, firstDueDate, cadence)
    }

    @JvmStatic
    fun remainingSchedule(
        financedCents: Long, balanceCents: Long, instalmentCents: Long,
        firstDueDate: LocalDate, cadence: Cadence
    ): List<Instalment> {
        require(financedCents > 0 && balanceCents in 0..financedCents)
        require(instalmentCents > 0)
        if (balanceCents == 0L) return emptyList()
        val paidCents = financedCents - balanceCents
        val completed = paidCents / instalmentCents
        val paidTowardsNext = paidCents % instalmentCents
        require(completed < 1200 && (balanceCents - 1) / instalmentCents < 1200) {
            "Repayment schedule exceeds the supported duration"
        }
        var remaining = balanceCents
        val result = mutableListOf<Instalment>()
        var instalmentIndex = completed
        while (remaining > 0L) {
            val amountDue = if (result.isEmpty()) instalmentCents - paidTowardsNext else instalmentCents
            val amount = minOf(amountDue, remaining)
            val dueDate = when (cadence) {
                Cadence.THIRTY_DAY -> firstDueDate.plusDays(Math.multiplyExact(30L, instalmentIndex))
                Cadence.CALENDAR_MONTH -> firstDueDate.plusMonths(instalmentIndex)
            }
            result.add(Instalment((instalmentIndex + 1).toInt(), dueDate, amount))
            remaining -= amount
            instalmentIndex++
            require(instalmentIndex <= 1201) { "Too many instalments" }
        }
        return result
    }

    @JvmStatic
    fun nextDueDate(
        financedCents: Long, balanceCents: Long, instalmentCents: Long,
        firstDueDate: LocalDate, cadence: Cadence
    ): LocalDate? = remainingSchedule(
        financedCents, balanceCents, instalmentCents, firstDueDate, cadence
    ).firstOrNull()?.dueDate
}
