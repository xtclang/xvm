/**
 * The XUnit DB test framework provides functionality to test Ecstasy database applications.
 *
 * The XUnit DB module is a `TestEngineExtender` so that it is automatically loaded by the XUnit
 * test framework when it is imported into a module.
 */
@TestEngineExtender(extensions.createTestEngineExtensions)
module xunit_db.xtclang.org {

    package jsondb import jsondb.xtclang.org;
    package oodb   import oodb.xtclang.org;
    package xunit  import xunit.xtclang.org;

    import xunit.annotations.TestEngineExtender;
}