package org.xvm.util;


import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;


/**
 * An ArrayList using identity for equality comparison.
 */
public class IdentityArrayList<E>
        extends ArrayList<E>
    {
    /**
     * Add the specified element if it is not already present in the list.
     *
     * @param e  the element to conditionally add
     *
     * @return true if the element was not previously in the list, but now it has been added
     */
    public boolean addIfAbsent(E e)
        {
        if (contains(e))
            {
            return false;
            }

        add(e);
        return true;
        }

    @Override
    public int indexOf(Object o)
        {
        for (int i = 0, c = size(); i < c; ++i)
            {
            if (o == get(i))
                {
                return i;
                }
            }
        return -1;
        }

    @Override
    public int lastIndexOf(Object o)
        {
        for (int i = size() - 1; i >= 0; --i)
            {
            if (o == get(i))
                {
                return i;
                }
            }
        return -1;
        }

    @Override
    public boolean remove(Object o)
        {
        for (int i = 0, c = size(); i < c; ++i)
            {
            if (o == get(i))
                {
                remove(i);
                return true;
                }
            }
        return false;
        }

    @Override
    public boolean equals(Object o)
        {
        if (o == this)
            {
            return true;
            }

        if (!(o instanceof List that))
            {
            return false;
            }

        if (this.size() != that.size())
            {
            return false;
            }

        for (ListIterator iterThis = this.listIterator(), iterThat = that.listIterator();
                iterThis.hasNext() && iterThat.hasNext(); )
            {
            if (iterThis.next() != iterThat.next())
                {
                return false;
                }
            }

        return true;
        }
    }