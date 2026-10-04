/**
 * Constant Boolean results must still evaluate a runtime left operand exactly once.
 * Run: ./gradlew :manualTests:runOne -PtestName=TestConditionalEffects
 *      -PincludeBuildManualTests=true -PincludeBuildAttachManualTests=true
 */
module TestConditionalEffects {
    Int calls = 0;

    Boolean probe(Boolean result) {
        ++calls;
        return result;
    }

    void checked() {
        assert calls == 1;
        calls = 0;
    }

    Boolean identity(Boolean value) = value;

    void run() {
        for (Boolean operand : [False, True]) {
            Boolean result = probe(operand) && False;
            assert !result;
            checked();

            result = probe(operand) || True;
            assert result;
            checked();

            if (probe(operand) && False) {
                assert False;
            }
            checked();

            if (!(probe(operand) && False)) {
                assert calls == 1;
            } else {
                assert False;
            }
            checked();

            if (probe(operand) || True) {
                assert calls == 1;
            } else {
                assert False;
            }
            checked();

            assert !identity(probe(operand) && False);
            checked();

            assert identity(probe(operand) || True);
            checked();

            result = probe(operand) && True;
            assert result == operand;
            checked();

            result = probe(operand) || False;
            assert result == operand;
            checked();

            result = True && probe(operand);
            assert result == operand;
            checked();

            result = False || probe(operand);
            assert result == operand;
            checked();
        }
        @Inject Console console;
        console.print("Conditional constant-result side effects: 22 checks passed");
    }
}
