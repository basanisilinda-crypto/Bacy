package za.co.bacyflex.core

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class FinanceScheduleTest {
    private val first = LocalDate.of(2026, 12, 5)
    private val cycle = FinanceSchedule.Cadence.THIRTY_DAY

    @Test fun lastInstalmentIsSmallerAndExact() {
        val s = FinanceSchedule.initialSchedule(170_000, 0, 30_000, first, cycle)
        assertEquals(6, s.size)
        assertEquals(20_000L, s.last().amountCents)
        assertEquals(170_000L, s.sumOf { it.amountCents })
    }
    @Test fun partialPaymentsDoNotMoveTheDueDate() {
        val s = FinanceSchedule.remainingSchedule(170_000, 155_000, 30_000, first, cycle)
        assertEquals(first, s.first().dueDate)
        assertEquals(15_000L, s.first().amountCents)
        assertEquals(155_000L, s.sumOf { it.amountCents })
    }
    @Test fun multipleCompletedCyclesAdvanceCorrectly() {
        assertEquals(LocalDate.of(2027, 2, 3),
            FinanceSchedule.nextDueDate(170_000, 110_000, 30_000, first, cycle))
    }
    @Test fun settlementProducesNoFurtherInstalments() {
        assertTrue(FinanceSchedule.remainingSchedule(170_000, 0, 30_000, first, cycle).isEmpty())
        assertNull(FinanceSchedule.nextDueDate(170_000, 0, 30_000, first, cycle))
    }
    @Test fun calendarMonthIsNotThirtyDays() {
        val s = FinanceSchedule.initialSchedule(9_000, 0, 3_000,
            LocalDate.of(2027, 1, 31), FinanceSchedule.Cadence.CALENDAR_MONTH)
        assertEquals(LocalDate.of(2027, 2, 28), s[1].dueDate)
        assertEquals(LocalDate.of(2027, 3, 31), s[2].dueDate)
    }
    @Test(expected = IllegalArgumentException::class)
    fun invalidFinanceTermsAreRejected() {
        FinanceSchedule.initialSchedule(0, 0, 300, first, cycle)
    }
}
