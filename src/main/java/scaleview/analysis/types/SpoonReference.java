package scaleview.analysis.types;

import java.util.HashMap;
import java.util.Iterator;

public abstract class SpoonReference {

	protected String id;

	public SpoonReference(String i) {
		id = i;
	}

	public String getId() {
		return id;
	}

	@Override
	public int hashCode() {
		return id.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof SpoonReference) {
			return ((SpoonReference) o).id.compareTo(id) == 0;
		}
		return false;
	}

	public static class SpoonReferenceContainer<T extends SpoonReference> implements Iterable<T> {

		private HashMap<String, T> values = new HashMap<>();

		public T putValue(T value) {
			return values.put(value.id, value);
		}

		public T getValue(String id) {
			return values.get(id);
		}

		public boolean containsValue(String id) {
			return values.containsKey(id);
		}

		@Override
		public Iterator<T> iterator() {
			return values.values().iterator();
		}

		public int size() {
			return values.size();
		}

		public void clear() {
			values.clear();
		}

	}

}
