module TestSimple.xqiz.it
    {
    import Ecstasy.collections.HashMap;

    @Inject Ecstasy.io.Console console;

    void run()
        {
        }

    private class Test
        {
        private enum FakeMark {NotPeeking}

        private Object undo = FakeMark.NotPeeking;
        }
    }
