module TestAnnotations {
    @Inject ecstasy.io.Console console;

    void run() {
        testWatch();
        testAnnotations();
        testAnnotations2();
        testMethodAnno();
        testClassAnno();
        testDefaultParams();
    }

    function void (Int) logger = (Int v) -> {
        @Inject ecstasy.io.Console console;
        console.print($"->{v}", True);
    };

    void testWatch() {
        console.print("\n** testWatch");

        // property-based
        for (@Watch(logger) Int i = 3; i > 0; --i) {}
        console.print();

        // var-based
        function void (Int) log = (Int v) -> {
            console.print($"{v}->", True);
        };

        @Watch(log) Int i;
        for (i = 3; i > 0; --i) {}
        console.print();

        // lambda-based
        @Watch((Int v) -> {console.print($"{v}, ", True);}) Int k;
        for (k = 3; k > 0; --k) {}
        console.print();
    }

    void testAnnotations() {
        console.print("\n** testAnnotations");

        Int age = 26;
        val p1  = new Named("Jane");
        val p2  = new @Aged(age + 1) Named("Joe");
        val p3  = new @Aged(age) @Skilled("Java") Named("Jon");
        val p4  = new @Skilled("Ecstasy") @Aged(21) Named("Joanne");
        try {
            new @Aged(-1) Named("Joe");
            throw new Exception();
        } catch (IllegalState e) {
            console.print($"expected assert: {e.text}");
        }
        try {
            new @Skilled("Ecstasy") @Aged(21) Named("");
            throw new Exception();
        } catch (IllegalState e) {
            console.print($"expected assert: {e.text}");
        }
    }

    const Named {
        construct(String name) {
            console.print($"construct (name) {this}");
            this.name = name;
        } finally {
            console.print($"finally (name) {this}");
        }

        assert() {
            assert name.size > 0;
            console.print($"assert (name) {name}");
        }
        String name;
    }

    annotation Aged into Named {
        construct(Int age) {
            console.print($"construct (aged) {this}");
            this.age = age;
        } finally {
            console.print($"finally (aged) {this}");
        }

        assert() {
            assert age >= 0;
            console.print($"assert (aged) {age}");
        }
        Int age;
    }

    annotation Skilled into Named {
        construct(String skill) {
            console.print($"construct (skill) {this}");
            this.skill = skill;
        } finally {
            console.print($"finally (skill) {this}");
        }

        assert() {
            assert skill.size > 0;
            console.print($"assert (skill) {skill}");
        }
        String skill;
    }

    void testAnnotations2() {
        new Parent().test();

        String descr = "from outside";
        new Parent().new @Parent.Anno(descr) Parent.Child().test();

        class Parent {
            annotation Anno(String descr) into Child {
                @Override
                void test() {
                    console.print($"in test at Anno {descr}");
                    super();
                }
            }

            void test() {
                String descr = "from inside";
                new @Anno(descr) Child().test();
            }

            class Child {
                void test() {
                    console.print("in test at Child");
                }
            }
        }
    }

    @Tagged(weight=1)
    void testMethodAnno(@Tagged(weight=2) (@AutoFreezable Freezable)? o = Null) {
        Method m = testMethodAnno;
        console.print(m);

        assert m.tag == "method" && m.weight == 1;
    }

    void testClassAnno() {
        Inner inner = new Inner();
        assert !inner.is(Tagged);

        @Tagged(weight=2)
        class Inner {}
    }

    annotation Tagged(String tag="", Int weight=-1)
            into Parameter | Method | Class {
        String tag.get() {
            String tag = super();
            return tag.size > 0    ? tag      :
                this.is(Parameter) ? "param"  :
                this.is(Method)    ? "method" :
                                     "class";
        }

        @Override
        Int estimateStringLength() {
            return super() + tag.size + "weight=".size + 2;
        }

        @Override
        Appender<Char> appendTo(Appender<Char> buf) {
            $"@Tagged({tag} {weight}) ".appendTo(buf);
            return super(buf);
        }
    }

    void testDefaultParams() {
        TaggedObject taggedMap = new @TaggedObject(weight=1) HashMap<Int, Int>();
        assert taggedMap.tag == "none" && taggedMap.weight == 1;

        TaggedConst taggedRange = new @TaggedConst(hash=2) Range<Int>(0, 1);
        assert taggedRange.hash == 2 && taggedRange.tag == "const" && taggedRange.weight == 1;
    }

    annotation TaggedObject(String tag="none", Int weight=-1)
        into Object;

    annotation TaggedConst(Int weight=1, Int hash=-1)
        extends TaggedObject("const", weight);
}