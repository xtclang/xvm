module TestSimple
    {
    @Inject Console console;
    @Inject Timer timer;

    package db import jsondb.xtclang.org;

    void run()
        {
        Byte[] bytes = [4, 2];
        db.dump("bytes", bytes);
        }
    }
