package rent_a_space_api_clone.enums;

public enum HolidayFrequencyType {
    WEEKLY,
    BI_WEEKLY,
    MONTHLY_NTH_OCCURRENCE, // Strictly the 1st, 2nd, etc., occurrence of a weekday
    MONTHLY_CALENDAR_WEEK,  // The visual calendar row (Week 1, Week 2, etc.)
    MONTHLY_FIXED_DATE,     // A specific day of the month (e.g., the 15th)
    LAST_DAY_OF_MONTH       // Automatically handles leap years/month lengths
}