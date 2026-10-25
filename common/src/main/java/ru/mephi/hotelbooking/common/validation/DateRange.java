package ru.mephi.hotelbooking.common.validation;

import java.time.LocalDate;

public interface DateRange {

    LocalDate startDate();

    LocalDate endDate();
}
