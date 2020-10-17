typedef function Result () Callable<Result>;
typedef function Void (Value) Consumer<Value>;

String hello = "hello";
Callable<String> getGreeting = () -> hello;
Consumer<String> setGreeting = s -> {hello = s; &hello.set(s)};

setGreeting("bonjour");
String greeting = getGreeting();

@Inject Printer console;

class C
    {
    static Void out(String s)
        {
        console.print(s);
        }

    void foo(Int i, String s) {...}

    someMethod(Iterator iter)
        {
        iter.forEach(C.out);
        iter.foreach(v -> console.print(v));

        function Void (String) print = s -> console.print(s);
        function Void print(String) = s -> console.print(s);
        function Void print(String s) {console.print(s);}

        iter.forEach(print);
        }

    // a method that takes a consumer of String and returns a consumer of Int
    (function Void (Int)) foo(function Void (String) fn)
        {
        // ...
        }

    void foo2()
        {
        function (function void (Int)) (function void (String)) converter = foo;
        }
    }

// function Void fn(V))
typedef function Void (String) Consumer;
interface Consumer2<V> {void action(V value);}

function Void(String) fn =
Consumer c1 = v -> console.print(v);
Consumer2 c2 = v -> console.print(v); // disallow?
Consumer c12 = c2; // legal?
Consumer2 c21 = c1; // legal?
Consumer c3 = c2.action;

c1("hello");
c2.action("hello");

typedef List<Person> People;

Collector<E, A, R> co = Collector.of(() -> new E(), (a, e) -> {a.add(e);}, (a, a2) -> {a.addAll(a2); return a;}, (a) -> a.toR());
Collector<E, A, A> co = UniformCollector.of(() -> new E(), (a, e) -> {a.add(e);}, (a, a2) -> {a.addAll(a2); return a;});

static Builder<Element> createBuilder(Element.Type type)

static Builder<Element> builderOf(Type A, A a)
    {
    Type t = Runnable.Type;

    return new StreamBuilder<Element>();
    }


Stream<Int> s2 = builder(Int.Type);


**** Stream

Stage<InType, OutType> implements Stream
    {
    typedef function conditional OutType (InType) Mapper;

    Stage                   head;
    Stage<Object, InType>?  prev;
    Stage<OutType, Object>? next;
    Mapper?                 transform;

    construct()
        {
        this.head = this; // HOW to create a circular ref? could it be a deferred assignment? otherwise, it has to be Nullable
        this.prev = null;
        this.next = null;
        }

    // alternative
    Stage<OutType, next.OutType> link(Stage<OutType, Object> next, Mapper mapper)
        {
        next.head   = this.head;
        this.next   = next;
        next.prev   = this;
        this.mapper = mapper;

        return next;
        }

    conditional (Result) evaluateHead(TerminalOp<OutType, Result> op)
        {
        assert this.head == this;
        for (Element el : iterator())
            {
            if (!evaluate(op, this)
            }
        }

    conditional (Result) evaluate(TerminalOp<OutType, Result> op, InType el)
        {
        Stage head = this.head;

        Iterator<OutType> iter = new Iterator<>()
            {
            Iterator iterOuter = head.iterator();

            @Override
            conditional OutType next()
                {
                nextElement:
                for (Object el : iterOuter)
                    {
                    Stage stage = head;

                    while (stage != null)
                        {
                        if (el : stage.transform(el))
                            {
                            stage = stage.next;
                            }
                        else
                            {
                            // the element was filtered out; el becomes not definitely assigned
                            continue nextElement;
                            }
                        }
                    assert:debug(el.Type == OutType);
                    return (true, el);
                    }
                }
            }

        return (op.process(iter));
        }

    interface Step<Element, Result>
        {
        Void begin();
        Boolean process(Element el);
        Result finish();
        }
    interface TerminalOp<Element, Result>
        {
        Void begin();
        Boolean process(Iterator<Element> iter);
        Result finish();
        }

    class CollectorOp<Element, Cumulative, Result)
                (Collector<Element, Cumulative, Result) collector)
            implements TerminalOp<Element, Result>
        {
        @Override
        conditional Result process(Iterator<Element> iter)
            {
            Cumulative container = collector.supply();
            for (Element element : iter)
                {
                if (!collector.accumulate(container, element))
                    {
                    break;
                    }
                }
            return (true, collector.finish(container));
            }
        }

    class AnyMatchOp<Element, Boolean)
                (function Boolean (Element) match)
            implements TerminalOp<Element, Result>
        {
        @Override
        conditional Result process(Iterator<Element> iter)
            {
            for (Element element : iter)
                {
                if (match(element))
                    {
                    return (true, true);
                    }
                }
            return (false);
            }
        }

// Int i = j; // VAR Int64 // #11
@future Int i = j; // RVAR Int64, FutureRef<Int64> // #11
           // COPY 10, 11
Boolean f = &i.isAssigned    // VAR Ref<Int> // #12
                             // REF 11, 12

@future Image img1 = service.getAdvertisement(type1);
@future Image img2 = service.getAdvertisement(type1);   // RVAR @FutureRef<Image> // #27
                                                        // Invoke ... -> 27
@future Image img3 = img2;

@soft Image img4 = getImage();

Int c = img1.size();                                    // Var Int // #28
                                                        // GET 27, @Referent, 28
&img1.whenDone(x->foo(this, img1))

&img1.thenAccept(...)

if (oldValue : replaceFailed(oldValue, newValue))
        {
        return newValue;
        }

T v;
conditional T cv;

Boolean f = (v : cv);  ==> if (cv[0]) {v = cv[1]}; f = cv[0];
Boolean f = !(v : cv); ==> if (cv[0]) {v = cv[1]}; f = !cv[0];

Array<((String, String), Int)> array = ...
for (((String s1, String s2), Int iVal) : array using Int index)

Nullable vs. conditional:

String? s1 = ...

if (s1 != null)
    {
    print(s1.length());
    }

conditional String cs2 = stream.reduce(...);
String* cs2 = ...;

if (String s : cs2)
    {
    print(s.length());
    }

Iterator
    {
    E* next();
    }

Iterator<String> iter = ...;

String s;
if (s : iter.next())
   {
   Int i = s.length();
   }


function Int (Int) sq = n -> n*n;
print(sq(5));
print((function Int (Int)) (n -> n*n) (5));

Int[] ai = new Int[5](1);

X  x = new X();
X? a = foo();
// assign a to x (if a isn't null)
x = a;    // compiler error
x = a?;


class T { void foo(); }
T? x = ...

Boolean b = @auto(x?);
x?.foo();

conditional T y = @auto(x?);

y?.foo();


for (x : y)
a?[1];

Object p;

Person p = new Person();
Child c = p.new Child();


// java
if (o1 .instanceof (Person))
if (o .? Person)
    {
    Person p = (Person) o;
    }

// .x
if (o.?(Person))
    {
    p = o2.as(Something).foo().as(Person).doSomePersonThing().doSomeOtherThing().etc();
    }

label1:
   while (Person p : listP)
        {
        int i = label1.counter;
        boolean f = label1.first;
        }

   function Int() foo = ...;

label2:
   switch (foo())
        {
        case 1:
        case 2:

        default:
            int n = label2.value;
        }

label3:
    if (foo() > 5)
        {

        }

if (..)
    {
    Ref<Int64> pi;
    while (true)
        {
        int i = 5;
        int j = 7;

        if (f)
            {
            pi = &i;
            pi.set(6);
            i = 16;
            someCrazyFunctionThatDoesWhoKnowswhatWrittenByDima(pi);
            }
        else
           {
           pi = &j;
           pi.set(8);
           j = 18; // MOV @18, #7
           }
        print(i);
        }

    if ()
       {
       x = 17;
       y = 18;   // MOV @18, #7
       }
    }
// there is no "pi" here, but "pi" still exists, because it got passed to Dima

MyService
    {
    Int prop;
    Void foo();
    }

// to call foo on a service and not care sync/async
svc.foo();

// to get a future for it
@future Tuple<> result = svc.foo<Tuple<>>();
@future Void result = svc.foo<Void>();

// to call foo on a service and not care sync/async
svc.prop = 5;

// to get a future for it
@future Tuple<> result = (&svc.prop).set<Tuple>(5);


// assignment of dynamic ref into a standard
@future Int fi;

foo(&fi);

@future Int fi2 = fi; // doesn't block
FutureRef<Int> rf = fi; // compile error
FutureRef<Int> rf = &fi; // doesn't block

Int i = fi; // blocks

// mixin: "incorporate" vs. "encapsulate":

@ThisMixinEncapsulates class MyClass
        incorporates ThisMixinGetsPutBeneathMyCodeButOverTheMyBaseClassCode
        extends MyBaseClass
        // etc.
    {
    }

@ObjectStreaming("Hello", 7)
class ObjectFileStream()
        extends FileStream
    {
    ...
    }

new @ObjectStreaming FileStream()

class C<Element, ListType extends List>
    {
    Void foo(Element elThis, ListType.Element elThat)
        {
        }

    <T extends List> Void foo(T list, T.Element elExcept)
    }

// isSubstitutableFor most generic examples

===============

// producers

class B1
    {
    B2 foo();
    }

class D1
    {
    D2 foo();
    }

class B2
    {
    B1 bar();
    }

class D2
    {
    D1 bar();
    }

// and a cross-cycle

class B1
    {
    B2 foo();
    }

class D1
    {
    D2 foo();
    }

class B2
    {
    D1 bar();
    }

class D2
    {
    B1 bar();
    }

// is D1 assignable to B1? According to our rules, it is if D2 is assignable to B2
// is D2 assignable to B2? It is if D1 is assignable to B1...

===============


class C<T>
    {
    T prop1; // produces(T) ALWAYS
             // consumes(T) iff !read-only

    A<T> prop2; // produces(T) iff A.produces(A_Type) || (!read-only && A.consumes(A_Type))
                // consumes(T) iff A.consumes(A_Type) || (!read-only && A.produces(A_Type))

    A<B<T>> prop3; // B.produces(B_Type) -> B<T> produces T
                   // A.produces(A_Type) -> A.produces(T)

                   // B.consumes(B_Type) -> B<T> consumes T
                   // A.consumes(A_Type) -> A.consumes(T)
    }

class A<A_Type> {}
class B<B_Type> {}

Theorem:

C<T> = A<B<T>> produces T iff
    p1) A produces A_Type && B produces B_Type, or
    p2) A consumes A_Type && B consumes B_Type

C<T> = A<B<T>> consumes T iff
    c1) A consumes A_Type && B produces B_Type, or
    c2) A produces A_Type && B consumes B_Type

Proof:
1. A produces A_Type, B produces B_Type -> C produces T
2. A produces A_Type, B consumes B_Type -> C consumes T
3. A consumes A_Type, B produces B_Type -> C consumes T
4. A consumes A_Type, B consumes B_Type -> C produces T


+++++++++++++++++

class B<T>
    {
    (T, List<T>) f1(T p1, List<T> p2);

//    <U> (U, List<U>)
//        f2(U p1, List<U> p2);
//
//    <U extends List<Number>> (U.Element, List<U.Element>)
//        f3(U.Element p1, List<U.Element> p2);
    }

class D<T, U>
    {
    (Int, ArrayList<U>) f1(Int p1, List<U> p2) {..}

    // note that the presence of this method would prevent
    // the type D<Int> from existing
    // (Int n, List<Int>) f1(Number p1, List<Int> p2) {..}
    }

<El> Void test(El e)
    {
    B<Number> b1 = new D<Int, Number>();

    (Number n, List<Number> ln) = b1.f1(1, new ArrayList<Int>); // sig = (T, List<T>)

    (El e, List<El> ln) = b1.f2<El>(e, new List<El>);

    (Number n, List<Number> ln) = b1.f1<List<Int>>(1, new LinkList<Int>());

    (Number n, List<Number> ln) = b1.f1<List<Number>>(1, new LinkList<Int>());
    }

// invocation calls

B<String> b = ...;
b.f1("a", {"b", "c"}); // sig-const = ("f1", T, "List<T>")

<U> Void bar(U u)
    {
    B<U> b = ...;
    b.f1(u, {u}); // sig-const = ("f1", T, "List<T>")
    }

****

class DPC<T> extends PC<T>
    {
    Void consume(Int n);
    }
PC<Object> pc = new PC<String>();
pc.consume(42); // 1) resolve Void(T) with T=String and invoke PC.consume(String), which should blow
                // 2) use the arguments' compile-time type to resolve the sig Void(Int); fail to find the match

PC<Object> pc = new DPC<String>();
pc.consume(42); // calls the "override"; not an exception; sig = Void(T); we'll find the method

****

// under no circumstances I1 and I2 are assignable to each other (property T vs U)
interface I1<T> {void foo(T);}
interface I2<U> {void foo(U);}

// I1<Int> is assignable to I2
interface I1<T> {void foo(T);}
interface I2 {void foo(Int);}

// C<Int> cannot be instantiated; should we allow it to compile? and who should check?
// what about C<Number> c = ...; c.foo(42) should go to foo(Int)
class C<T>
    {
    void foo(T t) {...};
    void foo(Int i) {...};
    }

interface I1 {void foo(I2 i);}
interface I2 {void foo(I1 i);}

class C1
    {
    void foo(C2 c) { ... }
    }

class C2
    {
    void foo(C1 c) { ... }
    }

C1 c1 = ...;
C1 c2 = ...;

I1 i1 = (I1) c1; // should it compile?

class C1 implements I1
    {
    void foo(I2 c) { ... }
    }

class C2 implements I2
    {
    void foo(I1 c) { ... }
    }


interface I<T, U> {void foo(T); Void bar(U);}
class C<U, T> {void foo(T) {...} Void bar(U) {...}}

C<String, Int> c = ...
I<Int, String> i = c; // OK?

**** Tuple return

class MyClass
    {
    Tuple<Int, Sting> propT;
    Int propI;

    static (Int, String) foo() {...};
    static Tuple<Int, String> foo2() {return 1, "s";}        // error
    static Tuple<Int, String> foo2() {return (1, "s");};

    Void bar()
        {
        // CALL_0N for a local property and a register (pre-allocated)
        (propI, Sting s) = foo();

        // CALL_0T on a register (pre-allocated)
        Tuple<Int, Sting> t = foo();

        // CALL_0T on a property
        propT = foo();

        // CALL_01 into a temp register and unboxing ops
        (propI, Sting s) = foo2();
        }
    }


    CALL_0T  rvalue-fn, lvalue-treturn
    CALL_OT: ...        a) +N - register N (type is Tuple) -> into special
                        b) -P - local property (type is Tuple)


// CALL_?T      is generated when the caller expects a Tuple back,
//              but the callee provides a single or multi-return
// RETURN_T     is generated when the method signature requires multiple returns,
//              but the return statement provides a Tuple (the number of elements musts match)

*** generic function parameters

// method struct:
// method.getReturn(0): type = Terminal -> Register 0
// method.getParam(0): name = "T"; type = Parameterized -> Type<Number>
// method.getParam(1): name = "t1"; type = Terminal -> Register 0
// method.getParam(2): name = "t2"; type = Terminal -> Register 0
static <T extends Number> T bar(T t1, T t2)
    {
    Boolean f = ...;

    return f ? t1 : t2; // VAR ?
    }

interface Finder<VF>
    {
    Boolean containsValue(VF value);
    }
class MyFinder<VMF> implements Finder<VMF> {}

interface Map<K, V> {...}

class MyClass1<K1, V1>
    implements Map<K1, V1> {}

class MyClass2<K2, V2>
    implements Finder<V2> {}

class MyClass3<K3, V3>
    extends MyFinder<V3> {}

MyClass1<String, Int> c1 = new MyClass1();
Finder<Number> finder = c1; // OK; duck-typing

MyClass2<String, Int> c2 = new MyClass2();
Finder<Number> finder = c2; // OK; contribution by "Implements"

MyClass3<String, Int> c3 = new MyClass3();
Finder<Number> finder = c3; // OK; contribution by "Extends"

mixin Marshaling
    if (Avro.isPresent) { extends AvroMarshaler }
    else if (Thrift.isPresent) { incorporates ThriftMarshaler }

mixin Marshaling
    extends
        if (Avro.isPresent) { AvroMarshaler }
    incorporates
        if (Thrift.isPresent) {ThriftMarshaler}

mixin Marshaling
    extends conditional (Avro.isPresent) AvroMarshaler
    extends conditional (Thrift.isPresent) ThriftMarshaler2
    incorporates conditional (Thrift.isPresent) {ThriftMarshaler}

// conditional mixin

public/private @Dual Int p;

mixin Dual<T>
    into Ref<T>
    conditional into Var<T>
    {
    Void set(T t)
        {
        super(t);
        }
    }

// auto-narrowing
class Person
    {
    List<Person> kids;

    Void test0()
        {
        List<Employee> emps = kids;
        }
    }

class Employee
        extends Person
    {
    Void test1()
        {
        List<Employee> emps = kids;
        }
    }

// auto-narrowing and override
interface I
    {
    I foo();   // the return type is "this:class"
    }

class C<T>
    {
    @Override
    C<T> foo(); // the return type is "this:class<T>"
    }

// -- abstract / sanscode

class C
    {
    @Override
    @Abstract void foo()
        {
        beep();
        }
    }

class D extends C
    {
    @Override
    void foo(); // does not need a pointless "{ super(); }"
    }


// instance child

class B<T>
    {
    class C
        {
        }

    void foo()
        {
        C c1 = new C(); // compile time type is B<T>.C

        B<Int> b2 = ...;
        C c2 = b2.new C(); // compile time error; type is B<Int>.C; not assignable to B<T>.C
        B<Int>.C c2 = b2.new C(); // OK

        B b3 = ...;
        C c3 = b3.new C(); // compile time error; type is B<?>.C; not assignable to B<T>.C
        B.C c3 = b3.new C(); // OK
        }
    }

// ----- const run-time processing

const Person(String name, List<Person> dependents, Runnable r);

// the line above compiles as
const Person()
    {
    String name;
    List<Person> dependents;
    Runnable r;

    construct(String name, List<Person> dependents, Runnable r)
        {
        this.name = name;
        this.dependents = dependents;
        this.r = r;
        }
    finally // <-- right at this point we do some internal processing that looks like:
            {
            if (!dependents.is(immutable Object) dependents.is(Freezable))
                {
                dependents = dependents.is(Freezable) ? dependents.freeze(false) : throw new WTF();
                }
            if (!r.is(immutable Object) r.is(Freezable))
                {
                r = r.is(Freezable) ? r.freeze(false) : throw new WTF();
                }
            } // <-- at this point we're done with the internal work and return control to the finally on the constructor
        {
        // this is after construction; the object is immutable at this point
        }
    }

