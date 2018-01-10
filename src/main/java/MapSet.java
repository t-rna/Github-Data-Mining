import com.sun.istack.internal.NotNull;

import java.util.HashMap;

/**
 * @author Kevin Ng
 *
 * A Map that has unique & non-overriding key-value mappings.
 *
 * Problem:
 *     Java Map implementations of put() method override the existing
 * value V, of a supplied key K, if a K-V mapping already exists.
 *
 * Solution:
 *     This is a HashMap wrapper class. The wrapping put() method checks
 * to see if the key already exists. If it does, we DO NOT override
 * the associated value -- Don't insert at all, since we want the map to
 * behave like a Set. Returns a boolean like Java Set's add() method.
 */
public class MapSet<K, V> {

    private HashMap<K, V> map;

    public MapSet() {
        map = new HashMap<>();
    }

    public MapSet(int initialCapacity) {
        map = new HashMap<>(initialCapacity);
    }

    public int size() {
        return map.size();
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    public V get(K key) {
        return map.get(key);
    }

    /**
     * Inserts a unique key-value mapping.
     * @param key       Key for the map
     * @param value     Value associated with the Key
     * @return          True, if and only if the key does not exist, and insert succeeds.
     */
    public boolean put(@NotNull K key, @NotNull V value) {
        // Key and/or Value must NOT be null
        if (key == null || value == null)
            return false;

        if (map.containsKey(key))
            return false;
        else
            return map.put(key, value) == null;

        /*
            MAP PUT
            Returns: the previous value associated with key, or null if there was no mapping for key.
                  (A null return can also indicate that the map previously associated null with key.)

            Since the first scenario and the bracket scenario are enforced to never occur, a new insertion
            will always return null, so the above will return true.
         */
    }

    public V remove(K key) {
        return map.remove(key);
    }

    public void clear() {
        map.clear();
    }
}
