import jsondb.Catalog;

import oodb.Connection;
import oodb.model.User;

import xunit.ExecutionContext;
import xunit.MethodOrFunction;

import xunit.extensions.AfterAllCallback;
import xunit.extensions.AfterEachCallback;
import xunit.extensions.BeforeAllCallback;
import xunit.extensions.BeforeEachCallback;

/**
 * An XUnit extension that can create a test database that is then available for tests.
 *
 * This extension is used by declaring a property in a test class and annotating it with the XUnit
 * `@RegisterExtension` annotation. For example
 *
 *     @RegisterExtension
 *     TestClientProvider clientProvider = new TestClientProvider();
 *
 * The `TestClientProvider` has two modes of operation determined by its `scope` property. The
 * default scope is `ForTest`.
 *
 * * The `ForTest` scope will create a unique `TestClient` for each `@Test` method executed in
 *   the test class. The database client is closed after each tests has executed.
 *
 * * The `ForClass` scope will create a unique `TestClient` once for the test class before any tests
 *   are executed. The database client is closed after all the tests in the class have executed.
 *
 * The database files for each client will be stored under a directory named `db/` which itsself is
 * stored under the XUnit injected `testOutput` directory.
 *
 * @param scope  the `Scope` value that determines the scope of the database client that this
 * extension will create.
 */
service TestClientProvider(Scope scope = ForTest)
        implements AfterAllCallback
        implements AfterEachCallback
        implements BeforeAllCallback
        implements BeforeEachCallback {

    enum Scope {ForClass, ForTest}

    private TestClient? client = Null;

    @Override
    @RO Boolean requiresTarget.get() = False;

    @Override
    void beforeAll(ExecutionContext context) {
        if (scope == ForClass) {
            createClient(context);
        }
    }

    @Override
    void beforeEach(ExecutionContext context) {
        if (scope == ForTest) {
            createClient(context);
        }
    }

    @Override
    void afterAll(ExecutionContext context) {
        if (scope == ForTest) {
            closeClient(context);
        }
    }

    @Override
    void afterEach(ExecutionContext context) {
        if (scope == ForTest) {
            closeClient(context);
        }
    }

    conditional TestClient getClient() {
        TestClient? client = this.client;
        if (client.is(TestClient)) {
            return True, client;
        }
        return False;
    }

    private void createClient(ExecutionContext context) {
        @Inject("testOutput")
        Directory testDir;

        TestClient? client = this.client;
        if (client.is(TestClient)) {
            closeClient(context);
        }

        MethodOrFunction? test    = context.testMethod;
        Directory         dataDir = testDir.dirFor("db").ensure();

        if (dataDir.exists && dataDir.size > 0) {
            dataDir.deleteRecursively();
        }

        if (test.is(DBInit)) {
            // The test is annotated with DBInit so initialize the database
            // from the directory specified in the annotation.
            Directory? initDir = test.dbData;
            if (initDir.is(Directory), initDir.exists) {
                copy(initDir, dataDir);
            }
        }

        User user = new User(1, "admin");
        Catalog<TestSchema> catalog = test_db.createCatalog(dataDir);
        catalog.ensureOpenDB("jsondb_test");
        client = catalog.createClient(user, autoShutdown=False).as(TestClient);
        this.client = client;
        context.registry.register(TestClient, client, behavior=Always);
    }

    private void closeClient(ExecutionContext context) {
        context.registry.unregister(TestClient);
        TestClient? client = this.client;
        if (client.is(TestClient)) {
            try {
                client.conn.as(Connection).close();
                client.catalog.close();
            } catch (Exception e) {
                // Ignore errors closing the client.
            }
        }
        this.client = Null;
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