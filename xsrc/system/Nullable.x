enum Nullable
    {
    Null
        {
        public readonly Type type
            {
            throw new NullPointerException();
            }

        public T as<T>()
            {
            throw new NullPointerException();
            }

        public T to<T>()
            {
            throw new NullPointerException();
Anno            }
        }
    };
