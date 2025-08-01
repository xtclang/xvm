module test0.examples.org {

    void run() {
        @Inject Console console;
        console.print("Hello World");

//        Int i1 = call1(0);
//        i1 = call1(0, 5);
//
//        (i1, Int i2) = call2(0);
//
//        if (Int i3 := call3(0)) {
//            i3++;
//        }
    }

    Int call1(Int i, Int j = 2) {
        return i + j;
    }
    (Int, Int) call2(Int i) {
        return i++, i;
    }
    conditional Int call3(Int i) {
        return True, i;
    }

    Boolean call4(Int i) {
        return i > 0;
    }
}