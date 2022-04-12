/**
 * A test for DBProcessor functionality.
 *
 *  To prepare the test, follow steps 1-5 outlined in TestSimpleWeb.
 *
 *  To run the test:
 *      curl -i -w '\n' -X GET http://[domain]:8080/run
 *
 *  To check the counters:
 *      curl -i -w '\n' -X GET http://[domain]:8080/dump
 *
 * See [CounterDB] database module.
 */
@web.WebModule
module CounterTest
    {
    package web import web.xtclang.org;

    package CounterDB import CounterDB;

    import CounterDB.CounterSchema;
    import CounterDB.oodb.Connection;
    import CounterDB.oodb.DBMap;

    @web.WebService
    service Test
        {
        @Inject Random        rnd;
        @Inject CounterSchema schema;

        @web.Get("/run")
        String run()
            {
            for (Int i : 1..3)
                {
                // pick a letter to schedule
                String name = ('A' + rnd.uint(26).toUInt32()).toString();
                schema.logger.add($"cranking up schedule \"{name}\"...");
                schema.cranker.schedule(name);
                }
            return dump();
            }

        @web.Get("/dump")
        String dump()
            {
            StringBuffer buf = new StringBuffer();
            for ((String name, Int count) : schema.counters)
                {
                buf.append($"{name}={count}, ");
                }
            return buf.toString().quoted();
            }
        }
    }