package org.xtclang.ecstasy;

import org.xtclang.ecstasy.reflect.Enumeration;
import org.xtclang.ecstasy.text.String;

/**
 * Native Enumeration<Ordered>.
 */
public class eOrdered extends Enumeration {
    private eOrdered() {
        super(null, $ctx().container.typeSystem.pool().typeOrdered());
    }

    public static final eOrdered $INSTANCE = new eOrdered();

    public static final String[] $names = new String[] {
        Ordered.Lesser.$INSTANCE.$name,
        Ordered.Equal.$INSTANCE.$name,
        Ordered.Greater.$INSTANCE.$name
    };

    public static final Ordered[] $values = new Ordered[] {
        Ordered.Lesser.$INSTANCE,
        Ordered.Equal.$INSTANCE,
        Ordered.Greater.$INSTANCE
    };

    @Override
    public long count$get$p() {
        return 3;
    }

    @Override
    public Ordered[] values$get() {
        return $values;
    }

    @Override
    public String[] names$get() {
        return $names;
    }
}
