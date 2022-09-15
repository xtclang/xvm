package org.xvm.asm.constants;

import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.ConstantPool;

/**
 * The Ecstasy cast operator ".as()" is used generally for two purposes. First usage pattern is a
 * type narrowing, analogous to Java's cast operation, overriding the compile-time type validation
 * with an explicit run-time check. It's used most commonly in reflection-based or deserialization
 * logic.
 * <br/>
 * The second usage pattern is type widening, which is related to the way that Ecstasy deals with
 * equality and comparison. It allows a developer to direct the run-time object comparison using
 * a specific compile-time type, regardless of the actual run-time object types.
 * <br/>
 * While the first pattern is completely covered by the IntersectionTypeConstant, the second requires the
 * knowledge that the combined type was produced explicitly by the ".as()" operation.
 * <br/>
 * Note: this type is transient in nature; used only by the compiler and cannot be stored in a
 * ConstantPool.
 */
public class CastTypeConstant
        extends IntersectionTypeConstant
    {
    public CastTypeConstant(ConstantPool pool, TypeConstant constType1, TypeConstant constType2)
        {
        super(pool, constType2.combine(pool, constType1), constType2);

        m_constType1Orig = constType1;
        }

    @Override
    public TypeConstant resolveTypedefs()
        {
        return getUnderlyingType();
        }

    @Override
    protected Relation calculateRelationToLeft(TypeConstant typeLeft)
        {
        return getUnderlyingType().calculateRelationToLeft(typeLeft);
        }

    @Override
    protected Relation calculateRelationToRight(TypeConstant typeRight)
        {
        return getUnderlyingType().calculateRelationToRight(typeRight);
        }

    @Override
    public Format getFormat()
        {
        return Format.CastType;
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        throw new IllegalStateException();
        }

    @Override
    public String getValueString()
        {
        return m_constType1Orig.getValueString() + ".as(" + m_constType2.getValueString() + ")";
        }

    private final TypeConstant m_constType1Orig;
    }