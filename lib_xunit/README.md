# XUnit - The Ecstasy Testing Framework

XUnit is a testing framework that allows developers to write unit tests in their Ecsatsy projects.
XUnit is based on the popular Java JUnit test framework.

## Writing Tests

A test in XUnit is a method or function annotated with `@Test`.
The `@Test` annotation is part of the Ecstasy core library, so you don't need to import anything 
to write simple tests.

Test methods and functions can be written inside modules, packages and classes.

```ecstasy
module MyApplication {

    @Test
    void testRange() {
        Range<Int> range = 10..100;
        assert range.covers(50..60);
    }
}
```

To write more feature-rich tests using the features of XUnit, the XUnit module should be 
imported into the module to be tested.

```ecstasy
module MyApplication {

    package xunit import xunit.xtclang.org;

}
```

Test methods cannot be placed inside none-static inner classes or inside special classes such as 
enums.

### Basic Test Annotations

The XUnit framework contains a number of annotations that can be used to customize the behavior of 
tests. These annotations are part of the `xunit.annotations` package.

- `@AfterAll` is used to annotate a function that will be executed after all tests in the 
functions containing class and any subclasses have executed. If the function is in a module or 
package, then the function will execute after all tests in the entire module or package have 
executed. The annotated function will execute regardless of whether any tests have failed or not.

- `@AfterEach` is used to annotate a method or function that will be executed after each test in the 
containing class. If the method or function is in a module or package and is static, then the 
method or function will execute after each test in the entire module or package has executed. 
The annotated function will execute regardless of whether any tests have failed or not.

- `@BeforeAll` is used to annotate a function that will be executed before all tests in the 
containing class and any subclasses have executed. If the function is in a module or package, 
then the function will execute after all tests in the entire module or package have executed. 
If the annotated function throws an exception, no further "before" processing will be executed 
and no tests will be executed for the containing class or any subclasses. Any "after all" 
processing will still be executed.

- `@BeforeEach` is used to annotate a method or function that will be executed before each test in 
the containing class is executed. If the method or function is in a module or package and is 
static, then the method or function will execute before each test in the entire module or 
package is executed. If the annotated method or function throws an exception, no further "before" 
processing will be executed and no tests will be executed for the containing class or any 
subclasses. Any "after" processing will still be executed.

- `@Disabled` is used to annotate a test method or function that should be ignored by the test 
runner. The annotation takes a single string argument that can be used to provide a reason for 
ignoring the test, which will be displayed in the test output.

- `@DisplayName` is used to annotate a test method or function to provide a custom name for the 
test that will be displayed in any test output.

### Test Fixture Lifecycle

By default, XUnit will create a new instance of a test class before executing each test in the 
class. So each test method in a class will be executed in a separate instance of the class. It 
is important to be aware of this behavior when writing tests that rely on any shared state. 
Ideally, relying on non-static shared state should be avoided in tests.

For tests that really do require shared state in the test class instance, the `@TestFixture` 
annotation can be used on the test class to specify the lifecycle of the test class instance.
The `@TestFixture` annotation takes a single parameter, which is an enum value that specifies the 
lifecycle of the test class instance.

- `@TestFixture(EveryTest)` this is the default behaviour where a new instance of the class is 
  created for every test method.
- `@TestFixture(Singleton)` a single instance of the class is created and all test methods in 
  the class will use that instance.

Ecstasy modules and packages are singletons, so any tests in a module or package will use the same 
instance of the module or package.  


### Assertions

Ecstasy's `assert` keyword already offers excellent assertion support so there is no real need to use 
any alternative assertion library.

#### Asserting Exceptions

One place where XUnit offers more flexibility is when asserting that some code should throw an 
exception. The `xunit.assertions.assertThrows` function can be used to assert that a block of code 
throws a specific type of exception. The function returns the exception that was thrown, to 
allow further assertions to be performed on it.

There are two forms of the `assertThrows` function:

- `void assertThrows(Type, Function)` which just asserts that the function throws an exception of 
the specified type.
- `<E extends Exception> E assertThrows(Function)` which asserts the function throws an 
  excpetion of type `E` and returns the thrown exception. 

For example, if an application has a const that should always be a positive number between 1 and 
100, we can test that the const throws an exception when the number is outside the valid range:

```ecstasy
module MyApplication {

    const NumberHolder(Int number) {
        assert() {
            assert:arg number > 0 && number < 100 as "Number must be between 1 and 99";
        }
    }

    @Test
    void shouldNotCreateHolderWithNegativeNumber() {
        assertThrows(IllegalArgument, () -> new NumberHolder(-1));
    }
}
```

If we wanted to assert the message of the exception, we could use the second form of the 
`assertThrows` function:

```ecstasy
    @Test
    void shouldNotCreateHolderWithNegativeNumber() {
        IllegalArgument thrown = assertThrows(() -> new NumberHolder(-1));
        assert thrown.message == "Number must be between 1 and 99";
    }
```

## Running Tests

XUnit tests can be run from the command line using the `xtc test` command.

The `xtc test` command take a number of options that can be used to control the behavior of the 
test runner. The options are the same as when running an Ecstasy module, with the additions of 
some test-specific options.

```
  -d, --deduce                     Automatically deduce locations when possible
  -h, --help                       Display this help message
  -L <path>                        Module path (can be specified multiple times, or use : separator)
  -v, --verbose                    Enable verbose logging and messages
  --version                        Display the Ecstasy runtime version
  --no-recompile                   Disable automatic compilation
  -o <file>                        If compilation is necessary, the file or directory to write compiler output to
  -I, --inject <name=value>        Specifies name/value pairs for injection; format is 'name=value'
  -c, --test-class <class>         the fully qualified name of a class to execute tests in
  -g, --test-group <group>         only execute tests with the specified @Test annotation group
  -p, --test-package <package>     the name of a package to execute tests in
  -t, --test-method <method>       the fully qualified name of a test method to execute
```

For example, if we have a module named `my-app.xtc` that contains test, the tests can be executed 
using the following command:

```shell
xtc test my-app.xtc
```

If the module file is not in the current directory, then just like with running the application, 
we can specify a module path.

For example if all the modules are under a directory named `libs/`:

```shell
xtc test -L libs/ my-app.xtc
```

### Running Specific Tests

By default, XUnit will run all tests in a module. If we want to run only specific tests, we can 
use one of the command line options that allow us to specify a test or tests.

The `--test-class` option allows us to specify the fully qualified name of a class to execute 
tests in. For example if an application had a test class named `Foo`, in a package named 
`a.b.c`, we could run the tests only in the `Foo` class using the following command:

```shell
xtc test --test-class a.b.c.Foo my-app.xtc
```

If we wanted to run tests in class `a.b.c.Foo` and `d.e.f.Bar`, then we can use the `--test-class` 
option twice:

```shell
xtc test --test-class a.b.c.Foo --test-class d.e.f.Bar my-app.xtc
```

The `--test-method` option allows us to specify the fully qualified name of a test method to 
execute. For example, if we wanted to run only the `testFoo` method in the `Foo` class, we could 
use the following command:

```shell
xtc test --test-method a.b.c.Foo.testFoo my-app.xtc 
```

The `--test-package` option allows us to specify the fully qualified name of a package to execute 
all tests in.  

For example, if we wanted to run all the tests in the package `a.b.c`, we could use the 
following command:

```shell
xtc test --test-package a.b.c my-app.xtc 
```
