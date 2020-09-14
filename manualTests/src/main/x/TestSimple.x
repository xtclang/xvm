module TestSimple
    {
    @Inject Console console;

    void run( )
        {
        import ecstasy.reflect.MethodTemplate;
        import ecstasy.reflect.Parameter;

        typedef function Int(String) FunSI;
        typedef function Int()       FunVI;

        FunSI f0 = Inner.&foo;
        if ((MethodTemplate template, Function f1, Map<Parameter, Object> params) := f0.isFunction())
            {
            assert f1.is(FunSI);

            console.println($"{template.parent.parent}/{template}");
            }
        }

    class Inner
        {
        static Int foo(String s)
            {
            return s.size;
            }
        }
    }
