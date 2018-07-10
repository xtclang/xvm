const Time(Int hour, Int minute, Int second=0, Int nanoseconds=0)
    {
    @Op Time add(Duration duration);
    @Op Duration sub(Time time);
    @Op Time sub(Duration duration);
    }
