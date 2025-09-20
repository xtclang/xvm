module test0.examples.org {

    @Inject Console console;

    void run() {
        console.print("Hello", True);
        console.print(" World");

        Int i1 = call1(0);
        console.print(i1);

        i1 = call1(i1, 5);
        console.print(i1);

        (i1, Int i2) = call2(0);
        console.print(i2);

        (Int? i1N, Int i2N) = call2N(10);
        console.print(i2N);
        console.print(i1N);

        if (Int i3 := call3(30)) {
            console.print(i3);
        }

        console.print(call4(-1));
        console.print(call4(1));

        for (Int i : 3..1) {
            console.print(i);
        }

        for (Int i = 0; i < 3; ++i) {
            if (i == 1) {
                continue;
              }
            console.print(i);
        }
    }

    Int call1(Int i, Int j = 2) {
        return i + j;
    }
    (Int, Int) call2(Int i) {
        return i*2, i-2;
    }
    (Int?, Int) call2N(Int i) {
        return Null, i/2;
    }
    conditional Int call3(Int i) {
        if (i > -1) {
            return True, i + 3;
        }
        return False;
    }
    static Boolean call4(Int i) {
        return i > 0;
    }
}