module TestSimple
    {
    @Inject Console console;

    void run()
        {
        String s0 = "ab";
        String s1 = "abc";
        String s2 = s0 + "c";

        assert test(s1, s2);
        assert &s1 == &s2;

        Int64 i1 = s1.hashCode();
        Int64 i2 = s2.hashCode();

        assert test(i1, i2);
        assert &i1 == &i2;
        }

    Boolean test(Object o1, Object o2)
        {
        return o1 == o2;
        }
    }