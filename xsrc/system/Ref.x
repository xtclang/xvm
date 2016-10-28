interface Ref<T>
    {
    @ro Type<T> RefType
        {
        Type<T> get()
            {
            return T;
            }
        };

    @ro T get();

    void set(T value);
    }
