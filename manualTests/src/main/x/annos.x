module TestAnnotations
    {
    @Inject ecstasy.io.Console console;

    void run()
        {
        testWatch();
        testMixin();
        testMixin2();
        testMethodMixin();
        testClassMixin();
        }

    function void (Int) logger = (Int v) ->
        {
        @Inject ecstasy.io.Console console;
        console.print($"->{v}");
        };

    void testWatch()
        {
        console.println("\n** testWatch");

        // property-based
        for (@Watch(logger) Int i = 3; i > 0; --i)
            {
            }
        console.println();

        // var-based
        function void (Int) log = (Int v) ->
            {
            console.print($"{v}->");
            };

        @Watch(log) Int i;
        for (i = 3; i > 0; --i)
            {
            }
        console.println();

        // lambda-based
        @Watch((Int v) -> {console.print($"{v}, ");}) Int k;
        for (k = 3; k > 0; --k)
            {
            }
        console.println();
        }

    void testMixin()
        {
        console.println("\n** testMixin");

        Int age = 26;
        val p1  = new Named("Jane");
        val p2  = new @Aged(age + 1) Named("Joe");
        val p3  = new @Aged(age) @Skilled("Java") Named("Jon");
        val p4  = new @Skilled("Ecstasy") @Aged(21) Named("Joanne");
        try
            {
            new @Aged(-1) Named("Joe");
            throw new Exception();
            }
        catch (IllegalState e)
            {
            console.println($"expected assert: {e.text}");
            }
        try
            {
            new @Skilled("Ecstasy") @Aged(21) Named("");
            throw new Exception();
            }
        catch (IllegalState e)
            {
            console.println($"expected assert: {e.text}");
            }
        }

    const Named
        {
        construct(String name)
            {
            console.println($"construct (name) {this}");
            this.name = name;
            }
        finally
            {
            console.println($"finally (name) {this}");
            }

        assert()
            {
            assert name.size > 0;
            console.println($"assert (name) {name}");
            }
        String name;
        }

    mixin Aged into Named
        {
        construct(Int age)
            {
            console.println($"construct (aged) {this}");
            this.age = age;
            }
        finally
            {
            console.println($"finally (aged) {this}");
            }

        assert()
            {
            assert age >= 0;
            console.println($"assert (aged) {age}");
            }
        Int age;
        }

    mixin Skilled into Named
        {
        construct(String skill)
            {
            console.println($"construct (skill) {this}");
            this.skill = skill;
            }
        finally
            {
            console.println($"finally (skill) {this}");
            }

        assert()
            {
            assert skill.size > 0;
            console.println($"assert (skill) {skill}");
            }
        String skill;
        }

    void testMixin2()
        {
        new Parent().test();
        new Parent().new @Parent.Mixin Parent.Child().test();

        class Parent
            {
            mixin Mixin into Child
                {
                @Override
                void test()
                    {
                    console.println("in test at Mixin");
                    super();
                    }
                }

            void test()
                {
                new @Mixin Child().test();
                }

            class Child
                {
                void test()
                    {
                    console.println("in test at Child");
                    }
                }
            }
        }

    @Tagged(weight=1)
    void testMethodMixin()
        {
        Method m = testMethodMixin;

        assert m.is(Tagged);
        assert m.tag == "none" && m.weight == 1;
        }

    void testClassMixin()
        {
        Inner inner = new Inner();
        assert !inner.is(Tagged);

        @Tagged(weight=2)
        class Inner
            {
            }
        }

    mixin Tagged(String tag="none", Int weight=-1)
            into Method | Class
        {
        }
    }

