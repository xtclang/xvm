package org.xvm.proto;

import org.xvm.proto.template.xTest;
import org.xvm.proto.template.xTest2;

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

        xTest test = new xTest(container.f_types);
        test.adapter = container.f_constantPoolAdapter;
        container.f_types.addTemplate(test);

        xTest2 test2 = new xTest2(container.f_types);
        test2.adapter = container.f_constantPoolAdapter;
        container.f_types.addTemplate(test2);

        ServiceContext context = container.createContext(test);

        runTests(test, context);
        runTests(test2, context);
        }

    protected static void runTests(TypeCompositionTemplate template, ServiceContext context)
        {
        System.out.println("### Running tests for " + template + " ###");
        template.forEachFunction(function ->
            {
            if (function.f_sName.startsWith("test") && function.m_cArgs == 0)
                {
                try
                    {
                    System.out.println("### Running " + function + " ###");

                    ObjectHandle hException = context.createFrame(null, null,
                            function, new ObjectHandle[function.m_cVars]).execute();
                    if (hException != null)
                        {
                        System.out.println("Function " + function.f_sName + " threw unhandled " + hException);
                        }
                    }
                catch (Exception e)
                    {
                    System.out.println("Failed to execute " + function);
                    e.printStackTrace(System.out);
                    }
                }
            });
        }

    }
