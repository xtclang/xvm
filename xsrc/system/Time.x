const Time(Int hour, Int minute, Int second=0, Int nanoseconds=0)
    {
    @Op Time add(Duration duration)
        {
        TODO
        }
    @Op Duration sub(Time time)
        {
        TODO
        }

    @Op Time sub(Duration duration)
        {
        TODO
        }

    static Time MIDNIGHT = new Time(0, 0, 0, 0);
    }
