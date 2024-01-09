module TestSimple {
    @Inject Console console;

    void run() {
        S s = new S();
    }

    interface I {
        @Atomic Int ap;
    }

    service S implements I {
        @Override @Atomic Int ap;
    }
}
