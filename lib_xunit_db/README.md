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

### The `@DatabaseTest` Annotation

XUnit DB can create a database instance for each test method, class, package or module. The 
`@DatabaseTest` annotation can be applied to a module, package, class or method.

Test database instance lifecycles are controlled by the `@DatabaseTest` annotation's `scope` 
parameter. The `scope` can be set to one of the following values:

- `Shared` - (default) A single database instance is shared by all tests within the annotated type.
- `PerTest` - A new database instance is created for each test method within the annotated type.

For example, if a package is annotated with `@DatabaseTest(PerTest)`, then a new database instance 
will be created for each test method within each class in that package and in any child packages.

Each `@DatabaseTest` annotation will override the behaviour of any parent annotations. For 
examples, if a package is annotated with `@DatabaseTest(PerTest)` and a class within the 
package is annotated with `@DatabaseTest(Shared)`, then all tests methods in that class will 
share the same database instance. If an individual method within the class it then annotated with 
`@DatabaseTest(PerTest)`, that method will have its own database instance. 

#### Default Database Scope

If the `@DatabaseTest` annotation is not present anywhere in the test module the default 
behaviour is to create a single database instance that will be shared by all tests. 

### Initializing Database Files

Sometimes it is necessary to initialize a database before the tests are run so that a test can 
start from a known initial dataset. XUnit DB supports initializing database files from a 
pre-existing set of database files by setting the `templateDir` parameter of the `@DatabaseTest` 
annotation.

The directory specified by the `templateDir` parameter must exist as an embedded resource 
in the test code as it is resolved as a compile time constant. To initialise a database from 
files elsewhere on the file system at runtime,
see [Advanced Database Configuration](#advanced-database-configuration) 


[//]: # ( JK: ToDo...)



## Advanced Database Configuration

[//]: # ( JK: ToDo...)

## Using Multiple Databases

XUnit DB supports testing applications that require multiple databases. The behaviour is
identical to testing with a single database, XUnit will create instances of whichever database
is required to be injected into code being tested.

[//]: # ( JK: ToDo...)
