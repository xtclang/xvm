module TestSimple
    {
    @Inject Console console;

    void run()
        {
        C a = new C(0);
        C b = new C(1);
        C c = new C(2);

        assert a == b || a == c;
        }

    const C(Int v)
        {
        @Override
        String toString()
            {
            return switch(v)
                {
                case 0: v.toString();
                case 1: "abcdef"*1000;
                default: throw new OutOfBounds("wrong value");
                };
            }

        @Override
        static <CompileType extends C> Boolean equals(CompileType value1, CompileType value2)
            {
            return value1.v == value2.v;
            }
        }
    }