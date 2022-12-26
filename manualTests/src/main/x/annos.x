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
        testDefaultParams();
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

        String descr = "from outside";
        new Parent().new @Parent.Mixin(descr) Parent.Child().test();

        class Parent
            {
            mixin Mixin(String descr) into Child
                {
                @Override
                void test()
                    {
                    console.println($"in test at Mixin {descr}");
                    super();
                    }
                }

            void test()
                {
                String descr = "from inside";
                new @Mixin(descr) Child().test();
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
    void testMethodMixin(@Tagged(weight=2) (@Unchecked Int64)? i = Null)
        {
        Method m = testMethodMixin;
        console.println(m);

        assert m.is(Tagged);
        assert m.tag == "method" && m.weight == 1;
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

    mixin Tagged(String tag="", Int weight=-1)
            into Parameter | Method | Class
        {
        String tag.get()
            {
            String tag = super();
            return tag.size > 0    ? tag      :
                this.is(Parameter) ? "param"  :
                this.is(Method)    ? "method" :
                                     "class";
            }

        @Override
        Int estimateStringLength()
            {
            return super() + tag.size + "weight=".size + 2;
            }

        @Override
        Appender<Char> appendTo(Appender<Char> buf)
            {
            $"@Tagged({tag} {weight}) ".appendTo(buf);
            return super(buf);
            }
        }

    void testDefaultParams()
        {
        TaggedObject taggedMap = new @TaggedObject(weight=1) HashMap<Int, Int>();
        assert taggedMap.tag == "none" && taggedMap.weight == 1;

        TaggedConst taggedRange = new @TaggedConst(hash=2) Range<Int>(0, 1);
        assert taggedRange.hash == 2 && taggedRange.tag == "const" && taggedRange.weight == 1;
        }

    mixin TaggedObject(String tag="none", Int weight=-1)
        into Object;

    mixin TaggedConst(Int weight=1, Int hash=-1)
        extends TaggedObject("const", weight);
    }