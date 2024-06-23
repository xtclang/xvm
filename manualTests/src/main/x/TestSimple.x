module TestSimple {
    @Inject Console console;

    void run() {
        @Inject Directory curDir;

        for (File file : curDir.filesRecursively()) {
            console.print(file);
        }
    }
}
