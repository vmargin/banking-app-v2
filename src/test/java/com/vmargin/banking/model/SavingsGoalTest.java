package com.vmargin.banking.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SavingsGoalTest {
    @Test
    void capsExtremeValidOverTargetAmountsBeforeNarrowingToInteger() {
        SavingsGoal goal = new SavingsGoal(1, 1, "Large milestone", new BigDecimal("0.01"),
            new BigDecimal("214748.37"), LocalDateTime.now());

        assertEquals(100, goal.progressPercent());
    }
}
