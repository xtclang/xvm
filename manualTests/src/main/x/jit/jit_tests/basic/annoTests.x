package annoTests {
    static TestConsole console = new TestConsole();

    void run() {
        testSimple();
        testIntoClass();
        testIntoChild();
        testIntoInheritedChild();
        testIntoInterface();
        testIntoException();
        testBuiltInAnnotation();
        testBuiltInChildAnnotation();
        testBuiltInChildConstructor();
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

        console.reset();
        Parent.Counted counted = derived.new @Parent.Counted(3) Parent.Child(7);
        assert counted.count == 3;
        assert counted.baseValue == 7;
        counted.test();
        assert console.output() == \|in test at Derived.Child
                                    |
                                    ;

        Boolean isAnno(Object value) = value.is(Parent.Anno);
        Parent.Anno asAnnotated(Object value) = value.as(Parent.Anno);

        class Derived extends Parent {
            @Override
            class Child {
                construct() {}

                construct(Int baseValue) {
                    construct Parent.Child(baseValue);
                }

                @Override
                void test() {
                    console.print("in test at Derived.Child");
                }
            }
        }

        class Parent {
            annotation Counted(Int count) into Child {}

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
                construct() {}

                construct(Int baseValue) {
                    this.baseValue = baseValue;
                }

                Int baseValue = 0;

                void test() {
                    console.print("in test at Child");
                }
            }
        }
    }

    void testIntoInheritedChild() {
        Base parent = asParent(new Derived());
        Base.C1 plain = asBaseChild(parent.new C1(5));
        assert plain.value == 6;
        assert plain.describe() == "derived:ready:base:6";

        Base.C1 child = asBaseChild(parent.new @Base.Named("anno") Base.C1(5));
        Derived.C0 refinedView = asRefinedChild(child);
        Base.Named annoView = asNamed(child);

        assert &child == &refinedView;
        assert &child == &annoView;
        assert child.value == 6;
        assert refinedView.tag == "ready";
        assert annoView.name == "anno";
        assert annoView.calls == 1;

        assert child.describe() == "anno:derived:ready:base:6";
        assert annoView.calls == 2;

        annoView.name = "updated";
        refinedView.tag = "changed";
        child.value = 10;
        assert refinedView.describe() == "updated:derived:changed:base:10";
        assert annoView.calls == 3;
        assert annoView.describe() == "updated:derived:changed:base:10";
        assert annoView.calls == 4;

        Base       asParent(Object o)       = o.as(Base);
        Base.C1    asBaseChild(Object o)    = o.as(Base.C1);
        Derived.C0 asRefinedChild(Object o) = o.as(Derived.C0);
        Base.Named asNamed(Object o)        = o.as(Base.Named);

        class Base {
            annotation Named into C0 {
                construct(String name) {
                    this.name = name;
                    ++calls;
                }

                String name;
                Int calls = 0;

                @Override
                String describe() {
                    ++calls;
                    return $"{name}:{super()}";
                }
            }

            class C0 {
                construct(Int value) {
                    this.value = value;
                }

                Int value;

                String describe() = $"base:{value}";
            }

            class C1 extends C0 {
                construct(Int value) {
                    construct C0(value);
                }
            }
        }

        class Derived extends Base {
            // C1 is inherited; its composition still includes this C0 override
            @Override
            class C0 {
                construct(Int value) {
                    construct Base.C0(value);
                    ++this.value;
                    tag = "ready";
                }

                String tag = "unset";

                @Override
                String describe() = $"derived:{tag}:{super()}";
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

    void testIntoException() {
        Failure failure = asFailure(new @Tagged("added") Failure("message"));
        assert failure.text == "message";
        assert asTagged(failure).tag == "added";

        Boolean caught = False;
        try {
            throw failure;
        } catch (Failure e) {
            assert e.text == "message";
            assert asTagged(e).tag == "added";
            caught = True;
        }
        assert caught;

        Failure asFailure(Object o) = o.as(Failure);
        Tagged asTagged(Object o) = o.as(Tagged);

        const Failure extends Exception {
            construct(String text) {
                construct Exception(text);
            }
        }

        annotation Tagged(String tag) into Exception {}
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

    void testBuiltInChildAnnotation() {
        Base.Child base = asBase(new Base().new Child());
        assert base.name == "base";

        Derived.Child child = asChild(new Derived().new Child());
        Base.Child baseView = asBase(child);
        Base.Named annoView = asNamed(child);

        assert child.name    == "base:derived";
        assert baseView.name == "base:derived";
        assert annoView.name == "base:derived";

        baseView.name = "updated";
        assert child.name == "updated";
        assert baseView.name == "updated";
        assert annoView.name == "updated";

        Base.Child    asBase(Object o)  = o.as(Base.Child);
        Derived.Child asChild(Object o) = o.as(Derived.Child);
        Base.Named    asNamed(Object o) = o.as(Base.Named);

        class Base {
            annotation Named into Child {
                String name = "base";
            }
            @Named class Child {}
        }

        class Derived extends Base {
            @Override
            class Child {
                construct() {
                    construct Base.Child();

                    // fixed annotation fields are accessed through the child interface
                    String initial = name;
                    name = initial + ":derived";
                }
            }
        }
    }

    void testBuiltInChildConstructor() {
        Base.Child base = asBase(new Base().new Child());
        Derived.Child child = asChild(new Derived().new Child());
        Base.Named baseAnno = asNamed(base);
        Base.Named childAnno = asNamed(child);

        assert base.name == "fixed";
        assert child.name == "fixed";
        assert baseAnno.calls == 1 as "Expected one Named constructor call for Base.Child";
        assert childAnno.calls == 1 as "Expected one Named constructor call for Derived.Child";

        Base.Child layered = asBase(new Base().new @Base.Added(42) Base.Child());
        assert layered.name == "fixed";
        assert asNamed(layered).calls == 1;
        assert asAdded(layered).addedCalls == 1;
        assert asAdded(layered).wide == 42;
        assert asAdded(layered).marker == Null;

        Int128 wide = 123;
        Int? marker = 17;
        Derived.Child explicit = asChild(new Derived().new
                @Base.Ignored(wide) @Base.Added(wide, marker) Derived.Child());
        assert explicit.name == "fixed";
        assert asNamed(explicit).calls == 1;
        assert asAdded(explicit).addedCalls == 1;
        assert asAdded(explicit).wide == wide;
        assert asAdded(explicit).marker == marker;

        Base.Child overloaded = asBase(new Base().new @Base.Added("text") Base.Child());
        assert overloaded.name == "fixed";
        assert asNamed(overloaded).calls == 1;
        assert asAdded(overloaded).addedCalls == 4;

        Base.Child    asBase(Object o)  = o.as(Base.Child);
        Derived.Child asChild(Object o) = o.as(Derived.Child);
        Base.Named    asNamed(Object o) = o.as(Base.Named);
        Base.Added    asAdded(Object o) = o.as(Base.Added);

        class Base {
            annotation Named into Child {
                construct(String name, Int count = 1) {
                    this.name = name;
                    calls += count;
                }

                construct(Int count) {
                    this.name = "number";
                    calls += count;
                }

                String name  = "unset";
                Int    calls = 0;
            }

            annotation Added into Child {
                construct(Int128 wide = 42, Int? marker = Null) {
                    this.wide = wide;
                    this.marker = marker;
                    ++addedCalls;
                }

                construct(String text) {
                    this.wide = 0;
                    this.marker = Null;
                    addedCalls += text.size;
                }

                Int    addedCalls = 0;
                Int128 wide;
                Int?   marker;
            }

            annotation Ignored into Child {
                construct(Int128 unused = 0) {}
            }

            @Named("fixed") class Child {}
        }

        class Derived extends Base {
            @Override
            class Child {}
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
