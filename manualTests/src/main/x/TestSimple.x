module TestSimple
    {
    @Inject Console console;

    package collections import collections.xtclang.org;

    import ecstasy.collections.*;
    import collections.*;

    void run()
        {
        Test t = new Test(1);
        console.println($"t.value2={t.value2} t.desc={t.desc}");

        t.test();
        }

    class Test(Int value1)
        {
        @Lazy Int value2.calc()
            {
            return this.Test.value1;
            }

        void test()
            {
            Child c = new Child();
            console.println($"c.desc() {c.desc()}");
            }

        @Lazy String desc.calc()
            {
            Type t = this.&Test.actualType;
            return t.toString();
            }

        class Child
            {
            @Lazy Int value3.calc()
                {
                return this.Test.value2 + 40;
                }

            String desc()
                {
                return " Child " + value3;
                }
            }
        }
    }
