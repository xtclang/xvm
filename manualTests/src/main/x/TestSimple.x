module TestSimple {
    @Inject Console console;

    void run() {
        Double i = 0.1;
        Int    j = 10;
        Int    k64 = (j / i).toInt64();

        // used to throw at run-time
        Int32  k32 = (j / i).toInt32();
        Int16  k16 = (j / i).toInt16();
        Int8   k8  = (j / i).toInt8();

        console.print($"{k64=} {k32=} {k16=} {k8=}");
    }
}

