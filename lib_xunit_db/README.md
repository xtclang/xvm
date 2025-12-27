# XUnit Database Testing

The XUnit DB module contains extensions for XUnit to support database testing. To use these 
extensions, an application should minimally import the XUnit DB module.

```ecstasy
module MyDBTests {

    package xunitdb import xunit_db.xtclang.org;

}
```

## Test Database Instances

An Ecstasy application that uses a database typically injects the database into application code 
wherever it is required using `@Inject` annotations. XUnit DB uses its own injector to inject test 
databases instead. The XUnit DB module will automatically create database instances as required 
by `@Inject` reference that is a type that extends the OODB module's `RootSchema` class.

The databse files will be stored in the `build/test-output/` directory under in the current 
working directory that the `xtc test` command is executed from. This is the same location that 
XUnits puts test output.

The default behavior of XUnit is to create a new instance of the parent class for every test 
method. This means that a database can be injected into a field in the class and each test can 
refer to the same field, even if the `@DatabaseTest` scope is set to `PerTest`.

For example:

```ecstasy
@DatabaseTest(PerTest)
class MyDBTests {

    @Inject Connection<MySchema> conn;
    
    void testOne() {
    }
    
    void testTwo() {
    }
}
```
The `testOne` and `TestTwo` methods will each have their own database schema even if they both 
refer to the same `conn` field in their code because XUnit will execute each method using a new 
instance of the `MyDBTests` class.

### The `@DatabaseTest` Annotation

XUnit DB can create a database instance for each test method, class, package or module. The 
`@DatabaseTest` annotation can be applied to a module, package, class or method.

Test database instance lifecycles are controlled by the `@DatabaseTest` annotation's `scope` 
parameter. The `scope` can be set to one of the following values:

- `Shared` - (default) A single database instance is shared by all tests within the annotated type.
- `PerTest` - A new database instance is created for each test method within the annotated type.

For example, if a package is annotated with `@DatabaseTest(PerTest)`, then a new database instance 
will be created for each test method within each class in that package and in any child packages.

Each `@DatabaseTest` annotation will override the behavior of any parent annotations. For 
examples, if a package is annotated with `@DatabaseTest(PerTest)` then all tests in that 
package will use a new database instance. But if a class within the package is annotated with 
`@DatabaseTest(Shared)`, then all tests methods in that class will share the same database 
instance. If an individual method within that test class is then annotated with `@DatabaseTest
(PerTest)`, that method will have its own database instance. 

For example, the module `foo` below contains database tests:
```
@DatabaseTest(PerTest)
module foo {

    package one {

    } 

    @DatabaseTest(Singleton)
    package two {
    
    } 

    @DatabaseTest(Singleton)
    package three {
    
        class SomeTests {
            @Test
            void testOne() {
            }
        
            @Test
            @DatabaseTest(PerTest)
            void testTwo() {
            }

            @Test
            void testThree() {
            }
        } 
    
        @DatabaseTest(PerTest)
        class SomeMoreTests {

        } 
    } 
}
```
- The module `foo` is annotated with `@DatabaseTest(PerTest)` so all tests in the module will 
  use a new database instance.
- Package `one` has no annotations so all tests will inherit the configuration from the module 
  and have their own database instance.
- Package `two` is annotated with `@DatabaseTest(Singleton)` so all tests in the package will 
  share the same database instance.
- Package `three` is annotated with `@DatabaseTest(Singleton)` so all tests in the package will 
  share the same database instance.
- Class `SomeTests` is annotated with `@DatabaseTest(PerTest)` so all tests in the class will 
  have their own database instance.
- Method `testOne` and `testThree` in class `SomeTests` are not annotated so they will share the 
  same database instance.
- Method `testTwo` in class `SomeTests` is annotated with `@DatabaseTest(PerTest)` so it will 
  have its own database instance.
- Class `SomeMoreTests` is annotated with `@DatabaseTest(PerTest)` so all tests in the class will 
  have their own database instance.


#### Default Database Scope

If the `@DatabaseTest` annotation is not present anywhere in the test module the default 
behavior is to create a single database instance that will be shared by all tests. 

### Initializing Database Files

Sometimes it is necessary to initialize a database before the tests are run so that a test can 
start from a known initial dataset. XUnit DB supports initializing database files from a 
pre-existing set of database files by setting the `dataDir` parameter of the `@DatabaseTest` 
annotation.

The directory specified by the `dataDir` parameter must exist as an embedded resorce 
in the test code as it is resolved as a compile time constant. To initialize a database from 
files elsewhere on the file system at runtime,
see [Advanced Database Configuration](#advanced-database-configuration) 

When XUnit DB creates a database instance from a configuration with a `dataDir` property set, 
the files in the `dataDir` are copied to the actual test database's data directory.

For example, a simple database test module named `db_tests` might look like this:
```
src/x/
  |- db_tests/
  |    |- databases/
  |    |    |- db1/
  |    |    |    |- ... database files ...
  |    |    |- db2/
  |    |         |- ... database files ...
  |    |- TestsA.x
  |    |- TestsB.x
  |- db_tests.x
```

The files `TestA.x` and `TestB.x` contain tests that will use a test database. The directories 
`databases/db1` and `databases/db2` contain the database files that will be used to initialize 
the test databases.

Directory paths to embedded resources are relative paths from the location of the `.x` file 
containing the `@DatabaseTest` annotation. To specify a test database for a test in the `TestsA.x`
file to be initialized from the `databases/db1` directory, the annotation would look like this:

```ecstasy
@DatabaseTest(dataDir=./databases/db1)
```

## Advanced Database Configuration

Setting the `scope` and `dataDir` properties of the `@DatabaseTest` annotation is enough for 
most use cases. However, there are some advanced use cases where it may be necessary to configure 
the database instance in more detail. For example, an application may use more than one database 
schema type and require different configurations for different schema. A test may require the 
database to be initialized from files that are not embedded module resources but exist 
elsewhere on the file system.

To support these advanced use cases, the `@DatabaseTest` annotation has a `configs` property 
which can be set to point to a function that optionally returns a `DbConfig` instance for a 
specific schema type. The function will be called whenever XUnit DB creates a database instance 
for tests within the annotated test fixture. If the function returns a `DbConfig` instance, that 
configuration will be used to configure the test database. 

The function can be inlined directly in the annotation, or it can be a static function on a 
module, package or class.

For example, an application may use two different database schemas, `Customers` and `Products`.
The default XUnit DB behavior would be to create one shared instance of the customers and 
products databases. When using the `@DatabaseTest` annotation, the scope or dataDir properties 
would be applied to both databases, which is not ideal for this scenario. To use different 
configurations for each database the `configs` property of the `@DatabaseTest` can be used.   

If a particular test class is testing functionality for customers, it may need to create a 
customer database for every test method, but the products database could be shared between 
all tests in the class.

The example below shows a `@DatabaseTest` annotation with an inlined function that configures 
the two databases:

```ecstasy
@DatabaseTest(configs = dbType -> {
    switch (dbType.is(_)) {
    case Customers:
        return True, new DbConfig(scope=PerTest, dataDir=./databases/customers);
    case Products:
        return True, new DbConfig(scope=Shared, dataDir=./databases/products);
    }
    return False;
})
class CustomerTests {

    // Each test will use a new instance of the Customers schema.
    @Inject Connection<Customers> customers; 

    // Each test will use the same shared instance of the Products schema.
    @Inject Connection<Products> products; 
}
```

Alternatively the same configuration could be defined as a static function on the `CustomerTests` 
class:

```ecstasy
@DatabaseTest(configs=CustomerTests.getDbConfig)
class CustomerTests {

    // Each test will use a new instance of the Customers schema.
    @Inject Connection<Customers> customers; 

    // Each test will use the same shared instance of the Products schema.
    @Inject Connection<Products> products; 

    static conditional DbConfig getDbConfig(Type<RootSchema> dbType) {
        switch (dbType.is(_)) {
        case Customers:
            return True, new DbConfig(scope=PerTest, dataDir=./databases/customers);
        case Products:
            return True, new DbConfig(scope=Shared, dataDir=./databases/products);
        }
        return False;
    }
}
```
