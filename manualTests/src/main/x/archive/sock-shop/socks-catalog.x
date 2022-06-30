/**
 * The Ecstasy Sock Shop catalog database.
 */
module SockShopCatalog
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

    interface SockShopCatalogSchema
            extends RootSchema
        {
        /**
         * The sock shop products (i.e. Socks).
         */
        @RO Products products;

        /**
         * Load the Sock data in the specified json file into the products map.
         */
        void loadSocks(File data)
            {
            assert data.exists;
            InputStream in    = new ByteArrayInputStream(data.contents);
            Sock[]      socks = Schema.DEFAULT.createObjectInput(new UTF8Reader(in)).read<Sock[]>();
            for (Sock sock : socks)
                {
                products.put(sock.id, sock);
                }
            }

        /**
         * A catalog products map.
         */
        mixin Products
                into DBMap<String, Sock>
            {
            /**
             * @return the count of socks matching one or more of the tags
             */
            Int count(String[] tags)
                {
                if (tags.size == 0)
                    {
                    return this.size;
                    }

                return this.filter(entry -> entry.value.hasAnyTag(tags)).size;
                }

            /**
             * Search for Socks in the database.
             */
            Sock[] findSocks(Array<String> tags, SockOrder sockOrder, Int page, Int pageSize)
                {
                Sock[] socks = new Array();

                if (tags.size == 0)
                    {
                    socks.addAll(this.values);
                    }
                else
                    {
                    socks.addAll(this.values.filter(sock -> sock.hasAnyTag(tags)));
                    }

                if (sockOrder == Name)
                    {
                    socks = socks.sorted((s1, s2) -> s1.name <=> s2.name).freeze(True);
                    }
                socks = socks.sorted((s1, s2) -> s1.price <=> s2.price).freeze(True);

                Int start = (page - 1) * pageSize;
                if (start < 0)
                    {
                    start = 0;
                    }
                Int end   = page * pageSize;
                if (end > socks.size)
                    {
                    end = socks.size;
                    }

                if (start > socks.size)
                    {
                    return [];
                    }
                return socks[start..end);
                }

            /**
             * Return all the distinct tag values.
             */
            String[] tags()
                {
                Set<String> tags = new HashSet();
                for (Sock sock : this.values)
                    {
                    for (String tag : sock.tag)
                        {
                        tags.add(tag);
                        }
                    }
                return new Array(Mutability.Constant, tags);
                }
            }
        }

    /**
     * This is the interface that will get injected.
     */
    typedef (oodb.Connection<SockShopCatalogSchema> + SockShopCatalogSchema) Connection;

    /**
     * This is the interface that will come back from createTransaction.
     */
    typedef (oodb.Transaction<SockShopCatalogSchema> + SockShopCatalogSchema) Transaction;

    /**
     * A sock in the sock shop catalog.
     *
     * @param id           the product identifier
     * @param name         the product name
     * @param description  the product description
     * @param imageUrl     the URI of the product image
     * @param price        the product price
     * @param count        the product stock count
     * @param imageUrl     the URLs for the images of this Sock
     * @param tag          the product tags
     */
    const Sock(String id, String name, String description, Float price, Int count, String[] imageUrl, String[] tag)
        {
        Boolean hasAnyTag(String[] tags)
            {
            if (tags.size == 0)
                {
                return True;
                }

            for (String t : tags)
                {
                if (tag.contains(t))
                    {
                    return True;
                    }
                }
            return False;
            }
        }

    /**
     * An enum representing the different sort orders when searching for socks.
     */
    enum SockOrder
        {
        Name,
        Price
        }
    }
