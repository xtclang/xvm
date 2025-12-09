# JSON DB Tests

This module contains the tests for the `jsondb.xtclang.org` module. These tests are run using the 
XUnit framework.

## Running the Tests

To run the full test suite use the `xtc test` command from the `manualTests` directory.

```shell
xtc test -L build/xtc/main/lib -o build/xtc/main/lib src/main/x/jsondb/jsondb_test.x
```

### Running Tests in One Class

The `xtc test` command allows you to run a single test class using the `--test-class` or `-c` 
options. The argument to `--test-class` or `-c` is the fully qualified name of the test class.

For example, to run the tests in the `storage.JsonMapStoreTest` class use the following command:

```shell
xtc test -L build/xtc/main/lib -o build/xtc/main/lib \
    --test-class storage.JsonMapStoreTest
    src/main/x/jsondb/jsondb_test.x
```

## Test Output

The tests create test databases in the `build/test-output` directory. 
There will be a subdirectory for each test class and within that will be a subdirectory for each test.

