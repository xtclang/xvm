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
    static final Map<String, String> reservedMethodName = new HashMap<>();
    static {
        reservedMethodName.put("Appender/add/1", "add");

        reservedMethodName.put("Iterator/next/0", "next");

        reservedMethodName.put("Freezable/freeze/1", "freeze");

        reservedMethodName.put("Object/equals/2", "equals");

        reservedMethodName.put("reflect.Ref/get/0", "get");
        reservedMethodName.put("reflect.Ref/peek/0", "peek");
        reservedMethodName.put("reflect.Var/set/1", "set");

        reservedMethodName.put("Boolean/not/0",    "not");
        reservedMethodName.put("Boolean/and/1",    "and");
        reservedMethodName.put("Boolean/or/1",     "or");
        reservedMethodName.put("Boolean/xor/1",    "xor");
        reservedMethodName.put("Boolean/toByte/0", "toByte");

        reservedMethodName.put("numbers.Bit/not/0", "not");
        reservedMethodName.put("numbers.Bit/and/1", "and");
        reservedMethodName.put("numbers.Bit/or/1",  "or");
        reservedMethodName.put("numbers.Bit/xor/1", "xor");

        reservedMethodName.put("numbers.Nibble.values/=/0", "values$init");

        reservedMethodName.put("collections.Array/add/1",        "add");
        reservedMethodName.put("collections.Array/addAll/1",     "addAll");
        reservedMethodName.put("collections.Array/delete/1",     "delete");
        reservedMethodName.put("collections.Array/elementAt/1",  "elementAt");
        reservedMethodName.put("collections.Array/getElement/1", "getElement");
        reservedMethodName.put("collections.Array/insert/2",     "insert");
        reservedMethodName.put("collections.Array/insertAll/2",  "insertAll");
        reservedMethodName.put("collections.Array/reify/1",      "reify");
        reservedMethodName.put("collections.Array/removeAll/1",  "removeAll");
        reservedMethodName.put("collections.Array/slice/1",      "slice");
        reservedMethodName.put("collections.Array.FreezableArray/freeze/1", "freeze");
        reservedMethodName.put("collections.Collection/reify/0", "reify");
        reservedMethodName.put("collections.Hashable/hashCode/2", "hashCode");
        reservedMethodName.put("collections.Tuple/add/2",         "add");
        reservedMethodName.put("collections.Tuple/addAll/1",      "addAll");
        reservedMethodName.put("collections.Tuple/elementAt/1",   "elementAt");
        reservedMethodName.put("collections.Tuple/equals/2",      "equals");
        reservedMethodName.put("collections.Tuple/freeze/1",      "freeze");
        reservedMethodName.put("collections.Tuple/getElement/1",  "getElement");
        reservedMethodName.put("collections.Tuple/remove/1",      "remove");
        reservedMethodName.put("collections.Tuple/removeAll/1",   "removeAll");
        reservedMethodName.put("collections.Tuple/replace/2",     "replace");
        reservedMethodName.put("collections.Tuple/slice/1",       "slice");

        reservedMethodName.put("collections.UniformIndexed/getElement/1", "getElement");
        reservedMethodName.put("Range/getElement/1",       "getElement");
        reservedMethodName.put("text.String/getElement/1", "getElement");

        reservedMethodName.put("numbers.Number/toInt8/2",    "toInt8$FP");
        reservedMethodName.put("numbers.Number/toInt16/2",   "toInt16$FP");
        reservedMethodName.put("numbers.Number/toInt32/2",   "toInt32$FP");
        reservedMethodName.put("numbers.Number/toInt64/2",   "toInt64$FP");
        reservedMethodName.put("numbers.Number/toInt128/2",  "toInt128$FP");
        reservedMethodName.put("numbers.Number/toIntN/1",    "toIntN$FP");
        reservedMethodName.put("numbers.Number/toUInt8/2",   "toUInt8$FP");
        reservedMethodName.put("numbers.Number/toUInt16/2",  "toUInt16$FP");
        reservedMethodName.put("numbers.Number/toUInt32/2",  "toUInt32$FP");
        reservedMethodName.put("numbers.Number/toUInt64/2",  "toUInt64$FP");
        reservedMethodName.put("numbers.Number/toUInt128/2", "toUInt128$FP");
        reservedMethodName.put("numbers.Number/toUIntN/1",   "toUIntN$FP");
        reservedMethodName.put("numbers.Number/toNibble/2",  "toNibble$FP");

        reservedMethodName.put("numbers.IntNumber/add/1",           "add");
        reservedMethodName.put("numbers.IntNumber/and/1",           "and");
        reservedMethodName.put("numbers.IntNumber/not/0",           "not");
        reservedMethodName.put("numbers.IntNumber/or/1",            "or");
        reservedMethodName.put("numbers.IntNumber/nextValue/0",     "nextValue");
        reservedMethodName.put("numbers.IntNumber/prevValue/0",     "prevValue");
        reservedMethodName.put("numbers.IntNumber/shiftAllRight/1", "shiftAllRight");
        reservedMethodName.put("numbers.IntNumber/shiftLeft/1",     "shiftLeft");
        reservedMethodName.put("numbers.IntNumber/shiftRight/1",    "shiftRight");
        reservedMethodName.put("numbers.IntNumber/skip/1",          "skip");
        reservedMethodName.put("numbers.IntNumber/stepsTo/1",       "stepsTo");
        reservedMethodName.put("numbers.IntNumber/sub/1",           "sub");
        reservedMethodName.put("numbers.IntNumber/xor/1",           "xor");

        reservedMethodName.put("numbers.IntN/abs/0",       "abs");
        reservedMethodName.put("numbers.IntN/add/1",       "add");
        reservedMethodName.put("numbers.IntN/div/1",       "div");
        reservedMethodName.put("numbers.IntN/divrem/1",    "divrem");
        reservedMethodName.put("numbers.IntN/mod/1",       "mod");
        reservedMethodName.put("numbers.IntN/mul/1",       "mul");
        reservedMethodName.put("numbers.IntN/neg/0",       "neg");
        reservedMethodName.put("numbers.IntN/pow/1",       "pow");
        reservedMethodName.put("numbers.IntN/remainder/1", "remainder");
        reservedMethodName.put("numbers.IntN/sub/1",       "sub");
        reservedMethodName.put("numbers.IntN/next/0",      "next");
        reservedMethodName.put("numbers.IntN/nextValue/0", "nextValue");
        reservedMethodName.put("numbers.IntN/prev/0",      "prev");
        reservedMethodName.put("numbers.IntN/prevValue/0", "prevValue");
        reservedMethodName.put("numbers.IntN/skip/1",      "skip");
        reservedMethodName.put("numbers.IntN/stepsTo/1",   "stepsTo");

        reservedMethodName.put("numbers.UIntN/abs/0",       "abs");
        reservedMethodName.put("numbers.UIntN/add/1",       "add");
        reservedMethodName.put("numbers.UIntN/div/1",       "div");
        reservedMethodName.put("numbers.UIntN/divrem/1",    "divrem");
        reservedMethodName.put("numbers.UIntN/mod/1",       "mod");
        reservedMethodName.put("numbers.UIntN/mul/1",       "mul");
        reservedMethodName.put("numbers.UIntN/pow/1",       "pow");
        reservedMethodName.put("numbers.UIntN/remainder/1", "remainder");
        reservedMethodName.put("numbers.UIntN/sub/1",       "sub");
        reservedMethodName.put("numbers.UIntN/next/0",      "next");
        reservedMethodName.put("numbers.UIntN/nextValue/0", "nextValue");
        reservedMethodName.put("numbers.UIntN/prev/0",      "prev");
        reservedMethodName.put("numbers.UIntN/prevValue/0", "prevValue");
        reservedMethodName.put("numbers.UIntN/skip/1",      "skip");
        reservedMethodName.put("numbers.UIntN/stepsTo/1",   "stepsTo");
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
            if (    classType.isA(pool.typeNumber()) ||
                    classType.isA(pool.typeFPNumber()) ||
                    classType.isA(pool.typeIntConvertible()) ||
                    classType.isA(pool.typeFPConvertible())) {
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
