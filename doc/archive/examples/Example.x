// TODOC - outermost item in a file needs to have explicit access modifier (context can not be assumed to be implicit beyond file boundary)
public class Example
    {
    protected int?[] x = new int[5];
    private People[] = new People[5] -> f();
    }

const Point(Int x, Int y);

class Point implements Const // and is immutable after construction
    {
    construct(Int x, Int y)
        {
        this.x = x;
        this.y = y;

        meta.immutable = true;
        }

    Int x;
    Int y;

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
        String | Runnable s = foo();
        if (s  insof Strring)
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

boolean f(T& value)           // note (Apr '18): we are NOT doing this; use "Ref<T>" or "Var<T>" instead

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
doObjectLogging(listString);    // compile: OK. runtime: RTE! Object auto-narrowing to String!!!
doStringLogging(listString);    // compile: OK. runtime: OK.  (DUH!)
doObjectExtracting(listString); // compile: OK. runtime: OK.  String auto-widening to Object is safe
doStringExtracting(listString); // compile: OK. runtime: OK.  (DUH!)

doObjectLogging(listObject);    // compile: OK. runtime: RTE!
doStringLogging(listObject);    // compile: ERR! (cast required). runtime: OK.
doStringLogging((List<String>) listObject); // compile: OK. runtime: OK.
doObjectExtracting(listObject); // compile: OK. runtime: OK.
doStringExtracting(listObject); // compile: ERR! (cast required). runtime: OK.
doStringExtracting((List<String>) listObject); // compile: OK. runtime: OK.

// Now, let's introduce a List that only contains objects:
List<Object> listOnlyObject = new List<Object>();
// and let's fix the obvious compile errors and see the results:
doObjectLogging(listOnlyObject);                        // compile: OK. runtime: OK.
doStringLogging((Logger<String>) listOnlyObject);       // compile: OK. runtime: RTE. (not List<String>)
doObjectExtracting(listOnlyObject);                     // compile: OK. runtime: OK.
doStringExtracting((Extractor<String>) listOnlyObject); // compile: OK. runtime: RTE. (not List<String>)

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

// implicit conversion from T2 to T1 example:
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
interface UniformIndexed<Index, Value>
interface Indexed<Value...>

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

Array<Element> : List<..>
  {
  int length;
  Element get(int)
  void set(int, Element)
  Element<Element> elementAt(int)

  Element<Element> : Ref<..>
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

typedef Map<String, Person> People;

Iterator<Element> iter =
    {
    conditional Element next()
        {
        if (...)
            {
            return true, list[i];
            }

        return false;
        }
    ElType el;
    if (el : iter.next())
        {
        print(el);
        }
    }

// equality

// when i say:
if (a == b) {...}
// how does that compile?
// first of all, compile time type of a and b must be equal
// second, the compile time type must _contain_ function Boolean equals(CTT value1, CTT value2)

Int i1 = 1;
Int i2 = 2;
Number n1 = i1;
Number n2 = i2;
if (i1 == i2) {...}
if (n1 == n2) {...}
if (i1 == n2) // CTE!!!

if (&i1 == &i2) {...} // false
if (&i1 == &n1) {...} // TRUE !!!!!!
i2 = 1;
if (&i1 == &i2) {...} // TRUE !!!!!!

Object o1, o2;
// ...
if (o1 == o2) // CTE!!!
if (&o1 == &o2)
if (&p1 == &o2)
if (((Object) p1) == o2)

// --- function as dynamic proxy

// let's say we have a function
function (Int, Boolean) fn(Int, Int) = (x, y) -> {x+y, True};

(i, b) = fn(1,2);

function Int (String) fn = String.length;
String s = "hello";

function String (Int) fn = s.substring(2, _);

function Int () fn = s.length.get;

// we know that it takes some number of parameters (0+) and has some number of return values (0+)
Int x  = fn(1, 2);
Int x2 = fn.invoke((1,2));
Int x3 = fn.invoke.invoke(((1,2)));
Int x4 = fn.invoke.invoke.invoke((((1,2))));

Function fn3 = fn.invoke;
fn3.invoke(((1,2)));

Function fn2 = fn;
((function Int fn(Int, Int)) fn2)(3, 4); // duh
Tuple tupleReturnValues = fn2.invoke(tupleParams);

fn = fn2; // this is the only question
// i.e. ...
function Int fn3(Int, Int) = fn2.to<function Int (Int, Int)>;

// so now I just have to create a Function
Function<f.ReturnTypes, f.ParamTypes> once(Function f)
    {
    return new Function()
        {
        Tuple invoke(Tuple params)
            {
            if (!alreadyDone)
                {
                prevReturnValue = f.invoke(params);
                alreadyDone = true;
                }
            return prevReturnValue;
            }
        Boolean alreadyDone = false;
        Tuple prevReturnValue = Void;
        }
    }

function Int (Int, Int) fn4 = once(fn).to<function Int (Int, Int)>;

Int test1 = fn4(1,2);
Int test2 = fn4.invoke((1,2))

// ---

function Void (Int) consumer = ...
function Void consumer(Int) {...}


foo(function Void consumer1(Int), function Void consumer2(Int))

// ---

service A
    {
    Void main()
        {
        B b = new B(this.bar);

        try critical
            {
            @future Boolean f1 = b.foo();

            // do some local mutation
            list.foreach(...);
            // etc. ...

            @future Boolean f2 = b.foo();

            // do some local mutation
            list.add(...);
            // etc. ...
            }
        catch (Deadlock e)
            {
            // WTF can we do anyhow?
            }

        Boolean f3 = f2.get();
        }

    Void bar()
        {
        // do some local mutation
        // ...
        list.add(...);
        // ...
        list.remove(...);
        // ...
        list.foreach(...);
        // ...
        }

    private List list = new List();
    }

service B(function Void fn())
    {
    Boolean foo()
        {
        Boolean f1 = false;

        // call out
        fn();

        // do something
        // ...
        f1 = ...
        // ...

        // call out
        fn();

        // do something else
        // ...
        f1 = ...
        // ...

        // call out
        fn();

        return f1;
        }
    }

// ---

assert expr;            // conditional assert: assertions must be enabled

assert:test expr;       // conditional assert: assertions ("test" level) must be enabled

assert:debug expr;      // conditional assert: assertions ("debug" level) must be enabled

assert:always expr;     // unconditional assertion (assertions do not have to be enabled)

assert:once expr;       // unconditional assertion that only happens one time

if:test
if:debug
if:present


assert:once ->
    {
    Boolean f = ...
    Int x = ...
    for (Int i = ...)
        {
        return false;
        }
    return true;
    };

if (@assertionsenabled)
    {
    static Boolean ALREADYDONE = false;
    if (!ALREADYDONE)
        {
        ALREADYDONE = true;
        Boolean f = ...
        Int x = ...
        for (Int i = ...)
            {
            assert ...;
            }
        }
    }

assert:once foo();

if (@assertionsenabled)
    {
    static Boolean ALREADYDONE = false;
    if (!ALREADYDONE)
        {
        ALREADYDONE = true;
        assert foo();
        }
    }

//

enum Key<ValType> {
    HOST<String>,
    PORT<Int>,
    SCORE<Dec>
}

interface PropertiesStore {
    Void put(Key key, Key.ValType value);
    Key.ValType get(Key key);
}


//

class B
    {
    Object foo1() {...}
    String foo1() {...}

    B! foo2() {...}

    Long foo3() {...}
    String foo3() {...}
    Object foo3() {...}

    Object foo4() {...}
    }

class D
        extends B
    {
    String foo1<Object>() {...}     // hides B.foo1()

    B foo2() {...}          // error
    D foo2() {...}          // hides B.foo2()

    Object foo3() {...}     // hides B.foo3()
    String foo3() {...}     // another foo3()

    String foo4() {...}     // hides B.foo4()
    }

//

interface B
    {
    Int foo()
        {
        return super();    //error
        }
    }

interface D extends B
    {
    Int foo()
        {
        return super();     // ok
        }
    }

//

interface B
    {
    Int foo()
        {
        return ...;
        }
    Int bar()
        {
        return ...;
        }
    }

interface D
    {
    Int foo()
        {
        return ...;
        }
    Int bar()
        {
        return ...;
        }
    }

interface C
        extends B
        extends D
    {
    Int foo()
        {
        return super();     // B.foo()
        }
    Int bar()
        {
        return super();     // B.bar()
        }
    }

//

// interruptable when:
// 1) you pop your service stack by returning from the zero-eth stack frame
// 2) you call yield, assuming there is such a thing

critical
    {
    // ...
    }

// or ...
try:critical
    {
    // ...
    }
catch (Deadlock e)
    {
    // ...
    }

// or ...
try (new CriticalSection())
    {
    // ...
    }
catch (Deadlock e)
    {
    // ...
    }

// a service is (obviously) an instance of Service, and Service has a “Boolean reentrant;”
// property; setting to false disallows reentrancy (causes an exception if reentrance is
// attempted)

// lastly, based on the obvious truth that “a call to console.log() should NEVER EVER CAUSE
// RE-ENTRANCY” etc., a service will be declarable in a way to say “i don’t care whether
// you’re in a critical section or not; i promise to never do anything that will call you
// back”, which is to say “from here on down is a terminal”
@nevergonnagiveyouup @nevergonnaletyoudown @nevergonnarunaroundanddesertyou service Logger
    {
    // ...
    }

//

service Toaster
    {
    Void toast(Bread bread) {...}
    }

class C
    {
    Void main()
        {
        Toaster+Service toaster = new Toaster();

        assert toaster instanceof Toaster;
        assert toaster instanceof Service;
        assert !(&toaster.as<Toaster>() instanceof Service);

        guardian.guard(&toaster.as<Service>());
        chef.doYourThing(&toaster.as<Toaster>());

        Service<Toaster> toasterService = somefactory.create(); // has
        Toaster toaster = toasterService.getImpl();
        }
    }

interface Service
    {
    @ro Boolean busy;
    @ro Int backlog;
    Void pleaseStop();
    Void iWasn'tKiddingPleasePleaseStop();
    Void fuckYouIsaidStopPleaseDoItNowOrI'llKillYou();
    Void kill();
    }

// --

const Point(Int x, Int y);

Point p = new Point(0, 0);
Int x = p.x;
p.x = 5;            // ok it's an error but only because it's a const
Property<Int> prop = &(p.x);
prop.set(5);        // ok it's an error but only because it's a const
Int x2 = prop.get();
PropertyInfo pi = Point.x;
Method mp = Point.somemethodthatdoesreflectionandgivesmeamethodbyname("x");

Int x3 = p.*pi.get();

function Int fn() = &(prop.get)

//

(Int result, Int remainder) = 5 /% 3;

//

try
    {
    // x ...
    }
catch (RTE e)
    {
    // y ...
    }
finally
    {
    // z ...
    }

GUARDALL
GUARD 1 RTE label1
    // x ...
ENDGUARD
JMP label2
label1:
    // y ...
FINALLY
    // z ...
ENDFINALLY
label2:

//

interface Doable
    {
    List do();
    }

const SuperDuper (List & AutoImmutable list)
        implements Doable
    {
    Boolean filter(String s)
        {
        return list.contains(s);
        }
    }

List   l = ["a", "b"];
Doable d = new SuperDuper(l);
function Boolean(String) f = d.filter;
function Boolean(String) f = s -> l.contains(s);

function void f() = new SuperDuper().do;

//

Int? x = ...
// what methods exist on the interface for "x"?
// - the intersection of the methods from Int and the methods from Nullable

// constructors

const Person(String name, Date dob)
    {
    construct Person(String name, Date dob)
        {
        // explicit validation
        assert:always name != "hitler";

        // this would be stupid to do, but isn't fatal
        this.name = name;

        this.firstname = ...;  // compiler error!

        // implicit call to:
        // this.name = name;
        // this.dob = dob;
        }

    String firstName
        {
        String get()
            {
            // ... split the name
            String s = super();
            if (s.startsWith
            return ...;
            }
        }

    String lastName
        {
        String get()
            {
            // ... split the name
            return ...;
            }
        }
    }

trait Taxable(String taxid);

const Employee(String name, Date dob, String taxid)
        extends Person(name, dob)
        incorporates Taxable(taxid)
    {
    construct Employee(String name, Date dob, String taxid)
        {
        // validation code here
        assert:always name.length > 0 -> "Name is too short %s".format(name);

        // construct Person(name, dob);
        // construct Taxable(taxid);
        }

    @lazy(this.calcRate) Dec taxrate;
    private Dec calcRate() {...}

    // alt
    @lazy Dec taxrate
        {
        Dec evaluate() {...}
        }

    }


// mutable capture of lvar ref

// stupid example
Int foo(function Void(Ref<Int>) do)
    {
    Int x = 3;

    do(&x);

    return x;
    }

// problem example
Ref<Int> foo()
    {
    Int x = 3;

    Ref<Int> xref = &x;

    // what is the value? 3, right?
    print xref.get();
    x = 5;
    // what is the value? 5, right?
    print xref.get();

    // off-topic example
    Ref<Ref<Int> xrefref = &xref; // can NOT say &&x;
    xrefref.get().set(4);

    return xref;
    }

Int count(List<String> list)
    {
    Int c = 0;
    Person p = ...;
    c++;
//    Ref<Int> cref = &c;

    function Void(String) visitor = s -> console.out(c);
    function Void(String) visitor = s -> p = new Person(s);
//    function Void(String) visitor = s -> cref.inc();

    list.visit(visitor);

    return c;
    }

// type narrowing on ref test

Object o = ...
if (o instanceof String && o.length > 4)
    {
    Ref<Object> oref = &o;  // Schrödinger's cat! opening the box (just getting the handle) ruins the type assumability
    print oref.Referent;    // prints "Object", not "String", although both compiler and runtime
                            // assume that it is (safely) a String (as long as there is no Ref!)
    oref.set(4);
    Char ch = o.charAt(4);  // can't do this now!!!
    // ...
    } // this line "detaches" oref from o (oref gets its own storage, and holds whatever o is
      // at this point

// Ref taking on (mixing in) capabilities of Referent

Int c = 0;
++c;    // what does this mean? what does it "compile as"

// option 1
c = c + 1;
INVOKE_11 c Int.add 1 -> c
// option 2
ADD c, 1 -> c
// option 3
INC c

Ref<Int> cref = &c;
cref.inc();

//

Object o;
Ref<Object> ref = &o;
o = new Person("bob");

//

class Person
    {
    construct Person(String name)
        {
        this.name = name;       // i.e. "this:struct[Person.name] = name;"
        }

    String name;
    }

    Person p = new Person("bob")

//

foo()
    {
    Frame frame = this:frame;
    print "cs:ip=%n:%n", frame.cs, frame.ip
    print "cs:ip=%n:%n", frame.cs, frame.ip
    }

// constructor usage dynamically to create a new class instance

Person p = new Person(name);
function Person(String) new1 = &(Person.new<Person>(String));

function Void() constructor = &(Person.construct(name));

Person p2 = Person.new(constructor);

//

class B { private Int x; Int y; }
class D extends B { Int x; }

construct D(Int x1, Int x2, Int y)
    {

    }

// class / singleton

interface /* TODO or class or const */ Module
    {
    Class resolveName(String name);
    }

const Class<ClassType>
    {
    conditional ClassType singleton();
    ClassType:struct newStruct();
    ClassType new(Tuple params = ());
    ClassType new(ClassType:struct struct);
    }

Class clzE = this:module.resolveName("dto.Employee")
Class clzC = this:module.resolveName("util.Config")
Class<Runnable> clzR = (<-) this:module.resolveName ("jobs.Reporter")

// -- meta

// auto-narrowing
class C
    {
    C foo();        // "C" will auto-narrow, i.e. this:type
    C.Type foo2();  // non-auto-narrowing "C"
    }

C o =  new C();
Ref<C> r = &o;

// how to get the "type" of o
Type t = o.Type;            // ugh. now Object has a Type property
Type t = &o.ActualType;     // yeah, this will work.
Type t = typeof(o);         // C called and wants its compiler back
Type t = o.meta.Type;       // only works if you have access to meta! (but it does make some sense)

// is o immutable? which one(s) of these would work?
if (o.immutable) ...        // ugh. now Object has an immutable property
if (&o.immutable) ...       // asking the reference seems weird
if (o.meta.immutable) ...   // obviously this could work, unless you can't get o.meta
if (o is immutable) ...     // hack

// ----

async Reader
    implements ...
    {
    }

// lazy prop

Point(Int x, Int y)
    {
    // lambda style
    @lazy(() -> x ^ y) Int hash;

    // alternatively use method bound to "this"
    @lazy(calcHash) Int hash;

    Int calcHash()
        {
        return x ^ y;
        }

    // or "implement the prop by over-riding calc"
    @lazy Int hash.calc()
        {
        return x ^ y;
        }
    }


// weak / soft prop
class C
    {

    }
const Movie
    {
    // ...
    @lazy(decompress) @soft Byte[] bytes;

    private Byte[] decompress() {...}
    }


class WHM<K,V>
    {
// got rid of this:    private RefStream notifier = new RefStream();
    class Entry<K,V>(K key, V value)
        {
        @weak(cleanup) K key;
        V value;
        // etc.

        Void cleanup()
            {
            WHM.this.remove(this);
            }
        }

    V get(K k)
        {
        [] a = getBucketArray()
        I  h = k.hash;
        E? e = a[h%a.length]
        while (e != null)
            {
            conditional K ko = e.&k.peek();
            if (ko)
               {
               (_, K k) = ko;

            if (K k2 : e.&k.peek() && k == k2)
                {
                return e.v;
                }
            }
        return null; // bad design; get() should be conditional
        }

    .. put(K k, V v)
        {
        Entry e = new Entry(k, v, notifier); // &key.tellMeAfterYouGCTheKey(notifier, this);

        // ...
        }

    Entry?[] getBucketArray()
        {
        // not this anymore: notifier.forEach(cleanup);
        this:service.processClearedRefEvents();
        // ...
        }


    }

// lambda

function Int(Int) foo(Int i)
    {
    return n -> n + i*i;

    const function Int(Int i, Int n) foo$1 = {return n + i*i;};
    return &foo$1(i,?);
    }

// auto-mixin (under consideration)

// what i want to be able to say is:
//   "yeah, there's a handy container data type C, and there's a handy data type D ...
//   but there's a third type that is implicit, which is a C<D>, and any time that
//   someone creates a C<D>, I have some extra capabilities that need to be included"
@auto mixin X           // name is required, and should be meaningful, although somewhat extraneous
        into C<D>
    {
    // the "this" is a C<D>
    }


// conditional type composition

class HandyDBDriver
        implements SpringResource // should only implement this if Spring is present
    {
    Connection connect(String url);

    // TODO SpringResource method(s)
    }

// mixins for refs

@lazy(function Referent ()?)
@weak(function Void ()?)
@soft(function Void ()?)
@future
@watch(function Void(Referent))

combos that work:
@lazy @weak
@lazy @soft

combos that don't work:
@lazy @future

TODO
@ro - HOW? WHAT? WTF?
@atomic
@inject

@auto

//  timeout

service Pi
    {
    String calc(Int digits)
        {
        String value;
        // some calculation code goes here
        // ...
        return value;
        }
    }

Void printPi(Console console)
    {
    Pi pi = new Pi();

    // blocking call to the Pi calculation service - wait for 100 digits
    console.print(pi.calc(100));

    // potentially async call to the Pi calculation service
    @future String fs = pi.withTimeout(...).calc(99999);
    fs.onResult(value -> console.print(value));
    fs.onThrown(e -> console.print(e.toString()));
    fs.onExpiry(() -> console.print("it took too long!"));
    fs.onFinish(() -> console.print("done"));
    }

Void foo()
    {
    this:service.pushSLA(5 seconds)
    printPi(console);
    this:service.popSLA()
    }


// --- atomic

service Progress
    {
    @watch(raiseEvents) Int percentDone;

    Void registerForUpdate(function Void (Int) notify) {list.add(notify);}

    private Void raiseEvents()
        {
        Int percent = percentDone;

        // notify each listener
        for (function Void (Int) notify : list)
            {
            notify(percent);
            }
        }
    }

service LongRunning
    {
    // instead of Progress
    public/private @atomic Int percentDone;

    Void runForALongTimeDoingSomethingImportant(Progress progress)
        {
        Int lastPercent = 0;
        for (Int i : 0..workCount)
            {
            Int percent = i * 100 / workCount
            // if (percent != lastPercent)
            if (percent != percentDone)
                {
                // progress.percentDone = percent;
                // lastPercent = percent;
                percentDone = percent;
                }

            // ...
        //
        }
    }

// lambda again

class c
    method m
        {
        foo(s -> s.length); // v1
        bar(y -> y.length); // v2
        }

// cas

String oldValue = ...
String newValue = ...
while (oldValue : casFailed(oldValue, newValue)
    {
    }

service LongRunning
    {
    @watch @atomic Int percentDone;
    // ....
    }

// future

// pairing down java's stage ..
public interface Future<T>
    {
    public <U> CompletionStage<U> thenApply(Function<? super T,? extends U> fn);

    public CompletionStage<Void> thenAccept(Consumer<? super T> action);

    public CompletionStage<Void> thenRun(Runnable action);

    public <U,V> CompletionStage<V> thenCombine
        (CompletionStage<? extends U> other,
         BiFunction<? super T,? super U,? extends V> fn);

    public <U> CompletionStage<Void> thenAcceptBoth
        (CompletionStage<? extends U> other,
         BiConsumer<? super T, ? super U> action);

    public CompletionStage<Void> runAfterBoth(CompletionStage<?> other,
                                              Runnable action);

    public <U> CompletionStage<U> applyToEither
        (CompletionStage<? extends T> other,
         Function<? super T, U> fn);

    public CompletionStage<Void> acceptEither
        (CompletionStage<? extends T> other,
         Consumer<? super T> action);

    public CompletionStage<Void> runAfterEither(CompletionStage<?> other,
                                                Runnable action);

    public <U> CompletionStage<U> thenCompose
        (Function<? super T, ? extends CompletionStage<U>> fn);

    public CompletionStage<T> exceptionally
        (Function<Throwable, ? extends T> fn);

    public CompletionStage<T> whenComplete
        (BiConsumer<? super T, ? super Throwable> action);

    public <U> CompletionStage<U> handle
        (BiFunction<? super T, Throwable, ? extends U> fn);
    }



service s

s.doSomething().thenDo(() -> ...).thenDo(() -> ...).thenDo(() -> ...).thenDo(() -> ...)

s.doSomething().or(s2.doSomething()).thenDo(() -> ...);
s.doSomething().and(s2.doSomething()).thenDo(() -> ...);

s.makeString().transform(s -> new IntLiteral(s).to<Int>()).passTo(countSlowly);

// stuff TODO from module.x

    interface Class
        {
        }

    interface Mixin
        {
        }

    interface Trait
        {
        }

    interface Enum
        {
        }

    value Binary
        {
        // TODO
        }

    interface Map<Key, Value>
            extends Indexed<Key>

// - equality

List -> AbstractList -> {ArrayList, LinkedList}

List l1 = new ArrayList() ...;
List l2 = new LinkedList() ...;
if (l1 == l2)       // calls xtc.ecstasy.org:collections/List/equals
    {
    //...
    }

// mixins

List + Serializable value = new List(x) + Serializable(y);

@Serializable(y) List value = new List(x);

typedef List + Serializable T;
T value = new T(x); // not sure how to construct Serializable with params

class MyList(Int x, Int y)
    implements List(x)
    incorporates Serializable(y)

// timeout

try (new Timeout(Duration:"1s"))
    {
    try (new Timeout(Duration:"500ms"))
        {
        // ...
        }
    catch (TimedOut e)
        {
        // ...
        }
    // ...
    }

service:
-> in flight requests
    -> request
-> busy (which request?)
-> contended or not / queue length
    -> request
-> is it in a critical section? how critical?

service-request
-> is someone waiting?
-> "thread" identity / back-call-stack info
-> timeout info
    -> incoming time-out (duration remaining)
    -> outgoing time-out (timeout object?)

service-stack:
-> # of outstanding proxy invocations (futures)
    -> future
-> # of frames
    -> code (method / function body) identifier
    -> location within the code
    -> registers

// enter critical section
using (new CriticalSection())
    {
    // ...
    }
// exit critical section

// ----- elvis (has entered the building)

a?.b();
if (a != null) { a.b(); }

x = a?.b?.c?.d : e;
// translates to:
x = a != null && a.b != null && a.b.c != null ? a.b.c.d : e;

if (a?) {..}
// translates to:
if (a != null) {..}

x = a.b ?: c;
// translates to:
x = a.b != null ? a.b : c;

x = a?.b ?: c;
// translates to:
x = a != null && a.b != null ? a.b : c;


// -- enums

interface Enum
        extends Const
    {
//    @ro int Count;
//    @ro String[] Names;
//    @ro T[] Values;
//    @ro Map<String, T> NameTable;

    @ro Enumeration enumeration;

    @ro int Ordinal;
    @ro String Name;

    // TODO some sort of "next / previous" that allows iteration over the individual elements?
    }

// for the "class" of enums:
Int                 numbools  = Boolean.count;      // 2
String[]            boolnames = Boolean.names;      // "False", "True"
Boolean[]           boolvals  = Boolean.values;     // False, True
byOrdinal
Map<String,Boolean> mapbools  = Boolean.byName;     // Map:{"False"=False, "True"=True}

// for an instance of an enum:
Int     n = False.ordinal;  // 0
String  s = True.name;      // "True"
if (Boolean b : False.next())        // consider a "prev" as well?
    {
    // b==True
    }

// - meta-model for classes and objects

Class           Object(1)
                Const
Enumeration(2)  Enum
                Service
                Ref
                Method
                Function
                Package
                Module
(1) class
(2) mixin


static Void foo() {...}
function Void () f = foo;
Function<Void, Void> f = foo;

function Void () f = () -> print "hello world";

Int i = 5;
String s = "world";
function Void () f = () -> print "hello " + s;   // not actually how it compiles ...
// function Void (String s) fHidden = s -> print "hello " + s;
// function Void () f = fHidden.bind(0, s);     // TODO figure out API for Function


Class
  Class   parentClass
  String  name
  ...     whatisit???   // Module/Package/Class/Interface/Mixin/Trait/Const/Enum/Service
  Boolean singleton     // Module/Package/Enum=true, Service/Const=true|false, others=false
  Boolean constant      // Module/Package/Const/Enum=true, Service=false, others=???
  ...     recipe        // composition-information

ultimately the question is: what is the class of a function?
- a property of T might be pretty straight-forward, since it is represented as a function returning a Ref<T>
- the class of a function is something that is invocable
  - possibly multiple ways, e.g. "Tuple invoke(Tuple)" as the most generic
  - possibly (T1, T2) "invoke(T3, T4, T5)" (specific to the function)
- also has code
- might have source code
- params
  - type
  - name
  - default
- return values
- doc
- file name
- starting line of code / length in lines of code


class Class<Nullable> incorporates Enumeration ...

abstract class Nullable
        implements Enum
    {
    final Int ordinal;
    Nullable(Int i)
        {
        ordinal = i;
        }
    static final Nullable Null = new Nullable(0) {};
    }

abstract class Boolean
        implements Enum
    {
    static final Boolean False = new Boolean(0) {};
    static final Boolean True  = new Boolean(1) {};
    }

// --- asOnly example

interface FileSystem
    {
    Binary read(String file);
    Void write(String file, Binary contents);
    }

service SuperDuperFileSystem(UnsafeIoObject unsafeIoObject)
        implements FileSystem
    {
    Void formatDisk() {...}
    Void eraseDrive() {...}
    Void eraseBackups() {...}
    Void trashHeads() {...}
    Binary read(String file) {...}
    Void write(String file, Binary contents) {...}
    }

Void injectStuff(Container container)
    {
    if (container.needs(FileSystem))
        {
        SuperDuperFileSystem fs = new SuperDuperFileSystem(unsafeIoObject);
        container.inject(&fs.asOnly(FileSystem));
        }
    if (container.needs(function Void (FileSystem)))
        {
        container.inject(Foo);
        }
    }

Void foo(FileSystem fs)
    {
    fs.formatDisk();                                        // compiler error
    assert fs instanceof SuperDuperFileSystem;              // runtime exception
    SuperDuperFileSystem sdfs = (SuperDuperFileSystem) fs;  // runtime exception

    // hey, the container called us back! passing us a FileSystem ... is it the one that we passed in?
    if (SuperDuperFileSystem sdfs : &fs.cast(SuperDuperFileSystem))
        {
        sdfs.formatDisk();      // ok
        }
    }

// later on the container calls us back on some callback function

// --- function binding

String foo(Int i, String s, Boolean b) {...}

// Function<(String), (Int)>
function String (Int) f2 = foo(?, "hi", True);

// -- tuples

Tuple<T1, T2, T3> t = ...

(T1 t1, T2 t2, T3 t3) = t;

t = (t1, t2, t3);

(T1, T2, T3) foo(T1 p1, T2 p2, T3 p3) {...}
t = foo(t);

Function<Tuple<T1,T2,T3>, Tuple<T1,T2,T3>> fn = foo;

Function<Tuple<T1,T2,T3>, Tuple<T2>> fn2 = fn.bind(t1, ?, t3);

// ----- arrays

// same as saying:
// Array<String> names = new Array<String>(); // mutable, growable, empty
String[] names = new String[];

names += "bob";     // bad ... do NOT support this crap
names += "sam";

names.add("bob");   // better
names.add("sam");

names[0] = "bob";   // arguably OK (each "set" is at the size boundary), but .. not 100% comfy with this
names[1] = "sam";

// ----- arrays ==

Person[] people1 = functionThatReturnsArrayOfEmployeeObjects();
Person[] people2 = functionThatReturnsArrayOfCustomerObjects();

Runnable[] people1 = functionThatReturnsArrayOfEmployeeObjects();
Runnable[] people2 = functionThatReturnsArrayOfCustomerObjects();

Hashable[] people1 = functionThatReturnsArrayOfEmployeeObjects();
Hashable[] people2 = functionThatReturnsArrayOfCustomerObjects();

Object[] people1 = functionThatReturnsArrayOfEmployeeObjects();
Object[] people2 = functionThatReturnsArrayOfCustomerObjects();

DataRecord[] people1 = functionThatReturnsArrayOfEmployeeObjects();
DataRecord[] people2 = functionThatReturnsArrayOfCustomerObjects();

if (people1 == people2) {...}

// the problem isn't the Array.equals function ...
// the problem is that Array.equals has NO IDEA WHAT THE Element IS!!!!!!

        // we can ask the runtime Element (Employee and Customer would be the answer), but we have
        // no idea what the compile-time Element was .. was it Person? was it DataRecord? Was it
        // Const? Was it Object? or whatever superclass or interface or type that these objects might
        // have in common?

interface Array<Element>
    {
    // ...

    static Boolean equals(Type<Array> ArrayType, ArrayType a1, ArrayType a2)
        {
        if (a1.size != a2.size)
            {
            return false;
            }

        for (ArrayType.Element v1 : a1,
             ArrayType.Element v2 : a2)
            {
            if (v1 != v2)
                {
                return false;
                }
            }

        return true;
        }
    }

interface Map<Key, Value>
    {
    // ...

    static Boolean equals(Type<Map> MapType, MapType map1, MapType map2)
        {
        if (map1.size != map2.size)
            {
            return false;
            }

        for (Map.Entry<MapType.Key, MapType.Value>> entry : map1)
            {
            if (!(MapType.Value value : map2.get(entry.key) && entry.value == value))
                {
                return false;
                }
            }

        return true;
        }
    }

// instead we need something like
Arrays.equals(Array a1, Array a2, Type compileTimeType)
// except that compileTimeType doesn't tell us what Element was... although we can figure that out
// but even figuring that out doesn't tell us what the equals function is for the type, so maybe:

Arrays.equals(Array<Element> a1, Array<Element> a2, Type Element, function Boolean(Element, Element))

// for any generic type C, it has some number of formal type parameters T1, T2, ...
// for any compile time type CTT that the compiler allows to be compared with == or !=, there exists
// a function on CTT (or a superclass (supertype?) thereof) of the conceptual form
//   "Boolean equals(? super CTT v1, ? super CTT v2)"

// how does the compiler communicate to that equals function what the values of T1, T2, etc. are, or
// alternatively, what the equals functions are for T1, T2, etc.
// 1) it can provide the compile-time type that it is using as an argument to equals
//    - e.g. "Array of Person" (example above)
//    - e.g. "Map of String to Int"
// 2) however, even with the compile time type being somehow non-ambiguous, i.e. T1 and T2 are known
//    example, how would the equals function on the genericized class find the equals function for
//    each of the formal type parameters?
//    - does Type interface have a property like "functionThatComparesForEquality"?
//    - if so, does Type interface have a property like "functionThatComparesForOrder"?
//    - if so, where does it stop? obviously the only capabilities it would naturally have are those
//      that we are explicitly aware of, so anything similar faced by a developer, and they are SOL

// here's the other issue. we are going to write Array.equals ...
// - what does it look like?
// - how does the comiler know what to call it with?
// - some types have 0 formal params; some have 1; some have 2 ... so it's not some simple signature

// - here's the other problem:
class Complicated<A,B,C,D>
    {
    A a;
    B b;
    C c;
    D d;

    static Boolean equals(Complicated value1, Complicated value2)
        {
        // 1) how would the compiler explain to this function that a certain type corresponds to
        //    "A", versus "B", versus "C", versus "D"
        //    -> we've lost "compile time information" .. only have runtime information
        //    -> we don't have a way to communicate "A"
        //    -> we could use a convention, like "Type A, Type B, Type C, Type D", i.e. matching names
        }

    // 1. let's assume that we use a standard signature for equals
    function Boolean equals(Type T, T, T)

    // 2. but we know
    }

// 1. let us posit that for any type T, if the type T is known at compile time, then the compiler can
// identify a function "equals" to use, or if T cannot be resolved at compile time, then the runtime
// can identify a function "equals" to use.

// 2. let us posit that for any parameterized type T1<T2>, that both the types T1 and T2 are known
// at runtime, and _may_ be known at compile time

// 3. the Type interface has to provide a means to resolve a function at runtime that provides the
// "equals" functionality
interface Type<T>
    {
    @ro function Boolean (T v1, T v2) compareForEquality;
    @ro function Ordered (T v1, T v2) compareForOrder;
    }

Person[] people1 = functionThatReturnsArrayOfEmployeeObjects();
Person[] people2 = functionThatReturnsArrayOfCustomerObjects();
if (people1 == people2) {...}
// compiler knows that the compile-time-type is Array<Person>
// so the "last line of code" that the compiler generates is the call to the function equals(v1,v2)


// --- gene's for loop

for (Person person : people, Int i : 0) //  (but also give me "i", "first", "last"))
    {
    if (first)
        {
        ...
        }

    if (index % 100 == 0)
        {
        // ...
        }

    if (last)
        {
        ...
        }
    }

// ---

switch (a <=> b)
    {
    case Lesser:
    case Greater:
    case Equal:
    }

Int i = 0;
Int j = new IntLiteral("123454");
Dec d = new IntLiteral("123454");
Float f = new IntLiteral("123454");

Float fl = 0;
Float fl2 = 0.0;
Dec d = 0.0;


// ----- Type

// idea is that Type itself is an "abstract const" that implements the "type contract", but has
// several implementations
const Type<DataType>
    {
    // obviously, it has one implied property: DataType
    Type DataType;

    // however, it has a number (0 or more) of other "type parameter" properties, one for each
    // unique type parameter of the type composition, e.g. for map:
    Type Key;
    Type Value;

    // example for what this would look like for the type of a HashMap<Key, Value>
    Type<Hashable>? Key;
    Type<Object>? Value;

    // the same information could be represented by a standard data structure, e.g. by name:
    Map<String, Type> typeParameterByName; // TODO type could be null, though .. if unresolved
    // for each type parameter, there is a name, there is a constraint, and there is a type

    // there is some concept of whether or not the type parameters have been resolved, i.e. if their
    // types are known, or if they are simply playing the part of a "type TBD"
    Boolean resolved;

    // it's not necessarily exposed as a property on Type, but the information is obviously there in
    // the background of what the "unresolved version of the Type" looks like ... is it exposed?
    Type unresolved;
    }

// what about function "param types"
// - might need to specify that the value has to be const
// - might need to specify that the value has to be immutable or service

// a Type that represents a value that is an instance of either type A or type B (possibly both)
const OrType
    {
    // the "all methods" represents the intersection of the two types
    // one property to obtain the resolved type of this, i.e. the intersection type
    // two properties to obtain the type A and type B
    // implements an equals to compare with another OrType
    // note: order (of A and B) is important
    }

// a Type that represents the public/protected/private interface of a "type composition" / class
const ClassType
    {
    enum Access {Public, Private, Protected}
    Class clz; // is this a public property? TODO shouldn't this be hidden?
    Access access;

    // note: this type also has a property for each "type parameter" of the class
    }

// arbitrary type created by modifying another type
const SyntheticType
    {
    }

// if there is a class C of some T, there is an unresolved type (the one that refers to type "T"),
// and there is a resolved type (where "T" has been replaced with a proper type)
class C<T>
    {
    T foo();
    Void bar(T value);
    }

Type t = C.Type;
// so what does "t" have? it has a method "foo" that returns an unresolved "T", and method "bar"
// that takes an unresolved "T"!

// Some of the questions that Type answers:
//  &object.Referent            what is the type constraint of the Ref that holds the reference to "object"?
//  &object.ActualType          what is the type of the object, as seen through the Ref?
//  type.TypeParams             what are the type params for the type?

// Some of the questions that Class answers:
//  enum {Module, Package, Class, Const, Enum, Trait, Mixin, Interface} form
//  Boolean isAbstract
//  Boolean isConst
//  Boolean isService
//  Boolean isSingleton
//  Type Type                   what is the (public) type of a class?
//  Type ProtectedType          what is the protected type of a class?
//  Type PrivateType            what is the private type of a class?
//  Type TypeParams             what are the type params (as a tuple) for the class?
//


Type t = HashMap.Type;              // compile time error .. requires the k & v to be provided      TODO disagree?
Type t = HashMap<String, Int>.Type; // compile time error .. requires the k & v to be provided
print t.class.name;                 // "NativeConstantPoolType"
Type t2 = t + Iterable.Type;
print t2.class.name;                // "SyntheticType"
Type t3 = t2 | String;
print t3.class.name;                // "OrType"

// back to the Type t of HashMap
Type k = t.Key;             // exception!


// ----- parameterized types

Map<String, Int> map = new HashMap<String, Int>();  // ok?
Map<String, Int> map = new HashMap<>(); // no! why is the "<>" required here? it seems STUPID
Map<String, Int> map = new HashMap();   // why not this instead?
Map map = new HashMap<String, Int>();   // is this the same thing? NO! type of "map" is <O,O>

Map map = new HashMap();            // three possible answers here:
                                    // 1. compiler error (two different type specs, <O,O> and <H,O>)
                                    // 2. type of "map" is <O,O>
                                    // 3. type of "map" is <H,O>

// ---- getting a class

// 1) you can get a class from something that you can name
Class c  = String;
Class c2 = Runnable;

// 2) you can get a class from loading a module
Class c3 = magicalInjectedContainerCreator.defineModule(...).giveMeAClass(...)


// ---- classes and types

Class c = HashMap;          // is that legal?
                            // 1. No. Key and Value are required. (there is no allowance for
                            //    a class that represents HashMap without its type parameters being
                            //    specified)
                            // 2. Yes. Key is assumed to be Hashable and Value is Object

// then would THIS be necessary?
Class c2 = HashMap<K,V>.Entry<K,V>;

Class pkg = x.collections;


//
interface Array<Element>
    {
    // "Array.Type" is not really using a ".Type" property on the "Array" class ... what this really
    // means is "Array", because when we say "Array" we actually mean "this:type" and NOT "Array"
    Array.Type<Element> ensureMutable();
    }

//
interface Array<Element>
    {
    // instead consider the use of "bang" to say "no, not this:type, but actually use (any) Array!"
    Array!<Element> ensureMutable();
    }


// discussion of const/immutable params / requiring immutable value

class Test
    {
    HashMap<String, Int> map;

    Void foo(Object o)
        {
        // the developer knows that he/she only passes a String to foo
        map.put(o, 1);
        }
    }

class SSN
        implements Hashable
    {
    String s;
    Void makeThisImmutable() {...}
    }

class Test
    {
    HashMap<SSN, Int> map;

    Void foo(SSN o)
        {
        Int n = map.get(o);

        // the developer knows that he/she only passes an immutable SSN to foo
        map.put(o, 1);
        }
    }


// ---

// what are the questions we want to be able to answer?
// 1) how do i specify in a signature that a type (e.g. param type) must be immutable?
// 2) how do i specify that something needs to implement the interface of a const without being
//    immutable? (Gene: "who cares .. don't do it ..")

class MyClass<T> {..}                   // T can be any object
class MyClass<T extends I> {..}         // T can be any object that is assignable to I
class MyClass<T extends immutable I> {..}   // T can be any object that is assignable to I and is immutable

// will this be useful?
class MyClass<T extends const I> {..}   // T can be any object that is assignable to I and is a const
class MyClass<T extends enum I> {..}    // T can be any object that is assignable to I and is an enum
class MyClass<T extends service I> {..} // T can be any object that is assignable to I and is a service

// as if Type included:
Boolean immutable;
Boolean isConst;
Boolean isEnum;
Boolean isService;

// then it could be e.g. typedef'd
typedef @ro Runnable ImmutableRunnable;

assert o instanceof ImmutableRunnable;
ImmutableRunnable ir = (ImmutableRunnable) runnableObject;

MyClass<T extends ImmutableRunnable>
    {
    Void foo(T ir)
        {
        ir.run();
        }
    }

Void foo2(Runnable runnable)
    {
    MyClass<ImmutableRunnable> my = new MyClass();
    // someone passed me a runnable
    my.foo((ImmutableRunnable) runnable);
    }


// ----- assignability / instanceof / successfully castable rules

T2 v2 = ...
T1 v1 = v2;

T2 is assignable to T1 iff
  - T2 === T1
  - class of T2 implements T1 (or class of T1)
  - class of T2 extends class of T1
  - class of T2 incorporates class of T1
  - for each m1 in {M1}, there exists an m2 in {M2} *with the same name* that at least one of the
    following holds true:
    - m1 === m2
    - m1 and m2 have the same # of parameters and return values and both of the following hold true:
      - for each parameter p1 of m1 and p2 of m2 at least one of the following holds true:
        - p1 is resolved from this:type and p2 is resolved from this:type
        - p1 is assignable to p2
        - both p1 and p2 are resolved from some type parameter P that is common to both T1 and T2
          and p2 assignable to p1 (!)
      - for each return r1 of m1 and r2 of m2 at least one of the following holds true:
        - r1 is resolved from this:type and r2 is resolved from this:type
        - r2 is assignable to r1

T2<TP21, TP22, ..., TP2N> is assignable to T1<TP11, TP12, ..., TP1N> iff both of the following are true
  - TP2i is assignable to TP1i
  - the fully resolved T2 is assignable to the fully resolved T1


--

let "Class category" be one of: module, package, class, const, enum, service, mixin, trait,
interface.

let "Class" be a named item within a specific Class category, defined as a composition of one or
more other Classes via extension, implementation, and/or incorporation, and subject to the
composition rules defined by the Ecstasy language specification.

for any two Classes CD and CB, let CD be a "derivative Class" of the "base Class" CB iff CD
extends, implements, or incorporates CB or any derivative Class of CB.

let "type parameter" be a formal named parameter of a Class whose value resolves to (or will at
runtime resolve to) a Type.

let "this:type" specify a type parameter that is present on every Class.

let "Type" be a set of methods (and the Type may specify that it is "explicitly immutable")
- let "formal Type" be a Type that may include unresolved type parameters
- let "resolved Type" be a Type that does not have any unresolved type parameters

let each Class define a formal Type.

let "parameterized Class" be a Class that declares one or more type parameters, and/or a Class with
a base Class that is a parameterized Class.

let the "type parameters" of the parameterized Class be the union of the one or more type parameters
declared by a parameterized Class, with the type parameters of all of the base Classes of the
parameterized Class.

let "parameterized type" be a Type that originates from the formal Type of a parameterized Class.

for any method m of a parameterized type with a type parameter T:

- let m "consume" T if any of the following holds true:
 1. m has a parameter type declared as T;
 2. m has a return type that "consumes T";
   - there is a notable exception to this rule for a method on a type corresponding to a property,
     which returns a Ref to represent the property type, and thus (due to the methods on Ref<T>)
     appears to "consume T"; however, if the type containing the property is explicitly immutable,
     or the method returning the Ref<T> is annotated with @RO, then m is assumed to not
     "consume T"
 3. m has a parameter type that "produces T".

- let m "produce" T if any of the following holds true:
 1. m has a return type declared as T;
 2. has a return type that "produces T";
 3. has a parameter type that "consumes T".

let T1 and T2 be two types
- let M1 be the set of all methods in T1 (including those representing properties)
- let M2 be the set of all methods in T2 (including those representing properties)
- let T2 be a "derivative type" of T1 iff
 1. T1 originates from a Class C1
 2. T2 originates from a Class C2
 3. C2 is a derivative Class of C1
- if T1 and T2 are both parameterized types, let "same type parameter" be a type parameter of
  T1 that also is a type parameter of T2 because T2 is a derivative type of T1, or T1 is a
  derivative type of T2, or both T1 and T2 are derivative types of some T3.

Type T2 is assignable to a Type T1 iff both of the following hold true:
1. for each m1 in M1, there exists an m2 in M2 for which all of the following hold true:
  1.1 m1 and m2 have the same name
  1.2 m1 and m2 have the same number of parameters, and for each parameter type p1 of m1 and p2 of
      m2, at least one of the following holds true:
    1.2.1 p1 is assignable to p2
    1.2.2 both p1 and p2 are (or are resolved from) the same type parameter, and both of the
          following hold true:
      1.2.2.1 p2 is assignable to p1
      1.2.2.2 T1 produces p1
  1.3 m1 and m2 have the same number of return values, and for each return type r1 of m1 and r2 of
      m2, the following holds true:
    1.3.1 r2 is assignable to r1
2. If T1 is explicitly immutable, then T2 must also be explicitly immutable.

// This is a summary of all of the above, in a simple 2x2 matrix:
//
//                     widening                  narrowing
// C<T1> -> C<T2>      T1=String -> T2=Object    T1=Object -> T2=String
// ----------------    ----------------------    -------------------------
// !(C consumes T1)    ok                        Compile Time Error
// !(C produces T1)    possible RTE              ok


// example covariance testing

class P<T>
    {
    T p() {...}
    }

class C<T>
    {
    Void c(T value) {...}
    }

class PC<T> extends P<T>, C<T>
    {
    }

class FakePCofObject
    {
    Object p() {...}
    Void c(Object value) {...}
    }

class FakePCofString
    {
    String p() {...}
    Void c(String value) {...}
    }

C<String>      x1 = new C<Object>();  // ok
C<String>      x2 = new PC<Object>();  // ok
C<Object>      x3 = new C<String>();  // fails
C<Object>      x4 = new PC<String>(); // fails
P<Object>      x5 = new P<String>();  // ok
P<String>      x6 = new P<Object>();  // fails
PC<Object>     x7 = new PC<String>(); // ok, but the RT needs to "safe-wrap" the consuming methods
FakePCofObject x8 = new PC<String>(); // fails
PC<String>     x9 = new PC<Object>(); // fails

class C<T>
    {
    T prop1;  // consumes & produces
    @ro T prop2; // produces only
    @ro P<T> prop3; // consumes only
    @ro PC<T> prop4; // consumes & produces
    }

// From conversation on 11/27/17 w/ Mark

// for consumer-only type C:
C<String> cs;
C<Object> co;
C c;

c = co;     // ok
c = cs;     // ok
cs = co;    // ok
co = cs;    // err; requires cast (fails at runtime if T is *not* Object, e.g. if T is String)
co = c;     // err; requires cast (fails at runtime if T is *not* Object, e.g. if T is String)
cs = c;     // err; requires cast (fails at runtime if String is not assignable to T, i.e. String or Object)

// for producer-consumer type PC:

PC<Object> pco;
PC<String> pcs;
PC pc;

c = pc;     // ok
c = pco;    // ok
c = pcs;    // ok

co = pc;    // err; requires cast (fails at runtime if T is *not* Object, e.g. if T is String)
co = pco;   // ok (fails at runtime if T is *not* Object, e.g. if T is String)
co = pcs;   // err; will not compile, even with a cast

cs = pc;    // err; requires cast (fails at runtime if String is not assignable to T, i.e. String or Object)
cs = pco;   // ok (fails at runtime if String is not assignable to T, i.e. String or Object)
cs = pcs;   // ok (fails at runtime if T is *not* String)

pco = cs;   // err; requires cast to PC or PC<Object> (fails if cs is not an instance of PC)
pco = co;   // err; requires cast to PC or PC<Object> (fails if co is not an instance of PC)

pcs = cs;   // err; requires cast to PC<String> (fails if cs is not an instance of PC and T is not String)
pcs = co;   // err; will not compile, even with a cast

pc = pco;   // ok
pc = pcs;   // ok
po = p;     // ok
pco = pc;   // ok; requires "wrapping" of consumption methods

// how does auto-mixin work with class -> formal type -> resolved type if the mixin mixes in
// because of the information in the resolved type?


// --- new

how does new work?

class C
    {
    construct C(Int i)
        {
        // ...
        }

    construct C(String s)
        {
        // ...
        return &finally;
        }
    finally
        {
        // ...
        }
    }

// compiles as
class C
    {
    function Void construct(this:struct this, Int i) {...}
    function function Void () construct(this:struct this, String s) {...}
    }

C instance1 = C.new_(C.construct(_, 5)));
C instance2 = C.new_(C.construct(_, "hello world")));

// 0xA0 NEW rvalue-class TODO fn-constructor
C c = new C(n);

IVAR Int n
...
IVAR Function temp
BIND construct _ n -> temp
IVAR C c
NEW  C temp -> c


class Class<ClassType>
    {
    ClassType new_(function Void (Struct) construct);
    ClassType new_(function function Void () (Struct) construct);
    }

// inner class example

class BaseParent
    {
    class Child {}

    static class Orphan {}

    Child createChild()
        {
        return new Child();
        }

    Orphan createOrphan()
        {
        return new Orphan();
        }
    }

class DerivedParent
    extends BaseParent
  {
  class extends Child {}

  static class extends Orphan {}
  }

BaseParent parent1 = new BaseParent();
BaseParent.Child  child1  = parent1.createChild();
BaseParent.Orphan orphan1 = parent1.createOrphan();

BaseParent parent2 = new DerivedParent();
BaseParent.Child  child2  = parent2.createChild();
BaseParent.Orphan orphan2 = parent2.createOrphan();

BaseParent p1 = new BaseParent()
BaseParent p2 = new DerivedParent()
Child      c1 = new p1.Child(); // cannot say "new BaseParent.Child()" - exception!!!
Child      c2 = new p2.Child();
Child      c3 = p2.makeMeABaby();

Orphan     o1 = new p1.Orphan();
Orphan     o2 = new p2.Orphan();

Orphan     o3 = new BaseParent.Orphan();     // is this a good idea to allow?
Orphan     o4 = new DerivedParent.Orphan();  // no! this is an exception (or just discouraged?)
Orphan     o5 = BaseParent.Orphan.findConstructor(Void)();

// question is "how do you de-serialize an orphan?" if you can't "new" the
// orphan by its class?

//--

Class<HashMap<Int,String>> c = HashMap.narrow(new TypeParameter("Key", Int), new TypeParameter("Value", String));

// -- class & type

class Point(Int x, Int y) {}

out.println(new TypeFormatter(&p.ActualType)).detailedOutput());
// Point
// Properties:
//   Int x
//   Int y
// Methods
//   to<String()>
//   ...

out.println(new TypeFormatter(&p.ActualType)).detailedRawOutput());
// Point
// Methods
//   Ref<Int> x();
//   Ref<Int> y();
//   String to();
//   ...

Method<Point, <Int>, Void> getter = Point.x.get;
function Int() fn = p.&x.get;
Int n = fn();
function Int() fn2 = getter.bindTarget(p);

// ----- Map

// we have a few choices here if key is missing:
// 1) throw an exception
// 2) return null (return type is Nullable | Value)
val = map[key];

// same problem here:
val = map.get(key);

// which is why we did this:
if (val : map.get(key)) {...}

// could us a "get with default", a la java's ...
val = map.getOrDefault(key, dftval);

if (val : map[key]) {...}
val = map[key]; // could throw on miss
val = map[key]; // returns null on miss

conditional Value load(Key key)
conditional Void store(Key key, Value val)
Value? get(Key);

// ----- entry processor

map.process(key, e -> e.delete());

map.entries.stream().filter(...).map(...).produceANewMap().do

    processor.ReturnTypes[0] process(Key key, EntryProcessor processor);

