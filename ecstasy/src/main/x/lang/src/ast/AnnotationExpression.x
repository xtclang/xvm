import io.TextPosition;

import reflect.Annotation;
import reflect.Argument;
import reflect.InvalidType;


/**
 * Represents an annotation, including its arguments if any.
 */
const AnnotationExpression(TypeExpression name,
                           Expression[]?  args,
                           TextPosition   start,
                           TextPosition   end)
        extends Expression
    {
    /**
     * Assemble the type that results from the application of an annotation to an underlying type.
     *
     * @param typeSystem      the `TypeSystem` to use to resolve type and class names
     * @param hideExceptions  pass True to catch type exceptions and return them as `False` instead
     *
     * @return True iff the annotation could be successfully resolved
     * @return (conditional) the annotation
     *
     * @throws InvalidType  if a type exception occurs and `hideExceptions` is not specified
     */
    conditional Annotation resolveAnnotation(TypeSystem typeSystem, Boolean hideExceptions = False)
        {
        Class mixinClass;
        if (Type mixinType  := name.resolveType(typeSystem, hideExceptions),
                 mixinClass := mixinType.fromClass())
            {
            }
        else
            {
            return False;
            }

        // build a reflect.Argument for each annotation arg
        Argument[] values = [];
        if (args != Null && !args.empty)
            {
            values = new Argument[];
            Loop: for (Expression expr : args)
                {
                if (immutable Const value := expr.hasConstantValue())
                    {
                    // REVIEW does this need to specify the Referent type?
                    values[Loop.count] = new Argument(value);
                    }
                else
                    {
                    return False;
                    }
                }
            }

        try
            {
            // create the annotation
            return True, new Annotation(mixinClass, values);
            }
        catch (InvalidType e)
            {
            if (hideExceptions)
                {
                return False;
                }

            throw e;
            }
        }

    @Override
    String toString()
        {
        Expression[]? args = this.args;
        return switch (args?.size)
            {
            case  0: $"@{name}()";
            case  1: $"@{name}({args[0]})";
            default:
                {
                StringBuffer buf = new StringBuffer();
                buf.add('@');
                name.appendTo(buf);
                buf.add('(');
                for (Expression arg : args)
                    {
                    buf.append(arg.toString())
                       .add(',').add(' ');
                    }
                return buf.truncate(-2).add(')').toString();
                };
            };

        return $"@{name}";
        }
    }
