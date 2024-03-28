module TestSimple {
    @Inject Console console;

    void run() {
        using (new Resource(1)) {
            using (new Resource(2)) { // this used to fail to compile
            }
        }
    }

    const Resource(Int value) implements Closeable {
        @Override
        void close(Exception? cause = Null) {
            console.print($"Close {this}");
        }
    }
}