module TestFiles
    {
    import ecstasy.fs.Directory;
    import ecstasy.fs.File;
    import ecstasy.fs.FileWatcher;
    import ecstasy.fs.Path;
    import ecstasy.fs.FileStore;

    @Inject            Console   console;
    @Inject("storage") FileStore store;

    void run()
        {
        testPaths();
        testInject();
        testModify();
        }

    void testPaths()
        {
        console.println("\n** testPaths()");
        console.println($"root={Path.ROOT}");
        console.println($"parent={Path.PARENT}");
        console.println($"current={Path.CURRENT}");

        Path path = new Path(Null, "test");
        console.println($"path={path}");

        path = new Path(path, "sub");
        console.println($"path={path}");

        path = new Path(path, "more");
        console.println($"path={path}");

        for (Int i : 0..2)
            {
            console.println($"path[{i}]={path[i]}");
            }

        Loop: for (Path each : path)
            {
            console.println($"iterating path[{Loop.count}]={each}");
            }
        console.println($"path[]={path.toArray()}");

        console.println($"path[1..2]={path[1..2]}");
        console.println($"path[0..1]={path[0..1]}");
        console.println($"path[2..0]={path[2..0]}");

        path = ROOT + path;
        console.println("path=" + path);

        console.println($"relativize root={Path.ROOT.relativize(Path.ROOT)}");
        console.println($"relativize parent={Path.PARENT.relativize(Path.PARENT)}");
        console.println($"relativize current={Path.CURRENT.relativize(Path.CURRENT)}");
        console.println($"relativize path to root = {Path.ROOT.relativize(path)}");
        console.println($"relativize root to path = {path.relativize(Path.ROOT)}");
        console.println($"relativize /a/b/c to /a/p/d/q = {new Path("/a/p/d/q").relativize(new Path("/a/b/c"))}");
        console.println($"relativize /a/p/d/q to /a/b/c = {new Path("/a/b/c").relativize(new Path("/a/p/d/q"))}");
        }

    void testInject()
        {
        console.println("\n** testInject()");

        console.println($"readOnly={store.readOnly}");
        console.println($"capacity={store.capacity}");
        assert store.bytesFree <= store.capacity;
        assert store.bytesUsed <= store.capacity;

        @Inject("rootDir") Directory root;
        console.println($"root={root} created {root.created}");

        @Inject("homeDir") Directory home;
        console.println($"home={home}");

        @Inject Directory curDir;
        console.println($"curDir={curDir}");

        console.println($"{curDir.name} content:");
        for (String name : curDir.names())
            {
            if (File|Directory node := curDir.find(name))
                {
                if (node.is(File))
                    {
                    if (!name.indexOf('.'))
                        {
                        console.println($"\tf {name}");
                        }
                    }
                else
                    {
                    console.println($"\td {name}");
                    }
                }
            }
        }

    void testModify()
        {
        console.println("\n** testModify()");

        @Inject Directory tmpDir;
        @Inject Timer timer;

        FileWatcher watcher = new FileWatcher()
            {
            @Override
            Boolean onEvent(Event event, Directory dir)
                {
                console.println($|[{this:service}]: Directory event: \"{event}\" {dir.name}\
                                 | after {timer.elapsed.seconds} sec
                                );
                return False;
                }

            @Override
            Boolean onEvent(Event event, File file)
                {
                console.println($|[{this:service}]: File event: \"{event}\" {file.name}\
                                 | after {timer.elapsed.seconds} sec
                                );
                return False;
                }
            };

        File file = tmpDir.fileFor("test.dat");

        function void () cancel = file.watch(watcher);

        console.println($"[{this:service}]: Creating {file.name}");
        file.create();
        assert file.exists;

        // on Mac OS the WatchService implementation simply polls every 10 seconds;
        // increase the "wait" value to see the events
        Int wait = 1;
        timer.schedule(Duration.ofSeconds(wait), () ->
            {
            console.println($|[{this:service}]: deleting {file.name}\
                             | after {timer.elapsed.seconds} sec
                            );

            file.delete();
            assert !file.exists;

            timer.schedule(Duration.ofSeconds(wait), () ->
                {
                @Inject Clock clock;
                assert tmpDir.modified.date == clock.now.date;

                console.println($"[{this:service}]: tmpDir={tmpDir}");
                cancel();
                });
            });
        }
    }