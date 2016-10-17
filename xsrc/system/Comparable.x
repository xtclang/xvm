interface Comparable
    {
    Ordered compareTo(Comparable that);

    static Comparator<> COMPARATOR = new Comparator<Comparable>()
        {
        Ordered compare(Comparable value1, Comparable value2)
            {
            return value1.compare(value2);
            }
        }
    }
