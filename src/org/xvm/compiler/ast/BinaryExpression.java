package org.xvm.compiler.ast;


import java.util.Map;

import org.xvm.asm.Constant;
import org.xvm.asm.ErrorListener;

import org.xvm.asm.constants.ImmutableTypeConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.ast.Statement.Context;

import org.xvm.util.ListMap;

import static org.xvm.util.Handy.byteArrayToHexDump;
import static org.xvm.util.Handy.byteArrayToHexString;


/**
 * A binary expression specifies a literal binary value. As in, bytes.
 */
public class BinaryExpression
        extends Expression
    {
    // ----- constructors --------------------------------------------------------------------------

    public BinaryExpression(byte[] bytes, long ofStart, long ofEnd, long lStartPos, long lEndPos)
        {
        this.bytes     = bytes;
        this.startPos  = ofStart;
        this.endPos    = ofEnd;
        this.lStartPos = lStartPos;
        this.lEndPos   = lEndPos;
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public long getStartPosition()
        {
        return lStartPos;
        }

    @Override
    public long getEndPosition()
        {
        return lEndPos;
        }


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    protected boolean validate(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        // a literal is validated by the lexer/parser, and there is nothing left to validate at this
        // point
        return true;
        }

    @Override
    public TypeConstant getImplicitType()
        {
        return pool().typeBinary();
        }

    @Override
    public boolean isAssignableTo(TypeConstant typeThat)
        {
        if (typeThat instanceof ImmutableTypeConstant)
            {
            // a binary literal is a "const" objects, so immutable is OK
            return isAssignableTo(typeThat.getUnderlyingType());
            }

        switch (typeThat.getEcstasyClassName())
            {
            case "Object":
            case "Const":
            case "Orderable":
            case "collections.Hashable":
                return true;

            case "collections.Array":
            case "collections.List":
            case "collections.Collection":
            case "collections.Sequence":
            case "Iterable":
                return !typeThat.isParamsSpecified() || (typeThat.isParamsSpecified(1)
                        && typeThat.getParamTypes().get(0).isA(pool().typeByte()));

            default:
                return super.isAssignableTo(typeThat);
            }
        }

    @Override
    public Constant toConstant()
        {
        return pool().ensureByteStringConstant(bytes);
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();
        sb.append("Binary:{\"");

        int cb = bytes.length;
        int ofEnd = Math.min(15, cb);
        sb.append(byteArrayToHexString(bytes, 0, ofEnd));

        if (ofEnd < cb)
            {
            sb.append(" (length=")
              .append(cb)
              .append(") ...");
            }

        sb.append("\"}");
        return sb.toString();
        }

    @Override
    public Map<String, Object> getDumpChildren()
        {
        ListMap<String, Object> map = new ListMap();
        map.put("bytes", byteArrayToHexDump(bytes, 64));
        return map;
        }


    // ----- fields --------------------------------------------------------------------------------

    protected byte[] bytes;
    protected long   startPos;
    protected long   endPos;
    protected long   lStartPos;
    protected long   lEndPos;
    }
