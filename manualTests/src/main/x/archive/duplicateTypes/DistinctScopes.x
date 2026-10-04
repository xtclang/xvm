// Positive control: identical simple names in different enclosing scopes are legal.
module DistinctScopes {
    class First {
        static class Nested {}
    }

    class Second {
        static class Nested {}
    }

    First.Nested makeFirst() = new First.Nested();
    Second.Nested makeSecond() = new Second.Nested();
}
