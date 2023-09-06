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
        throw new ReadOnly();
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
        throw new ReadOnly();
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

mixin LazyRef<Referent>
        into Ref<Referent>
    {
    private Boolean assignable = false;

    Referent get()
        {
        if (!assigned)
            {
            Referent value = calc();
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

    Void set(Referent value)
        {
        assert !assigned && assignable;
        super(value);                               // _WHICH_ super?
        }
                                                                                       s
    protected Referent calc();
    }

// -- example for splitting out set() into a separate interface

interface Ref<Referent> // basically the old Ref interface, minus set()
    {
    Referent get();
    @RO Boolean assigned;
    @RO Type ActualTypes;
    }

interface Var<Referent>
        extends Ref<Referent>
    {
    Void set(Referent value);
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

// property "limit access"

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

mixin BlackHole<Referent> into Var<Referent> {...}

mixin AtomicVar<Referent> into Var<Referent> {...}

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
    case a:                         // MUST BE OF TYPE TUPLE
    case b, c:                      // MUST BOTH (!!) BE OF TYPE TUPLE
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
        Method   mToStr=  Q.&toString();

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

class C {}
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

// ----- resolved types

mixin SimpleSorter<SortType>
        into Object
    {
    }

// GG thinks this should be illegal:
mixin Sorter
        into List
        extends SimpleSorter<Element>

// GG thinks this (or some other specificity) should be required:
mixin Sorter<Element>
        into List<Element>
        extends SimpleSorter<Element>

// ----- parameterized types

class B<T>
    {
    C cFirst;           // B<T>.C<???>
    C<Int> c2;
    C<Int,String> c3;   // error (both compile time and verifier)


    class C<T2>         // B<T>.C<T2>
        {
        void foo()
            {
            C c = ...   // B<T>.C<T2>
            }
        }
    }

// ----- injection

interface Console
    {
    void println(Object o = "");
    }

const Int
    {
    // ...
    }

module MyApp
    {
    class MyClass
        {
        @Inject Console console;    // obviously an interface type; no way to learn the class identity behind it

        @Inject Int pin_code;       //
        }
    }

// -----

const Point(Int x, Int y);

const Rectangle(Point topLeft, Point bottomRight)
    {
    Point topRight.get()
        {
        return new Point(bottomRight.x, topLeft.y);
        }

    Point bottomLeft.get()
        {
        return new Point(topLeft.x, bottomRight.y);
        }
    }

//

Int j;              // ctx 0
if (Int i : foo())  // ctx 1:0 -> decl i, split, 1.t[i]=assigned
    {               // ctx 2a:1.t
    j = ...         // 2a[j]=assigned
    }               // commit 2a
else
    {               // ctx 2b:1.f
    i = ...         // 2b[i]=assigned
    }               // commit 2b
                    // commit 1 (forces join of 1.t+1.f

// read/write

x = 1;              // write to x
foo(x);             // implicit read from "this", read from x
x = p;              // write to x, implicit read from "this" (property p)
x.y.z = 4;          // read from x (it's always the left-most that is the one marked for read or write)
p = 4;              // implicit read from "this" (property p)
x.foo();            // read from x
++x;                // read from x, write to x

// so basically, any use of a variable name (typically a name that has no "left") means "read" by default
// - the use of a name without a left can mean "this", which means that the "this" is read by default
// - the use of "&" before the name suppresses any read or write marking

// mark read:
// - NameExpression
// - InvocationExpression (if there is no "left left", and the left needs an implicit "this")

// the only things that "mark write" on a variable:
// - SequentialAssignExpression - pre- and post- increment and decrement
// - AssignmentStatement
// - VariableDeclarationStatement
// - MultipleDeclarationStatement

// ---- assertion with declaration

assert Int i : someConditionalMethod();
if (i > 4) {...}

// instead of:
if (Int i : someConditionalMethod())
    {
    if (i > 4) {...}
    }
else
    {
    assert;
    }

// property initializers
class C
    {
    construct ()
        {
        }
    finally
        {
        i = new I();   // ok (but wouldn't work in a "const")
        }

    Int x = 0;
    Int y = x;   // "="(this:struct)

    class I
        {
        // ***implicit*** property of type C called C
        public C C.get() { return MOV_THIS(1); };

        Int z = y; // return MOV_THIS(1).y
        }

    I i = new I(); // illegal (no "this" yet)
    }
// Question 1: Is it possible to allow "this:struct" access inside "=" functions for property initializers? (Or does it have to be passed in?)
// Solution: No. The structure needs to be passed in as a parameter. This is going to be determined by capture analysis.
//           Parameter type will be C:struct, name will be "this:struct"

// Question 2: Is it possible to allow MOV_THIS (>0) inside "=" functions for property initializers? (or do outer "this" instances need to be passed in?)
// Solution: No. The "outer this" needs to be passed in as a parameter. This is going to be determined by capture analysis.
//           Parameter type will be C:private, name will be "C" (and the inner class must NOT be static)
//           (In the case of an anonymous inner class, it will automatically be converted to non-static to allow the capture of the outer this.)

// Implication of the above:
// - the runtime must be able to automatically provide "="() functions (property initializers) with "this:struct" and any required outer this

// IfCondition

@Inject X.io.Console console;
if (hot)
    {
    console.println("This porridge is too hot!");
    }
else if (cold)
    {
    console.println("This porridge is too cold!");
    }
else
    {
    console.println("This porridge is just right.");
    }

void printNext(Iterator<String> iter)
    {
    @Inject X.io.Console console;
    if (String s : iter.next())
        {
        console.println("next string=" + s);
        }
    else
        {
        console.println("iterator is empty");
        }
    }

interface FooBar
    {
    (Boolean, String, Int, Int) foo();
    conditional (String, Int, Int) bar();
    }

FooBar  fb1 = /* ... */;
FooBar? fb2 = /* ... */;

if (fb1.foo()) {...}
if (fb1.bar()) {...}

if (String s, Int x, Int y : fb1.foo())
    {
    console.println("foo()=True, s={s}, x={x}, y={y}");
    }
else
    {
    console.println("foo()=False, s={s}, x={x}, y={y}");
    }

if (String s, Int x, Int y : fb1.bar())
    {
    console.println("bar()=True, s={s}, x={x}, y={y}");
    }
else
    {
    // this line would be illegal, because bar() has a
    // conditional return, and it returned False:
    //   console.println("bar()=False, s={s}, x={x}, y={y}");
    console.println("bar()=False");
    }

if (fb1.bar()) {...}

if (fb2?.foo()) {...}

// ---- for changes

conditional (Int, Int) foo() {..}
while (Int i, Int j : foo()) {....}

(Int, Int) bar() {..}
for ((Int i, Int j) = bar(); i < 100; ++i, ++j)
    {
    ...
    }

// stand-alone declaration
(Int i, Int j) = bar();

//

for ((Int i, Int j) = bar(), String s = foo(), (x, y, z) = p?.coords(), (a[0], a[1]) = bar(); i < 100; ++i, ++j)

//

if (String s : iter.next()) {..}

//

(Int i, Int j) = (5, 7);

(Color c, Flavor f) = (Other, Other);

interface List
    {
    void add(Object o);
    void addAll(List l)
        {
        // natural implemnetation for each ...
        }

    List tail(Object o)
        {
        // some natural impl
        }
    }

class List2 implements List
    {
    void add(Object o) {...}

    // implicitly has:
    // void addAll(List l)

    // does **NOT** implicitly have:
    // void addAll(List2 l)
    }

class List3 extends List2
    {
    void addAll(List3 l) {..}

    // implicit cap:
    // void addAll(List1 l) { addAll(l.as(List3)); }
    }

List2 l = new List3();

// --- definite assignment challenge

// REVIEW
// some contexts do not allow declarations, e.g. an expression context, or an inferring context
// a context that does allow declarations must handle tracking the "effectively final" status of variables (determined when the vars go out of scope)
// the main thing is that once a context goes into an "unreachable" state, it no longer tracks unassigned/assigned
// - its information is only used to prove/disprove effectively final

// ctx0
Int i;
L1: /*ctx1*/while (/*ctx1.1*/true) // ctx.enterLoop();
  {
  L2: /*ctx2*/while (true)
    {
    /*ctx3*/if (/*ctx3.1*/foo())
      {//ctx3.2
      break L1;
      }
    i = 1;
    } // ctx.exit();
  // is i assigned? no
  print i;
  }
// is i assigned? yes

// example of nested not interfering with outer context
{
Int i;
if (foo())
  {
  i = 1; // at this point, "i" is "AssignedOnce"
  return i;
  }
// what is the Assignment for "i" here? it must be "UnassignedOnce"
}

//
{
Int i = 1; // at this point, "i" is "AssignedOnce"
if (foo())
  {
  i = 2; // at this point, "i" is "Assigned"
  return i;
  }
// what is the Assignment for "i" here? it must be "Assigned" (NOT AssignedOnce, since there are 2 points that assign i, even though not in this "path")
}



// example changes
String s = ╔═════════════════════╗
           ║This could be any    ║
           ║freeform {xyz} that  ║
           ║{{for (Person p : people) { $.append(./persontemplate.jsp); } return ""; }}}║
           ║Ecstasy source file  ║
           ╚═════════════════════╝ + '.';
String s2 = ./template.jsp;     // same dir as this source file
String s3 = /template.jsp;      // same dir as the module source file
String s4 = /util/template.jsp; // relative to the module source file
String s4 = $./Example12.x;     // this file


// case "match" expressions
switch (a,b,c)
    {
    case ("hello", 2, false), ("bye", 7, true):
        foo();
        break;

    case ("ugh", 3..5, ?):

    }

console.println(╔═════════════════════════════════╗
                ║This is a report                 ║
                ║There are {list.size} items:     ║
                ║{{                               ║
                ║Each: for (var v : list)         ║
                ║    {                            ║
                ║    $.add("#{Each.count}={v}");  ║
                ║    }                            ║
                ║}}                               ║
                ║This is the end of the report.   ║
                ╚═════════════════════════════════╝);

console.println($"list={{L: for (var v : list) {$.add("{L.count}={v}");}}}")

// functions

String s = "hello";
Function f = s.reverse;

String s = "hello";
Function<<>, <String>> f = s.reverse;

String s = "hello";
function String() f = s.reverse;

while (i < 19)
    {
    }
foo();

// kotlin
val x: type = initializer

// this variable object (&x) is a Var<MyType>, which means that it is read/write holder
// of a value that must be an instance of MyType; if it is determined to be effectively
// final, then (&x) is a Ref<MyType>, which is a read-only holder of a MyType value
MyType x = initializer;


// this variable object (&y) is a Ref<MyType>, which is a read-only holder of a MyType
// value (inferred from the initializer)
val y = initializer;

// this variable object (&z) is a Var<MyType>, which means that it is read/write holder
// of a value that must be an instance of MyType (inferred from the initializer)
var z = initializer;

Int x = foo();
Int y = bar();
val z = (() -> x * y)();

// kotlin
fun foo(arg1: type1, arg2: type2): returnType {}

// ecstasy
static returnType foo(Type1 arg1, Type2 arg2) {}

// ecstasy
// ecstasy - 5 parameters, including 2 (hidden, i.e. auto)
// type params, 1 required param, and 2 optional params,
// returning 3 values
static <TP1 extends Map<Int, String>, TP2 extends TP1.Key> (TP1, TP2, TP1.Value) bar(TP1 map, TP2 key = 0, Boolean opt = true) {}

Map<Int, String> m = ...;
Int x;
(m, x, String s) = bar(m, opt=false);

//

interface Map<Key, Value>
    {
    static interface Entry
        {
        @RO Key key;
        Value value;
        }
    }

// note: this class can only exist outside of a class that implements Map because Entry is declared
//       as static
class SimpleEntry<X, Y> // could be Key, Value
        implements Map<X, Y>.Entry
    {
    // ...
    }

// TODO
mixin MapMixin into Map
    {
    mixin EntryMixin into Entry
    }

// somewhere on an Entry
Map mapThis = this.Map;
Map mapThat = that.Map;

// explicit, as if it were a property:
this.Map.put(k, v);
// "reference to the Map" style:
&Map.put(k, v);

// --- "value" class discussion

const Order
    {
    List<OrderLine> lines;

    Order:struct addOrderLine(OrderLine line)
        {
        Order orderNew = ensureMutable();
        orderNew.lines += line;
        return orderNew;
        }

    construct (Order that, List<OrderLine> lines)
        {
        construct Order(that.meta.struct_); // const always has one of these constructors e.g. for deser
        this.lines = lines;
        }

    Order addOrderLine(OrderLine line)
        {
        Order:struct order = &meta.struct_.ensureFixedSize(); // i.e. ensureMutableContents();
        order.lines = this.lines.add(line);
        oder.date = now;
        return new Order(order);

        return new Order(this, this.lines.add(line));
        }

    Order removeOrderLine(Int lineNumber) {...}

    List<Order> orderLines {...}
    }


// --- "shared mutable" discussion

service ConcurrentHashMap
    {
    @Atomic Int version;
    @Atomic Int bucketCount;

    Bucket bucketFor(Int n) {...}

    void addListener(Listener listener) {...}

    interface Listener
        {
        void newVersion(Int version);
        }
    }

service Bucket
    {
    }

const PartitionInfo
    {
    Int partitionCount;
    Bucket[] buckets;
    }

// -- complications for nested virtual child super

class D3 extends D1
    {
    @Override class C1
        {
        @Override class C2
            {
            @Override class C3
                {
                @Override class C4
                }
            }
        }
    }

class D1 extends B
    {
    @Override class C1
        {
        @Override class C2
            {
            }
        }
    }

class B
    {
    class C1
        {
        class C2
            {
            class C3
                {
                class C4
                }
            }
        }
    }

// -- switch again

// case "match" expressions
switch (a,b,c)
    {
    case ("hello", 2, false), ("bye", 7, true):
        foo();
        break;

    case ("ugh", 3..5, _):
    }

switch (i)
switch (i, s)
switch (Int i = foo())
switch ((Int i, String s) = foo())
    {
    case (1, "hello"):
    case ((1, "hello")):    // this works, but is definitely NOT required
    }
switch (Int i = foo(), String s = bar())    // ???
switch (i, String s = bar())                // ???
switch (foo(), String s = bar())            // ???
switch (x, (Int i, String s) = foo(), y)    // ???
switch (x, (Int i, String s) = (foo(), y))  // ???
    {
    // we must pick one of these:
    case (1, 2, "hello"):                   // flattening
    case (1, (2, "hello")):                 //
    }

switch (x)
    {
    case 0, 1, 2:
    case 3, 4, 5:
        ...
        continue;

    case 7:
        ...
        break;
    }

// multi-value result
(x, y) = switch (x, y)
    {
    case (0, 1): 1, 1;
    case (1, 1): 1, 0;
    case (1, 0): 0, 0;
    case (0, 0): 0, 1;
    }

// tuple result
(x, y) = switch (x, y)
    {
    case (0, 1): (1, 1);
    case (1, 1): (1, 0);
    case (1, 0): (0, 0);
    case (0, 0): (0, 1);
    }

class C
    {
    @Inject static Int Q;
    //...

    void foo(Int i)
        {
        switch (i)
            {
            case 0:  //...
            case Q:  //...
            default: //...
            }
        }
    }

switch (x)
    {
    case a:
        {A}
        break;
    case b:
        {B}
        break;
    case c:
        {C}
        break;
    default:
        {D}
        break;
    }

if (x==a)
    {
    A
    }
else
    {
    if (x==b)
        {
        B
        }
    else
        {
        if (x==c)
            {
            C
            }
        else
            {
            D
            }
        }
    }

// property
static Int X = 1;
//....
switch (X)
    {
    case 0:
    case 7, 9:
    case 8:
        //....
    case 1:
        //....
    case 2:
        //....
    default:
        //....
    }

// ----- using / try..catch

using (X x = new X())
    {
    ...
    }

ENTER
VAR_N X x
NEW_0 X -> x
GUARD_ALL
...
FINALLY (e)
GUARD
// if (x.is(Closeable)) { x.close(); }
JMP_NTYPE skip_close
NVOK_00 x Closeable.close
skip_close: GUARD_E
CATCH Exception e_close
// if e == null throw e_close
JMP_NNULL e skip
throw e_close
skip_throw: CATCH_E
FINALLY_E
EXIT

// --- services thoughts

// we need a buffer that we can pass between services. since it's a mutable buffer (even if it
// represents the data it carries immutably), it needs to be a "service" itself (kind of like
// how atomic properties are "services").

service ReadBuffer
    {
    Int first;

    Int last;

    void release();
    }

// --- Strings, take 10

// instead of the box, we do need something that gives us:
// - basically any characters are legal, even "escapes", white space, and what-not
// - multi-line support
// - basically like taking a file and pasting it in
// - ideally easy to do by hand
// - parsable
// - looks good
//
// example: here's the code that I want to paste in as a string:

<%@ taglib uri="/WEB-INF/jspwiki.tld" prefix="wiki" %>
<%@ taglib uri="http://stripes.sourceforge.net/stripes.tld" prefix="stripes" %>
<stripes:useActionBean binding="/View.action" />
<wiki:Include page="ViewTemplate.jsp" />
String myString = "This is my string\n" +
        " which I want to be \n" +
        "on multiple lines.";
|s = """ this is a very
        long string if I had the
        energy to type more and more ..."""
`string text`

`string text line 1
 string text line 2`

`string text ${expression} string text`

tag `string text ${expression} string text`
//const char* p = "\xfff"; // error: hex escape sequence out of range
const char* p = "\xff""f"; // OK: the literal is const char[3] holding {'\xff','f','\0'}


// here's how it would look in Ecstasy:

String s = `|<%@ taglib uri="/WEB-INF/jspwiki.tld" prefix="wiki" %>
            |<%@ taglib uri="http://stripes.sourceforge.net/stripes.tld" prefix="stripes" %>
            |<stripes:useActionBean binding="/View.action" />
            |<wiki:Include page="ViewTemplate.jsp" />
            |String myString = "This is my string\n" +
            |        " which I want to be \n" +
            |        "on multiple lines.";
            ||s = """ this is a very
            |        long string if I had the
            |        energy to type more and more ..."""
            |`string text`
            |
            |`string text line 1
            | string text line 2`
            |
            |`string text ${expression} string text`
            |
            |tag `string text ${expression} string text`
            |//const char* p = "\xfff"; // error: hex escape sequence out of range
            |const char* p = "\xff""f"; // OK: the literal is const char[3] holding {'\xff','f','\0'}
          ; // semi-colon is the end of the declaration statement

// also, it nests:
String s2 = `|// here's how it would look in Ecstasy:
             |
             |String s = `|<%@ taglib uri="/WEB-INF/jspwiki.tld" prefix="wiki" %>
             |            |<%@ taglib uri="http://stripes.sourceforge.net/stripes.tld" prefix="stripes" %>
             |            |<stripes:useActionBean binding="/View.action" />
             |            |<wiki:Include page="ViewTemplate.jsp" />
             |            |String myString = "This is my string\n" +
             |            |        " which I want to be \n" +
             |            |        "on multiple lines.";
             |            ||s = """ this is a very
             |            |        long string if I had the
             |            |        energy to type more and more ..."""
             |            |`string text`
             |            |
             |            |`string text line 1
             |            | string text line 2`
             |            |
             |            |`string text ${expression} string text`
             |            |
             |            |tag `string text ${expression} string text`
             |            |//const char* p = "\xfff"; // error: hex escape sequence out of range
             |            |const char* p = "\xff""f"; // OK: the literal is const char[3] holding {'\xff','f','\0'}
             |          ; // semi-colon is the end of the declaration statement
; // this has to go on a new line, otherwise it's part of the raw string

// (and so on!)

String s3 = `| // also, it nests:
             | String s2 = `|// here's how it would look in Ecstasy:
             |              |
             |              |String s = `|<%@ taglib uri="/WEB-INF/jspwiki.tld" prefix="wiki" %>
             |              |            |<%@ taglib uri="http://stripes.sourceforge.net/stripes.tld" prefix="stripes" %>
            ...

// also supports the "$" concept:

String s = $|...

// also handles long long long lines well:

String s = "When she had a child, it had to be sent out to nurse. When he came home, the lad was spoilt as if he were a prince. His mother stuffed him with jam; his father let him run about barefoot, and, playing the philosopher, even said he might as well go about quite naked like the young of animals. As opposed to the maternal ideas, he had a certain virile idea of childhood on which he sought to mould his son, wishing him to be brought up hardily, like a Spartan, to give him a strong constitution. He sent him to bed without any fire, taught him to drink off large draughts of rum and to jeer at religious processions. But, peaceable by nature, the lad answered only poorly to his notions. His mother always kept him near her; she cut out cardboard for him, told him tales, entertained him with endless monologues full of melancholy gaiety and charming nonsense. In her life’s isolation she centered on the child’s head all her shattered, broken little vanities. She dreamed of high station; she already saw him, tall, handsome, clever, settled as an engineer or in the law. She taught him to read, and even, on an old piano, she had taught him two or three little songs. But to all this Monsieur Bovary, caring little for letters, said, "It was not worth while. Would they ever have the means to send him to a public school, to buy him a practice, or start him in business? Besides, with cheek a man always gets on in the world." Madame Bovary bit her lips, and the child knocked about the village.\nHe went after the labourers, drove away with clods of earth the ravens that were flying about. He ate blackberries along the hedges, minded the geese with a long switch, went haymaking during ..."

// or:

String s = `|When she had a child, it had to be sent out to nurse. When he came home, the lad was
         + `| spoilt as if he were a prince. His mother stuffed him with jam; his father let him run
         + `| about barefoot, and, playing the philosopher, even said he might as well go about quite
         + `| naked like the young of animals. As opposed to the maternal ideas, he had a certain
         + `| virile idea of childhood on which he sought to mould his son, wishing him to be brought
         + `| up hardily, like a Spartan, to give him a strong constitution. He sent him to bed
         + `| without any fire, taught him to drink off large draughts of rum and to jeer at
         + `| religious processions. But, peaceable by nature, the lad answered only poorly to his
         + `| notions. His mother always kept him near her; she cut out cardboard for him, told him
         + `| tales, entertained him with endless monologues full of melancholy gaiety and charming
         + `| nonsense. In her life’s isolation she centered on the child’s head all her shattered,
         + `| broken little vanities. She dreamed of high station; she already saw him, tall,
         + `| handsome, clever, settled as an engineer or in the law. She taught him to read, and
         + `| even, on an old piano, she had taught him two or three little songs. But to all this
         + `| Monsieur Bovary, caring little for letters, said, "It was not worth while. Would they
         + `| ever have the means to send him to a public school, to buy him a practice, or start him
         + `| in business? Besides, with cheek a man always gets on in the world." Madame Bovary bit
         + `| her lips, and the child knocked about the village.
            |He went after the labourers, drove away with clods of earth the ravens that were flying
         + `| about. He ate blackberries along the hedges, minded the geese with a long switch, went
         + `| haymaking during ...
; // this has to go on a new line, otherwise it's part of the raw string


// downsides of this approach:
// - trailing whitespace could be an issue (solution: use the file include syntax instead)
// - when used with $, there is a number of rules that have to be ordered for precedence, such as escapes



// -- IO thoughts

Path.Root + "library" +
Path.Current
Path.Parent

FileStore
    @RO Boolean readonly;
    conditional Int usedBytes();
    conditional Int capacityBytes();
    conditional Int unusedBytes();
    @RO Directory root;
    conditional Directory|File find(Path)
    Cancellable watch(Path, FileWatcher)
    Cancellable watchRecursively(Path, FileWatcher)

Node
    @RO Path path
    @RO String name
    @RO Boolean exists
    conditional Directory|File rename(String)
    Boolean create()
    Boolean delete()
    @RO Int size
    Cancellable watch(FileWatcher)

Directory
    Iterator<String> names()
    Iterator<Directory> dirs()
    Iterator<File> files()
    conditional Directory|File find(String name)
    Directory assumeDir(String name)
    File assumeFile(String name)
    conditional Directory createDir(String name)
    deleteRecursively()
    conditional File createFile(String sName)
    Cancellable watchRecursively(FileWatcher)

File
    Boolean truncate()
    created / last-updated / last-accessed
    readable / writable / deletable
    @RO Byte[] contents
    FileChannel open(Boolean read=true, Boolean write=true, )

FileWatcher
    Boolean directoryCreated(Directory)
    Boolean directoryDeleted(Directory)
    Boolean fileCreated(File)
    Boolean fileDeleted(File)
    Boolean fileModified(File)
    Boolean notificationsDiscarded()

const Path implements Sequence<Path>
    @RO String name
    @RO Path? parent
    Boolean startsWith(Path)
    Boolean endsWith(Path)
    @RO Boolean absolute
    @RO Boolean relative = !absolute
    @RO Boolean normalized
    Path normalize()
    Path resolve(Path)
    Path relativize(Path)
    Path sibling(String)
    Path sibling(Path)
    Path add(String)
    Path add(Path)


PathElement {Root, Parent, Current, Name}

--

Byte[] bytes = #40A295B440A295B440A295B440A295B440A295B440A295B440A295B4;
Byte[] bytes = #|40A295B440A295B440A295B440A295B440A295B440A295B440A295B4
                |40A295B440A295B440A295B440A295B440A295B440A295B440A295B4
                |40A295B440A295B440A295B440A295B440A295B440A295B440A295B4
                |40A295B440A295B440A295B440A295B440A295B440A295B440A295B4
                |40A295B440A295B440A295B440A295B440A295B440A295B440A295B4
                |40A295B440A295B440A295B440A295B440A295B440A295B440A295B4
                |40A295B440A295B440A295B440A295B440A295B440A295B440A295B4
                |40A295B440A295B440A295B440A295B440A295B440A295B440A295B4
                ;
Byte[] bytes = #./TestMisc.xtc;

--

// calling a conditional, with assignment
while (String s : iter.next())      // old
while (String s := iter.next())     // new

if (String s : iter.next())      // old
if (String s := iter.next())     // new

for (Int i : 0..5)
for (String s : list)
for (Iterator<String> iter = list.iterator(); String s := iter.next(); )            // now OK

String? foo() {...}
if (String s ?= foo())  // basically, this turns type "T?" into "conditional T"
    {
    // ...
    }

-- discussion with Jon

class C
    {
    void foo(String x, Int y) {...}
    }

function void(String) f = c.foo(_, 5);


Matrix matrix = new Float16[5,6];
Float16[?,?] matrix = new Float16[5,6];

assert Int x : foo();       // "conditional Int foo()" returns both a Boolean and (iff True) an Int
assert Int x := foo();

Int x := foo();

String? foo();

String s = foo()?;
s ?:=

s = (s == null ? foo() : s);  ==> s ?:= foo();

if (String name := iter.next())
    {
    ... // name is assigned here
    }
else
    {
    // name not assigned here
    }

if (String? name = foo())
    {
    if (name != Null)
        {
        ... // name is NOT NULL
        }
    }

if (String name = foo()?)
if (String name ?= foo())
    {
    ... // name is NOT NULL
    }

String? == Nullable | String
s?.foo() ==> if (s != Null) {s.foo();}

--

assert a && b;      // same as:  assert a; assert b;
assert a || b;      // same as:  if (!a) assert a; assert b;
assert !(a || b);   // same as:  assert !a && !b;  ==> assert !a; assert !b;

// old
if (i < 0 || i > MAX)
    {
    throw new IllegalArgumentException("i must be betwee 0 and " + MAX);
    }
// new
assert:arg i >= 0 && i < MAX;

assert:once foo() > 5;
// compiles as:
JMP_NFIRST end
NVOK_01 foo() -> nextvar
IS_GT nextvar 5 A_STACK
ASSERT A_STACK, construct Assertion(String), "foo() > 5, foo()={0}", 1:(nextvar)
end:

assert:bounds i >= 0 && i < size;
// compiles as:
IS_GTE i 0 A_STACK
ASSERT A_STACK, construct OutOfBounds(String), "i >= 0 && i < size, i={0}", 1:(i)
LGET size, hold_size
IS_LT i hold_size A_STACK
ASSERT A_STACK, construct OutOfBounds(String), "i >= 0 && i < size, i={0}, size={1}", 2:(i, hold_size)

assert:rnd(100) !map.values.contains(n);
// compiles as:
JMP_NSAMPL 100, end
NVOK_01 foo() -> nextvar
IS_GT nextvar 5 A_STACK
ASSERT A_STACK, construct Assertion(String), "foo() > 5, foo()={0}", 1:(nextvar)
end:

// ----- non-Ecstasy equals() problem

class Person
    {
    String first;
    String last;
    Date   dob;

    Boolean equals(Person that)
        {
        return this.first == that.first
            && this.last  == that.last
            && this.dob   == that.dob;
        }
    }

class Employee
        extends Person
    {
    String title;
    Date   hired;
    Dec    salary;

    Boolean equals(Employee that)
        {
        return this.title == that.title
            && this.hired == that.hired
            && this.dob   == that.dob
            && super.equals(that);
        }
    }

Collection<Person> c1 = new HashSet<>();
c1.add(new Person(...));
Collection<Person> c2 = new ArrayList<>();
c2.add(new Employee(...));
if (c1.equals(c2))
// or conversely:
// if (c2.equals(c1))
    {
    // ...
    }


// ----- assumptions

// 1) this and outer this's (assumption-safe) : "this", "this.outer", "this.outer.outer", etc.
// 2) constants (assumption-safe) : ClassName+"."+ConstantName
// 3) local variables : VariableName
//    a) captured read/write (Var) or annotated variable (+checked-assumptions)
//       - the act of capture is assumption-invalidating
//    b) assigned once (assumption-safe)
//    c) assigned more than once (temporally assumption-safe)
// 3) property of X : X+"."+PropertyName (+checked-assumptions)
// 4) indexed element N of X : X+"["+N+"]" (+checked-assumptions)
//    -> NOTE: NOT GOING TO SUPPORT THIS (unless "lifting" values becomes a supported feature)

Use a Map?
|----------------------------|
| String key | Assumption(s) |
|----------------------------|
|            |               |
|----------------------------|

or a Tree

Node
- Node   parent
- String name
- Assumption(s)
- children
  |--------------------|
  | String name | Node |
  |--------------------|
  |             |      |
  |--------------------|


// so:
// - every AST node could generate one or more assumptions (identity / assumption pairs)
// - every AST node could invalidate one or more assumption identities

// assumptions today are all type related:
// - formal parameter has an associated type (also, when true/when false)
// - variable can be "shadowed" (a narrowed type register)

// what invalidates assumptions?
// - for any assignable expression o, any assignment to o invalidates all assumptions for o, and
//   then adds any assumptions that are present from the right hand side of the assignment
// - for any method m() on reference o of non-immutable type T, o.m() invalidates all assumptions for o

// invalidations have to be "sticky"
// - create an Assumption for an invalided identity that says "this was invalidated"
// - without a sticky invalidation, you can't "percolate up" the fact that an invalidation occurred
// - there has to be a set of invalidations accumulated within the context, in addition to the
//   assumptions that have survived (or been registered since) those invalidations

invalidate(String id, Branch branch);
assume(String id, TypeConstant type); // or Argument (TargetInfo | ShadowRegister)

// invalidations only need to stick if there are any assumptions for the same (or dependent) identity
// - a dependent identity is one that starts with the identity+'.'
// e.g.
static boolean isDependent(String sDepId, String sId)
    {
    return sDepId.startsWith(sId) && (sDepId.length() == sId.length() || sDepId.charAt(sId.length()) == '.');
    }


// so every expression (or maybe AST node):
// - with respect to assumptions:
//   - may invalidate all assumptions for an identity
//   - may produce an assumption for an identity
// - with respect to completion
//   - may short circuit
//   - may abruptly non-complete (break, return, throw, etc.)
//     - with or without an error, e.g. the T0D0 expression should *silently* non-complete

// --

if (s != Null)
    {
    console.println($"String s is ${s.size} characters long.");
    }

x = x!=Null ? x : y;
x = x? : y;
x = x ?: y;
x ?:= y;

String? foo();
if (String s ?= foo())
    {

    }

s ?= foo();
s = foo()?;

if (s != null)
    {
    // ...
    }


interface Duck
    {
    void waddle();
    void quack();
    }

class Gosling
    {
    void waddle() {}
    void quack() {}
    }

Duck foo(Gosling james)
    {
    return james;
    }
