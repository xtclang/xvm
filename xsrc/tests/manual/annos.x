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
        String skill;
        }
    }

