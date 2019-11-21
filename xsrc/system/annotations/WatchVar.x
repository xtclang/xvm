/**
 * The WatchVar (`@Watch`) mixin is used to create event notifications whenever the value of
 * the reference changes.
 *
 * Usage example:
 *   @Watch(n -> console.print("new value=" + n)) Int n = 0;
 */
mixin WatchVar<RefType>(function void (RefType) notify)
        into Var<RefType>
    {
    /**
     * The notification function.
     */
    private function void (RefType) notify;

    @Override
    void set(RefType value)
        {
        super(value);
        notify(value);
        }
    }