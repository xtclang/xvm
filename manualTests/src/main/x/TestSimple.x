module TestSimple {

    @Inject Console console;

    void run() {
        Test.report();
        Test.init(1);
        Test.report(); // this used to print "0" instead of "1"
    }

    static service Test {
        Int value = 0;

        void init(Int value) {
            this.value = value;
        }

        void report() {
            console.print($"{value=}");
        }
    }
}