package org.xvm.javajit;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.TypeConstant;

/**
 * This class serves as a registry for pre-reserved class/method/property names.
 */
public class NativeNames {

    /**
     * Map of methods names keyed by the class/method-name/number-of-args combination.
     */
    private static final Map<String, String> reservedMethodName;
    static {
        // build locally, publish frozen: this is a process-wide lookup table
        var map = new HashMap<String, String>();

        map.put("Appender/add/1", "add");

        map.put("Iterator/next/0", "next");

        map.put("reflect.Ref/get/0", "get");
        map.put("reflect.Ref/peek/0", "peek");
        map.put("reflect.Var/set/1", "set");

        map.put("Boolean/not/0",    "not");
        map.put("Boolean/and/1",    "and");
        map.put("Boolean/or/1",     "or");
        map.put("Boolean/xor/1",    "xor");
        map.put("Boolean/toByte/0", "toByte");

        map.put("numbers.Bit/not/0", "not");
        map.put("numbers.Bit/and/1", "and");
        map.put("numbers.Bit/or/1",  "or");
        map.put("numbers.Bit/xor/1", "xor");

        map.put("collections.Array/add/1",        "add");
        map.put("collections.Array/addAll/1",     "addAll");
        map.put("collections.Array/delete/1",     "delete");
        map.put("collections.Array/insert/2",     "insert");
        map.put("collections.Array/insertAll/2",  "insertAll");
        map.put("collections.Array/reify/1",      "reify");
        map.put("collections.Array/removeAll/1",  "removeAll");
        map.put("collections.Collection/reify/0", "reify");
        map.put("collections.Hashable/hashCode/2", "hashCode");

        map.put("numbers.Number/toInt8/2",    "toInt8$FP");
        map.put("numbers.Number/toInt16/2",   "toInt16$FP");
        map.put("numbers.Number/toInt32/2",   "toInt32$FP");
        map.put("numbers.Number/toInt64/2",   "toInt64$FP");
        map.put("numbers.Number/toInt128/2",  "toInt128$FP");
        map.put("numbers.Number/toIntN/1",    "toIntN$FP");
        map.put("numbers.Number/toUInt8/2",   "toUInt8$FP");
        map.put("numbers.Number/toUInt16/2",  "toUInt16$FP");
        map.put("numbers.Number/toUInt32/2",  "toUInt32$FP");
        map.put("numbers.Number/toUInt64/2",  "toUInt64$FP");
        map.put("numbers.Number/toUInt128/2", "toUInt128$FP");
        map.put("numbers.Number/toUIntN/1",   "toUIntN$FP");
        map.put("numbers.Number/toNibble/2",  "toNibble$FP");

        map.put("numbers.IntNumber/add/1",           "add");
        map.put("numbers.IntNumber/and/1",           "and");
        map.put("numbers.IntNumber/not/0",           "not");
        map.put("numbers.IntNumber/or/1",            "or");
        map.put("numbers.IntNumber/nextValue/0",     "nextValue");
        map.put("numbers.IntNumber/prevValue/0",     "prevValue");
        map.put("numbers.IntNumber/shiftAllRight/1", "shiftAllRight");
        map.put("numbers.IntNumber/shiftLeft/1",     "shiftLeft");
        map.put("numbers.IntNumber/shiftRight/1",    "shiftRight");
        map.put("numbers.IntNumber/skip/1",          "skip");
        map.put("numbers.IntNumber/stepsTo/1",       "stepsTo");
        map.put("numbers.IntNumber/sub/1",           "sub");
        map.put("numbers.IntNumber/xor/1",           "xor");

        map.put("numbers.IntN/abs/0",       "abs");
        map.put("numbers.IntN/add/1",       "add");
        map.put("numbers.IntN/div/1",       "div");
        map.put("numbers.IntN/divrem/1",    "divrem");
        map.put("numbers.IntN/mod/1",       "mod");
        map.put("numbers.IntN/mul/1",       "mul");
        map.put("numbers.IntN/neg/0",       "neg");
        map.put("numbers.IntN/pow/1",       "pow");
        map.put("numbers.IntN/remainder/1", "remainder");
        map.put("numbers.IntN/sub/1",       "sub");
        map.put("numbers.IntN/next/0",      "next");
        map.put("numbers.IntN/nextValue/0", "nextValue");
        map.put("numbers.IntN/prev/0",      "prev");
        map.put("numbers.IntN/prevValue/0", "prevValue");
        map.put("numbers.IntN/skip/1",      "skip");
        map.put("numbers.IntN/stepsTo/1",   "stepsTo");

        map.put("numbers.UIntN/abs/0",       "abs");
        map.put("numbers.UIntN/add/1",       "add");
        map.put("numbers.UIntN/div/1",       "div");
        map.put("numbers.UIntN/divrem/1",    "divrem");
        map.put("numbers.UIntN/mod/1",       "mod");
        map.put("numbers.UIntN/mul/1",       "mul");
        map.put("numbers.UIntN/pow/1",       "pow");
        map.put("numbers.UIntN/remainder/1", "remainder");
        map.put("numbers.UIntN/sub/1",       "sub");
        map.put("numbers.UIntN/next/0",      "next");
        map.put("numbers.UIntN/nextValue/0", "nextValue");
        map.put("numbers.UIntN/prev/0",      "prev");
        map.put("numbers.UIntN/prevValue/0", "prevValue");
        map.put("numbers.UIntN/skip/1",      "skip");
        map.put("numbers.UIntN/stepsTo/1",   "stepsTo");

        reservedMethodName = Map.copyOf(map);
    }

    /**
     * @return a reserved name for the specified method or null if unknown
     */
    public static String findReservedJitName(MethodConstant methodId) {
        IdentityConstant classId   = methodId.getNamespace();
        TypeConstant     classType = classId.getType();
        String           className = classId.getPathString();
        String           key       = createKey(className, methodId);
        String           jitName   = reservedMethodName.get(key);

        if (jitName == null) {
            // there was no match for the exact type and method, try to match on super types

            ConstantPool pool = classId.getConstantPool();
            if (classType.isA(pool.typeNumber()) || classType.isA(pool.typeFPNumber()) ||
                    classType.isA(pool.ensureEcstasyTypeConstant("numbers.IntConvertible")) ||
                    classType.isA(pool.ensureEcstasyTypeConstant("numbers.FPConvertible"))) {
                // the type is a Number or IntConvertible
                key     = createKey("numbers.Number", methodId);
                jitName = reservedMethodName.get(key);

                String methodName = methodId.getName();
                if (jitName == null &&
                        (methodName.startsWith("toInt") || methodName.startsWith("toUInt") ||
                         methodName.startsWith("toDec") || methodName.startsWith("toFloat") ||
                         methodName.startsWith("toNibble"))) {
                    jitName = methodName;
                }
            }
        }

        return jitName;
    }

    private static String createKey(String className, MethodConstant methodId) {
        return className + "/" +
               methodId.getName() + "/" +
               methodId.getSignature().getParamCount();
    }

    /**
     * @return all method names that must be reserved before JIT name generation starts
     */
    public static Set<String> reservedJitNames() {
        Set<String> names = new HashSet<>(reservedMethodName.values());
        addConversionNames(names, "toInt",   "", "8", "16", "32", "64", "128", "N", "Literal");
        addConversionNames(names, "toUInt",  "", "8", "16", "32", "64", "128", "N");
        addConversionNames(names, "toNibble", "");
        addConversionNames(names, "toDec",   "", "32", "64", "128", "N");
        addConversionNames(names, "toFloat", "8e4", "8e5", "16", "32", "64", "128", "N");
        return names;
    }

    private static void addConversionNames(Set<String> names, String prefix, String... suffixes) {
        for (String suffix : suffixes) {
            names.add(prefix + suffix);
        }
    }
}
