module welcomeDB
        incorporates oodb.Database
    {
    package oodb import oodb.xtclang.org;

    interface WelcomeSchema
            extends oodb.RootSchema
        {
        @RO oodb.DBCounter count;
        }
    }