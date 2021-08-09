import ecstasy.reflect.Annotation;
import ecstasy.reflect.MethodTemplate;
import ecstasy.reflect.MultiMethodTemplate;
import ecstasy.reflect.ParameterTemplate;
import ecstasy.reflect.ParameterTemplate.Category;
import ecstasy.reflect.TypeTemplate;


/**
 * The native reflected MethodTemplate implementation.
 */
class RTMethodTemplate
        extends RTComponentTemplate
        implements MethodTemplate
    {
    @Override
    MultiMethodTemplate parent.get() { TODO("native"); }

    @Override
    Annotation[] annotations.get()   { TODO("native"); }

    @Override
    ParameterTemplate[] parameters.get()
        {
        return new ParameterTemplate[parameterCount](index ->
            {
            (String? name, TypeTemplate type, Boolean formal, Boolean hasDefault, Const? defaultValue) =
                    getParameter(index);
            Category category = formal
                    ? TypeParameter
                    : hasDefault
                            ? DefaultParameter
                            : RegularParameter;
            return new RTParameterTemplate(name, index, type, defaultValue, category);
            });
        }

    @Override
    ParameterTemplate[] returns.get()
        {
        return new ParameterTemplate[returnCount](index ->
            {
            (String? name, TypeTemplate type, Boolean cond) = getReturn(index);
            Category category = cond
                    ? ConditionalReturn
                    : RegularReturn;
            return new RTParameterTemplate(name, index, type, Null, category);
            });
        }

    private Int parameterCount.get() { TODO("native"); }
    private Int returnCount.get()    { TODO("native"); }

    private (String? name, TypeTemplate type, Boolean formal, Boolean hasDefault, Const? defaultValue)
        getParameter(Int index) { TODO("native"); }

    private (String? name, TypeTemplate type, Boolean cond)
        getReturn(Int index) { TODO("native"); }
    }
