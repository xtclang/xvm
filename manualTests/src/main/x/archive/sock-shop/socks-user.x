/**
 * The Ecstasy Sock Shop user database.
 */
module SockShopUser
        incorporates Database
    {
    package json import json.xtclang.org;
    package oodb import oodb.xtclang.org;

    import oodb.Connection;
    import oodb.Database;
    import oodb.DBMap;
    import oodb.RootSchema;
    import oodb.Transaction;

    interface SockShopUserSchema
            extends RootSchema
        {
        /**
         * The sock shop users.
         */
        @RO Users users;

        /**
         * The shopping user map.
         */
        mixin Users
                into DBMap<String, User>
            {
            void add(User user)
                {
                put(user.username, user);
                }

            /**
             * Add an `Address` to the specified user.
             *
             * @param userId  the user id
             * @param address the address to add
             *
             * @return the `AddressId` of the address
             */
            conditional AddressId addAddress(String userId, Address address)
                {
                if (User user := get(userId))
                    {
                    put(userId, user.addAddress(address));
                    return True, new AddressId(userId, address.addressId);
                    }
                return False;
                }

            /**
             * Return the `Address` for the specified address identifier.
             *
             * @param id the address id
             *
             * @return the `Address` with the specified identifier
             */
             conditional Address getAddress(AddressId id)
                {
                if (User user := get(id.user))
                    {
                    return user.getAddress(id.addressId);
                    }
                return False;
                }

            /**
             * Remove the address with the specified identifier.
             *
             * @param id the address id
             *
             * @return True if the address was removed
             */
            Boolean removeAddress(AddressId id)
                {
                if (User user := get(id.user))
                    {
                    if (User updated := user.removeAddress(id.addressId))
                        {
                        put(id.user, updated);
                        return True;
                        }
                    }
                return False;
                }

            /**
             * Add a Card to the specified user.
             *
             * @param userId the user id
             * @param card   the card to add
             *
             * @return the `CardId` of the card
             */
            conditional CardId addCard(String userId, Card card)
                {
                if (User user := get(userId))
                    {
                    put(userId, user.addCard(card));
                    return True, new CardId(userId, card.cardId);
                    }
                return False;
                }

            /**
             * Return the `Card` with the specified card identifier.
             *
             * @param id the card id
             *
             * @return the `Card` with the specified card id
             */
            conditional Card getCard(CardId id)
                {
                if (User user := get(id.user))
                    {
                    return user.getCard(id.cardId);
                    }
                return False;
                }

            /**
             * Remove the card  with the specified identifier.
             *
             * @param id the card id
             */
            Boolean removeCard(CardId id)
                {
                if (User user := get(id.user))
                    {
                    if (User updated := user.removeCard(id.cardId))
                        {
                        put(id.user, updated);
                        return True;
                        }
                    }
                return False;
                }

            /**
             * Return an existing `User` for the specified user identifier;
             * or a newly created `User`.
             *
             * @param id the user id
             *
             * @return the `User` with the specified user id
             */
            User getOrCreate(String id)
                {
                if (User user := get(id))
                    {
                    return user;
                    }

                User user = new User(id, "", "", "", "");
                put(id, user);
                return user;
                }

            /**
             * Remove the `User` with the specified user identifier;
             *
             * @param id the id
             *
             * @return the removed `User`
             */
            conditional User removeUser(String id)
                {
                if (User user := get(id))
                    {
                    remove(user.username);
                    return True, user;
                    }
                return False;
                }

            /**
             * Authenticate a `User` with the specified username against
             * the specified password.
             *
             * @param username the username of the user to be authenticated
             * @param password the password to authenticate against
             *
             * @return true if passwords match
             */
            Boolean authenticate(String username, String password)
                {
                if (User user := get(username))
                    {
                    return password == user.password;
                    }
                return False;
                }

            /**
             * Register the specified user.
             *
             * @param user the user to be registered
             *
             * @return the registered user
             */
            Boolean register(User user)
                {
                if (keys.contains(user.username))
                    {
                    return False;
                    }

                put(user.username, user);
                return True;
                }
            }
        }

    /**
     * This is the interface that will get injected.
     */
    typedef (oodb.Connection<SockShopUserSchema> + SockShopUserSchema) Connection;

    /**
     * This is the interface that will come back from createTransaction.
     */
    typedef (oodb.Transaction<SockShopUserSchema> + SockShopUserSchema) Transaction;

    /**
     * A sock shop user.
     *
     * @param customerId  the identifier of the user.
     */
    const User(String username, String firstName, String lastName, String email, String password,
               Address[] addresses = [], Card[] cards = [])
        {
        User! addAddress(Address address)
            {
            if (address.addressId == "")
                {
                address = address.withId(addresses.size.toString());
                return new User(username, firstName, lastName, email, password, addresses.add(address), cards);
                }

            Address[] newAddresses = new Array(Mutable, addresses);
            for (Int i : 0 ..< addresses.size)
                {
                if (addresses[i].addressId == address.addressId)
                    {
                    newAddresses[i] = address;
                    return new User(username, firstName, lastName, email, password, newAddresses, cards);
                    }
                }

            newAddresses.add(address);
            return new User(username, firstName, lastName, email, password, newAddresses, cards);
            }

        conditional Address getAddress(String addressId)
            {
            for (Int i : 0 ..< addresses.size)
                {
                if (addresses[i].addressId == addressId)
                    {
                    return (True, addresses[i]);
                    }
                }
            return False;
            }

        conditional User! removeAddress(String addressId)
            {
            (Address[] newAddresses, Int removed) = addresses.removeAll(address -> address.addressId == addressId);
            if (removed != 0)
                {
                return True, new User(username, firstName, lastName, email, password, newAddresses, cards);
                }
            return False;
            }

        User! addCard(Card card)
            {
            if (card.cardId == "")
                {
                card = card.withId(cards.size.toString());
                return new User(username, firstName, lastName, email, password, addresses, cards.add(card));
                }

            Card[] newCards = new Array(Mutable, cards);
            for (Int i : 0 ..< cards.size)
                {
                if (cards[i].cardId == card.cardId)
                    {
                    newCards[i] = card;
                    return new User(username, firstName, lastName, email, password, addresses, newCards);
                    }
                }

            newCards.add(card);
            return new User(username, firstName, lastName, email, password, addresses, newCards);
            }

        conditional Card getCard(String cardId)
            {
            for (Int i : 0 ..< cards.size)
                {
                if (cards[i].cardId == cardId)
                    {
                    return (True, cards[i]);
                    }
                }
            return False;
            }

        conditional User! removeCard(String cardId)
            {
            (Card[] newCards, Int removed) = cards.removeAll(card -> card.cardId == cardId);
            if (removed != 0)
                {
                return True, new User(username, firstName, lastName, email, password, addresses, newCards);
                }
            return False;
            }

        // ----- Orderable and Hashable ----------------------------------------------------------------

        /**
         * Calculate a hash code for the specified User instances.
         */
        static <CompileType extends User> Int hashCode(CompileType value)
            {
            return value.username.hashCode();
            }

        /**
         * Compare two User instances for the purposes of ordering.
         */
        static <CompileType extends User> Ordered compare(CompileType value1, CompileType value2)
            {
            return value1.username <=> value2.username;
            }

        /**
         * Compare two User instances for equality.
         */
        static <CompileType extends User> Boolean equals(CompileType value1, CompileType value2)
            {
            return value1.username == value2.username;
            }
        }

    /**
     * A representation of a user's address.
     */
    const Address(String addressId, String number, String street, String city, String postcode, String country)
        {
        Address! withId(String id)
            {
            return new Address(id, number, street, city, postcode, country);
            }

        // ----- Orderable and Hashable ----------------------------------------------------------------

        /**
         * Calculate a hash code for the specified Address instances.
         */
        static <CompileType extends Address> Int hashCode(CompileType value)
            {
            return value.addressId.hashCode();
            }

        /**
         * Compare two Address instances for the purposes of ordering.
         */
        static <CompileType extends Address> Ordered compare(CompileType value1, CompileType value2)
            {
            return value1.addressId <=> value2.addressId;
            }

        /**
         * Compare two Address instances for equality.
         */
        static <CompileType extends Address> Boolean equals(CompileType value1, CompileType value2)
            {
            return value1.addressId == value2.addressId;
            }
        }

    /**
     * An address identifier.
     */
    const AddressId(String user, String addressId)
        {
        }

    /**
     * A representation of a user's credit card.
     */
    const Card(String cardId, String longNum, String expires, String ccv)
        {
        Card! withId(String id)
            {
            return new Card(id, longNum, expires, ccv);
            }

        // ----- Orderable and Hashable ----------------------------------------------------------------

        /**
         * Calculate a hash code for the specified Card instances.
         */
        static <CompileType extends Card> Int hashCode(CompileType value)
            {
            return value.cardId.hashCode();
            }

        /**
         * Compare two Card instances for the purposes of ordering.
         */
        static <CompileType extends Card> Ordered compare(CompileType value1, CompileType value2)
            {
            return value1.cardId <=> value2.cardId;
            }

        /**
         * Compare two Card instances for equality.
         */
        static <CompileType extends Card> Boolean equals(CompileType value1, CompileType value2)
            {
            return value1.cardId == value2.cardId;
            }
        }

    /**
     * A credit card identifier.
     */
    const CardId(String user, String cardId)
        {
        construct (String id)
            {
            String[] parts = id.split(':');
            assert:arg parts.size == 2;
            user   = parts[0];
            cardId = parts[1];
            }
        }
    }
