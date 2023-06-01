import io.TextPosition;

import reflect.Annotation;
import reflect.InvalidType;

import src.Lexer.Token;


/**
 * Represents a child of a NamedTypeExpression, for example:
 *
 *     ecstasy.collections.HashMap<String?, IntLiteral>.Entry
 */
const ChildTypeExpression(TypeExpression          parent,
                          AnnotationExpression[]? annotations,
                          Token[]                 names,
                          TypeExpression[]?       params,
                          TextPosition            end)
        extends TypeExpression {
    @Override
    TextPosition start.get() {
        return parent.start;
    }

    @Override
    conditional Type resolveType(TypeSystem typeSystem, Boolean hideExceptions = False) {
        if (Type  parentType  := parent.resolveType(typeSystem, hideExceptions),
            Class parentClass := parentType.fromClass()) {
            // resolve names
            Type type = parentClass;
            assert !names.empty;
            for (Token name : names) {
                if (!(type := type.childTypes.get(name.valueText))) {
                    return False;
                }
            }

            // resolve type parameters
            if (params != Null) {
                Type[] paramTypes = new Type[];
                for (Int i : 0 ..< params.size) {
                    if (Type paramType := params[i].resolveType(typeSystem, hideExceptions)) {
                        paramTypes.add(paramType);
                    } else {
                        return False;
                    }
                }

                try {
                    type = type.parameterize(paramTypes);
                } catch (InvalidType e) {
                    if (hideExceptions) {
                        return False;
                    }
                    throw e;
                }
            }

            // resolve annotations
            for (AnnotationExpression expr : annotations?) {
                if (Annotation annotation := expr.resolveAnnotation(typeSystem, hideExceptions)) {
                    try {
                        type = type.annotate(annotation);
                    } catch (InvalidType e) {
                        if (hideExceptions) {
                            return False;
                        }
                        throw e;
                    }
                } else {
                    return False;
                }
            }

            return True, type;
        }

        return False;
    }

    @Override
    String toString() {
        StringBuffer buf = new StringBuffer();

        parent.appendTo(buf);
        buf.add('.');

        Loop: for (AnnotationExpression anno : annotations?) {
            anno.appendTo(buf);
            buf.add(' ');
        }

        Loop: for (Token token : names) {
            if (!Loop.first) {
                buf.add('.');
            }
            token.appendTo(buf);
        }

        if (params != Null) {
            buf.add('<');
            Loop: for (TypeExpression param : params) {
                if (!Loop.first) {
                    buf.add(',').add(' ');
                }
                param.appendTo(buf);
            }
            buf.add('>');
        }

        return buf.toString();
    }
}
