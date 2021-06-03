module TestSimple.test.org
    {
    import ecstasy.Duplicable;

    @Inject Console console;

    void run( )
        {
        }

    class Base
        {
        construct()
            {
            }
        finally
            {
            }

        assert()
            {
            }

        void foo()
            {
            }

        void bar()
            {
            }

        private void zoo()
            {
            }
        }

    class Test
            extends Base
            implements Duplicable
        {
        private construct()
            {
            }
        finally
            {
            }

        assert()
            {
            }

        construct(Test that)
            {
            }

        @Override
        private void foo()
            {
            }

        @Override
        protected void bar()
            {
            }

        private void zoo()
            {
            }
        }
    }