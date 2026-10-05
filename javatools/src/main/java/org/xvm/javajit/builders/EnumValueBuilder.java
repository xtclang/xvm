package org.xvm.javajit.builders;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.CodeBuilder;

import java.lang.constant.ClassDesc;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;

import org.xvm.javajit.TypeSystem;
import org.xvm.javajit.TypeSystem.Artifact;

/**
 * The builder for Enum value types.
 *
 * <p>It overrides the {@link CommonBuilder} to do the following:
 *   - create a synthetic "$name" field to hold the enum value name
 *   - supply the "ordinal" and "name" properties
 */
public class EnumValueBuilder
        extends CommonBuilder
        implements EnumValueBuilderSupport {
    /**
     * Create an {@link EnumValueBuilder}.
     *
     * @param typeSystem the {@link TypeSystem}
     * @param art        the {@link Artifact} to build
     */
    public EnumValueBuilder(TypeSystem typeSystem, Artifact art) {
        super(typeSystem, art);
        ClassStructure enumStruct = (ClassStructure) classStruct.getParent();
        assert enumStruct.getFormat() == Component.Format.ENUM;
        ClassStructure[] enumValues = EnumerationBuilderSupport.getEnumValues(enumStruct);
        ordinal = EnumValueBuilderSupport.calculateOrdinal(classStruct, enumValues);
        nextCD  = EnumValueBuilderSupport.calculateNextCD(this, enumValues, ordinal);
        prevCD  = EnumValueBuilderSupport.calculatePrevCD(this, enumValues, ordinal);
    }

    /**
     * The ordinal of this enum value.
     */
    private final int ordinal;

    /**
     * The {@link ClassDesc} of the previous enum value, or {@code null} if this is the first
     * value for the parent enum.
     */
    private final ClassDesc prevCD;

    /**
     * The {@link ClassDesc} of the next enum value, or {@code null} if this is the last value
     * for the parent enum.
     */
    private final ClassDesc nextCD;

    @Override
    public int getOrdinal() {
        return ordinal;
    }

    @Override
    public ClassDesc getNextCD() {
        return nextCD;
    }

    @Override
    public ClassDesc getPrevCD() {
        return prevCD;
    }

    @Override
    public ClassDesc getSuperCD() {
        return getEnumValueSuperCD(this);
    }

    @Override
    public boolean assembleClass(ClassBuilder classBuilder) {
        assembleEnumValueClass(this, classBuilder);
        return super.assembleClass(classBuilder);
    }

    @Override
    protected void appendCLInit(CodeBuilder code, int ctxSlot) {
        appendEnumValueCLInit(this, code);
    }

    @Override
    protected void assembleMethods(ClassBuilder classBuilder) {
        super.assembleMethods(classBuilder);
        assembleEnumValueMethods(this, classBuilder);
    }

    @Override
    protected void assembleProperties(ClassBuilder classBuilder) {
        assembleEnumValueProperties(this, classBuilder);
        super.assembleProperties(classBuilder);
    }
}
