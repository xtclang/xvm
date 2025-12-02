/**
 * This contains JSON Database tests.
 *
 * Run the tests from the manualTests directory using XUnit:
 * ToDo this will change once we are able to run tests using "xtc test"
 *
 *    xec  -L build/xtc/main/lib -o build/xtc/main/lib xunit_engine.xtclang.org jsondb_test.xtclang.org
 *
 * Most test methods will execute with their own unique database.
 * The database files for each test are stored in the build/test-output/<test-class>/<test-method-name> directory.
 */
module jsondb_test.xtclang.org
    incorporates test_db.TestCatalogMetadata {

    package collections import collections.xtclang.org;
    package json        import json.xtclang.org;
    package jsondb      import jsondb.xtclang.org;
    package oodb        import oodb.xtclang.org;
    package xunit       import xunit.xtclang.org;
}