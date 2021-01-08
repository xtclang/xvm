    class Client%propertyTypeName%
            extends imdb.ClientDBMap<%keyType%, %valueType%>
            incorporates %propertyType%
        {
        construct(Server%appSchema%.Server%propertyTypeName% %propertyName%)
            {
            construct imdb.ClientDBMap(%propertyName%);
            }

        // ----- mixin methods ---------------------------------------------------------------------

        %ClientDBMapMethods%

        // ----- class specific --------------------------------------------------------------------

        protected (Boolean, db.Transaction) ensureTransaction()
            {
            ClientTransaction? tx = this.Client%appSchema%.transaction;
            return tx == Null
                    ? (True, createTransaction())
                    : (False, tx);
            }

        // ----- ClientDBMap interface ------------------------------------------------------------

        @Override
        Boolean autoCommit.get()
            {
            return this.Client%appSchema%.transaction == Null;
            }

        @Override
        Tuple dbInvoke(String | Function fn, Tuple args = Tuple:(), (Duration|DateTime)? when = Null)
            {
            %ClientDBMapInvocations%

            throw new UnsupportedOperation(fn.toString());
            }

        @Override
        class ClientChange
            {
            construct()
                {
                // TODO CP - would be nice if it read "construct super();"
                construct imdb.ClientDBMap.ClientChange();
                }
            finally
                {
                ClientTransaction? tx = this.Client%appSchema%.transaction;
                assert tx != Null;
                tx.dbTransaction.contents.put("%propertyTypeName%", this);
                }
            }
        }
