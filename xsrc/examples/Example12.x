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
