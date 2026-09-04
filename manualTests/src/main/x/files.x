module TestFiles {
    import ecstasy.fs.FileWatcher;

    @Inject            Console   console;
    @Inject("storage") FileStore store;

    void run() {
        testPaths();
        testInject();
        testModify();
        testListing();
    }

    void testPaths() {
        console.print("\n** testPaths()");
        console.print($"root={Path.ROOT}");
        console.print($"parent={Path.PARENT}");
        console.print($"current={Path.CURRENT}");

        Path path = new Path(Null, "test");
        console.print($"path={path}");

        path = new Path(path, "sub");
        console.print($"path={path}");

        path = new Path(path, "more");
        console.print($"path={path}");

        for (Int i : 0..2) {
            console.print($"path[{i}]={path[i]}");
        }

        Loop: for (Path each : path) {
            console.print($"iterating path[{Loop.count}]={each}");
        }
        console.print($"path[]={path.toArray()}");

        console.print($"path[1..2]={path[1..2]}");
        console.print($"path[0..1]={path[0..1]}");
        console.print($"path[2..0]={path[2..0]}");

        path = ROOT + path;
        console.print("path=" + path);

        console.print($"relativize root={Path.ROOT.relativize(Path.ROOT)}");
        console.print($"relativize parent={Path.PARENT.relativize(Path.PARENT)}");
        console.print($"relativize current={Path.CURRENT.relativize(Path.CURRENT)}");
        console.print($"relativize path to root = {Path.ROOT.relativize(path)}");
        console.print($"relativize root to path = {path.relativize(Path.ROOT)}");
        console.print($"relativize /a/b/c to /a/p/d/q = {new Path("/a/p/d/q").relativize(new Path("/a/b/c"))}");
        console.print($"relativize /a/p/d/q to /a/b/c = {new Path("/a/b/c").relativize(new Path("/a/p/d/q"))}");
    }

    void testInject() {
        console.print("\n** testInject()");

        console.print($"readOnly={store.readOnly}");
        console.print($"capacity={store.capacity}");
        assert store.bytesFree <= store.capacity;
        assert store.bytesUsed <= store.capacity;

        @Inject("rootDir") Directory root;
        console.print($"root={root} created {root.created}");

        @Inject("homeDir") Directory home;
        console.print($"home={home}");

        @Inject Directory curDir;
        console.print($"curDir={curDir}");

        console.print($"{curDir.name} content:");
        for (String name : curDir.names()) {
            if (File|Directory node := curDir.find(name)) {
                if (node.is(File)) {
                    if (!name.indexOf('.')) {
                        console.print($"\tf {name} {node.size} bytes");
                    }
                } else {
                    console.print($"\td {name}");
                }
            }
        }
    }

    void testModify() {
        console.print("\n** testModify()");

        @Inject Directory tmpDir;
        @Inject Timer timer;

        timer.start();
        FileWatcher watcher = new FileWatcher() {
            @Override
            Boolean onEvent(Event event, Directory dir) {
                console.print($|[{this:service}]: Directory event: \"{event}\" {dir.name}\
                                 | after {timer.elapsed.seconds} sec
                                );
                return False;
            }

            @Override
            Boolean onEvent(Event event, File file) {
                console.print($|[{this:service}]: File event: \"{event}\" {file.name}\
                                 | after {timer.elapsed.seconds} sec
                                );
                return False;
            }
        }.makeImmutable();

        File file = tmpDir.fileFor("test.dat");

        function void () cancel = file.watch(watcher);

        console.print($"[{this:service}]: Creating {file.name}");

        file.contents = #/files.x;

        Int from = "module ".size;
        Int to   = "module TestFiles".size;
        Byte[] bytes = file.read(from ..< to);
        assert bytes.unpackUtf8() == "TestFiles";

        // on Mac OS the WatchService implementation simply polls every 10 seconds;
        // increase the "wait" value to see the events
        Int wait = 1;
        @Future Tuple done;
        timer.schedule(Duration.ofSeconds(wait), () -> {
            console.print($|[{this:service}]: deleting {file.name}\
                             | after {timer.elapsed.seconds} sec
                            );

            file.delete();
            assert !file.exists;

            timer.schedule(Duration.ofSeconds(wait), () -> {
                @Inject Clock clock;
                assert tmpDir.modified.date == clock.now.date;

                console.print($"[{this:service}]: tmpDir={tmpDir}");
                cancel();
                done = ();
            });
        });

        // this will force the caller to wait
        return done;
    }

    /**
     * Covers `Directory.dirs()` and `files()`.
     */
    void testListing() {
        console.print("\n** testListing()");

        @Inject Directory tmpDir;

        Directory probe = tmpDir.dirFor("xvm_listing_probe");
        if (probe.exists) {
            probe.deleteRecursively();
        }
        probe.ensure();

        for (String name : ["a.txt", "b.txt", "c.txt", "d.txt", "e.txt"]) {
            assert probe.fileFor(name).create();
        }
        assert probe.dirFor("sub").create();

        assert probe.files().count() == 5;
        assert probe.dirs().count() == 1;

        // remove entries the iteration has not reached yet; they must be skipped
        Int seen = 0;
        for (File f : probe.files()) {
            if (seen == 0) {
                for (String name : ["d.txt", "e.txt"]) {
                    if (File victim := probe.findFile(name)) {
                        victim.delete();
                    }
                }
            }
            seen++;
        }
        assert seen >= 1 && seen <= 5;

        probe.deleteRecursively();
        assert !probe.exists;
    }
}
