package org.xvm.runtime.template._native.collections;

import org.xvm.asm.ClassStructure;

import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.numbers.xInt64;

import org.xvm.util.ByteHashCollector;

/**
 * The injectable "HashCollector" that computes basic hash codes.
 */
public class xBasicHashCollector
        extends ClassTemplate {

    public static xBasicHashCollector INSTANCE;

    private static final String[] TYPE_HASH_COLLECTOR = { "collections.HashCollector" };

    public xBasicHashCollector(Container container, ClassStructure structure, boolean fInstance) {
        super(container, structure);

        if (fInstance) {
            INSTANCE = this;
        }
    }

    @Override
    public void initNative() {
        markNativeMethod("add"    , new String[]{"numbers.UInt8"}, TYPE_HASH_COLLECTOR);
        markNativeMethod("reset"  , null, TYPE_HASH_COLLECTOR);
        markNativeMethod("compute", null, INT);

        invalidateTypeInfo();
    }

    @Override
    public TypeConstant getCanonicalType() {
        return pool().ensureEcstasyTypeConstant("collections.HashCollector");
    }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn) {

        HashCollectorHandle hCollector = (HashCollectorHandle) hTarget;
        switch (method.getName()) {
            case "reset": {
                hCollector.collector.reset();
                return frame.assignValue(iReturn, hTarget);
            }
            case "compute": {
                JavaLong hHash = xInt64.makeHandle(hCollector.collector.compute());
                return frame.assignValue(iReturn, hHash);
            }
        }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
    }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle hArg, int iReturn) {
        HashCollectorHandle hCollector = (HashCollectorHandle) hTarget;
        switch (method.getName()) {
            case "add": {
                long n = ((JavaLong) hArg).getValue();
                hCollector.collector.addByte((byte) (n & 0xFFL));
                return frame.assignValue(iReturn, hTarget);
            }
        }
        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
    }

    /**
     * Injection support method.
     */
    public ObjectHandle ensureCollector(Frame frame, ObjectHandle hOpts) {
        TypeComposition clz = getCanonicalClass();
        return new HashCollectorHandle(clz);
    }

    // ----- inner class: HashCollectorHandle ------------------------------------------------------

    /**
     * A service handle wrapping a {@link ByteHashCollector}.
     */
    public static class HashCollectorHandle
            extends ObjectHandle {

        protected final ByteHashCollector collector;

        public HashCollectorHandle(TypeComposition clazz) {
            super(clazz);
            // TODO make the seed configurable?
            collector = new ByteHashCollector.Simple(ByteHashCollector.HashCodeSeed);
        }
    }
}
