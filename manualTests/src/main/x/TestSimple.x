module TestSimple {
    @Inject Console console;

    import ecstasy.fs.DirectoryFileStore;

    void run() {
        @Inject Directory curDir;

        // all ".path" used to be "/" and "toString" used to expose the original path
        Directory root = new DirectoryFileStore(curDir).root;
        console.print($"{root=} ({&root.actualType}) {root.path=} {root.name=}");

        assert Directory dir := root.findDir("src/main");
        console.print($"{dir=} ({&dir.actualType}) {dir.path=} {dir.name=}");

        assert File file := root.findFile("src/main/x/errors.x");
        console.print($"{file=} ({&file.actualType}) {file.path=} {file.name=}");
    }
}

