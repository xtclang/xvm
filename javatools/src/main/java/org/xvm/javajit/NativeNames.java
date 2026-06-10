package org.xvm.javajit;

import java.util.HashMap;
import java.util.Map;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.TypeConstant;

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

        reservedMethodSuffix.put("collections.Array/add/1",                 "add");
        reservedMethodSuffix.put("collections.Array/addAll/1",              "addAll");
        reservedMethodSuffix.put("collections.Array/delete/1",              "delete");
        reservedMethodSuffix.put("collections.Array/insert/2",              "insert");
        reservedMethodSuffix.put("collections.Array/insertAll/2",           "insertAll");
        reservedMethodSuffix.put("collections.Array/reify/1",               "reify");
        reservedMethodSuffix.put("collections.Array/removeAll/1",           "removeAll");
        reservedMethodSuffix.put("collections.Array.ArrayDelegate/reify/1", "reify");
        reservedMethodSuffix.put("collections.Collection/reify/0",          "reify");

        reservedMethodSuffix.put("numbers.Number/toInt8/1",    "toInt8");
        reservedMethodSuffix.put("numbers.Number/toInt16/1",   "toInt16");
        reservedMethodSuffix.put("numbers.Number/toInt32/1",   "toInt32");
        reservedMethodSuffix.put("numbers.Number/toInt64/1",   "toInt64");
        reservedMethodSuffix.put("numbers.Number/toInt128/1",  "toInt128");
        reservedMethodSuffix.put("numbers.Number/toIntN/0",    "toIntN");
        reservedMethodSuffix.put("numbers.Number/toUInt8/1",   "toUInt8");
        reservedMethodSuffix.put("numbers.Number/toUInt16/1",  "toUInt16");
        reservedMethodSuffix.put("numbers.Number/toUInt32/1",  "toUInt32");
        reservedMethodSuffix.put("numbers.Number/toUInt64/1",  "toUInt64");
        reservedMethodSuffix.put("numbers.Number/toUInt128/1", "toUInt128");
        reservedMethodSuffix.put("numbers.Number/toUIntN/0",   "toUIntN");

        reservedMethodSuffix.put("numbers.Number/toDec32/0",   "toDec32");
        reservedMethodSuffix.put("numbers.Number/toDec64/0",   "toDec64");
        reservedMethodSuffix.put("numbers.Number/toDec128/0",  "toDec128");
        reservedMethodSuffix.put("numbers.Number/toFloat16/0", "toFloat16");
        reservedMethodSuffix.put("numbers.Number/toFloat32/0", "toFloat32");
        reservedMethodSuffix.put("numbers.Number/toFloat64/0", "toFloat64");

        reservedMethodSuffix.put("numbers.Number/toInt8/2",    "toInt8$FP");
        reservedMethodSuffix.put("numbers.Number/toInt16/2",   "toInt16$FP");
        reservedMethodSuffix.put("numbers.Number/toInt32/2",   "toInt32$FP");
        reservedMethodSuffix.put("numbers.Number/toInt64/2",   "toInt64$FP");
        reservedMethodSuffix.put("numbers.Number/toInt128/2",  "toInt128$FP");
        reservedMethodSuffix.put("numbers.Number/toIntN/1",    "toIntN$FP");
        reservedMethodSuffix.put("numbers.Number/toUInt8/2",   "toUInt8$FP");
        reservedMethodSuffix.put("numbers.Number/toUInt16/2",  "toUInt16$FP");
        reservedMethodSuffix.put("numbers.Number/toUInt32/2",  "toUInt32$FP");
        reservedMethodSuffix.put("numbers.Number/toUInt64/2",  "toUInt64$FP");
        reservedMethodSuffix.put("numbers.Number/toUInt128/2", "toUInt128$FP");
        reservedMethodSuffix.put("numbers.Number/toUIntN/1",   "toUIntN$FP");
    }

    /**
     * @return a reserved name for the specified method or null if unknown
     */
    public static String findReservedJitName(MethodConstant methodId) {
        IdentityConstant classId   = methodId.getNamespace();
        TypeConstant     classType = classId.getType();
        String           className;

        ConstantPool pool = classId.getConstantPool();
        if (classType.isA(pool.typeNumber()) ||
                classType.isA(pool.ensureEcstasyTypeConstant("numbers.FPNumber")) ||
                classType.isA(pool.ensureEcstasyTypeConstant("numbers.FPConvertible"))) {
            className = "numbers.Number";
        } else {
            className = classId.getPathString();
        }

        String key = className + "/" +
                     methodId.getName() + "/" +
                     methodId.getSignature().getParamCount();
        return reservedMethodSuffix.get(key);
    }
}
