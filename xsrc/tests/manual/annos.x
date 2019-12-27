module TestAnnotations.xqiz.it
    {
    @Inject Ecstasy.io.Console console;

    void run()
        {
        testWatch();
        testMixin();
        }

    function void (Int) logger = (Int v) ->
        {
        @Inject Ecstasy.io.Console console;
        console.print($"->{v}");
        };

    void testWatch()
        {
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
    }

