/**
 * The WatchVar (`@Watch`) mixin is used to create event notifications whenever the value of
 * the reference changes.
 *
 * Usage example:
 *   @Watch(n -> {console.print($"new value={n}");}) Int n = 0;
 */
mixin WatchVar<Referent>(function void (Referent) notify)
        into Var<Referent>
    {
    /**
     * The notification function.
     */
    private function void (Referent) notify;

    @Override
    void set(Referent value)
        {
        super(value);
        notify(value);
        }
    }