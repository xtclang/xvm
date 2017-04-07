package org.xvm.proto;

import org.xvm.proto.template.xService;
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

//        runTests(test, container);
//        runTests(test2, container);
        runTests(testService, container);
        }

    protected static void runTests(xService service, Container container)
        {
        System.out.println("\n\n##### Running tests for " + service + " #####");

        xService.ServiceHandle hService = container.startService(service, null);

        service.forEachMethod(method ->
            {
            if (method.f_sName.startsWith("test") && method.m_cArgs == 0)
                {
                container.runMethod(hService, method.f_sName, Utils.OBJECTS_NONE);
                }
            });

        try
            {
            Thread.sleep(10000);
            }
        catch (InterruptedException e) {}
        }

    }
