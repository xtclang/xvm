module TestSimple.test.org
    {
    @Inject Console console;

    void run( )
        {
        assert !test1("hi", "there");
        assert !test2("hi", "there");
        }

    static <CompileType> Boolean test1(CompileType e1, CompileType e2)
        {
        CompileType.Comparer compare = CompileType.comparer;
        return compare(e1, e2);
        }

    static <CompileType> Boolean test2(CompileType e1, CompileType e2)
        {
        return CompileType.comparer(e1, e2);
        }
    }
