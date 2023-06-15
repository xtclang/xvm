# XUnit Tests

This module contains tests for XUnit.
Obviously we cannot really use XUnit to test XUnit, so although the tests in this module are all annotated
with `@Test` they are discovered and executed by a simple set of methods in the `xunit_test` package.

The code under the `xunit_test.test_packages` package is not executed as part of the XUnit test suite. 
This code is used by the tests themselves to verify features in XUnit.
             
To run the XUnit tests:

```
runOne -PtestName=xunit/xunit_test.x
```
