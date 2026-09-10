package org.xtclang.ecstasy.temporal;

import org.xtclang.ecstasy.nConst;

import org.xtclang.ecstasy.text.String;
import org.xvm.javajit.Ctx;

/**
 * Native wrapper for the single-slot XVM primitive representation of {@code temporal.Date}.
 */
public class Date
        extends nConst {

    private Date(int epochDay) {
        super(null);
        this.epochDay = epochDay;
    }

    /**
     * The primitive slot backing the Ecstasy {@code epochDay} property.
     */
    public int epochDay;

    /**
     * Box the epochDay a primitive Date value.
     */
    public static Date $box(int epochDay) {
        return new Date(epochDay);
    }

    /**
     * The primitive implementation of the Ecstasy {@code epochDay} property getter.
     */
    public int epochDay$get(Ctx ctx) {
        return epochDay;
    }

    /**
     * The static primitive implementation of the Ecstasy {@code epochDay} property getter.
     */
    public static int epochDay$get$p(int thi$, Ctx ctx) {
        return thi$;
    }

    /**
     * The static primitive implementation of toString().
     */
    public static String toString$p(int thi$, Ctx ctx) {
        return $box(thi$).toString(ctx);
    }

    /**
     * The native implementation of the estimateStringLength() method.
     */
    public long estimateStringLength$p(Ctx ctx) {
        return 10;
    }

    /**
     * Compare two signed epochDay values.
     */
    public static int $compare(int epochDay1,int epochDay2) {
        return Integer.compare(epochDay1, epochDay2);
    }

    /**
     * Test two primitive Date values for equality.
     */
    public static boolean $equals(int epochDay1,int epochDay2) {
        return epochDay1 == epochDay2;
    }
}
