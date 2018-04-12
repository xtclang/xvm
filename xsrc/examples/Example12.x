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
    case 1:
    case 2:     -1;
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
    case 0:     x=0;
    case 1:
    case 2:     x=-1;
    case 3:     {
                    x = 99;
                    for (Int i : 5..10)
                        {
                        x += i;
                        }
                    }
    case 4:     x=12;
    default:    x=17;
    }

// for first boolean expression match:
Int x = switch ()
    {
    case (a > 3):   0;
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