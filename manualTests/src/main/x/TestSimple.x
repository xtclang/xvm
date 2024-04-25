module TestSimple {
    @Inject Console console;

    void run() {
        console.print(simulate(42));
    }

    Int attempts = 100000;

    Dec simulate(Int pardoned) {
        return pardoned / attempts;
    }
}