module TestSimple {
    @Inject Console console;

    void run() {
        // this test used to fail to call Resource.close();
        try (val t = new Resource(1)) {
            throw new Exception();
        } catch (Exception e) {}
    }

    class Resource(Int t) implements Closeable {
        @Override
        void close(Exception? e = Null) {
            console.print($"close {t}");
        }
    }
}
