module TestSimple {
    @Inject Console console;

    void run(String[] args=[""]) {
        @Inject Directory rootDir;
        @Inject Directory homeDir;
        @Inject Directory curDir;

        String path = args[0];
        Directory dir =
            path.startsWith("~/") ? homeDir.dirFor(path.substring(2)) :
            path.startsWith("/")  ? rootDir.dirFor(path.substring(1)) :
                                    curDir .dirFor(path);

        for (File file : dir.filesRecursively()) {
            assert file.exists;
            console.print(file);
        }

        console.print($|
                       |Checking content of "{dir}"
                       |
                       );

        for (String name : dir.names()) {
            if (File|Directory node := dir.find(name)) {
                if (File link := node.linkAsFile()) {
                    console.print($"{name} is a link; {link.exists=}");
                } else {
                    console.print($"{name} is a {node.is(File) ? "file" : "dir"}");
                }
            } else {
                File link = dir.fileFor(name);
                console.print($"{name} is a broken link {link.exists=}");
            }
        }
    }
}
