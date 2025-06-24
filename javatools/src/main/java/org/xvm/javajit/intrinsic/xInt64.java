package org.xvm.javajit.intrinsic;


/**
 * Native Int64 wrapper.
 */
public class xInt64 extends xConst {
    public xInt64(long value) {
        super(-1);
        $value = value;
    }
    public final long $value;

    @Override
    public xType $type() {
        return $xvm().ecstasyPool.typeInt64().ensureXType(null);
    }
}
