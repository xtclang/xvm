/**
 * This contains JSON Database tests.
 *
 * Run the tests from the manualTests directory using XUnit:
 *
 *    xtc test -L build/xtc/main/lib -o  build/xtc/main/lib src/main/x/jsondb/jsondb_test.x
 *
 * Most test methods will execute with their own unique database.
 * The database files for each test are stored in the
 * build/test-output/<test-class>/<test-method-name> directory.
 */
module jsondb_test.xtclang.org
    incorporates test_db.TestCatalogMetadata {

    package collections import collections.xtclang.org;
    package json        import json.xtclang.org;
    package jsondb      import jsondb.xtclang.org inject (ecstasy.reflect.Injector _) using xunit.PassThruInjector;
    package oodb        import oodb.xtclang.org inject (ecstasy.reflect.Injector _) using xunit.PassThruInjector;
    package xunit       import xunit.xtclang.org;
}
