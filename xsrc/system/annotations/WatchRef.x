/**
 * TODO
 */
mixin WatchRef<RefType>
        into Ref<RefType>
    {
    construct()
        {
        }

    construct(function Void () | function Void (RefType) notify)
        {
        notifies.add(normalize(notify));
        }

    /**
     * The set of notify functions to call when the referent is set.
     */
    private Set<function Void (RefType)> notifies = new ListSet<>();

    @Override
    Void set(RefType value)
        {
        super(value);
        for (function Void (RefType) notify : notifies)
            {
            notify(value);
            }
        }

    Void addWatch(function Void () | function Void (RefType) notify)
        {
        notifies.add(normalize(notify));
        }

    Void removeWatch(function Void () | function Void (RefType) notify)
        {
        notifies.remove(normalize(notify));
        }

    static function Void (RefType) normalize(function Void () | function Void (RefType) notify)
        {
        return (notify instanceof function Void ()) ? v -> notify() : notify;
        }
    }
