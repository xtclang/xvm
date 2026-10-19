import ecstasy.collections.HashCollector;

/**
 * A basic implementation of the HashCollector service.
 */
service BasicHashCollector
        implements HashCollector {

    @Override HashCollector add(UInt8 value) = addByte(value);

    @Override HashCollector reset() = TODO("native");

    @Override Int compute() = TODO("native");

    /**
     * This method is here so that it will have a consistent name in the JIT and on the JIT Bridge
     * implementation of this type. If we used the add method it would not be consistent due to
     * HashCollector having so many version of the add method.
     */
    HashCollector addByte(UInt8 value) = TODO("native");
}
