module TestSimple {
    @Inject Console console;
    @Inject Timer timer;

    void run() {
        doMaintenance();
    }

    protected void doMaintenance() {
        // that used to "stack overflow" and leak a Fiber object per cycle
        timer.schedule(Duration.Millisec, &doMaintenance);
    }
}

