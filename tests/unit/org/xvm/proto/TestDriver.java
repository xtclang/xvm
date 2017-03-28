package org.xvm.proto;

import org.xvm.proto.template.xTest;

/**
 * TODO:
 *
 * @author gg 2017.03.27
 */
public class TestDriver
    {
    public static void main(String[] asArg)
        {
        Container container = new Container();

        xTest test = new xTest(container.f_types, container.f_constantPoolAdapter);

        container.f_types.addTemplate(test);

        ServiceContext context = container.createContext(test);

        test.forEachFunction(function ->
            {
            if (function.f_sName.startsWith("test") && function.m_cArgs == 0)
                {
                ObjectHandle[] ahReturn = new ObjectHandle[function.m_cReturns];

                try
                    {
                    ObjectHandle hException = context.createFrame(null, null,
                            function, new ObjectHandle[function.m_cVars], ahReturn).execute();
                    if (hException != null)
                        {
                        System.err.println("Function " + function.f_sName + " threw unhandled " + hException);
                        }
                    }
                catch (Exception e)
                    {
                    System.err.println("Failed to execute " + function);
                    e.printStackTrace();
                    }
                }
            });
        }

    }
