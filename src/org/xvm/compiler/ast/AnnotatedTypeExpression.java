package org.xvm.compiler.ast;


/**
 * An annotated type expression is a type expression preceded with an annotation.
 *
 * @author cp 2017.03.31
 */
public class AnnotatedTypeExpression
        extends TypeExpression
    {
    public AnnotatedTypeExpression(Annotation annotation, TypeExpression type)
        {
        this.annotation = annotation;
        this.type       = type;
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(annotation)
          .append(' ')
          .append(type);

        return sb.toString();
        }

    public final Annotation annotation;
    public final TypeExpression type;
    }
