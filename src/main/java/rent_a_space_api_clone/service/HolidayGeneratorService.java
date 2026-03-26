package rent_a_space_api_clone.service;

import org.springframework.stereotype.Service;
import rent_a_space_api_clone.entity.HolidayOverride;
import rent_a_space_api_clone.entity.HolidayRule;
import rent_a_space_api_clone.enums.HolidayFrequencyType;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.IsoFields;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;

@Service
public class HolidayGeneratorService {

    /**
     * Generates a definitive list of holiday dates for a specific year and month.
     */
    public List<LocalDate> getHolidaysForMonth(int year, int month, List<HolidayRule> rules, List<HolidayOverride> overrides) {
        List<LocalDate> holidays = new ArrayList<>();
        YearMonth yearMonth = YearMonth.of(year, month);
        int daysInMonth = yearMonth.lengthOfMonth();

        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate targetDate = yearMonth.atDay(day);

            if (isHoliday(targetDate, rules, overrides)) {
                holidays.add(targetDate);
            }
        }
        return holidays;
    }

    public boolean isHoliday(LocalDate date, List<HolidayRule> rules, List<HolidayOverride> overrides) {
        // Calculate the bitmask for the current date's day of the week
        // Java standard: Mon=1, Sun=7. Modulo 7 shifts Sun to 0. (1 << 0 = 1, 1 << 1 = 2)
        int currentDayBit = 1 << (date.getDayOfWeek().getValue() % 7);

        rules = rules == null ? new ArrayList<>() : rules;
        overrides = overrides == null ? new ArrayList<>(): overrides;

        // PRIORITY 1: Check Overrides (Seasonal ranges and specific exceptions)
        for (HolidayOverride override : overrides) {
            if (!date.isBefore(override.getStartsAt()) && !date.isAfter(override.getEndsAt())) {
                // Check if this override applies to this specific day of the week
                if ((override.getDayMask() & currentDayBit) > 0) {
                    return override.getIsClosed(); // Instantly returns, respecting the SQL sort order
                }
            }
        }

        // PRIORITY 2: Evaluate Recurring Rules
        for (HolidayRule rule : rules) {
            if (matchesRule(date, rule, currentDayBit)) {
                return true;
            }
        }

        return false;
    }

    private boolean matchesRule(LocalDate date, HolidayRule rule, int currentDayBit) {
        // Edge Case 1: Fixed Date (Ignores dayMask entirely)
        if (rule.getFrequencyType() == HolidayFrequencyType.MONTHLY_FIXED_DATE) {
            return date.getDayOfMonth() == rule.getNthOccurrence();
        }

        // Edge Case 2: Last Day of Month (Ignores dayMask entirely)
        if (rule.getFrequencyType() == HolidayFrequencyType.LAST_DAY_OF_MONTH) {
            return date.equals(YearMonth.from(date).atEndOfMonth());
        }

        // For all other types, if the day doesn't match the bitmask, skip the math
        if ((rule.getDayMask() & currentDayBit) == 0) {
            return false;
        }

        return switch (rule.getFrequencyType()) {
            case WEEKLY -> true;

            case BI_WEEKLY -> {
                WeekFields visualCalendar = WeekFields.of(DayOfWeek.SUNDAY, 1);
                int currentCalendarWeek = date.get(visualCalendar.weekOfMonth());
                yield (currentCalendarWeek % 2) == rule.getNthOccurrence();
            }

            case MONTHLY_NTH_OCCURRENCE -> {
                if (rule.getNthOccurrence() == -1) {
                    // "Last occurrence": Adding 7 days pushes into the next month
                    yield date.plusWeeks(1).getMonth() != date.getMonth();
                } else {
                    int occurrenceOfMonth = (date.getDayOfMonth() - 1) / 7 + 1;
                    yield occurrenceOfMonth == rule.getNthOccurrence();
                }
            }

            case MONTHLY_CALENDAR_WEEK -> {
                // Defines a visual calendar where weeks start on Monday and require at least 1 day
                WeekFields visualCalendar = WeekFields.of(DayOfWeek.SUNDAY, 1);
                int currentCalendarWeek = date.get(visualCalendar.weekOfMonth());

                if (rule.getNthOccurrence() == -1) {
                    // "Last calendar week": Check against the week number of the month's final day
                    int finalWeekOfMonth = date.withDayOfMonth(date.lengthOfMonth())
                            .get(visualCalendar.weekOfMonth());
                    yield currentCalendarWeek == finalWeekOfMonth;
                } else {
                    yield currentCalendarWeek == rule.getNthOccurrence();
                }
            }

            // Exhaustive switch guarantees we don't need a default branch
            default -> throw new IllegalStateException("Unexpected value: " + rule.getFrequencyType());
        };
    }
}