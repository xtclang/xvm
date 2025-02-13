module TestSimple {

    @Inject Console console;

    void run() {
        String provider = "github";

        @Inject(opts=provider) String clientId;

        Boolean assigned = &clientId.assigned; // this used to fail to compile
        console.print($"{assigned=}");
    }
}