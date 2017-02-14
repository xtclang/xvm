mixin LazyRef<RefType>(function RefType() calculate)
        into Ref<RefType>
    {
    private function RefType() calculate;
    private Boolean assignable = false;

    conditional RefType peek()
        {
        if (assigned)
            {
            return true, get();
            }

        return false;
        }

    RefType get()
        {
        if (!assigned)
            {
            RefType value = calculate();
            try
                {
                assignable = true;
                set(value);
                }
            finally
                {
                assignable = false;
                }

            return value;
            }

        return super();
        }

    Void set(RefType value)
        {
        assert !assigned && assignable;
        super(value);
        }

    RefType calc()
        {
        return calculate();
        }
    }
