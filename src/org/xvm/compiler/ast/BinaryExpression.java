package org.xvm.compiler.ast;


import java.util.Map;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;

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
    public TypeConstant getImplicitType(Context ctx)
        {
        return pool().typeBinary();
        }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        ConstantPool pool     = pool();
        Constant     constVal = pool.ensureByteStringConstant(bytes);
        return finishValidation(typeRequired, pool.typeBinary(), TypeFit.Fit, constVal, errs);
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
