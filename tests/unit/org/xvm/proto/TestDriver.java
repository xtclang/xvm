package org.xvm.proto;

import org.xvm.proto.template.xTest;
import org.xvm.proto.template.xTest2;
import org.xvm.proto.template.xTestService;

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
        container.f_types.addTemplate(test);

        xTest2 test2 = new xTest2(container.f_types);
        container.f_types.addTemplate(test2);

        xTestService testService = new xTestService(container.f_types);
        container.f_types.addTemplate(testService);

        ServiceContext context = container.createContext();

        runTests(test, context);
        runTests(test2, context);
        runTests(testService, context);
        }

    protected static void runTests(TypeCompositionTemplate template, ServiceContext context)
        {
        System.out.println("\n\n##### Running tests for " + template + " #####");
        template.forEachFunction(function ->
            {
            if (function.f_sName.startsWith("test") && function.m_cArgs == 0)
                {
                try
                    {
                    System.out.println("\n### Calling " + function + " ###");

                    ObjectHandle hException = context.createFrame(null, function, null,
                            new ObjectHandle[function.m_cVars]).execute();
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
