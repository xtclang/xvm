/**
 * A `ResourceProvider` implementation that can provide resources to inject
 * into the database tests.
 */
service TestResourceProvider(Directory storeageDir, CatalogBuilder catalogBuilder)
        extends BasicResourceProvider {

    import ecstasy.annotations.Inject.Options;
    import ecstasy.fs.FileNode;
    import ecstasy.mgmt.*;
    import ecstasy.reflect.ModuleTemplate;

    import jsondb.Catalog;

    import oodb.model.User;

    import test_db.TestClient;
    import test_db.TestSchema;
    import test_db.DBInit;

    /**
     * The name of the current test class.
     */
    private String? testClassName = Null;

    /**
     * The current `Test`.
     */
    private Test? currentTest = Null;

    /**
     * The current `TestClient`.
     */
    private TestClient? currentClient = Null;

    /**
     * The `CatalogBuilder` to use to create a `Catalog`.
     */
    typedef function Catalog<TestSchema>(Directory) as CatalogBuilder;

    /**
     * The `FileStore` to use to access files.
     */
    @Lazy FileStore store.calc() {
        import ecstasy.fs.DirectoryFileStore;
        return new DirectoryFileStore(storeageDir);
    }

    @Override
    Supplier getResource(Type type, String name) {
        import Container.Linker;

        switch (type, name) {
        case (TestResourceProvider, "provider"):
            return this;

        case (FileStore, "storage"):
            return &store.maskAs(FileStore);

        case (Directory, _):
            switch (name) {
            case "rootDir":
                return testDirectory();

            case "homeDir":
                return testDirectory();

            case "curDir":
                return testDirectory();

            case "tmpDir":
                return tempDir;

            default:
                throw new Exception($"Invalid Directory resource: \"{name}\"");
            }

        case (TestClient, "client"):
            return ensureTestClient;

        case (Linker, "linker"):
            @Inject Linker linker;
            return linker;

        case (ModuleRepository, "repository"):
            @Inject ModuleRepository repository;
            return repository;
        }
        return super(type, name);
    }

    /**
     * Returns the directory to for any files specific for the current test.
     */
    Directory testDirectory() {
        Directory root = store.root;
        return testDirectory(&root.maskAs(Directory));
    }

    /**
     * Returns the directory to for any files specific for the current test.
     *
     * @param root  the root directory to place the test directory under
     *
     * @return the directory to for any files specific for the current test
     */
    Directory testDirectory(Directory root) {
        Directory dir = root;
        String? name = testClassName;
        if (name.is(String)) {
            dir = dir.dirFor(name);
            Test? test = currentTest;
            if (test.is(Test)) {
                dir = dir.dirFor(test.name);
            }
        }
        return dir;
    }

    /**
     * Returns the directory to for any temporary files.
     */
    Directory tempDir(Options opts) {
        Directory temp = store.root.find("_temp").as(Directory).ensure();
        return testDirectory(&temp.maskAs(Directory));
    }

    /**
     * Ensures that a `TestClient` is available for the current `Test`..
     *
     * @return The `TestClient` for the current `Test`
     */
    TestClient ensureTestClient(Options opts) {
        TestClient? client = currentClient;
        if (client.is(TestClient)) {
            return client;
        }
        Test?     test    = currentTest;
        Directory testDir = testDirectory();
        Directory dataDir = testDir.dirFor("db").ensure();

        if (test.is(DBInit)) {
            // The test is annotated with DBInit so initialize the database
            // from the directory specified in the annotation.
            FileNode  initDir   = test.dbData;
            if (initDir.exists) {
                copy(initDir, dataDir);
            }
        }

        User user = new User(1, "admin");
        Catalog catalog = catalogBuilder(dataDir);
        catalog.ensureOpenDB("jsondb_test");
        client = catalog.createClient(user, autoShutdown=False).as(TestClient);
        currentClient = client;
        return client;
    }

    /**
     * Sets the current `Test`.
     *
     * @param className  the name of the current test class
     * @param test       the current `Test`
     */
    void setTest(String className, Test test) {
        testClassName = className;
        currentTest = test;
    }

    /**
     * Resets the state of this provider.
     */
    void reset() {
        TestClient? client = currentClient;
        if (client.is(TestClient)) {
            client.conn.as(oodb.Connection).close();
        }
        currentClient = Null;
        currentTest = Null;
        testClassName = Null;
    }

    /**
     * Recursively copies the contents of the given directory to the given destination.
     *
     * @param src   the source directory
     * @param dest  the destination directory
     *
     * @throws FileAlreadyExists if the destination directory already exists and is not empty
     * @throws FileNotFound      if the source directory does not exist
     */
    private void copy(Directory src, Directory dest) {
        import ecstasy.fs.FileAlreadyExists;
        import ecstasy.fs.FileNotFound;

        if (src.exists) {
            if (dest.exists && dest.size != 0) {
                throw new FileAlreadyExists(dest.path);
            }
            dest.ensure();
            for (File file : src.files()) {
                copy(file, dest.fileFor(file.name));
            }
            for (Directory dir : src.dirs()) {
                copy(dir, dest.dirFor(dir.name));
            }
        } else {
            throw new FileNotFound(src.path);
        }
    }

    /**
     * Copies the contents of the given file to the given destination.
     *
     * @param src   the source file
     * @param dest  the destination file
     *
     * @throws FileAlreadyExists if the destination file already exists
     */
    private void copy(File src, File dest) {
        import ecstasy.fs.FileAlreadyExists;
        if (src.exists) {
            if (dest.exists) {
                throw new FileAlreadyExists(dest.path);
            }
            dest.append(src.contents);
        }
    }
}
