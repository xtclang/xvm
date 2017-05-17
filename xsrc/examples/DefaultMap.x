
public class DefaultMap<K, V>
    {
    @ro public boolean empty
        {
        get()
            {
            return size == 0;
            }
        }

    public boolean containsValue(Object value)
        {
        for (V v : values())
            {
            if  (v == value)
                {
                return true;
                }
            }
        return false;
        }

    public boolean containsKey(Object key)
        {
        for (K k : keys())
            {
            if  (k == key)
                {
                return true;
                }
            }
        return false;
        }

    public V getOrDefault(K key, V valueDefault) {
        V v;
        return ((v = get(key)) != null) ? v : valueDefault;
        }

    public void putAll(Map<K, V> map)
        {
        for ((K k, V v) : map.entries())
            {
            put(k, v);
            }
        }

    public void forEach(func (void (K, V)) action) {
        for ((K k, V v) : entries()) {
            action(k, v);
            }
        }

    public void forEach(func (void (Entry<K, V>)) action) {
        for (Entry<K, V> entry : entrySet()) {
            action(entry);
            }
        }

    public void replaceAll(func (V (K, V)) update) {
        for (Map.Entry<K, V> entry : entrySet()) {
            entry.value = update(entry.key, entry.value);
            }
        }

    public V putIfAbsent(K key, V value) {
        V v = get(key);
        if (v == null) {
            v = put(key, value);
            }

        return v;
        }

    public boolean remove(K key, V value) {
        V valueCur = get(key);
        if (valueCur == value)) {
            remove(key);
            return true;
            }
        return false;
        }

    public boolean replace(K key, V oldValue, V newValue) {
        Object curValue = get(key);
        if (!Objects.equals(curValue, oldValue) ||
            (curValue == null && !containsKey(key))) {
            return false;
        }
        put(key, newValue);
        return true;
    }

    public V replace(K key, V value) {
        V curValue;
        if (((curValue = get(key)) != null) || containsKey(key)) {
            curValue = put(key, value);
        }
        return curValue;
    }

    public V computeIfAbsent(K key,
            Function<? super K, ? extends V> mappingFunction) {
        Objects.requireNonNull(mappingFunction);
        V v;
        if ((v = get(key)) == null) {
            V newValue;
            if ((newValue = mappingFunction.apply(key)) != null) {
                put(key, newValue);
                return newValue;
            }
        }

        return v;
    }

    public V computeIfPresent(K key,
            BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        Objects.requireNonNull(remappingFunction);
        V oldValue;
        if ((oldValue = get(key)) != null) {
            V newValue = remappingFunction.apply(key, oldValue);
            if (newValue != null) {
                put(key, newValue);
                return newValue;
            } else {
                remove(key);
                return null;
            }
        } else {
            return null;
        }
    }

    public V compute(K key,
            BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        Objects.requireNonNull(remappingFunction);
        V oldValue = get(key);

        V newValue = remappingFunction.apply(key, oldValue);
        if (newValue == null) {
            // delete mapping
            if (oldValue != null || containsKey(key)) {
                // something to remove
                remove(key);
                return null;
            } else {
                // nothing to do. Leave things as they were.
                return null;
            }
        } else {
            // add or replace old mapping
            put(key, newValue);
            return newValue;
        }
    }

    public V merge(K key, V value,
            BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        Objects.requireNonNull(remappingFunction);
        Objects.requireNonNull(value);
        V oldValue = get(key);
        V newValue = (oldValue == null) ? value :
                   remappingFunction.apply(oldValue, value);
        if(newValue == null) {
            remove(key);
        } else {
            put(key, newValue);
        }
        return newValue;
        }
    }
