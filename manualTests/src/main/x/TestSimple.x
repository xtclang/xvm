module TestSimple {
    @Inject Console console;

    void run() {
        new TestVar().test();
    }

    class TestVar {
        String[] assignedProp = new String[];

        @Unassigned
        String[] unassignedProp;

        @Lazy String[] calculatedProp.calc() {
            return [""];
        }

        @Lazy String[] uncalculatedProp.calc() {
            return [""];
        }

        void test() {
            testVars();
            testProps();
        }

        void testVars() {
            String[] assignedVar = new String[];
            Var<String[]> varA = &assignedVar;

            varA.makeImmutable();
            validateImmutable(varA);
            validateImmutable(assignedVar);

            String[] unassignedVar;
            Var<String[]> varU = &unassignedVar;
            varU.makeImmutable();
            validateImmutable(varU);
        }

        void testProps() {
            Var<String[]> varA = &assignedProp;

            varA.makeImmutable();
            validateImmutable(varA);
            validateImmutable(assignedProp);

            Var<String[]> varU = &unassignedProp;
            varU.makeImmutable();
            validateImmutable(varU);

            String[] strings = calculatedProp;
            Var<String[]> varC = &calculatedProp;
            varC.makeImmutable();
            validateImmutable(varC);
            validateImmutable(strings);

            Var<String[]> varUC = &uncalculatedProp;
            varUC.makeImmutable();
            validateImmutable(varUC);
            try {
                strings = uncalculatedProp; // too late; cannot be calculated anymore
                assert;
            } catch (ReadOnly ignore) {}

        }

        void validateImmutable(Var<String[]> var) {
            assert var.is(immutable);
            assert !var.assigned || var.isImmutable;
            try {
                var.set([""]);
                assert;
            } catch (ReadOnly ignore) {}
        }

        void validateImmutable(String[] referent) {
            assert referent.is(immutable);
            assert referent.mutability == Constant;
        }
    }
}