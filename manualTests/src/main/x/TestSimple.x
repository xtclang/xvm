module TestSimple
        delegates Beepable(ConsoleBeeper.DEFAULT)
    {
    @Inject Console console;

    void run()
        {
        console.println("Running ...");
        beep();
        }

    interface Beepable
        {
        void beep();
        }

    const ConsoleBeeper
            implements Beepable
        {
        static ConsoleBeeper DEFAULT = new ConsoleBeeper();

        @Override
        void beep()
            {
            console.println("beep");
            }
        }
    }
