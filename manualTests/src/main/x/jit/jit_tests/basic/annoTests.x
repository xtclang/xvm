package annoTests {
    static TestConsole console = new TestConsole();

    void run() {
        testSimple();
        testIntoClass();
        // TODO: annotated child construction currently casts the class object to an instance
        // testIntoChild();
        testIntoInterface();
        testBuiltInAnnotation();
        testOptimizedConstructors();
        testConstructorOverloads();
        testConstructorLambda();
    }

    void testSimple() {
        C c = new @A C();

        assert isA(c);
        assert c.fC() == "C";
        assert c.fA() == "A";

        A a = new @A C();
        assert isC(a);
        assert a.fA() == "A";
        assert a.fC() == "C";

        Boolean isA(Object o) = o.is(A);
        Boolean isC(Object o) = o.is(C);

        class C {
            String fC() = "C";
        }

        annotation A into C {
            String fA() = "A";
        }
    }

    void testIntoClass() {
        console.reset();

        Int age = 26;
        val p1  = new Named("Jane");
        val p2  = new @Aged(age + 1) Named("Joe");
        val p3  = new @Aged(age) @Skilled("Java") Named("Jon");
        val p4  = new @Skilled("Ecstasy") @Aged(21) Named("Joanne");

//      TODO: this is the ultimate target - with finalizers
//        assert console.output() == \|construct name Jane
//                                    |assert name Jane
//                                    |finally name Jane
//                                    |construct aged 27
//                                    |construct name Joe
//                                    |assert aged 27
//                                    |assert name Joe
//                                    |finally aged 27
//                                    |finally name Joe
//                                    |construct aged 26
//                                    |construct skill Java
//                                    |construct name Jon
//                                    |assert aged 26
//                                    |assert skill Java
//                                    |assert name Jon
//                                    |finally aged 26
//                                    |finally skill Java
//                                    |finally name Jon
//                                    |construct skill Ecstasy
//                                    |construct aged 21
//                                    |construct name Joanne
//                                    |assert skill Ecstasy
//                                    |assert aged 21
//                                    |assert name Joanne
//                                    |finally skill Ecstasy
//                                    |finally aged 21
//                                    |finally name Joanne
//                                    |
//                                    ;
        assert console.output() == \|construct name Jane
                                    |assert name Jane
                                    |construct aged 27
                                    |construct name Joe
                                    |assert aged 27
                                    |assert name Joe
                                    |construct aged 26
                                    |construct skill Java
                                    |construct name Jon
                                    |assert aged 26
                                    |assert skill Java
                                    |assert name Jon
                                    |construct skill Ecstasy
                                    |construct aged 21
                                    |construct name Joanne
                                    |assert skill Ecstasy
                                    |assert aged 21
                                    |assert name Joanne
                                    |
                                    ;

        Boolean rejected = False;
        try {
            new @Aged(-1) Named("Joe");
        } catch (IllegalState e) {
            rejected = True;
        }
        assert rejected;

        assert asNamed(p4).name == "Joanne";
        assert asSkilled(p4).skill == "Ecstasy";
        assert asAged(p4).age == 21;

        Named asNamed(Object value) = value.as(Named);
        Skilled asSkilled(Object value) = value.as(Skilled);
        Aged asAged(Object value) = value.as(Aged);

        rejected = False;
        try {
            new @Skilled("Ecstasy") @Aged(21) Named("");
        } catch (IllegalState e) {
            rejected = True;
        }
        assert rejected;
    }

    const Named {
        construct(String name) {
            console.print($"construct name {name}");
            this.name = name;
//        } finally {
//            console.print($"finally name {name}");
        }

        assert() {
            assert name.size > 0;
            console.print($"assert name {name}");
        }

        String name;
    }

    annotation Aged into Named {
        construct(Int age) {
            console.print($"construct aged {age}");
            this.age = age;
//        } finally {
//            console.print($"finally aged {age}");
        }

        assert() {
            assert age >= 0;
            console.print($"assert aged {age}");
        }

        Int age;
    }

    annotation Skilled into Named {
        construct(String skill) {
            console.print($"construct skill {skill}");
            this.skill = skill;
//        } finally {
//            console.print($"finally skill {skill}");
        }

        assert() {
            assert skill.size > 0;
            console.print($"assert skill {skill}");
        }

        String skill;
    }

    void testIntoChild() {
        console.reset();

        new Parent().test();
        assert console.output() == \|in test at Anno from inside
                                    |in test at Child
                                    |
                                    ;

        console.reset();
        String descr = "from outside";
        new Parent().new @Parent.Anno(descr) Parent.Child().test();
        assert console.output() == \|in test at Anno from outside
                                    |in test at Child
                                    |
                                    ;

        // The runtime parent selects the overridden child, including in inherited Parent.test().
        // Annotated child constructors retain the base child's return descriptor.
        console.reset();
        Parent derived = new Derived();
        derived.test();
        assert console.output() == \|in test at Anno from inside
                                    |in test at Derived.Child
                                    |
                                    ;

        console.reset();
        Parent.Child child = derived.new @Parent.Anno(descr) Parent.Child();
        assert asAnnotated(child).descr == descr;
        assert isAnno(child);
        Parent.Anno anno = child;
        anno.test();
        assert console.output() == \|in test at Anno from outside
                                    |in test at Derived.Child
                                    |
                                    ;

        Boolean isAnno(Object value) = value.is(Parent.Anno);
        Parent.Anno asAnnotated(Object value) = value.as(Parent.Anno);

        class Derived extends Parent {
            @Override
            class Child {
                @Override
                void test() {
                    console.print("in test at Derived.Child");
                }
            }
        }

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

    void testIntoInterface() {
        interface Predicate {
            Boolean eval();
        }

        annotation PreComputed(Boolean value = False) into Predicate {
            @Override
            Boolean eval() = value;
        }

        @PreComputed(True)
        class ClassA implements Predicate {
            ClassB b = new ClassB();

            @Override
            Boolean eval() = b.eval();
        }

        @PreComputed(True)
        class ClassB implements Predicate {}

        assert new ClassA().eval();
    }

    void testBuiltInAnnotation() {
        C plain = new C();
        assert plain.value() == "built-in:base";
        assert isBuiltIn(plain);
        assert asBuiltIn(plain).value() == "built-in:base";

        C layered = new @Added C();
        assert layered.value() == "added:built-in:base";
        assert isBuiltIn(layered);
        assert isAdded(layered);
        assert asBuiltIn(layered).value() == "added:built-in:base";

        C stacked = new @Extra @Added C();
        String stackedValue = stacked.value();
        assert stackedValue == "extra:added:built-in:base";

        BuiltIn asBuiltIn(Object value) = value.as(BuiltIn);
        Boolean isBuiltIn(Object value) = value.is(BuiltIn);
        Boolean isAdded(Object value) = value.is(Added);

        class Base {
            String value() = "base";
        }

        @BuiltIn
        class C extends Base {}

        annotation BuiltIn into C {
            @Override
            String value() = "built-in:" + super();
        }

        annotation Added into C {
            @Override
            String value() = "added:" + super();
        }

        annotation Extra into C {
            @Override
            String value() = "extra:" + super();
        }
    }

    void testOptimizedConstructors() {
        Int128 wide = 123;
        Int? marker = 17;

        val explicit = new @Count(4, marker) Counter(9, wide);
        assert explicit.seed == 9 && explicit.wide == wide;
        assert explicit.amount == 4 && explicit.marker == marker;

        val defaults = new @Count Counter();
        assert defaults.seed == 3 && defaults.wide == 0;
        assert defaults.amount == 5 && defaults.marker == Null;

        val chained = new @Ignored(wide) @Count(8, Null) Counter(2, wide);
        assert chained.seed == 2 && chained.wide == wide;
        assert chained.amount == 8 && chained.marker == Null;

        const Counter {
            construct(Int seed = 3, Int128 wide = 0) {
                this.seed = seed;
                this.wide = wide;
            }

            Int seed;
            Int128 wide;
        }

        annotation Count into Counter {
            construct(Int amount = 5, Int? marker = Null) {
                this.amount = amount;
                this.marker = marker;
            }

            Int amount;
            Int? marker;
        }

        annotation Ignored into Counter {
            construct(Int128 unused = 0) {}
        }
    }

    void testConstructorLambda() {
        val item = new @Decorated("text") Item("name");
        assert item.name == "name";
        assert item.label == "text!";

        const Item(String name);

        annotation Decorated into Item {
            construct(String text) {
                function String (String) decorate = (String value) -> value + "!";
                this.label = decorate(text);
            }
            String label;
        }
    }

    void testConstructorOverloads() {
        Int  signedAmount   = 7;
        UInt unsignedAmount = 7;
        val  signed = new @Choose(signedAmount) Item("signed");
        val  unsigned = new @Choose(unsignedAmount) Item("unsigned");

        assert signed.name == "signed" && signed.signed;
        assert unsigned.name == "unsigned" && !unsigned.signed;

        val signedPair   = new @Choose(signedAmount)   @Label(signedAmount)   Item("ss");
        val mixed        = new @Choose(signedAmount)   @Label(unsignedAmount) Item("su");
        val reversed     = new @Choose(unsignedAmount) @Label(signedAmount)   Item("us");
        val unsignedPair = new @Choose(unsignedAmount) @Label(unsignedAmount) Item("uu");

        assert signedPair.signed    && signedPair.labelSigned;
        assert mixed.signed         && !mixed.labelSigned;
        assert !reversed.signed     && reversed.labelSigned;
        assert !unsignedPair.signed && !unsignedPair.labelSigned;

        const Item(String name);

        annotation Choose into Item {
            construct(Int amount) {
                this.signed = True;
            }
            construct(UInt amount) {
                this.signed = False;
            }
            Boolean signed;
        }

        annotation Label into Item {
            construct(Int amount) {
                this.labelSigned = True;
            }
            construct(UInt amount) {
                this.labelSigned = False;
            }
            Boolean labelSigned;
        }
    }
}
