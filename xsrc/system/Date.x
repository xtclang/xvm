/**
 * The Date value type is used to represent the information about a date in Gregorian form.
 */
const Date(Int year, Int month, Int day)
        implements Sequential
    {
    @RO Int dayOfYear;
    enum DayOfWeek {Sunday, Monday, Tuesday, Wednesday, Thursday, Friday, Saturday}

    // ops
    Time add(Duration duration);
    Duration sub(Time time);
    Time sub(Duration duration);

    // Sequential
    conditional Date prev();
    conditional Date next();

    DateTime to<DateTime>()
        {
        return new DateTime(this, Time:"00:00");
        }
    }
