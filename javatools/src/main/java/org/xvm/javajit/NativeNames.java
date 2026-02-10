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
        reservedMethodSuffix.put("collections.Collection/reify/0", "reify");
        reservedMethodSuffix.put("collections.Array.ArrayDelegate/reify/1", "reify");
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
