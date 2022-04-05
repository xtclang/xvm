/**
 * The module for all platform APIs.
 */
module platform.xtclang.org
    {
    /**
     * A Log as a service. REVIEW CP: where does it belong?
     */
    service ErrorLog
            delegates Log(errors)
        {
        String[] errors = new String[];

        void reportAll(function void (String) report)
            {
            for (String error : errors)
                {
                report(error);
                }
            }
        }
    }