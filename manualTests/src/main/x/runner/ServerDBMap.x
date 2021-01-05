    class Server%propertyTypeName%
            extends imdb.ServerDBMap<%keyType%, %valueType%>
            incorporates %propertyType%
        {
        construct()
            {
            construct imdb.ServerDBMap(this.Server%appSchema%, "%propertyName%");
            }

        @Override
        Tuple dbInvoke(String | Function fn, Tuple args = Tuple:(), (Duration|DateTime)? when = Null)
            {
            TODO
            }
        }
