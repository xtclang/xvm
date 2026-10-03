package org.xvm.javajit.builders;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;

import org.xvm.asm.constants.PropertyInfo;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.JitMethodDesc;

import static java.lang.constant.ConstantDescs.CD_MethodHandle;
import static java.lang.constant.ConstantDescs.CD_long;
import static java.lang.constant.ConstantDescs.CD_void;
import static java.lang.constant.ConstantDescs.INIT_NAME;

import static org.xvm.javajit.Builder.CD_ArrayObj;
import static org.xvm.javajit.Builder.CD_Ctx;
import static org.xvm.javajit.Builder.CD_Enumeration;
import static org.xvm.javajit.Builder.CD_Int64;
import static org.xvm.javajit.Builder.CD_Object;
import static org.xvm.javajit.Builder.CD_String;
import static org.xvm.javajit.Builder.CD_TypeConstant;
import static org.xvm.javajit.Builder.Instance;
import static org.xvm.javajit.Builder.MD_StringOf;
import static org.xvm.javajit.Builder.MD_xvmVoid;
import static org.xvm.javajit.Builder.OPT;
import static org.xvm.javajit.Builder.md;

import static org.xvm.javajit.builders.CommonBuilder.CONST_PROP;

/**
 * A "mixin" interface to generate bytecodes for operations on Ecstasy Enumeration types.
 */
public interface EnumerationBuilderSupport {
    /**
     * @return {@code true} if this builder is for the base Enumeration type, {@code false}
     *         otherwise
     */
    static boolean isBaseEnumeration(CommonBuilder builder) {
        return isBaseEnumeration(builder.thisType);
    }

    /**
     * @return {@code true} if this builder is for the base Enumeration type, {@code false}
     * otherwise
     */
    static boolean isBaseEnumeration(TypeConstant type) {
        return !type.getParamType(0).isEnum();
    }

    /**
     * Get an array of {@link ClassStructure} instances representing the enum values for a
     * specified enum {@link ClassStructure}.
     * <p>An empty array is returned if the {@link ClassDesc} is not an enum class.
     *
     * @param clz  the {@link ClassStructure} of the enum to get the values for
     *
     * @return the enum values for a specified enum type ordered by ordinal.
     */
    static ClassStructure[] getEnumValues(ClassStructure clz) {
        return clz.children()
                .stream()
                .filter(c -> c.getFormat() == Component.Format.ENUMVALUE)
                .map(ClassStructure.class::cast)
                .toArray(ClassStructure[]::new);
    }

    /**
     * The name of the Ecstasy Enum value count property.
     */
    String PROP_COUNT = "count";

    /**
     * The name of the Ecstasy Enum value names property.
     */
    String PROP_NAMES = "names";

    /**
     * The name of the Ecstasy Enum value byNames property.
     */
    String PROP_BY_NAME = "byName";

    /**
     * The name of the Ecstasy Enum values property.
     */
    String PROP_VALUES = "values";

    /**
     * The name of the static field holding the enum names.
     */
    String FIELD_NAMES = "$names";

    /**
     * The name of the static field holding the enum values.
     */
    String FIELD_VALUES = "$values";
}
