@oodb.Database
module welcomeDB
    {
    package oodb import oodb.xtclang.org;

    interface WelcomeSchema
            extends oodb.RootSchema
        {
        @RO oodb.DBCounter count;
        }
    }