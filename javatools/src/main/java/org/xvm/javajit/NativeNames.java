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
     * Map of methods names keyed by the class/method-name/number-of-args combination.
     */
    static final Map<String, String> reservedMethodName = new HashMap<>();
    static {
        reservedMethodName.put("Appender/add/1", "add");

        reservedMethodName.put("Boolean/not/0", "not");
        reservedMethodName.put("Boolean/toByte/0", "toByte");

        reservedMethodName.put("collections.Array/add/1",                 "add");
        reservedMethodName.put("collections.Array/addAll/1",              "addAll");
        reservedMethodName.put("collections.Array/delete/1",              "delete");
        reservedMethodName.put("collections.Array/insert/2",              "insert");
        reservedMethodName.put("collections.Array/insertAll/2",           "insertAll");
        reservedMethodName.put("collections.Array/reify/1",               "reify");
        reservedMethodName.put("collections.Array/removeAll/1",           "removeAll");
        reservedMethodName.put("collections.Array.ArrayDelegate/reify/1", "reify");
        reservedMethodName.put("collections.Collection/reify/0",          "reify");

        reservedMethodName.put("numbers.Number/toInt8/1",    "toInt8");
        reservedMethodName.put("numbers.Number/toInt16/1",   "toInt16");
        reservedMethodName.put("numbers.Number/toInt32/1",   "toInt32");
        reservedMethodName.put("numbers.Number/toInt64/1",   "toInt64");
        reservedMethodName.put("numbers.Number/toInt128/1",  "toInt128");
        reservedMethodName.put("numbers.Number/toIntN/0",    "toIntN");
        reservedMethodName.put("numbers.Number/toUInt8/1",   "toUInt8");
        reservedMethodName.put("numbers.Number/toUInt16/1",  "toUInt16");
        reservedMethodName.put("numbers.Number/toUInt32/1",  "toUInt32");
        reservedMethodName.put("numbers.Number/toUInt64/1",  "toUInt64");
        reservedMethodName.put("numbers.Number/toUInt128/1", "toUInt128");
        reservedMethodName.put("numbers.Number/toUIntN/0",   "toUIntN");

        reservedMethodName.put("numbers.Number/toDec32/0",   "toDec32");
        reservedMethodName.put("numbers.Number/toDec64/0",   "toDec64");
        reservedMethodName.put("numbers.Number/toDec128/0",  "toDec128");
        reservedMethodName.put("numbers.Number/toFloat16/0", "toFloat16");
        reservedMethodName.put("numbers.Number/toFloat32/0", "toFloat32");
        reservedMethodName.put("numbers.Number/toFloat64/0", "toFloat64");

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
        return reservedMethodName.get(key);
    }
}
