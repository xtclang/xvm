/**
 * An annotation on a [Freezable] class that indicates to the runtime that instances of that class
 * should be frozen automatically when necessary in order to be [Passable].
 *
 * This is intended to be used to make simple [Freezable] classes whose purpose is to carry
 * information across a service boundary, saving the developer from having to explicitly `freeze()`
 * these objects (or otherwise `makeImmutable()`) before passing them to (or returning them from) a
 * service invocation.
 *
 * It is expected that these objects will be designed to freeze "in place", which avoids having to
 * repeatedly freeze-by-copying the same object if it crosses more than one service boundary; it is
 * possible to override this default by specifying the `inPlace` default value:
 *
 *     @AutoFreezable(inPlace=False) class Message {...}
 */
annotation AutoFreezable(Boolean inPlace = True)
        into Freezable {
    @Override
    immutable AutoFreezable freeze(Boolean? inPlace = Null) {
        return super(inPlace ?: this.inPlace);
    }
}