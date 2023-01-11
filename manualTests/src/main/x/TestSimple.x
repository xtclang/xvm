module TestSimple
    {
    @Inject Console console;

    const C(Int v)
        {
        @Override
        String toString()
            {
            return v == 0
                ? v.toString()
                : throw new OutOfBounds();
            }

        @Override
        static <CompileType extends C> Boolean equals(CompileType value1, CompileType value2)
            {
            return value1.v == value2.v;
            }
        }

    void run()
        {
        C a = new C(0);
        C b = new C(1);

        assert a == b;
        }
    }