package ru.mephi.hotelbooking.booking.dto.booking;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BookingCreateRequestValidationTest {

    private static final LocalDate TOMORROW = LocalDate.now().plusDays(1);

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void requestWithRoomIdIsValid() {
        var request = new BookingCreateRequest("req-1", 1L, false, TOMORROW, TOMORROW.plusDays(2));

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void autoSelectRequestWithoutRoomIdIsValid() {
        var request = new BookingCreateRequest("req-1", null, true, TOMORROW, TOMORROW.plusDays(2));

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void manualSelectionWithoutRoomIdIsRejected() {
        var request = new BookingCreateRequest("req-1", null, false, TOMORROW, TOMORROW.plusDays(2));

        assertThat(messages(validator.validate(request)))
                .containsExactly("roomId is required when autoSelect is false");
    }

    @Test
    void endDateNotAfterStartDateIsRejected() {
        var request = new BookingCreateRequest("req-1", 1L, false, TOMORROW, TOMORROW);

        assertThat(messages(validator.validate(request)))
                .containsExactly("endDate must be after startDate");
    }

    @Test
    void startDateInPastIsRejected() {
        var yesterday = LocalDate.now().minusDays(1);
        var request = new BookingCreateRequest("req-1", 1L, false, yesterday, TOMORROW);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("startDate");
    }

    @Test
    void missingRequestIdIsRejected() {
        var request = new BookingCreateRequest(" ", 1L, false, TOMORROW, TOMORROW.plusDays(1));

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("requestId");
    }

    private static Set<String> messages(Set<ConstraintViolation<BookingCreateRequest>> violations) {
        return violations.stream().map(ConstraintViolation::getMessage).collect(java.util.stream.Collectors.toSet());
    }
}
