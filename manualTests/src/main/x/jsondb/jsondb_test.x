/**
 * This contains JSON Database tests.
 */
module jsondb_test.xtclang.org
    incorporates TestCatalogMetadata {

    package json   import json.xtclang.org;
    package jsondb import jsondb.xtclang.org;
    package oodb   import oodb.xtclang.org;

    import ecstasy.mgmt.*;
    import ecstasy.reflect.ModuleTemplate;

    import test_db.TestCatalogMetadata;

    @Inject Console console;

    void run() {
        @Inject Directory curDir;
        assert curDir.fileFor("src/main/x/jsondb/jsondb_test.x").exists
                as "Not in \"manualTests\" directory";

        // Create the test output directory under the build directory, removing any old data from previous test runs.
        Directory testDir = curDir.dirFor("build/test-output");
        if (testDir.exists) {
            testDir.deleteRecursively();
        }
        testDir.ensure();

        // The tests will run inside a container that uses our own resource provider.
        // This allows us to control the lifecycle of database resources and inject them into the tests.
        @Inject("repository") ModuleRepository repository;
        ModuleTemplate template = repository.getResolvedModule("jsondb_test.xtclang.org");
        Container container = new Container(template, Lightweight, repository, new TestResourceProvider(testDir, test_db.createCatalog));
        container.invoke("runTests");
    }

    /**
     * Run all the `@Test` annotated methods in this module.
     *
     * This method is invoked inside a container created in the run() method.
     */
    void runTests() = new TestRunner(this).run();
}