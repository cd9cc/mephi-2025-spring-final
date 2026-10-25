package ru.mephi.hotelbooking.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class DateRangeValidator implements ConstraintValidator<ValidDateRange, DateRange> {

    @Override
    public boolean isValid(DateRange range, ConstraintValidatorContext context) {
        if (range == null || range.startDate() == null || range.endDate() == null) {
            return true;
        }
        return range.endDate().isAfter(range.startDate());
    }
}
