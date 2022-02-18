module TestSimple
    {
    @Inject Console console;

    package collections import collections.xtclang.org;

    import ecstasy.collections.*;
    import collections.*;

    void run()
        {
//        console.println(new Test());
//        console.println(new @M(op="yikes") Test2());
//        console.println(new @M("test3") Test2());

        Class<Test> clz = Test;
        assert Struct structure := clz.allocate();
        assert structure.is(struct Test);

        try
            {
            console.println($"structure: anno={structure.anno}, value={structure.value}");
            structure.value = "hello";
            }
        catch (Exception e)
            {
            console.println(e);
            }

        Test t1 = clz.instantiate(structure);
        console.println($"t1: {t1}\n");

        Type type2 = @M("test2") Test2;

        assert Class clz2 := type2.fromClass();
        assert Struct structure2 := clz2.allocate();
        assert structure2.is(struct M + struct Test2);

        try
            {
            console.println($"structure2: anno={structure2.anno}, value={structure2.value}");
            structure2.value = "HELLO";
            }
        catch (Exception e)
            {
            console.println(e);
            }

        M t2 = clz2.instantiate(structure2).as(M);
        console.println($"t2: anno={t2.anno} {t2}\n");

        console.println($"default: {new @M Test2()}");
        console.println($"custom: {new @M(42) Test2()}");
        }

    @M("test")
    class Test
        {
        String value = init();

        @Override
        String toString()
            {
            return $"anno={anno} svc:{super()} {value}";
            }

        static String init()
            {
            return "initial";
            }
        }

    class Test2
        {
        String value = INIT;

        @Override
        String toString()
            {
            return $"svc:{super()} {value}";
            }
        }

    mixin M(String anno = "none") //, String op = "?")
            into Object
        {
        construct(Int n)
            {
            anno = n.toString();
            }
        }

    static String INIT = "INITIAL";
    }
