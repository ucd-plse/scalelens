package scaleview.analysis.types;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeMap;

public class SortedMultiMap<T extends Comparable, K> implements Iterable<Entry<T, List<K>>> {

	private TreeMap<T, List<K>> values;
	private int size;

	public SortedMultiMap(Comparator<T> comp) {
		this.values = new TreeMap<>(comp);
	}

	public List<K> put(T key, K value) {
		++size;
		List<K> newValue = values.get(key);
		if (newValue != null) {
			newValue.add(value);
			return newValue;
		} else {
			newValue = new ArrayList<>();
			newValue.add(value);
			values.put(key, newValue);
			return null;
		}
	}

	public Collection<K> flatten() {
		Collection<K> flat = new LinkedList<>();
		for (Entry<T, List<K>> e : values.entrySet()) {
			flat.addAll(e.getValue());
		}
		return flat;
	}

	@Override
	public Iterator<Entry<T, List<K>>> iterator() {
		return values.entrySet().iterator();
	}

	public int size() {
		return size;
	}
}
