/**
 * This module contains the Object Oriented Database (OODB) APIs.
 */
module oodb.xtclang.org
    {
    /**
     * This mixin is used to mark a module as being a database module.
     */
    mixin Database
            into Module
        {
        }

    /**
     * Marks specific DBObjects as extra-transactional. Applies only to DBCounter and DBLog.
     */
    mixin NoTx
            into Property<DBSchema, DBCounter | DBLog>
        {
        }

    /**
     * Used for specifying an initial value for a DBValue. Must be used if the DBValue's `Value`
     * type does not have a `default` value. For example, the String type does not have a default
     * value, but `String?` does (it defaults to `Null`), as does Boolean (False) and Int (0).
     */
    mixin Initial<Value>(Value initial)
             into Property<DBSchema, DBValue<Value>>
        {
        }

    /**
     * Used for specifying the expiry policy for a DBLog.
     *
     * @param expiry  the duration of time to hold the log information for
     */
    mixin AutoExpire(Duration expiry)
            into Property<DBLog>
        {
        }

    /**
     * Used for specifying the truncation policy for a DBLog.
     *
     * @param sizeLimit  the size limit in bytes
     */
    mixin AutoTruncate(Int sizeLimit)
            into Property<DBLog>
        {
        }

    /**
     * Indicates that an exception related to the database processing has occurred.
     */
    const DBException(String? text = Null, Exception? cause = Null)
            extends Exception(text, cause)
        {
        }

    /**
     * A DBClosed exception is raised by the database when it's no longer active.
     */
    const DBClosed(String? text = Null, Exception? cause = Null)
            extends DBException(text, cause)
        {
        }

    /**
     * A CommitFailed exception is raised by the database when an "auto-commit" transaction fails.
     */
    const CommitFailed(Transaction.TxInfo info, Transaction.CommitResult result, String? text = Null,
                        Exception? cause = Null)
            extends DBException(text, cause)
        {
        }

    /**
     * Check to see if any of the database object name rules is broken by the specified name, and
     * if so, provide an explanation of the broken rule.
     *
     * @param name  the name to check
     *
     * @return True iff the name violates the database object name rules
     * @return (conditional) a description of how the name broke the naming rules
     */
    static conditional String isInvalidName(String name)
        {
        import ecstasy.lang.src.Lexer;
        import Lexer.isIdentifierStart;
        import Lexer.isIdentifierPart;
        import Lexer.Id.allKeywords;

        if (name == "")
            {
            return True, "The name is blank";
            }

        if (allKeywords.contains(name))
            {
            return True, $"Name ({name.quoted()}) is an Ecstasy keyword";
            }

        for (Char ch : name)
            {
            // explicitly call out characters that are reserved for filing system use
            switch (ch)
                {
                case '.':
                    return True, $"The name ({name.quoted()}) contains a period ('.')";
                case ' ':
                    return True, $"The name ({name.quoted()}) contains a space (' ')";
                case ':':
                    return True, $"The name ({name.quoted()}) contains a colon (':')";
                case '\\':
                    return True, $"The name ({name.quoted()}) contains a back-slash ('\\')";
                case '/':
                    return True, $"The name ({name.quoted()}) contains a forward-slash ('/')";
                }

            if (!isIdentifierPart(ch))
                {
                return True, $|Name ({name.quoted()}) must not contain the character {ch.quoted()}\
                              | ({ch.category.description})
                             ;
                }
            }

        if (!isIdentifierStart(name[0]))
            {
            return True, $"The name ({name.quoted()}) does not begin with a letter or an underscore";
            }

        // REVIEW compiler message when accidentally including a unary prefix '!': if (!name[name.size-1] == '_')
        if (name[name.size-1] == '_')
            {
            return True, $"The name ({name.quoted()}) ends with an underscore";
            }

        return False;
        }
    }
