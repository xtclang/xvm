module TestSimple {

    @Inject Console console;

    void run() {
        (Directory d1, Directory d2, Directory d3) = showDirs();
        console.print(d1); // this used to produce a wrong output

        Tuple t = showDirs();
        console.print(t);  // this used to assert at runtime
    }

    Directory showDir() {
        @Inject Directory curDir;
        return curDir;
    }

    (Directory, Directory, Directory) showDirs() {
        @Inject Directory curDir;
        @Inject Directory homeDir;
        @Inject Directory tmpDir;
        return curDir, homeDir, tmpDir;
    }
}