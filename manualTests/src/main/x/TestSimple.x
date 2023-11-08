module TestSimple {
    @Inject Console console;

    import ecstasy.fs.FileNode;

    void run() {
        test(/wrongName);
    }

    void test(FileNode node) {
        FileNode node2 = /wrongName;
    }
}
