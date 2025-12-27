/**
 * The XUnit DB test framework provides functionality to test Ecstasy database applications.
 *
 * The XUnit DB module is a `TestEngineExtender` so that it is automatically loaded by the XUnit
 * test framework when it is imported into a module.
 *
 * XUnit DB is able to create test database instances and inject them into code under test wherever
 * there is an @Inject annotated type that implements the `oodb` module's `RootSchema` interface.
 *
 * The minimal requirement to use XUnit DB is to declare it as a module import. The default
 * behaviour will be to create a single shared instance of a test database for all tests. The
 * `@DatabaseTest` annotation can be used to control the scope and configuration of a test
 * database instance.
 */
@TestEngineExtender(extensions.createTestEngineExtensions)
module xunit_db.xtclang.org {

    package jsondb import jsondb.xtclang.org;
    package oodb   import oodb.xtclang.org;
    package xunit  import xunit.xtclang.org;

    import xunit.annotations.TestEngineExtender;
}
