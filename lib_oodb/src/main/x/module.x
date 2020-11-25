/**
 * This module contains the Object Oriented Database (OODB) APIs.
 */
module oodb.xtclang.org
    {
    typedef (DBInvoke<<>, <Boolean>> | function Boolean()) Condition;

    /**
     * This mixin is used to mark a module as being a database module.
     */
    mixin Database
            into Module
        {
        }
    }
