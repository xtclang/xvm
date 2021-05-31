module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        import ecstasy.fs.File;
        import ecstasy.fs.FileStore;

        @Inject FileStore storage;

        File file = storage.fileFor(Path:/Users/cameron/Development/xvm/test.txt);
        file.contents = #12345678;
        assert file.size == 4 && file.contents == #12345678;
        file.append(#CAFE);
        assert file.size == 6 && file.contents == #12345678CAFE;
        file.truncate(3);
        assert file.size == 3 && file.contents == #123456;
        file.append(#DEADBEEF);
        assert file.size == 7 && file.contents == #123456DEADBEEF;
        file.truncate();
        assert file.size == 0 && file.contents.size == 0;
        }
    }