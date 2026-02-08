package org.xvm.util;


import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;


/**
 * A List that uses identity ({@code ==}) instead of {@link Object#equals} for element comparison.
 * <p>
 * Previously, this class extended {@link ArrayList} directly. That inheritance pulled in the
 * {@link java.io.Serializable} and {@link Cloneable} contracts from {@code ArrayList}, neither of
 * which is needed or used here, and caused {@code [serial]} lint warnings about a missing
 * {@code serialVersionUID}. By switching to composition over an internal {@code ArrayList} delegate,
 * with {@link AbstractList} as the base class, this class provides the same mutable random-access
 * {@link List} semantics without inheriting any unneeded contracts.
 */
public class IdentityArrayList<E>
        extends AbstractList<E> {

    private final ArrayList<E> delegate = new ArrayList<>();

    // ----- AbstractList required overrides --------------------------------------------------------

    @Override
    public E get(int index) {
        return delegate.get(index);
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public void add(int index, E element) {
        delegate.add(index, element);
    }

    @Override
    public E set(int index, E element) {
        return delegate.set(index, element);
    }

    @Override
    public E remove(int index) {
        return delegate.remove(index);
    }

    // ----- identity-based overrides --------------------------------------------------------------

    /**
     * Add the specified element if it is not already present in the list.
     *
     * @param e  the element to conditionally add
     *
     * @return true if the element was not previously in the list, but now it has been added
     */
    public boolean addIfAbsent(E e) {
        if (contains(e)) {
            return false;
        }

        add(e);
        return true;
    }

    @Override
    public int indexOf(Object o) {
        for (int i = 0, c = size(); i < c; ++i) {
            if (o == get(i)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int lastIndexOf(Object o) {
        for (int i = size() - 1; i >= 0; --i) {
            if (o == get(i)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean remove(Object o) {
        for (int i = 0, c = size(); i < c; ++i) {
            if (o == get(i)) {
                remove(i);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }

        if (!(o instanceof List<?> that)) {
            return false;
        }

        if (this.size() != that.size()) {
            return false;
        }

        var iterThis = this.listIterator();
        var iterThat = that.listIterator();
        while (iterThis.hasNext() && iterThat.hasNext()) {
            if (iterThis.next() != iterThat.next()) {
                return false;
            }
        }

        return true;
    }
}
