package fr.doan.achilles.wrapper;

import java.util.Map;
import java.util.Map.Entry;

public class MapEntryWrapper<K, V> extends AbstractWrapper<K, V> implements Map.Entry<K, V>
{

	private final Map.Entry<K, V> target;

	public MapEntryWrapper(Map.Entry<K, V> target) {
		this.target = target;
	}

	@Override
	public K getKey()
	{
		return this.target.getKey();
	}

	@Override
	public V getValue()
	{
		return this.target.getValue();
	}

	@Override
	public V setValue(V value)
	{
		V result = this.target.setValue(value);
		this.markDirty();
		return result;
	}

	public boolean equals(Entry<K, V> entry)
	{
		K key = entry.getKey();
		V value = entry.getValue();

		boolean keyEquals = this.target.getKey().equals(key);

		boolean valueEquals = false;
		if (this.target.getValue() == null && value == null)
		{
			valueEquals = true;
		}
		else if (this.target.getValue() != null && value != null)
		{
			valueEquals = this.target.getValue().equals(value);
		}

		return keyEquals && valueEquals;
	}

	public int hashCode()
	{
		K key = this.target.getKey();
		V value = this.target.getValue();
		int result = 1;
		result = result * 31 + key.hashCode();
		result = result * 31 + (value == null ? 0 : value.hashCode());
		return result;
	}

	public Map.Entry<K, V> getTarget()
	{
		return target;
	}

}