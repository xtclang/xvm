module TestSimple
    {
    @Inject Console console;

    void run( )
        {
        new Parent<String>.Child();
        }

    void foo(Parent<String>.Child c)
        {
        }

    class Parent<T>
            implements IParent<T>
        {
        static class Child
                implements IChild
            {
            }
        }

    interface IParent<T>
        {
        static interface IChild
            {
            }
        }
    }
