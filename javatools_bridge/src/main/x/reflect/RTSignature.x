import ecstasy.reflect.MethodTemplate;
import ecstasy.reflect.Parameter;
import ecstasy.reflect.Return;
import ecstasy.reflect.Signature;

/**
 * The native Signature implementation.
 */
const RTSignature<ParamTypes extends Tuple<ParamTypes>, ReturnTypes extends Tuple<ReturnTypes>>
        implements Signature<ParamTypes, ReturnTypes>
    {
    @Override @RO String      name                         .get() { TODO("native"); }
    @Override @RO Parameter[] params                       .get() { TODO("native"); }
    @Override @RO Return[]    returns                      .get() { TODO("native"); }
    @Override @RO Boolean     conditionalResult            .get() { TODO("native"); }
    @Override @RO Boolean     futureResult                 .get() { TODO("native"); }

    @Override conditional MethodTemplate hasTemplate()            { TODO("native"); }

    // these methods are currently implemented as natural code:
    //   conditional Parameter findParam(String name)
    //   conditional Return findReturn(String name)
    }
