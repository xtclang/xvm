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