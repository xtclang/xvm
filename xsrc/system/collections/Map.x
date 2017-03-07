interface Map<KeyType, ValueType>
	{
	@ro Int size;

	conditional ValueType get(KeyType key);

    Map!<KeyType, ValueType> put(KeyType key, ValueType value);

    // ...
	}
