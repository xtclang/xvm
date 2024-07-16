module TestSimple {
    @Inject Console console;

    void run() {
        Duration d = Duration.ofMinutes(21);

        d *= 2; // this used to choose Duration.mul(Dec) rather than Duration.mul(Int) op method

        console.print(d);
    }
}