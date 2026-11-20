package com.val00n.dmpg.cache;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public abstract class LruCache<K, V> {

    private final int maxSize;
    private final LinkedHashMap<K, V> map;
    private int size;

    public LruCache(int maxSize) {
        if (maxSize > 0) {
            map = new LinkedHashMap<K, V>(0, 0.75f, true);
            this.maxSize = maxSize;
        } else {
            throw new IllegalArgumentException();
        }
    }

    public abstract int sizeOf(V value);

    public final synchronized V get(K key) {
        return map.get(key);
    }

    public final synchronized V put(K key, V value) {
        if (value == null) {
            return remove(key);
        }

        int valueSize = sizeOf(value);

        if (valueSize > maxSize) {
            return null;
        }


        if (map.containsKey(key)) {
            V prevValue = map.get(key);
            size -= sizeOf(prevValue);

            if (size + sizeOf(value) > maxSize) {
                trimToSize(maxSize - sizeOf(value));
            }

            size += sizeOf(value);
            map.put(key, value);

            return prevValue;
        }

        if (size + valueSize > maxSize) {
            trimToSize(maxSize - valueSize);
        }

        size += sizeOf(value);
        map.put(key, value);

        return null;
    }

    public final synchronized V remove(K key) {
        if (map.containsKey(key)) {
            V value = map.get(key);
            size -= sizeOf(value);
            map.remove(key);
            return value;
        }

        return null;
    }

    public final synchronized void trimToSize(int limit) {
        Iterator<Map.Entry<K, V>> iterator = map.entrySet().iterator();
        while (size > limit && iterator.hasNext()) {
            Map.Entry<K, V> entry = iterator.next();
            size -= sizeOf(entry.getValue());
            iterator.remove();
        }
    }

    public final synchronized int entryCount() {
        return map.size();
    }

    public final synchronized void removeByPrefix(String prefix) {
        if (prefix == null) return;

        Iterator iterator = map.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry entry = (Map.Entry) iterator.next();
            Object key = entry.getKey();
            if (key instanceof String && ((String) key).startsWith(prefix)) {
                size -= sizeOf((V) entry.getValue());
                iterator.remove();
            }
        }
    }
}
