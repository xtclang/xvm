module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        console.println();
        new Test<String>().test(5);
        }

    class Test<Key>
        {
        typedef Map<Key,    Range<Int>>  EntryLayout;
        typedef Map<String, EntryLayout> FileLayout; // very often - just a single key per file
        protected SkiplistMap<Int, FileLayout> storageLayout = new SkiplistMap();

        void test(Int desired)
            {
            FileLayout fileLayout = storageLayout.computeIfAbsent(desired, () -> new HashMap());
            EntryLayout layout     = fileLayout.computeIfAbsent("hello", () -> new HashMap<Key, Range<Int>>());
            console.println(fileLayout);
            }
        }
    }