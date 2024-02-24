module TestSimple {
    @Inject Console console;

    void run() {
        Test   t = new Test();
        Method m = Test.curDir;
        Tuple  r = m.invoke(t, Tuple:()); // this used to NPE at run time

        console.print(r);
    }

    service Test {
        conditional Directory curDir() {
            @Inject Directory curDir;
            return True, curDir;
        }
    }
}

