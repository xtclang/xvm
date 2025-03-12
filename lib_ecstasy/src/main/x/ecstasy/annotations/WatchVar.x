/**
 * The WatchVar (`@Watch`) annotation is used to create event notifications whenever the value of
 * the reference changes.
 *
 * Usage example:
 *   @Watch(n -> {console.print($"new value={n}");}) Int n = 0;
 */
annotation WatchVar<Referent>(function void (Referent) notify)
        into Var<Referent> {
    /**
     * The notification function.
     */
    private function void (Referent) notify;

    @Override
    void set(Referent value) {
        super(value);
        notify(value);
    }
}