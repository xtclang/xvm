/**
 * A mixin into a freezable class that indicates to the runtime that instances of that class should
 * be frozen automatically when necessary.
 *
 * This is intended to be used to make simple [Freezable] classes whose purpose is to carry
 * information across a service boundary, saving the developer from having to explicitly `freeze()`
 * or `makeImmutable()` on every call. As such, the objects are assumed, by default, to be designed
 * to freeze "in place"; the mixin constructor allows that assumption to be overridden:
 *
 *     @AutoFreezable(inPlace=False) class Message {...}
 */
mixin AutoFreezable(Boolean inPlace=True)
        into Freezable
    {
    @Override
    immutable AutoFreezable freeze(Boolean? inPlace=Null)
        {
        return super(inPlace ?: this.inPlace);
        }
    }