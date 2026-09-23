module TestFiles {
    import ecstasy.fs.FileChannel;
    import ecstasy.fs.FileWatcher;

    @Inject            Console   console;
    @Inject("storage") FileStore store;

    void run() {
        testPaths();
        testInject();
        testModify();
        testFileChannels();
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

    /**
     * Wait for actual events; a delay followed by success would not test the watcher.
     */
    void testModify() {
        Directory probe = createProbe("xvm_watch");
        File      file  = probe.fileFor("test.dat");
        Watcher   first = new Watcher(file.path);
        Watcher   other = new Watcher(file.path);
        function void () cancelFirst = file.watch(first);
        function void () cancelOther = probe.watch(other);

        // This is a deadlock guard, not a requirement on event delivery latency. Native watchers
        // may poll and coalesce notifications, so each mutation waits for the preceding event.
        using (new Timeout(Duration:1M)) {
            try {
                file.contents = #/files.x;
                first.awaitCreated();
                other.awaitCreated();

                Int from = "module ".size;
                Int to   = "module TestFiles".size;
                assert file.read(from ..< to).unpackUtf8() == "TestFiles";

                cancelFirst();
                cancelFirst();
                assert file.delete();
                other.awaitDeleted();
                assert !file.exists;
                cancelOther();

                // Cancelling the last listener must still allow the same path to be watched again.
                Watcher again = new Watcher(file.path);
                function void () cancelAgain = file.watch(again);
                try {
                    assert file.create();
                    again.awaitCreated();
                } finally {
                    cancelAgain();
                }
            } finally {
                cancelFirst();
                cancelOther();
                probe.deleteRecursively();
            }
        }
    }

    @Concurrent
    service Watcher(Path expectedPath)
            implements FileWatcher {
        @Future Boolean created;
        @Future Boolean deleted;

        @Override
        Boolean onEvent(Event event, File file) {
            if (file.path == expectedPath) {
                switch (event) {
                case Created:
                    if (!&created.assigned) {
                        created = True;
                    }
                    break;
                case Deleted:
                    if (!&deleted.assigned) {
                        deleted = True;
                    }
                    break;
                }
            }
            return False;
        }

        void awaitCreated() { assert created; }
        void awaitDeleted() { assert deleted; }
    }

    /**
     * Explicit close and exception cleanup are application obligations until abandoned native
     * channels are registered with the request owner. Closing a channel must not delete its file.
     */
    void testFileChannels() {
        Directory probe = createProbe("xvm_channel");
        File file = probe.fileFor("test.dat");
        try {
            file.contents = #/files.x;
            FileChannel channel = file.open(write=[]);
            try {
                assert channel.readable;
                assert channel.size == file.size;
                assert channel.position == 0;
                channel.position = 7;
                assert channel.position == 7;
            } finally {
                channel.close();
            }
            channel.close();
            assert !channel.readable;
            assert !channel.writable;
            assert file.exists;

            channel = file.open(write=[]);
            try {
                using (channel) {
                    throw new IllegalState("expected file-channel failure");
                }
            } catch (IllegalState e) {
                assert e.message == "expected file-channel failure";
            }
            assert !channel.readable;
            assert !channel.writable;
            assert file.contents == #/files.x;
        } finally {
            probe.deleteRecursively();
        }
    }

    /**
     * Claim a new temporary directory without deleting another test's files.
     */
    Directory createProbe(String prefix) {
        @Inject Directory tmpDir;
        Int id = 0;
        while (True) {
            Directory probe = tmpDir.dirFor($"{prefix}_{id++}");
            if (probe.create()) {
                return probe;
            }
        }
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
