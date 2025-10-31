/**
 * A package containing the test database schema.
 */
package test_db {

    /**
     * An annotation that can be used on test methods to provide a directory of files to use
     * to Initialize the test database prior to running the tests.
     */
    annotation DBInit(Directory dbData);

    /**
     * Create a new `Catalog` for the test database.
     *
     * @param dir  the directory to use for the database
     *
     * @return the test database `Catalog`
     */
    jsondb.Catalog<TestSchema> createCatalog(Directory dir)
            = new jsondb.Catalog<TestSchema>(dir.ensure(), jsondb_test, False);
}