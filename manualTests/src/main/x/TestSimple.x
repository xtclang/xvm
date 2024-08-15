module TestSimple {
    @Inject Console console;
    @Inject Clock clock;
    @Inject Timer timer;

    void run() {
        timer.start();
        doMaintenance();
    }

    protected void doMaintenance() {
        if (count++ % 2 == 0) {
            console.print($"Maintenance with clock... {count}");
            clock.schedule(Duration.ofSeconds(2), &doMaintenance, count <= 5);
        } else {
            console.print($"Maintenance with timer... {count}");
            timer.schedule(Duration.ofSeconds(2), &doMaintenance, count <= 5);
        }
    }

    @Transient Int count;
}

