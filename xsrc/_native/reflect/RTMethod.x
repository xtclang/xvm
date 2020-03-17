import ecstasy.reflect.Access;

/**
 * The native Method implementation.
 */
const RTMethod<Target, ParamTypes extends Tuple<ParamTypes>, ReturnTypes extends Tuple<ReturnTypes>>
        extends RTSignature<ParamTypes, ReturnTypes>
        implements Method<Target, ParamTypes, ReturnTypes>
    {
    @Override @RO Access access                                           .get() { TODO("native"); }

    @Override conditional String[] formalParamNames(Int i)                       { TODO("native"); }
    @Override conditional String[] formalReturnNames(Int i)                      { TODO("native"); }

    @Override Function<ParamTypes, ReturnTypes> bindTarget(Target target)        { TODO("native"); }
    @Override ReturnTypes invoke(Target target, ParamTypes args)                 { TODO("native"); }

    // these methods are currently implemented as natural code:
    //   conditional Parameter findParam(String name)
    //   conditional Return findReturn(String name)
    //   Boolean consumesFormalType(String typeName)
    //   Boolean producesFormalType(String typeName)
    //   Boolean isSubstitutableFor(Method!<> that)
    }
