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
    Listener listener2 = listenerOfString;
    }
  }

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
