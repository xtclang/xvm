module test.examples.org {
    import ecstasy.TypeMismatch;

    @Inject Console console;

    void run() {
        val p1 = new @Skilled("Ecstasy") Named("Jim"); // new @Skilled_Named#17(ctx, @Skilled("Ecstasy") Named)
        assert p1.name == "Jim";
        assert p1.skill == "Ecstasy";
        assert asNamed(p1).name == "Jim";
        assert asSkilled(p1).skill == "Ecstasy";

        String skill = "Java";
        val p2 = new @Skilled(skill) Named("Jim");  // @Skilled_Named#17
        assert asNamed(p2).name == "Jim";
        assert asSkilled(p2).skill == skill;

        testAnnotatedCast();
        testDifferentAnnotations();
    }

    void testAnnotatedCast() {
        @A C direct = new @A C();
        assert direct.fC() == "C";
        assert direct.fA() == "A";
        assert asC(direct).fC() == "C";
        assert asA(direct).fA() == "A";

        @A C derived = asAC(new @A D());
        assert derived.fC() == "D";
        assert derived.fA() == "A";
        assert asC(derived).fC() == "D";
        assert asA(derived).fA() == "A";

        Boolean rejected = False;
        try {
            asAC(new C());
        } catch (TypeMismatch e) {
            rejected = True;
        }
        assert rejected;

        A    asA(Object o) = o.as(A);
        C    asC(Object o) = o.as(C);
        @A C asAC(Object o) = o.as((@A C));
    }

    void testDifferentAnnotations() {
        @B @A C source = new @B @A C();
        @B I target = project(source);
        assert target.fI() == "I";
        assert target.fB() == "B";
    }

    @B I project(@B @A C source) = source;

    Named   asNamed(Object o)   = o.as(Named);
    Skilled asSkilled(Object o) = o.as(Skilled);

    class C {
        String fC() = "C";
    }

    class D extends C {
        @Override String fC() = "D";
    }

    interface I {
        String fI();
    }

    annotation A into C implements I {
        String fA() = "A";

        @Override
        String fI() = "I";
    }

    annotation B into I {
        String fB() = "B";
    }

    const Named {
        construct(String name) {
            console.print($"construct name {name}");
            this.name = name;
        }

        assert() {
            assert name.size > 0;
            console.print($"assert name {name}");
        }

        String name;
    }

    annotation Skilled into Named {
        construct(Int skill) {

        }
        construct(String skill) {
            console.print($"construct skill {skill}");
            this.skill = skill;
        }

        assert() {
            assert skill.size > 0;
            console.print($"assert skill {skill}");
        }

        String skill;
    }
}
