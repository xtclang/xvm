module TestSimple {
    @Inject Console console;

    void run() {
        console.print(getDirs().as(Tuple)); // this used to assert at run-time
    }

    (Directory, Directory, Directory) getDirs() {
        @Inject Directory rootDir;
        @Inject Directory curDir;
        @Inject Directory tmpDir;
        return rootDir, curDir, tmpDir;
    }
}
