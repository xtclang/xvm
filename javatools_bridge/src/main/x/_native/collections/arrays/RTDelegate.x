import ecstasy.Freezable;

import ecstasy.collections.Array.ArrayDelegate;
import ecstasy.collections.Array.Mutability;

/**
 * The native ArrayDelegate<Element> class.
 */
class RTDelegate<Element>
        implements ArrayDelegate<Element> {
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
    RTDelegate! reify(Mutability? mutability = Null);

    /**
     * Native constructor helper; fill the array from the Iterable source.
     */
    private static <Element> Array<Element> fillFromIterable(
            Array<Element> array, Iterable<Element> iterable, Mutability mutability) {

        loop: for (Element element : iterable) {
            array[loop.count] = mutability == Constant ? Freezable.frozen(element) : element;
        }
        return array.reify(mutability);
    }
}