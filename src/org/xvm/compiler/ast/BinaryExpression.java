package org.xvm.compiler.ast;


import org.xvm.compiler.Token;
import org.xvm.util.Handy;

import static org.xvm.util.Handy.byteArrayToHexString;


/**
 * A literal expression specifies a literal value.
 *
 * @author cp 2017.03.28
 */
public class BinaryExpression
        extends Expression
    {
    public BinaryExpression(byte[] bytes, long startPos, long endPos)
        {
        this.bytes    = bytes;
        this.startPos = startPos;
        this.endPos   = endPos;
        }

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

    public final byte[] bytes;
    public final long startPos;
    public final long endPos;
    }
