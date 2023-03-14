module TestSimple
    {
    @Inject Console console;

    class Box<A>(A object);

    <A> void f(A a, Int n = 0)
        {
        console.print(A);
        if (n < 10)
            {
            f(new Box<A>(a), n+1);
            }
        }

    void run()
        {
        f("hello");
        }
    }