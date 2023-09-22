module TestSimple {
    @Inject Console console;

    void run() {
        @Custom Int i = 0; // this used to blow up the run-time
        while (i < 5) {
            function Int() f = () -> ++i;
            console.print("result=" + f());
        }
    }

    mixin Custom<Referent>
        into Var<Referent> {
    }
}