interface List<T>
  {
  void add(T value);
  int size;
  T get(int i);
  }

{String get(int)} instanceof {Object get(int)} == true
{void add(String)} instanceof {void add(Object)} == true // only true because we need it to be

class Provider<T>
    {
    T get(int i);
    }

String stringExtractor(Provider<String> provider)
	{
	return provider.get(0);
	}

int intExtractor(Provider<int> provider)
	{
	return provider.get(0);
	}

List<Object> lo = new ArrayList();
String s = stringExtractor(lo);
int n = intExtractor(lo);

{void add(Object)} instanceof {void add(String)} == false // can I say Object o = "foo"

{Object get(int)} instanceof {String get(int)} == false // can I say String s = new Object();

class Logger<T>
    {
    void add(T t);
    }

// why is it LEGAL to pass a List<Object> to this method? i.e List<S> = List<O>
void stringLogger(Logger<String> logger)
	{
	logger.add("hi cam");
	}

void intLogger(Logger<int> logger)
	{
	logger.add(42);
	}

stringLogger(lo); // would be an error today ..
intLogger(lo);    // would be an error today ..


//

List<Object>
  void add(Object)
  Object get(int)

List<String>
  void add(String)
  String get(int)

List<int>
  void add(int)
  int get(int)


//

interface LogFile<T>
  {
  void log(T val);
  @ro T last;
  }

class SuperDuperLogFile<T>
    implements LogFile<T>
  {
  // ...
  }

interface Logger<T>
  {
  void log(T val);
  }

interface Recent<T>
  {
  @ro T last;
  }

void logSomeObject(Logger<Object> logger)
  {
  // this will work for Logger<Object>
  // this will work for Logger<int>
  // RTE for any other
  logger.log(3);
  }

void logSomeString(Logger<String>)
  {
  // this will work for Logger<Object>
  // this will work for Logger<String>
  // RTE for any other
  logger.log("Hello world!");
  }

void extractSomeObject(Recent<Object> history)
  {
  // this will work for any Recent<T>
  Object o = history.last;
  }

void extractSomeString(Recent<String> history)
  {
  // this will work for Recent<String>
  // RTE for any other
  String s = history.last;
  }

void test()
  {
  LogFile<Object> logAny = new SuperDuperLogFile<Object>;
  LogFile<String> logStr = new SuperDuperLogFile<String>;
  LogFile<int   > logInt = new SuperDuperLogFile<int   >;

  logSomeObject(logAny); // types match, no RTE
  logSomeObject(logStr); // auto-castable ("only b/c we need it to be"), but RTE!
  logSomeObject(logInt); // auto-castable ("only b/c we need it to be"), no RTE

  logSomeString(logAny); // auto-castable (naturally), no RTE
  logSomeString(logStr); // types match, no RTE
  logSomeString(logInt); // compile time error! (would also be an RTE)

  extractSomeObject(logAny); // types match, no RTE
  extractSomeObject(logStr); // auto-castable, no RTE
  extractSomeObject(logInt); // auto-castable, no RTE

  extractSomeString(logAny); // compile time error! (would also probably be an RTE)
  extractSomeString(logStr); // types match, no RTE
  extractSomeString(logInt); // compile time error! (would also be an RTE)
  }

// --- narrowing of return values

class StringBuilder
  {
  // return value is not actually compiled as "StringBuilder", but rather as "this-class"
  StringBuilder append(String s) {..}
  }

class SuperDuperStringBuilder extends StringBuilder
  {
  // implicitly: SuperDuperStringBuilder append(String s);
  }

// ----- syntax for "this class" etc.

class Box<T>
  {
  class SomeOtherInner<T> {..}

  // allocation
  new Inner();
  // or
  Inner.new();

  class Inner<T>
    {
    T foo2();       // same as "Inner.T"
    Inner.T foo3(); // same as "T"
    this.T foo4();  // seems like this should also be legal

    Inner foo4();           // auto-narrowing
    Box foo5();             // auto-narrowing?
    SomeOtherInner foo6();  // auto-narrowing?

    Box.this foo7();        // the problem with this is ..
    Box.this.T foo7();      // this

    // so will we have "static" inner classes? i.e. no ref to outer
    // do we specify that the inner is "static", or that it is "instance"
    // maybe @orphan vs. @child
    @child class Inner<T>
      {
      // has implicit "Box" property?
      // assume that either of these should work:
      Box.T foo();
      this.Box.T foo();
      }



    Box.T foo1(); // error: need a ref to Box
    SomeOtherInner.T; // error: need a ref to SomeOtherInner


    }

  }


// --- structs

class Base
  {
  private int x;
  }
  
class Derived
  {
  private int x;
  }
  
// what does the struct look like?

struct Derived
  {
  int x;
  int x;	// uh .. nope!
  }
  
// answer 1 - i'm writing code and i know my hierarchy
class Derived
  {
  private int x;
  
  void writeExternal(OutputStream out)
    {
    int n1 = struct:Derived.x
    int n2 = struct:Base.x
    }
  }

// answer 2 - for code that has to work on any arbitrarily
// horribly complicated hierarchy of composition
mixin SuperDuperSerializer
  {
  construct SuperDuperSerializer(InputStream in)
    {
    // there is some "struct" here that we get to fill in
    // 1) is it a paramater to the constructor?
    // 2) or is it like "this" in that it is implicitly available?
    // ...
    }
    
  void writeExternal(OutputStream out)
    {
    // ...
    Struct struct = this:struct;
    
    // or
    for (Struct struct : this) // or this.???
    }
  }

// --- static v non-static new

class BaseParent
  {
  Child makeMeABaby() { return new Child(); }
  
  class Child {...}
  
  static class Orphan {...}
  }
  
class DerivedParent
	extends BaseParent
  {
  class Child {...}

  static class Orphan {...}
  }

BaseParent p1 = new BaseParent()
BaseParent p2 = new DerivedParent()
Child      c1 = new p1.Child();	// cannot say "new BaseParent.Child()" - exception!!!
Child      c2 = new p2.Child();
Child      c3 = p2.makeMeABaby();

Orphan     o1 = new p1.Orphan();
Orphan     o2 = new p2.Orphan();

Orphan     o3 = new BaseParent.Orphan();     // is this a good idea to allow?
Orphan     o4 = new DerivedParent.Orphan();	 // no! this is an exception (or just discouraged?)
Orphan     o5 = BaseParent.Orphan.findConstructor(Void)();

// question is "how do you de-serialize an orphan?" if you can't "new" the
// orphan by its class?


// ---- what is a "static final" constant field and a "static method" in ecstasy?

class Math
  {
  // java has "static final double PI = 3.14;"
  static double PI = 3.14; // code kind of looks like Java
  // but it actually is a nested class called "PI" of type "Double" (which is a value)
  // and it is a "singleton value", just like "Null" and "True" and "False"
  
  // java has "static int max(int v1, int v2) {...}"
  static int max(int v1, int v2) {...}
  // but it is NOT a method
  // it is actually a nested class called "max" of type "Function" (which is a value)
  // and it is a "singleton value"
  }