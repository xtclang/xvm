/**
 * A package containing the test database schema.
 */
package test_db {

    import jsondb.Catalog;

    /**
     * An annotation that can be used on test methods to provide a directory of files to use
     * to Initialize the test database prior to running the tests.
     *
     * @param dbData  the directory of files to use to initialize the test database
     */
    annotation DBInit(Directory? dbData = Null);

    /**
     * Create a new `Catalog` for the test database.
     *
     * @param dir  the directory to use for the database
     *
     * @return the test database `Catalog`
     */
    Catalog<TestSchema> createCatalog(Directory dir)
            = new jsondb.Catalog<TestSchema>(dir.ensure(), jsondb_test, False);

    /**
     * A simple const that can be used as a DB key.
     */
    const Id(String fieldOne, String fieldTwo){}
}