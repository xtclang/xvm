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

// mark's problem about "? super Person" being a referenceable type is solved:

public interface List<T>
  {
  void add(T value);
  T first();
  T last();
  // etc...
  }

void handy(List list)
  {
  // always planned to make this ok at run-time
  Type t = list.T;

  // what if we made this work at compile time too?
  list.T value = list.first();
  // makes this ok
  list.add(value);
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

/**
* Example bad code that I plan to disallow:
*/
class HashMap<K,V>
    implements Map<V,K>   // this is illegal in Ecstasy!
  {
  // ...

  // the reason that this does not work in Ecstasy is that it implies 4 properties:
  Type K; // Map::K;
  Type V; // Map::V;
  Type K; // HashMap::K;
  Type V; // HashMap::V;
  // lots of name collisions, so the types / names are unique and (w.r.t. inheritance
  // etc.) they have to "line up" in "columns"
  }

void bar()
  {
  foo(new Map<String, int>);
  }

void foo(Map<K,V> map)
  {
  foo2(new ArrayList<map.Entry>);
  }

void foo2(List<Map.Entry /* compiler adds "<Object,Object>" */ > list)
  {
  list.T.K key = list.get(0).key;
  Type tK = list.T.K; // this is VERY different!
  Type tK = list.get(0).key; // this is VERY different!

  list.T.K key = list.get(0).key;
  list.T.V val = list.get(0).value;

  // so this is the question to answer: how to do type literals?
  Type eventinterface = Type:"{void onNotify(list.T.K);}";
  // or something like:
  typedef eventinterface
    {
    void onNotify(list.T.K);
    }
  // or both?

  // same question for e.g. functions
  Function notificationfunction = Function:"void f(list.T.K)";
  // or something like:
  funcdef void notificationfunction(list.T.K);
  // or both?
  }

// --

void foo()
  {
  Type t = String;
  t s = "hello"; // illegal because "t" is not usable as a compile-time type
  }

// type example

class T1 { int x; int y; }
class T2 { int y; int x; }

assert new T1().type == new T2().type; // success!

// -- 6 example --

String s = 6.toString();
int sum = 6 + 5;
// same as:
int sum = 6.add(5);

// -- list.T example --

// consider a generic box:
class Box<T> {private T value; T peek() {..}; void poke(T value) {..}}
// so class "Box" has parameter of name "T" of type "Object"->"ecstasy.xtclang.org"
// how does e.g. poke() declare its parameter type? ClassConstant(Box, "T")

// this one makes total sense
void foo(List list, list.T element)
// but what is the type?  i.e. the method has a parameter of type "List"->"collections"->"ecstasy.xtclang.org",
// but what is "list.T"? ClassConstant(foo, "list.T")

// this one is a bit harder to defend ("forward declaration"), but still makes sense
void foo(list.T element, List list)

// so what's the issue? the issue is that for each type, there has to be a ClassConstant
// and that ClassConstant may be referenced by other e.g. modules
// e.g. "I have a variable of that type over in that module, e.g. a return type" - how does it reference it?
// it has to say "there is class with some type T over in that module, and I'm calling a method that returns that T"

// so let's start by solving the problem within "this" module, i.e. first the declaration
// then second anyone dependent on that declaration (e.g. a caller)
// and then we can figure out how to reference it from a different module

