package cp.articlerep.ds;

import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author Ricardo Dias
 */
public interface Map<K extends Comparable<K>, V> {
	public V put(K key, V value);

	public boolean contains(K key);

	public V remove(K key);

	public V get(K key);

	public Iterator<V> values();

	public Iterator<K> keys();

	public ReentrantReadWriteLock getLock(K key);

	public List<ReentrantReadWriteLock> getLocks(List<K> keys);
}
