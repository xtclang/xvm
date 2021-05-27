import ecstasy.collections.Array.ArrayDelegate;
import ecstasy.collections.Array.Mutability;

/**
 * The native ArrayDelegate<Object> class.
 */
class RTDelegate<Element>
        implements ArrayDelegate<Element>
    {
    Element getElement(Int index);

    void setElement(Int index, Element value);

    @Override
    @RO Mutability mutability;

    @Override
    @RO Int capacity;

    @Override
    @RO Int size;

    @Override
    Var<Element> elementAt(Int index);

    @Override
    RTDelegate insert(Int index, Element value);

    @Override
    RTDelegate delete(Int index);

    @Override
    RTDelegate reify(Mutability? mutability = Null);
    }
