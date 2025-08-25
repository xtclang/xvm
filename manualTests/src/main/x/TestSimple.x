
module TestSimple {
    @Inject Console console;

    void run() {
        new Test().report();
    }

    class Base {
        Object baseValue.get() = this;
    }

    class Test extends Base {
        // this line below used to be allowed to compile and then would NPE at run-time
        Base derivedValue = baseValue.as(Base);

        void report() {
            console.print(derivedValue);
        }
    }
}
