package org.xvm.javajit;

import java.util.HashMap;
import java.util.Map;

import org.xvm.asm.constants.MethodConstant;

/**
 * This class serves as a registry for pre-reserved class/method/property names.
 */
public class NativeNames {

    /**
     * Map of methods suffixes keyed by the class/method-name/number-of-args combination.
     */
    static final Map<String, String> reservedMethodSuffix = new HashMap<>();
    static {
        reservedMethodSuffix.put("Appender/add/1", "add");

        reservedMethodSuffix.put("Boolean/not/0", "not");
        reservedMethodSuffix.put("Boolean/toByte/0", "toByte");

        reservedMethodSuffix.put("collections.Array.ArrayDelegate/reify/1", "reify");
        reservedMethodSuffix.put("collections.Collection/reify/0", "reify");

        reservedMethodSuffix.put("numbers.FPNumber/toInt8/2",   "toInt8");
        reservedMethodSuffix.put("numbers.FPNumber/toInt16/2",  "toInt16");
        reservedMethodSuffix.put("numbers.FPNumber/toInt32/2",  "toInt32");
        reservedMethodSuffix.put("numbers.FPNumber/toInt64/2",  "toInt64");
        reservedMethodSuffix.put("numbers.FPNumber/toInt128/2", "toInt128");
        reservedMethodSuffix.put("numbers.FPNumber/toIntN/1",   "toIntN");
        reservedMethodSuffix.put("numbers.FPNumber/toUInt8/2",  "toUInt8");
        reservedMethodSuffix.put("numbers.FPNumber/toUInt16/2", "toUInt16");
        reservedMethodSuffix.put("numbers.FPNumber/toUInt32/2", "toUInt32");
        reservedMethodSuffix.put("numbers.FPNumber/toUInt64/2", "toUInt64");
        reservedMethodSuffix.put("numbers.FPNumber/toUInt128/2","toUInt128");
        reservedMethodSuffix.put("numbers.FPNumber/toUIntN/1",  "toUIntN");
    }

    /**
     * @return a reserved name for the specified method or null if unknown
     */
    public static String findReservedJitName(MethodConstant methodId) {
        String key = methodId.getNamespace().getPathString() + "/" +
                     methodId.getName() + "/" +
                     methodId.getSignature().getParamCount();
        return reservedMethodSuffix.get(key);
    }
}
