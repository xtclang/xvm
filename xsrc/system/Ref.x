interface Ref<T>
    {
    @ro Type<T> RefType
        {
        Type<T> get()
            {
            return T;
            }
        };

    T get();
    void set(T value);
    }
