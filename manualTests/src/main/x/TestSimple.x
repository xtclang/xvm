module TestSimple.test.org
    {
    @Inject Console console;

    void run( )
        {
        Parent p = new Parent();
        Parent.Child c = p.new Child();

        assert c.test();
        }

    class Parent
        {
        function Boolean() isAutocommit = () -> False;

        Boolean checkAutoCommit()
            {
            return True;
            }

        class Child
            {
            construct()
                {
                isAutocommit = checkAutoCommit;
                }

            Boolean test()
                {
                return isAutocommit();
                }
            }
        }
    }