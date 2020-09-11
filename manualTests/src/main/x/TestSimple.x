module TestSimple
    {
    @Inject Console console;

    void run()
        {
        typedef function Int(String) FunSI;
        typedef function Int()       FunVI;

        FunSI f0 = &foo;
        f0("a");

        FunVI f1 = &foo("b");
        f1();

        Parameter<String> p0 = f0.params[0].as(Parameter<String>);

        FunVI f2 = f0.bind(p0, "c").as(FunVI);
        f2();

        if ((Function f4, Map<Parameter, Object> params) := f2.isFunction())
            {
            assert f4.is(FunSI);

            f4("d");
            }

        FunSI m0 = &moo;
        m0("a");

        FunVI m1 = &moo("b");
        m1();

        FunVI m2 = m0.bind(p0, "c").as(FunVI);
        m2();

        if ((TestSimple             target,
             Method<TestSimple>     m4,
             Map<Parameter, Object> params) := m2.isMethod())
            {
            m4.invoke(this, Tuple:("d"));
            }
        }

    static Int foo(String s)
        {
        @Inject Console console;

        console.println(s);
        return s.size;
        }

    Int moo(String s)
        {
        console.println(s);
        return s.size;
        }
    }
