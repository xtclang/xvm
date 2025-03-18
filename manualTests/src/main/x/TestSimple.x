module TestSimple {
    @Inject Console console;

    void run(  ) {
        new Main().route();
        new @WebService("test") Base().route();
    }

    annotation WebService(String path) into service {
        void route() = console.print($"WebService.route {path=}");
    }

    @WebService("/")
    service Main extends Base {
        @Override
        void route() = console.print($"Main.route");
    }

    service Base {
        void route() = console.print($"Base.route");
    }
}