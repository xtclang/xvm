module TestSimple {

    @Inject Console console;

    void run() {
        Runner r = new Runner() { // this used to fail to compile
            @Override
            void run() {
                console.print($"runner @ {this:service}");
            }
        };
        r.run();
    }

    class Runner() {
        void run() {}
    }
}