@oodb.Database
module welcomeDB.examples.org {
    package oodb import oodb.xtclang.org;

    interface WelcomeSchema
            extends oodb.RootSchema {

        @RO oodb.DBCounter count;
    }
}
