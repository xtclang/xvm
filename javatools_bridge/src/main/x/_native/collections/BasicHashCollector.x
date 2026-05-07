import ecstasy.collections.HashCollector;

/**
 * A basic implementation of the HashCollector service.
 */
service BasicHashCollector
        implements HashCollector {

    @Override HashCollector add(UInt8 value) = TODO("native");

    @Override BasicHashCollector reset() = TODO("native");

    @Override Int compute() = TODO("native");
}
