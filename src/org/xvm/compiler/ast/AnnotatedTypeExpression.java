package org.xvm.compiler.ast;


import org.xvm.util.ListMap;

import java.util.Map;


/**
 * An annotated type expression is a type expression preceded with an annotation.
 *
 * @author cp 2017.03.31
 */
public class AnnotatedTypeExpression
        extends TypeExpression
    {
    // ----- constructors --------------------------------------------------------------------------

    public AnnotatedTypeExpression(Annotation annotation, TypeExpression type)
        {
        this.annotation = annotation;
        this.type       = type;
        }


    // ----- accessors -----------------------------------------------------------------------------


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(annotation)
          .append(' ')
          .append(type);

        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }

    @Override
    public Map<String, Object> getDumpChildren()
        {
        ListMap<String, Object> map = new ListMap();
        map.put("annotation", annotation);
        map.put("type", type);
        return map;
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Annotation annotation;
    protected TypeExpression type;
    }
