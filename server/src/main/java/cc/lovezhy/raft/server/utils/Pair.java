package cc.lovezhy.raft.server.utils;

import com.google.common.base.Objects;

public class Pair<K, V> {


    public static <K,V> Pair<K,V> of(K key, V value) {
        return new Pair<>(key, value);
    }

    private K key;

    public K getKey() {
        return key;
    }

    private V value;

    public V getValue() {
        return value;
    }

    public Pair(K key, V value) {
        this.key = key;
        this.value = value;
    }

    @Override
    public String toString() {
        return key + "=" + value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Pair<?, ?> pair = (Pair<?, ?>) o;
        return Objects.equal(key, pair.key) &&
                Objects.equal(value, pair.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(key, value);
    }
}

