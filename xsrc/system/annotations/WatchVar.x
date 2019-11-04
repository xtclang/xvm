/**
 * The WatchVar (`@Watch`) mixin is used to create event notifications whenever the value of
 * the reference changes.
 *
 *   @Watch(n -> console.print("new value=" + n)) Int n = 0;
 */
mixin WatchVar<Referent>
        into Var<Referent>
    {
    /**
     * Construct a watchable reference.
     */
    construct()
        {
        }

    /**
     * Construct a watchable reference that invokes the specified notification when the value of the
     * reference changes.
     */
    construct(function void () | function void (Referent) notify)
        {
        notifies.add(normalize(notify));
        }

    /**
     * The set of notify functions to call when the referent is set.
     */
    private Set<function void (Referent)> notifies = new collections.ListSet();

    @Override
    void set(Referent value)
        {
        super(value);
        for (function void (Referent) notify : notifies)
            {
            notify(value);
            }
        }

    /**
     * Add the specified notification function to the list of notifications that this WatchVar
     * is configured to deliver.
     */
    void addWatch(function void () | function void (Referent) notify)
        {
        notifies.add(normalize(notify));
        }

    /**
     * Remove the specified notification function from the list of notifications that this WatchVar
     * is configured to deliver.
     */
    void removeWatch(function void () | function void (Referent) notify)
        {
        notifies.remove(normalize(notify));
        }

    /**
     * The `normalize` method is used to turn the "either" of a no-parameter function and a
     * one-parameter function, into the "always" of a one-parameter function.
     */
    static function void (Referent) normalize(function void () | function void (Referent) notify)
        {
        return notify.is(function void (Referent)) ? notify : v -> notify();
        }
    }
