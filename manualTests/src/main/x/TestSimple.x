module TestSimple {

    @Inject Console console;

    void run() {
        console.print(ts);
    }

    @Lazy TypeSystem ts.calc() {
        return &this.actualType.typeSystem;
    }
}