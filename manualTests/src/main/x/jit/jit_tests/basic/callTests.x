package callTests {

    @Inject Console console;

    Int val1 = 42;
    Int val2.get() = 43;

    void run() {
        console.print(">>>> Running CallTests >>>>");

        // const property initialization
        assert val2 - 1 == val1;

        // standard
        Int i1 = call1(0);
        assert i1 == 2;

        // multi params
        i1 = call1(i1, 5);
        assert i1 == 7;

        // multi returns
        (i1, Int i2) = call2(0);
        assert i1 == 0 && i2 == -2;

        (Int? i1N, Int i2N) = call2N(10);
        assert i1N == Null && i2N == 5;

        // conditional
        if (Int i3 := call3(30)) {
            assert i3 == 33;
        }

        console.print(call4(-1));
        console.print(call4(1));

        // loops
        for (Int i : 3..1) {
            console.print(i);
        }
    }

    Int call1(Int i, Int j = 2) = i + j;

    (Int, Int) call2(Int i) = (i*2, i-2);

    (Int?, Int) call2N(Int i) = (Null, i/2);

    conditional Int call3(Int i) {
        if (i > -1) {
            return True, i + 3;
        }
        return False;
    }

    static Boolean call4(Int i) = i > 0;
}
