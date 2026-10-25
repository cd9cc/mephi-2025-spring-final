package ru.mephi.hotelbooking.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DateRangeValidatorTest {

    private static final LocalDate DAY = LocalDate.of(2026, 1, 10);

    private final DateRangeValidator validator = new DateRangeValidator();

    @Test
    void endAfterStartIsValid() {
        assertThat(validator.isValid(range(DAY, DAY.plusDays(1)), null)).isTrue();
    }

    @Test
    void endEqualToStartIsInvalid() {
        assertThat(validator.isValid(range(DAY, DAY), null)).isFalse();
    }

    @Test
    void endBeforeStartIsInvalid() {
        assertThat(validator.isValid(range(DAY, DAY.minusDays(1)), null)).isFalse();
    }

    @Test
    void missingDateIsLeftToNotNullConstraint() {
        assertThat(validator.isValid(range(null, DAY), null)).isTrue();
        assertThat(validator.isValid(range(DAY, null), null)).isTrue();
    }

    private static DateRange range(LocalDate start, LocalDate end) {
        return new DateRange() {
            @Override
            public LocalDate startDate() {
                return start;
            }

            @Override
            public LocalDate endDate() {
                return end;
            }
        };
    }
}
