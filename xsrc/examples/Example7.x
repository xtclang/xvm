// conditions

module MyApp
    {
    package Spring
        import springframework.vmware.org require 2 prefer 3.1;

    const Employee
            if (Spring.present) { implements Spring.Serializer }
        {
        if (Spring.version(3))
            {
            public void write(Spring.OutputStream out)
                {
                // ...
                }
            }
        }
    }


file
  module MyApp
    package Spring = springframework.vmware.org
  module springframework.vmware.org
    class Serializer
      mmethod write
        method public/(Void)/(OutputStream)
    class OutputStream


// --- futures & pass throughs

service MyService
    {
    Int foo()
        {
        Int n = 0;
        for (...)
            {
            // lots of processing
            n *= ...
            }
        return n;
        }
    }

MyService myService = new MyService();
@future Int i = foo5(); // this one works already
@future Int i = foo1(); // how to make this work? TODO "VAR" has to be special & not unbox
bar(foo1());

Int foo1()
    {
    return foo2();
    }

Int foo2()
    {
    return foo3();
    }

Int foo3()
    {
    // compiles as:
    //  VAR Int              ; #1
    //  INVOKE01 foo4, 1
    //  RETURN1 1
    return foo4();
    }

Int foo4()
    {
    // compiles as:
    //  VAR Int              ; #3
    //  INVOKE01 foo5, 3
    //  RETURN1 3
    return foo5();
    }

Int bar()
    {
    @future Int i = foo4();
    return i;
    }

Int foo5()
    {
    return myService.foo();
    }

// this COULD work in theory, because there's no code here that looks inside the box, or ...
Int bar(Int i)
    {
    return i;
    }
Int bar(@future Int i)
    {
    return i;
    }

bar(foo1());    // VAR Int              ; #1
                // INVOKE01 foo1, 1
                // INVOKE10 bar, 1
&foo1().get();  // VAR Int              ; #1
                // INVOKE01 foo1, 1
                // REF 1                ; #2
                // INVOKE00 get, 2

&foo1().as(FutureRef<Int>).andThen(...);
                // VAR Int              ; #1
                // INVOKE01 foo1, 1


Array<@future Int> a = ...

// ----

Int[] ai = new Int[10](_->foo());

Svc svc = new MyService();
FutureRef<Int>[] afi  = new FutureRef<Int>[10](_->svc.inc());       // COMPILER ERROR
FutureRef<Int>[] afi  = new FutureRef<Int>[10](_->&(svc.inc()));    // no disagreement

SameIfaceAsSvcButNotNecessarilyAService iface = svc;
FutureRef<Int>[] afi  = new FutureRef<Int>[10](_->&(iface.inc()));  // COMPILER ERROR?

@future Int j = svc.inc()
FutureRef<Int> rj = &j;

af1[0] = j;
af1[0] = rj;

//

@Soft Image    img = render();
SoftRef<Image> ref = &img;

// we liked this one
SoftRef<Image> ref2 = (&render()).as(SoftRef<Image>);

//

    Void foo()
        {
        @future Int i = svc.foo();
        (&i).whenComplete(()->...)
        i = 4;      // either completes or throws a runtime error

        FutureRef<Int> fi = &foo(); // same as: FutureRef<Int> fi = (&foo()).as(FutureRef<Int>);

        SoftRef<Image> ref2 = (&render()).as(SoftRef<Image>);

        @future Int[] afi1 = new @future Int[10];    // left does not match right
        (@future Int)[] afi2 = new (@future Int)[10];
        @future Int[] afi2 = foo();
        FutureRef<Int[]> raf = &afi2;
        }

// question is: "how do we move between the '@future' and the FutureRef?"
// question is: "how do you get an '@future' from a FutureRef?"

@future Int i = svc.foo();
FutureRef<Int> fi = &i;                 // this is OK
@future Int i2 = fi;    // is this OK?

@future Int i3 = fi.createContinuation(i -> i);


// what should this do?

@future Int i = svc.foo();

@future Int j = i;

