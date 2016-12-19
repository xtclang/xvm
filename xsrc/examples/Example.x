// TODOC - outermost item in a file needs to have explicit access modifier (context can not be assumed to be implicit beyond file boundary)
public class Example
    {
    protected int?[] x = new int[5];
    private People[] = new People[5] -> f();
    }

value Point(int x, int y);

class Point implements Value // and is immutable after construction
    {
    validator(int x, int y) {..}

    initializer(int x, int y)
        {
        this.x = x;
        this.y = y;

        Immutable = true; // ???
        }

    package @soft int x;
    module int y;

    // hashcode
    // equals
    // tostring
    // etc.
    }

Point origin = new Point(y:0, x:0);

trait AddHashcode
    {
    int hashcode()
        {
        import x.List as Putty;

        return super() + new Putty("hello", "world").hashcode();
        }
    }

interface Bar extends Foo
    {
    }

interface Foo<T>
    {
    Foo<T1> op<T1, T2>(Foo<T2> f);
    }

public class TraitExample
    {
    Object foo(@AddHascode Object o)
        {
        return o;
        }

    void test()
        {
        RA ra = new RA();
        ra.
        }

    private class RA // implements Runnable
        {
        private void run()
            {
            // TODO...
            }

        Runnable as<Runnable>()
            {
            return this:private; // this would be bad
            }

        void runAsync()
            {
            // TODO...
            }
        }

    foo(Ref<Integer> i)
        {
        int x = i++;   // i is an l-value
        // t = i.incAndGet(1);
        // x.set(t);
        }

    int v = 1;
    foo(v!);
    S.O.print(v); // 2
    }


value String(@ro char[] Chars)
    {
    foo
        {
        String? s = ..;
        if (s?)
            {
            s.length;
            }
        }

    @lazy int Hashcode
        {
        int get() {...}
        }
    }



-- this --

@future int i = svc.next();
if (!i!.isDone(100ms)) {...}

-- or that --

Future f = i!;
if (!f.isDone(100ms)) {...}

--

if (!&i.isDone(100ms)) {...}   // 1 vote - it's the same FUGLY as C++
if (!i&.isDone(100ms)) {...}   // 1 vote - it's the same FUGLY as C++
if (!(i).isDone(100ms)) {...}
if (!<i>.isDone(100ms)) {...}
if (!^i.isDone(100ms)) {...}

public value Point(int x, int y);
Point p = ...
Property prop = p.&x;
Method mGet = p.&x.&get;


public class Whatever { void foo(); int foo(int x); void foo(String s); }
Method m = Whatever.foo(int)!;

Whatever w = ...
Function f = w.&foo();
Function f = w.foo; // ??? overloaded

&x.y    x!.y
(&x).y  x!.y
&(x.y)  x.y!
x.&y    x.y!


class Person {
  int age;
  boolean oldEnough();

  void foo(Map<String, Person> map) {
    map.values(oldEnough);
    map.values(p -> p.age > 17);
  }
  static int add(int a, int b) -> a + b;
  }


class Map<K,V>
  {
  Set<V> values(boolean filter(V v))
    {
    entries(e -> filter(e.value)).map(e -> e.value);
    }
  }

class Filter1<V>
  {
  boolean evaluate(V v);
  }

class Map<K,V>
  {
  function<V> boolean Filter2(V v);

  Set<V> values(Filter2 filter)

  Set<V> values(Any filter)
    {
    entries(e -> filter(e.value)).map(e -> e.value);
    }
  }

test() {
  Filter2<Person> f2 = p -> p.isMale;
  foo(map.values(f2));

  Filter1<Person> f1 = getFilter1FromSomewhere();
  foo(map.values(f1.evaluate);

  foo(map.values(p -> p.isMale);

  Bob bob = new Bob();
  foo(map.values(bob.testBob(?, 3));
  foo(map.values(Bob.testAge(7));  // REVIEW
}

class Bob extends Person {
  boolean testBob(Person o, int x) {..}
  boolean testAge(int x) {..}
}

foo(Map<String, Person> map) {
  class Any {
    boolean Bar(Person p);
  }
  function boolean Bar(Person p);
  static Bar X = p -> p.age > 17;
  map.values(X);
}

--

module M1 {
  package P1 {
    class C1 {
    #ifdef V1
      int X;
      public void foo() {..};
   #else ifdef V2
      long X;
      private void foo() {..};
      #endif
    }
  }
}

import M1;
module M2 {
  class C2 {
    void main() {
      C1 o = new M1.P1.C1();
      print o.X;
      print o.Y;    // compiler error! no such property
      #ifdef DEBUG
      print o.Y;
      #endif
    }
  }
}


module M3 {
  package P3 {
    class C3 {
      #ifdef TEST
        int X;
      #else
        long X;
      #endif
    }
  }
}


#ifdef A
  ...
  #ifdef B
  ...
  #endif
#endif

#ifdef B
 ..
 #ifdef A
 ..
 #endif
#endif

// for some <T>

boolean f(T& value)

T value;
while (f(&value))
  {
  ...
  }

//

interface Listener<E>
  {
  void notify(E event);  // this method implies "consumer" aka "? super .."
  E getLastEvent();      // this method implies "provider" aka "? extends .."
  }

interface NamedCache<K, V>
  {
  void addValueListener(Listener<? super V> listener)
    {
    m_listener = listener;
    }

  void repeat()
    {
    // legal:
    m_listener.notify(m_listener.getLastEvent());

    // illegal:
    Object o = m_listener.getLastEvent();
    m_listener.notify(o);

    // legal:
    Object o = m_listener.getLastEvent();
    m_listener.notify((V) o);
    }

  void put(K key, V value)
    {
    m_listener.notify(value);
    }
  }

class SomeApp
  {
  @inject Listener listenerOfAnything;
  @inject NamedCache<String, Person> people;

  void main()
    {
    people.addValueListener(listenerOfAnything); // compiler error???
    }
  }

// example 2 with auto-infer of consumer/producer "? super" crap

interface Listener<E>
  {
  void notify(E event);  // this method implies "consumer" aka "? super .."
  }

interface NamedCache<K, V>
  {
  void addValueListener(Listener<V> listener)  // implied: "? super"
    {
    m_listener = listener;
    }

  void test(Object o, V value)
    {
    // legal:
    m_listener.notify(value);

    // illegal:
    m_listener.notify(o);
    }

  void put(K key, V value)
    {
    m_listener.notify(value);
    }
  }

class SomeApp
  {
  @inject Listener listenerOfAnything;
  @inject Listener<Person> listenerOfPerson;
  @inject Listener<String> listenerOfString;
  @inject NamedCache<String, Person> people;

  void main()
    {
    // legal (!!!)
    people.addValueListener(listenerOfAnything);

    // legal
    people.addValueListener(listenerOfPerson);

    // illegal (!!!)
    Listener listener2 = listenerOfString;
    }
  }

// example 3 with auto-infer of both consumer/producer

interface Listener<E>
  {
  void notify(E event);
  E getLastEvent();
  }

interface NamedCache<K, V>
  {
  void addValueListener(Listener<V> listener)
    {
    m_listener = listener;
    }

  void test(Object o, V value)
    {
    // legal:
    m_listener.notify(value);

    // illegal:
    m_listener.notify(o);

    // legal:
    Object o2 = listener.getLastEvent();

    // legal:
    V v2 = listener.getLastEvent();
    }

  void put(K key, V value)
    {
    m_listener.notify(value);
    }
  }

class SomeApp
  {
  @inject Listener listenerOfAnything;
  @inject Listener<Person> listenerOfPerson;
  @inject Listener<String> listenerOfString;
  @inject NamedCache<String, Person> people;

  void main()
    {
    // illegal (!!!)
    people.addValueListener(listenerOfAnything);

    // legal
    people.addValueListener(listenerOfPerson);

    // illegal?
    people.addValueListener(listenerOfString);
    }
  }

// example 4

// the goal with container types and generics is to be able to have a "List of String"
// be usable as a "List of Object". In other words, just like a List can be treated as
// an Object, and a String can be treated as an Object, a List<String> should be treatable
// as a List<Object>

// the first challenge of this is that when one treats a List<String> as a List<Object>,
// that implies that methods that would otherwise only accept a String must now also
// accept any Object. more specifically, it's not that the List has to accept any object,
// but that it is possible for the List to expose itself as a more generic container, i.e.
// as a container of any Object
List<String> listString = new List<String>();
// so what can I add to a List of String? just a String; nothing else
listString.add("hello"); // legal
listString.add(new Object()); // illegal - compile time error! (and it would also be a RTE)
// but a List<String> is (i.e. can be used as) a List<Object> right?
List<Object> listObject = listString; // legal
// so what can I add to a List of Object? any Object, right?!?!
listObject.add("hello"); // legal
listObject.add(new Object()); // compiler does not detect the error, but it _IS_ a RTE!

// in this manner, container types (parameterized types) act like "array" does in Java.
// consider the method:
public void foo(List<Object> list) {...}
// now, using the list from the above example:
foo(listString); // legal
foo(listObject); // legal

// so let's imagine what occurs within foo(), and how that actually works:
public void foo(List<Object> list)
    {
    // take the first item out of the list
    Object o = list.remove(0);
    // add that item to the end of the list
    list.add(o);
    }

// ok, that should work fine. if something is in the list, it shouldn't be
// a problem sticking it back in, right? but what if it did something like this?
public void foo(List<Object> list)
    {
    // add a plain old object to the end of the list
    list.add(new Object());
    }
// this one should fail at runtime! because in reality the list is a list of string,
// and the "new Object()" is not a string! but what it implies is that the ref being
// passed to foo() is actually a ref to a List<Object>, in that there exists a method
// for List.add(Object) for example. and somehow that List.add(Object) goes to a piece
// of code that verifies (type asserts) that the argument is actually a String

// let's consider this in terms of "auto cast from sub-class towards object" versus
// "auto cast from object (or some other super class) to sub-class". when a List<String>
// is passed to a method that takes List<Object>, that is an implicit "auto-cast from
// String to Object" on the "out values" (e.g. return values), and an implicit "auto-cast
// from Object to String" on the "in values" (e.g. parameter arguments).

// to illustrate this, let's create two interfaces, one that is for return values and one
// that is for parameters:
interface Extractor<T>
    {
    public T first();
    }
interface Logger<T>
    {
    public void add(T value);
    }

// let's also assume that the List interface includes the same:
interface List<T>
    {
    public T get(Int i);
    // ...
    public T first();
    public T last();
    // ...
    public void add(T value);
    public boolean remove(T value);
    // etc. ...
    }

// so anything that implements List<T> also implements Extractor<T> and Logger<T>.
// let's imagine methods that take these interfaces:
void doObjectLogging(Logger<Object> logger)
    {
    logger.add(new Object());
    }
void doStringLogging(Logger<String> logger)
    {
    logger.add("hello");
    }
void doObjectExtracting(Extractor<Object> extractor)
    {
    Object o = extractor.first();
    }
void doStringExtracting(Extractor<String> extractor
    {
    String s = extractor.first();
    }

// now, going back to our previous example:
doObjectLogging(listString);    // compile: OK. runtime: RTE!
doStringLogging(listString);    // compile: OK. runtime: OK.
doObjectExtracting(listString); // compile: OK. runtime: OK.
doStringExtracting(listString); // compile: OK. runtime: OK.

doObjectLogging(listObject);    // compile: OK. runtime: RTE!
doStringLogging(listObject);    // compile: ERR! (cast required). runtime: OK.
doStringLogging((List<String>) listObject); // compile: OK. runtime: OK.
doObjectExtracting(listObject); // compile: OK. runtime: OK.
doStringExtracting(listObject); // compile: ERR! (cast required). runtime: OK.
doStringExtracting((List<String>) listObject); // compile: OK. runtime: OK.

// Now, let's introduce a List that only contains objects:
List<Object> listOnlyObject = new List<Object>();
// and let's fix the obvious compile errors and see the results:
doObjectLogging(listOnlyObject);                    // compile: OK. runtime: OK.
doStringLogging((Logger<String>) listOnlyObject);     // compile: OK. runtime: RTE. (not List<String>)
doObjectExtracting(listOnlyObject);                 // compile: OK. runtime: OK.
doStringExtracting((Extractor<String>) listOnlyObject);  // compile: OK. runtime: RTE. (not List<String>)

//                   widening                narrowing
// T<P1> -> T<P2>    P1=String -> P2=Object  P1=Object -> P2=String
// ----------------  ----------------------  -------------------------
// !(T consumes P1)  1. Implicit conversion  2. Illegal (Compile Time Error)
// !(T produces P1)  3. Implicit conversion  4. Implicit conversion
//                      (but possible RTE)

// so, for a genericized type parameterized with T, we say that the type "consumes T" iff there
// is at least one method that satisfies any of the following:
// (1) has T as a parameter type;
// (2) has a return type that "consumes T";
// (3) has a parameter type that "produces T".

// for a genericized type parameterized with T, we say that the type "produces T" iff there
// is at least one method that satisfies any of the following:
// (1) has T as a return type;
// (2) has a return type that "produces T";
// (3) has a parameter type that "consumes T".

// while we define "produces" and "consumes" in the positive sense, we only use it in the
// negative sense

// implicit converson from T2 to T1 example:
// T2 v2 = ...
// T1 v1 = v2;
//
// implicit conversion from non-genericized type T2 to T1;
// 1) if T2 === T1
// 2) if T2 > T1 (e.g. T2 is a sub-class of T1, e.g. T2 is String and T1 is Object)
//
// implicit converson from T2<P2> to T1<P1> example:
// T2<P2> v2 = ...
// T1<P1> v1 = v2;
//
// implicit conversion from genericized type T2<P2> to T1<P1>:
// 1) if the non-genericized T2 can be implicitly converted to the non-genericized T1, ___AND___
// 2) a) if the non-genericized P2 can be implicitly converted to the non-genericized P1 __OR__
//    b) if P1 > P2 (e.g. P1 is String, P2 is Object) _AND_ T1 does not "produce" P1

// --

// java helper
List<T> <T extends Comparable> sort(List<T> list) {...}

// xtc equivalent
List<list.T> sort(List<Comparable> list) {...}

--

interface List<T>
  {
  void add(T value);
  int size;
  T get(int i);
  }

class ArrayList<T>
    implements List<T>
  {
  // ...
  }

interface Map<K,V>
  {
  interface Entry<K,V> // auto-picked-up from Map? or do they need to be spec'd?
    {
    K key;
    V value;
    }
  // ...
  }

void foo(Map<K,V> map)
  {
  foo2(new ArrayList<map.Entry>);
  }

void foo2(List<Map.Entry> list)
  {
  list.T.K key = list.get(0).key;
  list.T.V val = list.get(0).value;
  }

--

// return types

void foo();

String foo();

(String, Int) foo();

// with names
(String name, Int age) foo();

// method type params

T foo<T>();

(T1, T2) foo<T1, T2, T3>(T3 value);

// method params

--

// tuple example
Tuple t = (1, "hello");
Int n = t[0];
String s = t[1];
Int n2 = t[1]; // compile time error (and obviously it would be a runtime error if it could compile)

// basically, it's some way to do non-uniform index of an "array" like structure
// i.e. fields by index
interface UniformIndexed<IndexType, ValueType>
interface Indexed<ValueType...>

--

class MyFunction<P1, P2, R>
    {
    Tuple<P1, P2> Params;

    R foo(P1 p1, P2 p2) {...}
    }


Class clz = MyFunction;

...
MyFunction f = new MyFunction();

--

    interface Tuple<FieldTypes...>

void foo(Tuple<Int, String> t)
  {
  Int i = t[0];
  }

void foo(Tuple<T0, T1> t)
  {
  T0 val0 = t[0];
  T1 val1 = t[1];
  }

// so for a multi-type list ("..." format), each element can be referred to using:
// 1) a constant
// 2) a parameter

interface Tuple<FieldTypes...>
    {
    FieldTypes[index] get(Int index);
    void set(Int index, FieldTypes[index] newValue);
    // ...
    }

void foo(Tuple t)
    {
    t.FieldTypes[0] val0 = t[0]; // ugly but correct
    }

void foo(Tuple t, Int i)
    {
    t.FieldTypes[i] val = t[i]; // even uglier but correct
    }

// --

// numbers:
// how to have number literals be usable as "int" or "float"?
// how to have support for various sized and signed ints? signed/unsigned, 8/16/32/64/128/etc.
// how to have detection for out of bounds numbers? underflow/overflow

Number PI = 3.14159267;
Float f = PI;
Dec d = PI;
Integer MAX_UINT8 = 255;
Int n = MAX_UINT8;

Byte b = 0x00;  // compile time OK, runtime OK
Byte b = 0xFF;  // compile time OK, runtime OK
Byte b = -1;    // compiler error, RTE
Byte b = 0x100; // compiler error, RTE
// so the compiler has to know something here about the range of Byte
// or the compiler has to know how to use Byte at compile time to find out what is legal here
// the values (0x00, 0xFF, -1, 0x100) are all "literals"
// what if instead it were a variable?
Int n = 255;
Byte b = n; // should this be a compiler error? it wouldn't be an RTE!
// or this instead
Byte b = n.to<Byte>();
// so what does this mean?
Byte b = (Byte) n; // error --> n is a ref to Int, not Byte! this is attempting to assign the ref itself
// maybe there is an @auto that one can add to a "to<T>()" method to indicate that the compiler add it automatically
class Int {... @auto Byte to<Byte>() {...}}
// then there could be an IntegerLiteral type with:
@auto Int16 to<Int16>()
@auto Int32 to<Int32>()
@auto Int64 to<Int64>()
@auto Int128 to<Int128>()
@auto Dec32 to<Dec32>()
@auto Dec64 to<Dec64>()
@auto Dec128 to<Dec128>()
@auto Float32 to<Float32>()
@auto Float64 to<Float64>()
@auto Float128 to<Float128>()
// etc.
// and a FPNumberLiteral type with:
@auto Dec32 to<Dec32>()
@auto Dec64 to<Dec64>()
@auto Dec128 to<Dec128>()
@auto Float32 to<Float32>()
@auto Float64 to<Float64>()
@auto Float128 to<Float128>()
// so the compiler simply needs to map literal numeric values to one of two
// "constant types", that in turn map to IntegerLiteral or FPNumberLiteral

// --
Int64  x = ..
Int32  y = ..
UInt16 z = ..
Int64 r = x * y * z; // or some similar
// how are these "combined" / converted?
// 1) It's pretty obvious that Int64 should have "Int64 mul(Int64 n);"
// 2) Does it _also_ have a mul(Int32) and mul(UInt16) and so on?
// 3) Or does it have a mul(Integer)?
// 4) And/or a mul(Number)?

//--

// java int-to-byte assignment
int n = ...
byte b = (byte) n; // NEVER EVER EVER EVER EVER CORRECT! NOT ONE SINGLE TIME!!!
byte b = (byte) (n & 0xFF); // ALWAYS! EVERY SINGLE TIME! EVERY SINGLE TIME CORRECT!

byte b = 128; // ILLEGAL!!! COMIPLER ERROR!!!

// -- min/max
int x;
int y;
z = x.max(y);


// ---

Array<ElementType> : List<..>
  {
  int length;
  ElementType get(int)
  void set(int, ElementType)
  Element<ElementType> elementAt(int)

  Element<ElementType> : Ref<..>
    {
    value = get()
    set(value)
    }
  }

List<String> ls = ..
// can't do this
// List<? extends Object> lo = ls;
List<Object> lo = ls; // is this a warning? (???) is it legal? (yes)

foo1(ls);
foo2(ls);

foo1(ls, new Object());

void foo1<T>(List<T> ao, T v) {
ao.add(v);
}

void foo2(List<Object> ao) {
  ao.set(0, new Object()); // calls the method "set(int,Object)V" on the ao reference
}
foo(ls); // does NOT have a "set(int,Object)V" ... does it? needs a "shim"?

// tools:
// (1) shim
// (2) immutable
// (3) @ro

// so how about this:
foo(@ro Object[] ao)
  {
  ao.set(0, new Object()); // fails -- at COMPILE time
  }

Set<V>
  {
  boolean contains(V value) {...}
  boolean contains(!V value) {return false;}
  // or ...
  boolean contains(V value) {...} else {return false;}
  // this would have been the shim provided by the runtime if we didn't have a "!V":
  // boolean contains(Object value) {if value instanceof V return contains((V) value) else throw RTE}


  boolean add(V value) {...}
  // shim:
  boolean add(!V value) {throw RTE}
  // or to make mark happy
  boolean add(SHIMTYPE value) {if value instanceof V return add((V) value) else throw RTE}
  }

// -- how to do immutability?

Object
  protected Meta meta

Meta
  boolean Immutable
  // TODO composition of the type itself / reflection

// -- function / method / type short-hand

Iterator iter =
    {
    conditional ElementType next()
        {
        if (...)
            {
            return true, list[i];
            }

        return false;
        }
    }
