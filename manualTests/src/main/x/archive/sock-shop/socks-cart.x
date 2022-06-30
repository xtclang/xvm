/**
 * The Ecstasy Sock Shop catalog database.
 */
module SockShopCart
        incorporates Database
    {
    package json import json.xtclang.org;
    package oodb import oodb.xtclang.org;

    import oodb.Connection;
    import oodb.Database;
    import oodb.DBMap;
    import oodb.RootSchema;
    import oodb.Transaction;

    import ecstasy.io.ByteArrayInputStream;
    import ecstasy.io.InputStream;
    import ecstasy.io.UTF8Reader;

    import json.Schema;

    interface SockShopCartSchema
            extends RootSchema
        {
        /**
         * The sock shop carts.
         */
        @RO Carts carts;

        /**
         * The shopping cart map.
         */
        mixin Carts
                into DBMap<String, Cart>
            {
            /**
             * Merge the source cart into the target cart, and remove the source cart.
             *
             * @param targetId the target cart/customer ID
             * @param sourceId the source cart/customer ID
             *
             * @return {@code True} if the carts were merged successfully, {@code False} otherwise
             */
            Boolean merge(String targetId, String sourceId)
                {
                if (Cart source := get(sourceId))
                    {
                    if (Cart target := get(targetId))
                        {
                        put(targetId, target.merge(source));
                        }
                    else
                        {
                        put(targetId, new Cart(targetId, source.items));
                        }
                    remove(sourceId);
                    return True;
                    }
                return False;
                }

            /**
             * Add the specified item to the Cart with the specified identifier,
             * creating the Cart if required.
             *
             * @param id    the Cart identifier
             * @param item  the Item to add to the Cart
             *
             * @return the Item added to the Cart
             */
            Item addItem(String id, Item item)
                {
                if (Cart cart := get(id))
                    {
                    (Cart updatedCart, Item itemAdded) = cart.add(item);
                    put(id, updatedCart);
                    return itemAdded;
                    }

                put(id, new Cart(id, [item]));
                return item;
                }

            /**
             * Update the specified item in the Cart with the specified identifier,
             * creating the Cart if required.
             *
             * @param id    the Cart identifier
             * @param item  the Item to update in the Cart
             *
             * @return the Item added to the Cart
             */
            void updateItem(String id, Item item)
                {
                if (Cart cart := get(id))
                    {
                    put(id, cart.update(item));
                    }
                else
                    {
                    put(id, new Cart(id, [item]));
                    }
                }

            void removeItem(String id, String itemId)
                {
                if (Cart cart := get(id))
                    {
                    put(id, cart.remove(itemId));
                    }
                }
            }
        }

    /**
     * This is the interface that will get injected.
     */
    typedef (oodb.Connection<SockShopCartSchema> + SockShopCartSchema) as Connection;

    /**
     * This is the interface that will come back from createTransaction.
     */
    typedef (oodb.Transaction<SockShopCartSchema> + SockShopCartSchema) as Transaction;

    /**
     * A shopping cart.
     *
     * @param customerId  the identifier of the customer this cart belongs to.
     * @param items       the items in the cart
     */
    const Cart(String customerId, Item[] items)
        {
        Cart! merge(Cart source)
            {
            if (source.items.empty)
                {
                return this;
                }

            if (items.empty)
                {
                return new Cart(customerId, new Array(Constant, source.items));
                }

            Item[] mergedItems = new Array();
            for (Item sourceItem : source.items)
                {
                if (Item targetItem := getItem(sourceItem.itemId))
                    {
                    mergedItems.add(sourceItem + targetItem);
                    }
                else
                    {
                    mergedItems.add(sourceItem);
                    }
                }
            return new Cart(customerId, mergedItems);
            }

        /**
         * Add the specified item to this cart, or update quantity if present.
         *
         * If the item with the same ID already exists in this cart, the quantity
         * will be incremented by the quantity specified in the specified item.
         * Otherwise, the item will be added to the cart as-is.
         *
         * @param item  the item to add
         *
         * @return the updated Cart
         * @return the added or updated item
         */
        (Cart!, Item) add(Item item)
            {
            if (items.empty)
                {
                return (new Cart(customerId, [item]), item);
                }

            Boolean added     = False;
            Item[]  newItems  = new Array();
            Item    itemAdded = item;

            for (Item existing : items)
                {
                if (existing.itemId == item.itemId)
                    {
                    itemAdded = existing + item;
                    newItems.add(itemAdded);
                    added = True;
                    }
                else
                    {
                    newItems.add(existing);
                    }
                }

            if (!added)
                {
                newItems.add(item);
                }

            return (new Cart(customerId, newItems), itemAdded);
            }

        /**
         * Replace specified Item, or add it if it's not already present in the Cart.
         *
         * @param item  the Item to add to the Cart, or to replace the existing item with
         *
         * @return the updated Cart
         */
        Cart! update(Item item)
            {
            if (items.empty)
                {
                return new Cart(customerId, [item]);
                }

            Item[]  newItems = new Array();
            Boolean updated  = False;

            for (Item existing : items)
                {
                if (existing.itemId == item.itemId)
                    {
                    newItems.add(item);
                    }
                else
                    {
                    newItems.add(existing);
                    }
                }

            if (!updated)
                {
                newItems.add(item);
                }

            return new Cart(customerId, newItems);
            }

        /**
         * Remove specified item from the cart.
         *
         * @param id  the item identifier
         *
         * @return the updated Cart
         */
        Cart! remove(String id)
            {
            if (items.empty)
                {
                return this;
                }

            return new Cart(customerId, items.removeAll(item -> item.itemId == id));
            }

        conditional Item getItem(String id)
            {
            return findItem(id, items);
            }

        static conditional Item findItem(String id, Item[] items)
            {
            for (Item item : items)
                {
                if (item.itemId == id)
                    {
                    return True, item;
                    }
                }
            return False;
            }
        }

    /**
     * An item in a shopping cart.
     *
     * @param itemId    the product identifier
     * @param quantity  the quantity being purchased
     * @param count     the product unit price
     */
    const Item(String itemId, Int quantity, Float unitPrice)
        {
        /**
         * Add the quantity of the specified item to this items quantity.
         *
         * A new Item with an updated quantity
         */
        @Op("+")
        Item! add(Item! item)
            {
            Int qty = quantity + (item.quantity == 0 ? 1 : item.quantity);
            return new Item(itemId, qty, unitPrice);
            }
        }
    }
