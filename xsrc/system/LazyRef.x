mixin LazyRef<RefType>(function RefType() calculate)
        into Ref<RefType>
    {
    private function RefType() calculate;

    conditional RefType peek()
        {
        if (assigned)
            {
            return (true, get());
            }

        return false;
        }

    RefType get()
        {
        if (!assigned)
            {
            RefType value = calculate();
            set(value);
            return value;
            }

        return super();
        }

    Void set(RefType value)
        {
        assert !assigned;
        super(value);
        }
    }
