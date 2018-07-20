// resolving types (flattening classes) - basic examples:
class B
    {
    private Void pri();
    protected Void pro();
    public Void pub();
    }
// private B: pri(), pro(), pub()
// protected B: pro(), pub()
// public B: pub()

class D
        extends B
    {
    }
// private D: pro(), pub()
// protected D: pro(), pub()
// public D: pub()

// but protected methods have to be accumulated, even if it will ultimately be removed, because
// they are theoretically reachable as part of a method chain; similar for properties
class B
    {
    private Void pri();
    protected Void pro();
    public Void pub();
    }

class D
        extends B
    {
    private Int pri();
    public Void pro();
    public Void pub();
    }
// private D: Int pri() with no super, Void pro() with protected super, Void pub() with public super
// protected D: Void pro() with protected super, Void pub() with public super
// public D: Void pro() with protected super, Void pub() with public super

// alternatively ...
class D
        extends B
    {
    private Int pri();
    protected Void pro();
    public Void pro();
    public Void pub();
    }
// private D: Int pri() with no super, (protected) Void pro() with protected super, (public) Void pub() with public super
// protected D: (protected) Void pro() with protected super, (public) Void pub() with public super
// public D: (public) Void pro() with protected super, (public) Void pub() with public super

// property
public/private Int x;
// acts something like:
Int x
    {
    public Int get()
        {
        return super();
        }
    public Void set(Int v)
        {
        throw new ReadOnlyException();
        }
    private Void set(Int v)
        {
        super(v);
        }
    }

// adding mixin
public/private Int x;
// acts something like:
Int x
    {
    public Int get()
        {
        return super();
        }
    public Void set(Int v)
        {
        throw new ReadOnlyException();
        }
    private Void set(Int v)
        {
        super(v);
        }
    }

mixin SoftRef
        into Ref<T>
    {
    private set(T v)
        {
        // private-only functionality here
        super(v);
        }
    protected set(T v)
        {
        // protected-only functionality here
        super(v);
        }
    public set(T v)
        {
        super(v);
        }
    }

mixin LazyRef<RefType>
        into Ref<RefType>
    {
    private Boolean assignable = false;

    RefType get()
        {
        if (!assigned)
            {
            RefType value = calc();
            try
                {
                assignable = true;
                set(value);
                }
            finally
                {
                assignable = false;
                }

            return value;
            }

        return super();                              // _WHICH_ super?
        }

    Void set(RefType value)
        {
        assert !assigned && assignable;
        super(value);                               // _WHICH_ super?
        }
                                                                                       s
    protected RefType calc();
    }

// -- example for splitting out set() into a separate interface

interface Ref<RefType> // basically the old Ref interface, minus set()
    {
    RefType get();
    @RO Boolean assigned;
    @RO Type ActualTypes;
    }

interface Var<RefType>
        extends Ref<RefType>
    {
    Void set(RefType value);
    }

class B
    {
    @Lazy public/private Int Hashcode.calc()
        {
        return ...;
        }

    Void bar()
        {
        Ref<Int> r = &Hashcode;
        }
    }

Void foo(B b)
    {
    Ref<Int> r = &b.Hashcode;   // does NOT compile because Hashcode in the public interface is a CRef
    }

// So all properties/variables/constants/etc. are Ref's (@RO), and some (those with an accessible
// setter) are Var's, although a Var can still reject a set() (e.g. an immutable object)

--

interface Fooish
    {
    String foo()
        {
        return "Fooish";
        }
    }

mixin M1
        into Fooish
    {
    String foo()
        {
        return "M1, " + super.foo();
        }
    }

mixin M2
        extends M1
    {
    String foo()
        {
        return "M2," + super.foo();
        }
    }

class B
        implements Fooish
        incorporates M1
    {
    String foo()
        {
        return "B," + super.foo();
        }
    }

class D
        extends B
        incorporates M2
    {
    String foo()
        {
        return "D," + super.foo();
        }
    }

console.print(new B().foo());
console.print(new D().foo());
console.print(new @M2 B().foo());
console.print(new @M2 D().foo());

--

// properties can delegate

interface Hopper
    {
    Void jump();

    Int height;
    }

class BigHopper
    implements Hopper
    {
    // ...
    }

class FakeHopper
    delegates Hopper(big)
    {

    BigHopper big = ...;
    }

--

// properties from an interface

interface State
    {
    String abbrev;
    @RO String name;
    }

@Abstract class AbstractState
    implements State
    {
    // implicitly:
    // R/W String abbrev;
    // @RO String name;
    }

@Abstract class AbstractState2
    implements State
    {
    String name.get()
        {
        return super();
        }
    }

class SimpleState
    implements State
    {
    // implicitly:
    // String abbrev;
    // String name;
    }

class ComplicatedState
    implements State
    {
    String name.get()
        {
        return ...;
        }
    }

--

// proprerty "limit access"

class Base
    {
    public/private Int x;
    protected/private Int y;
    public/protected Int z;
    }

class Derived
        extends Base
    {
    // ...
    }

// typeinfo for Base:private
// Int x - Var, field, ...
// Int y - Var, field, ...
// Int z - Var, field, ...

// typeinfo for Derived requires typeinfo for Base:protected, which triggers limitAccess(protected)
// Int x - Ref (via SuppressVar), field, ...
// Int y - Ref (via SuppressVar), field, ...
// Int z - Var, field, ...

// typeinfo for Derived:private
// Int x - Ref (via SuppressVar), field, ...
// Int y - Ref (via SuppressVar), field, ...
// Int z - Var, field, ...

Derived d = new Derived(...);

// this requires the typeinfo for Derived:public
// Int x - Ref (via SuppressVar), field, ...
// Int z - Ref (via SuppressVar), field, ...

--

// version conflicts that would cause a compiler error, but not a verifier error

module m1 // v1
    {
    class Base
        {
        Void foo() {..}
        }
    }

module m2 // v2
    {
    package q import m1;

    class Derived
            extends q.Base
        {
        Void foo() {if (x == 3) ... else x = ..}

        private Int x;
        }
    }

module m1 // v3
    {
    class Base
        {
        Void foo() {..}

        public/private Int x;
        }
    }

// at this point, m2 will no longer compile
// m1 compiles in the absence of m2 (assume they're made by different groups or companies)
// m1 still works
// m2 still works ... even though there is a conflict that would prevent the compiler from succeeding

// ----- mixins / annotations into Ref/Var

mixin BlackHole<RefType> into Var<RefType> {...}

mixin AtomicVar<RefType> into Var<RefType> {...}

// TODO TypeConstant (and AnnotatedTypeConstant) needs

@Atomic T t;

type of &t is: AtomicVar<T> + Var<T>                // we can drop the "+ Var<T>" because it's redundant

@Atomic @Lazy T t;

type of &t is: AtomicVar<T> + LazyVar<T> + Var<T>   // we can drop the "+ Var<T>" because it's redundant

@Atomic (Runnable | String) p;
// type of p is "(Runnable | String)"
// type of &p is "AtomicVar<Runnable | String>"     // we've started dropping the Var by this point
Annotated(M1, Annotated(M2, Annotated(Atomic, Parameterized(Ref, T))))

// ----- @Override

interface I1
    {
    @Override Void foo();                           // compiler error
    }

interface I2
        extends I3
    {
    @Override Void foo();                           // ok
    }

interface I3
    {
    Void foo();
    }

mixin M1 into I2
    {
    @Override Void foo();                           // ok because "into" should lay down an implicit foo()
    }


// ----- conditional returns

conditional Int indexOf(Char ch);

if (Int of : s.indexOf('p'))
    {
    return s.substring(of);
    }
else
    {
    return s;
    }

// how to do with ternary operator?
(Boolean found, Int of) = s.indexOf('p');       // "of" not definitely assigned?!?
return found ? s.substring(of) : s;

(Boolean found, Int of) = s.indexOf('p');       // "of" not definitely assigned?!?
return found ? s.substring(of) : s;

// also you forgot ...
Int of = -1;
of := s.substring(of);

Alternatively:
(Boolean, Int) indexOf(Char ch);

--

- library (passive) - not "startable"
- "one and done"
    - command line
    - console
- daemon (server model)
    - database service?
- request/response (service model)
    - micro service
    - web app

"one and done"
"service model"

interface Module
    {
    Boolean supports(ApplicationStyle style);


    }

module My1nDModule
    {
    Void run()
        {
        @Inject String[] args;
        @Inject Console console;
        console.println("hello world!");
        }
    }

interface RequestProcessor
    {
    Response process(Request req)
        {
        TODO
        }
    }

mixin WebApp
        into Module
        implements RequestProcessor
    {
    @RO RequestProcessor handler.get()
        {
        return this;
        }
    }

@WebApp module HelloWorld
    {
    @Override
    Response process(Request req)
        {
        return new Response("hello world");
        }
    }

@WebApp module FusionApps
    {
    @Override
    RequestProcessor handler.get()
        {
        return FAService;
        }
    }

static service FAService
        implements RequestProcessor
    {
    @Override
    Response process(Request req)
        {
        return new Response("hello world");
        }
    }


// --- inference

// legal of course
List<Person> list = new ArrayList<Person>()

// we did NOT want to do this (Java hack), because "<>" has a meaning in Ecstasy
List<Person> list = new ArrayList<>() // error (because the <> _specifies_ "no type specified")

// could do this, because "<Person>" is inferable
List<Person> list = new ArrayList()

Map<String, Int> map = new HashMap<String>(); // error

// ---- expression type analysis / validation

(Int, String) foo();
Tuple<Int, String> bar();
Tuple<Int, String> t;

t = foo();          // allowed (packing)
t = bar();          // allowed (direct assignment)

Int i = foo();      // allowed (String is discarded)
Int i = bar();      // error (Tuple cannot be assigned to Int)

// ---- expression type analysis / validation (2)

(IntLiteral, String) foo();
Tuple<IntLiteral, String> bar();
Tuple<Int, String> zoo();

Tuple<Int, String> t;

t = (4, "hello");                   // allowed (conv pack)
(Int i, String s) = (4, "hello");   // allowed (conv pack unpack)
t = foo();                          // allowed (conv pack)
t = bar();                          // error
(Int i, String s) = foo();          // allowed
(Int i, String s) = zoo();          // allowed
Int i = zoo();                      // error
(Int i, String s) = bar();          // error

Int i = foo();      // sure, this is OK (String is discarded)
Int i = bar();      // error



// precision and conversions

Int x = 4/3;        // 1
Dec d = 4/3;        // 1.3333... (conversion of 4->Dec and 3->Dec and THEN the division)
Byte b = 300-50;    // compiler error (300 is out of byte range)

Dec sqkm = PI * (r * r) / (1000 * 1000);
Int sqkm = PI * (r * r) / (1000 * 1000);                // compiler error (PI can't convert to Int)
Int sqkm = (PI * (r * r) / (1000 * 1000)).to<Int>();    // ok iff r is of FP type (calc done with that type)

// how to handle this one?
Int zero = (1/3) * 10;


// --- switch expression

// for constant value match:
Int x = switch (y)
    {
    case 0:     0;
    case 1, 2:  -1;
    case 3:     {
                Int j = 99;
                for (Int i : 5..10)
                    {
                    j += i;
                    }
                return j;
                }
    case 4:     12;
    default:    17;
    }

// (as ternary instead)
Int x = y == 0 ? 0
      : y == 1 ||
        y == 2 ? -1
      : y == 3 ? () -> {
                    Int j = 99;
                    for (Int i : 5..10)
                        {
                        j += i;
                        }
                    return j;
                    }               // how to ".run()" it?
      : y == 4 ? 12;
               : 17;
    }

// (as switch statement instead)
Int x;
switch (y)
    {
    case 0:     x=0; break;
    case 1:
    case 2:     x=-1; break;
    case 3:     {
                    x = 99;
                    for (Int i : 5..10)
                        {
                        x += i;
                        }
                    }
                 break;
    case 4:     x=12;  break;
    default:    x=17;  break;
    }

// for first boolean expression match:
Int x = switch ()
    {
    case (a > 3):   0;
    case (b == c):
    case (b < 0):   75;
    default:        17;
    }

Int x = (a > 3) ? 0
      : (b < 0) ? 75
                : 17

// To consider: "right side of lambda expression", i.e. "()->{...}();" (define AND invoke)
// name it "statement expression"?
Int x = {
        Int j = 99;
        for (Int i : 5..10)
            {
            j += i;
            }
        return j;
        }

// (semantically equivalent to this)
Int x = () -> {
        Int j = 99;
        for (Int i : 5..10)
            {
            j += i;
            }
        return j;
        }();

// try, using, etc.
Int x =
    {
    try (Socket socket = createSocket())
        {
        return socket.readInt();
        }
    catch (Exception e)
        {
        return -1;
        }
    }

// or even ..
Int x = try (Socket socket = createSocket())
        {
        return socket.readInt();
        }
    catch (Exception e)
        {
        return -1;
        }

// or even ..
Int x = using (Socket socket = createSocket())
    {
    return socket.readInt();
    }

// in C ...

int x = 1, 2, 3;    // x = 3 ... stupid!

// tuple
switch (x, y)
    {
    case (0, 1):
    case (1, 0):
    case (2,4), (5,7):
    }

case 'a': case 'b': case 'c': ...   // bad
case 'a', 'b', 'c':                 // good

// no-deref

class C
    {
    Void foo();
    Void foo(String s);

    Void bar(String s, Int i);
    Void bar(Int h, Int i);

    Int zoo();
    String zoo();

    Int x;
    }

C c = ...

c.foo;          // error
c.&foo;         // error

c.&foo();       // Function<<>, <>>
C.&foo();       // method
c.&foo("x");    // Function<<>, <>>
c.foo(?);       // Function<<String>, <>>
c.&foo(?);      // error
c.&foo(String); //

c.bar(?, 5)             // error (ambiguous)

c.&bar(Int,Int)(?, 5);  // ... how to know that Int is a parameter type and not an argument?
c.&bar(?.as(Int), 5);   // ... ugh ugh ugly
c.&bar(<Int>?, 5);      // ... reads well
c.&bar(?<Int>, 5);      // ... and not so well

c.bar("Hello", ?);      // not ambiguous

c.zoo<Int>;     // bad (unreadable)
c.&zoo<Int>();  // good

c.x;            // Int
c.&x;           // Var<Int>
C.x;            // Property<Int>

// ---
case throw a?.b?.c?.d?.e :

class Q<T>
    {
    construct(Int x)
        {
        // ...
        }
    }

class B
    {
    construct(Int x)
        {
        // ...
        }
    }

class C extends B
    {
    construct(Int x)
        {
        construct C("hello");

        // ...

        construct B(x);
        }
    finally
        {
        foo();
        }

    construct(String s)
        {
        // ...
        }

    static Int bar(String s)
        {
        // ...
        }

    Void foo()
        {
        // ...
        }

    Void bar()
        {
        Function fFoo  = &foo();
        Function fBar  = &bar();
        Method   mFoo  = C.&foo();                  // can NOT omit the "C." because then it is a function
        Function fCon  = C.&construct C(<Int>?);    // ugly
        Function fCon2 =   &construct C(<Int>?);    // can omit the "C." because it is implicit (from this) and because we specify "C"
        Function fCon3 = B.&construct B(<Int>?);    // ugly
        Function fCon4 =   &construct B(<Int>?);    // can omit the "B." both because it is implicit (from this) and because we specify "B"
        Function fCon5 =   &construct Q(<Int>?);    // OK
        Function fCon6 =   &construct Q<Int>(<Int>?);    // OK
        Function fCon7 =  Q<Int>.&construct Q(5);    // not wrong but ugly
        Method   mToStr=  Q.&to<String>();

        ... = &bar(?).conditionalResult);           // type of "&bar(?)" is Function, which has a "conditionalResult" property
        ... = bar().maxvalue;                       // bar returns an Int64, and Int64 has a static constant "maxvalue"
        ... = bar().&maxvalue;                      // this yields a Ref<IntLiteral>
        ... = bar().sign;                           // bar returns an Int64, which has a "sign" property
        ... = bar().&sign;                          // this yields a Ref<Signum>
        }
    }

// list literal

List l1 = {1,2,3};      // old (no longer supported; collides with StatementExpression)
List l2 = [1,2,3];      // new
Int i = [1,2,3][1];     // ugh! but ok

// --- isA() discussion

class C<T1, T2 extends List<T1>> // any use of T2 (param or return) results in T1 being consumed and produced
    {
    // ...
    T2 bar();
    }

C<Person> c = new C();

class D<T3>
    {
    C<T3> foo()     // the result of this is that D both consumes and produces T3
        {
        // ...
        }
    }

//

// consider the case of "Point p;"

Point                   // Class
Point.x                 // Property
Point.x.assigned        // Property
Point.x.&get()          // Method

Origin                  // Singleton instance
&Origin                 // Class
&Origin.x               // Property
Origin.x                // Int value
Origin.&x               // Ref/Var
Origin.x.assigned       // Boolean value
Point.x.&get()          // Method

p.x                     // Int
p.&x                    // bound property, i.e. Ref/Var: Var<Int>
p.&x.&get()             // Function


Class c = Point;
Boolean f = c.isAbstract;
// could have also said:
Boolean f = Point.isAbstract;

// what if class Person has a property "name" (note that Class class also has a property "name")
Person.name             // *must* look first on Person, not on Class, so this refers to the Property "name" on Person
Person.name.assigned    // the "assigned" Property of the "name" Property of Person

String  s = Point.name              // String value of the "name" property of the "Class" class for the Point class
Boolean b = Point.name.assigned     // Boolean value of the "assigned" property of the above

// ----- functions as types (for mark)

void f1() {...}
void f2() {...}
Boolean f3() {...}

(f3() ? f1 : f2)();

// or ...
function void() f = f3() ? f1 : f2;
f();


void f5(Int n) {...}
void f6(String s) {...}
Boolean f3() {...}

f3() ? f5(4) : f6("hello");

// diff types

Boolean f3() {...}
String f7(Int n) {...}
Int f8(String s) {...}
f3() ? f7(4) : f8("hello");     // compiler error: types of the two expressions don't match
f3() ? {f7(4);} : {f8("hello");};

// methods and functions and binding oh my!

class C
    {
    void foo(Int n, Int n2, String s) {...}
    void foo1(Int n, function Int () fn2, String s) {...}
    void foo2(Int i) {...}
    Int foo3() {...}
    }

C c = ...;

function void (String) f1 = c.foo(5, bar(), ?);
function void (String) f1 = c.&foo3();
function void (String) f6 = s -> c.foo(5, bar(), s);

function Int () f7 = c.&foo3(); Int j = f7();
function Int () f8 = c.&foo2(5); Int j = f8();

var f2 = c.foo(5, 5, ?);
Function f3 = c.foo(5, 5, ?);
Function<<String>, <>> f4 = c.foo(5, 5, ?);
f1("hello");
f3("hello"); // error

function Point (Int) pg = y -> new Point(bar(), y);
function Point (Int) pg = new Point(bar(), ?);

function Util (void) pu = &new Util();


Method<C, <Int, Int, String>, <>> m  = C.foo(?, ?, ?);
Method<C, <Int, Int, String>, <>> m2 = C.foo(<Int>?, <Int>?, <String>?);

Method m3 = C.foo(3, ?, ?);     // error (foo is not bound to a target; no partial or full binding of methods)

function f = new C().&foo(?, ?, ?);
f(1, 2, "hello");                   // error - type is Function, not!!! Function<<Int, Int, String>, <>>
                    // note: using type inference, this WILL work, i.e. NOT an error

// not name expression example

class C
    {
    function void () foo()
        {
        return &bar();
        }

    void bar()
        {
        ...
        }
    }

new C().foo()();

// turtles

typedef Function<<>, <Turtle>> Turtle;   // note: this capability is not yet supported
Turtle f = ...;
f()()()()()()()()()()()();

// ----- constructors

// from Example.x, with modifications

const Person(String name, Date dob)
    {
    // implicit:
    String name;

    // implicit:
    Date dob;

    // implicit:
    construct(String name, Date dob)
        {
        construct Object();
        this.name = name;
        this.dob  = dob;
        }
    }

mixin Taxable(String taxid)
    {
    // implicit:
    String taxid;

    // implicit:
    construct(String taxid)
        {
        this.taxid = taxid;
        }
    }

const Employee(String name, Date dob, String taxid)
        extends Person(name, dob)
        incorporates Taxable(taxid)
    {
    // implicit:
    construct Employee(String name, Date dob, String taxid)
        {
        construct Person(name, dob);
        construct Taxable(taxid);
        }
    }

// ----- array instantiation

// old-style syntax: this means "create an array of 4 elements and set each element to the default value of the type" e.g. Int=0
Int[] ai = new Int[4];

// new-style syntax: this means "create an growable array of elements of the specified type, starting with an empty array"
Int[] ai = new Int[];

// ... or explicitly in the Array constructor
Int[] ai = new Array<Int>(4, i -> i);

// new-style syntax: this means "create an fixed size array of n elements of the specified type, using the provided function to initialize each element"
Int[] ai = new Int[4].fill(i -> i);
Int[] ai = new Int[].fill(0..3, i -> i);

@Inject Database<Person> db;
Person[] people = new Person[].cap(100).fill(0..99, i -> db.load(i));

//

Map.Entry<String, Int> e1 = ..
Map.Entry<String, Int> e2 = ..
if (e1 == e2)   // compiles as: Map.Entry.equals(Map.Entry<String, Int>, e1, e2)

//

interface I { void a(); void b(); void c(); }

class B
    {
    void a()
        {
        print("on B");
        }
    }

class D
        extends B
        delegates (I-B) (TODO("just mocking"))
    {
    I someOtherI;

    void b()
        {
        print("on D");
        super(); // goes to "delegates"
        }
    }

//

List<Animal> l0 = new List<Dog>;
List<Dog>    l1 = l0.as(List<Dog>);
List<Animal> l2 = l1;

// child classes

class B
    {
    class Child
    }

// ambiguity?

// type: function Object foo(Object)
void foo(Object o)
void foo2(Object o, Object o2)
void fooOpt(Object o, Int i = 0, String s = "hello");

// option #1: disallow automatic unpacking altogether; treat the tuple argument as any other object
// option #2: allow automatic unpacking only for functions/methods requiring more than one parameter;
//            (i.e. disallow a tuple being used as an "unpack" argument to a single-parameter method,
//            and instead treat tuple as any other object when calling with only one argument)
//         2a: disallow only when the parameter type is wider than Tuple (e.g. Object)
//         2b: disallow always (preference du jour)
//           -> i) force widening type assertion, e.g. "t.as(Object)"
//           -> ii) just treat it as a wider type automatically, as in #1
//           -> iii) labeled expression: "foo(o=t)"

// when making a call, passing only one argument, and that argument is a compile time tuple:
// 1) there is more than one required parameter, the tuple is unpacked,

Tuple<String> t = ...
foo(t);     // could be ok, but that could be either way, which is ambiguous
            // could be compiler error (or warning?)

Tuple<String, Int> t2 = ...
foo2(t2);   // error
foo(t2);

void zip(Tuple t) {...}
void test()
    {
    Tuple<Tuple<Tuple>>> ttt = ...
    zip(ttt); // ???
    }

// multiple returns

(Int, Int) qoo();
Tuple<Int,Int> t = qoo();

(Int, Int) bar(Int i, Int j);
bar(qoo());
bar(bar(qoo()));

// arbitrary analogy example
Tuple t2 = (1,2);       // ok
Tuple t2 = Tuple:(1,2); // ok (same as above)
Tuple t  = 1;           // error
Tuple t  = (1);         // error (same as "(1*1)" or "(1+0)", i.e. it's just a parenthesized expression)
Tuple t  = Tuple:(1);   // ok (explicit tuple literal)

// -- equals()

Object  o1 = ..
Object  o2 = ..
if (o1 == o2) {..}      // ok because there is an equals() function on Object itself

class C extends Object {}
C c1 = ..
C c2 = ..
if (c1 == c2) {..}      // should this be allowed? (no equals() function on C, but one is on Object)

class A {}              // assume that it has both equals() and compare() function
class B {}              // assume that it has both equals() and compare() function
if (a1 == a2) ...
if (a1 < a2) ...

(A | B) ab1 = ..
(A | B) ab2 = ..
if (ab1 == ab2) ...     // ok
if (ab1 < ab2) ...      // ... is this ok?

// something simpler

@M1 Int mi1 = ..
@M1 Int mi2 = ..
if (mi1 == mi2) {..}        // what does it mean?


@M1 @M2 (A | B) mmab1 = ..
@M1 @M2 (A | B) mmab2 = ..
if (mmab1 == mmab2) ...     // ???


(A | B) ab1 = ..
(B | A) ba2 = ..
if (ab1 == ba2) ...         // ok? Gene says no

A a1 = ..
immutable A a2 = ...
if (a1 == a2)               // Gene says no

// problem with mutable / immutable, List/Array etc. when constants are involved
List<Int> list = ...
if (list == [0,1,2]) ...    // what is the type of the constant? it's an Array<Int>, not a List<Int>

//

class C
    {
    construct (Int n) {..}
    (String, Int) foo(String s, Int i, Boolean f) {..}
    Int bar() {..}
    }

C c = ...
function (String, Int) (String) f = c.foo(?, 5, true);
function (String, Int) (String) f = (String s) -> c.foo(s, 5, true);

function List<String> () f = () -> new List<String>(5);

function Int () f = c.&bar();

Class.Constructor f = &construct c(5)

// -- .is()

if (x.is(String)) {...}

// short-circuit

a? : b
a?:b        // same as above
a ?: b      // completely different operator, but same result

//   a op= b
// translates to:
//   a = a op b, where any sub-expressions of a are only executed once

a ?:= b

// where to allow trailing "?" short-circuiting expressions?
// rule #1: anywhere that it grounds using ElseExpression
bar(a?.b?.c : d);

// rule #2: right side of assignment:
a = b?;     // as if: "a = b ?: a;"

// GG ugly example
a[foo()] = foo()?;

// rule #2 exception: right side of declaration is NOT allowed:
Int a = b?; // compiler error!

// left side of invocation (very UNcontroversial BECAUSE ALL SIDE EFFECTS ARE LEFT TO RIGHT)
a?.foo();

// what about left side of assignment?
a?.b = c;           // desirable
a?[3] = b;          // this is not as easy to give a thumbs-up to .. but WHY?

a?[b?] += c?;

bar()[foo()] += c?;
// compiles as
t0 = bar();
t1 = foo();
t2 = t0[t1]
if (c != null)
    {
    t0[t1] = t2 + c;
    }

// handy example
list? += err;

// only slightly more confusing
list? += err?;  // i might not be tracking errors, and it might not have been an error, but if it was
                // an error and if i'm tracking errors, then log the error
// alternative:
if (list != null && err != null)
    {
    list += err;
    }

// 100% certain that we want to disallow inside invocation
foo(a?);

// 100% certain that we want to disallow inside construction
X x = new X(a?);

// .. including tuples
Tuple t = (a, b, c?.d);

// actual example
if (lvalueNew != null)
    {
    lvalue = lvalueNew;
    }
// would be ..
lvalue = lvalueNew?;

// GG asks: "why not .."
lvalue ?= lvalueNew;
lvalue =? lvalueNew;

// java
a[foo()] += foo() ? 1 : 2;
// compiles as
t0 = foo()
t1 = a[t0]
t2 = foo()
a[t0] = t2 ? 1 : 2

// ----- mutating lambdas

// ugly and bad, but completely supported
Int i = 0;
Int j = 0;
function void() f1 = () -> {j *= i;};
function void() f2 = () -> {++j;};
for (i : 0..5)
    {
    f1();
    f2();
    }
// at this point, j==326

// ----- inner class child

class Dict<KT,VT>
    {
    class Entry
        {
        KT key;
        VT val;
        }
    }

// can you get KT from an Entry?
void foo(Dict.Entry entry)
    {
    entry.KT k = entry.key;         // compile error
    Dict.entry.KT k = entry.key;    // ok

    // decision
    entry.Dict.KT k = entry.key;
    // i.e. deprecate "ClassName.this" and replace with "expr.ClassName" synthetic property
    }

// ---- short circuiting decisions: in conditionals

if (a?.b?.c) // short-circuit DEFINITELY doesn't go to the "then"
    {
    // ...
    }

if (var x : a?.b?.c()) // ... but does it go to the else?     (as if trailed by " : false")
    {
    // ...
    }
else
    {
    // does it come here? (GG: yes, CP: yes)
    // NOTE: x is not definitely assigned
    }

if (var x : a?.b?.c())
    {
    // x is definitely assigned
    // ...
    }
else
    {
    // short circuit comes here (this is potentially confusing, since it's not the inverse of the previous example)
    // NOTE: x is not definitely assigned
    }

if (a != null)
    {
    if (a.b != null)
        {
        if (var x : a.b.c())
            {
            // then code
            }
        }


if (a != null && a.b != null && a.b.c == "hello") // could be re-written: if (a?.b?.c == "hello")
    {
    // do something
    }
else
    {
    // do something else
    }

if (X) {} else {}

if (a != null)
    {
    if (a.b != null)
        {
        if (a.b.c == "hello") // could be re-written: if (a?.b?.c == "hello")
            {
            }
        else
            {
            // do something
            }
        }
    else
        {
        // do something else
        }
    }
else
    {
    // do something else
    }

if (a && b && c)
    {
    // then code
    }
else
    {
    // else code
    }

// compiles the EXACT same way as
if (a)
    {
    if (b)
        {
        if (c)
            {
            // then code
            }
        else
            {
            // else code
            }
        }
    else
        {
        // else code
        }
    }
else
    {
    // else code
    }

// switch statement - similar idea, in that the short-circuit is grounded by the "expression portion"
// of the statement, not the statement level itself (i.e. it doesn't skip the statement)
switch (var x = a?.b?.c) // as if trailed by some "impossible other" value " : -99999999999999999999999.."
    {
    case 0:
        // ...
        break;
    case 1,2:
        // ...
        break;
    default:
        // does it go here? (CP: yes, GG: yes)
        // NOTE: x is not definitely assigned in the default branch
        foo();
        break;
    }
// or does it go here?

// switch expression - same

while () // .. same (as if trailed by " : false")