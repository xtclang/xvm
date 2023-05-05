module TestSimple
    {
    @Inject Console console;

    package test import TestInjection;

    void run()
        {
        console.print("test");
        }
    }