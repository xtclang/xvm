module TestTypeInfo {
    void run() {
        testMixinInheritance();
        testUnionContribution();
    }

    void testMixinInheritance() {
        import mixinInheritance.*;

        @Inject Console console;

        // build the mixin's TypeInfo before C's; C must still inherit Mix0's methods
        Type<Mix0> type0 = Mix0;
        console.print(type0.dump());

        Type<Mix1> type1 = Mix1;
        console.print(type1.dump());

        Type<C> classType = C;
        assert classType.methods.any(method -> method.name == "value");
    }

    package mixinInheritance {
        class C incorporates Mix1 {}

        mixin Mix0 into C {
            String value() = "parent";
        }

        mixin Mix1 extends Mix0 {}
    }

    void testUnionContribution() {
        // Base's "into C | X" is incomplete while C's TypeInfo is being built;
        // X's TypeInfo is already complete; recording X as unfinished prevents C from completing
        class C incorporates Wide, Narrow {}

        class X {}

        mixin Base into C | X {
            String value() = "parent";
        }

        mixin Narrow into C extends Base {}

        mixin Wide into C | X {}

        Type<C> classType = C;
        assert classType.methods.any(method -> method.name == "value");
    }
}
