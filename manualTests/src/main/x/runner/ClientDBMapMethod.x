        @Override
        %methodSignature%
            {
            (Boolean autoCommit, db.Transaction tx) = ensureTransaction();

            super(%methodArguments%);

            if (autoCommit)
                {
                tx.commit();
                }
            }
