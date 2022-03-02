module TestSimple
    {
    @Inject Console console;

    package collections import collections.xtclang.org;

    import ecstasy.collections.*;
    import collections.*;

    void run()
        {
        Class<Test> clz = Test;
        assert Struct structure := clz.allocate();
        assert structure.is(struct Test);

        try
            {
            console.println($|structure: anno={structure.anno}, op={structure.op} \
                             |value={structure.value} extra={structure.extra}
                             );
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
            console.println($|structure2: anno={structure2.anno}, op={structure2.op} \
                             |value={structure2.value} extra={structure2.extra}
                             );
            structure2.value = "HELLO";
            }
        catch (Exception e)
            {
            console.println(e);
            }

        M t2 = clz2.instantiate(structure2).as(M);
        console.println($"t2: anno={t2.anno} {t2}\n");

        console.println($"default: {new @M Test2()}");
        }

    class Base(Int base)
        {
        }

    class Derived(String s, Int prop=6)
            extends Base(prop)
        {
        construct(String s, Int i=7)
            {
            prop = i+4;
            super(i);
            }
        }

    @M("extra", "anno3")
    const Test
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

    const Test2
        {
        String value = INIT;

        @Override
        String toString()
            {
            return $"svc:{super()} {value}";
            }
        }

    mixin MBase(String anno = "none",  String op="nop")
            into Object
        {
        }

    mixin M(String extra = "no-extra", String anno="anno2")
            extends MBase(anno, "nop2")
            into Object
        {
        }

    static String INIT = "INITIAL";
    }
