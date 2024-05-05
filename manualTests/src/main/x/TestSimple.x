module TestSimple {

    void run() {
        @Inject Console console;
        assert &console.assigned; // this used to assert

        @Inject String greeting;
        console.print(&greeting.assigned
                ? greeting
                : "no greeting");
    }
}