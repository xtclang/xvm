
module db
    {
    @Inject Console console;

    package json import Json.xtclang.org;
    package oodb import OODB.xtclang.org;
    package jsondb import JsonDB.xtclang.org;

    void run()
        {
        console.println("*** db test ***\n");

        @Inject FileStore storage;
        @Inject Directory curDir;
        Directory dataDir = curDir.dirFor("scratch").ensure();

        console.println("file names:");
        console.println(dataDir.emitListing(new StringBuffer()));

        File file1 = dataDir.fileFor("test1.txt");
        file1.contents = [0x43, 0x61, 0x6D];
        storage.copy(file1.path, dataDir.fileFor("test2.txt").path);
        storage.move(file1.path, dataDir.fileFor("test3.txt").path);

        import jsondb.Catalog;
        Catalog cat = new Catalog(dataDir);
        }
    }