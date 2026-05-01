package scaleview.analysis.utils;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtExecutableReference;
import scaleview.analysis.types.SDLoop;
import scaleview.analysis.types.SpoonMethodReference;

public class AnalysisUtils {

	public static final String ID_SEP = ":";
	public static final String ID_SEP_FIELD = "#";

	public static String getMethodId(CtMethod method) {
		return method.getDeclaringType().getQualifiedName() + ID_SEP + method.getSignature();
	}

	public static String getMethodId(CtExecutableReference methodCall) {
		return methodCall.getDeclaringType().getQualifiedName() + ID_SEP + methodCall.getSignature();
	}

	public static String getMethodId(String className, String methodSig) {
		return className + ID_SEP + methodSig;
	}

	public static boolean hasBody(CtMethod method) {
		return !method.isAbstract() && !method.isNative() && method.getBody() != null;
	}

	public static boolean inSameMethod(SDLoop loop, SpoonMethodReference r) {
		return loop.getRelatedMethodId().compareTo(r.getMethodId()) == 0;
	}

	public static void toJsonFile(Object o, Class<?> c, String fileName) throws IOException {
		Writer w = new FileWriter(fileName);
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		gson.toJson(o, c, w);
		w.flush();
		w.close();
	}

	public static <T extends Comparable<T>> Stats<T> statsFromList(List<T> numbers, boolean sort) {
		// sort of necessary
		if (sort) {
			Collections.sort(numbers);
		}
		// get the percentile
		return new Stats<T>(numbers.get(0),
				percentileFromSortedList(numbers, 25),
				percentileFromSortedList(numbers, 50),
				percentileFromSortedList(numbers, 75),
				percentileFromSortedList(numbers, 90),
				percentileFromSortedList(numbers, 95),
				numbers.get(numbers.size() - 1));
	}

	public static <T extends Comparable<T>> T percentileFromSortedList(List<T> numbers, int percentile) {
		int index = (int) Math.ceil(percentile / 100.0 * numbers.size());
		return numbers.get(index - 1);
	}

	public static class Stats<T extends Comparable<T>> {

		private T median;
		private T min;
		private T max;
		private T p75;
		private T p25;
		private T p90;
		private T p95;

		public Stats(T min, T p25, T median, T p75, T p90, T p95, T max) {
			this.min = min;
			this.p25 = p25;
			this.median = median;
			this.p75 = p75;
			this.p90 = p90;
			this.p95 = p95;
			this.max = max;
		}

		public T[] toArray() {
			List<T> values = new ArrayList<>();
			values.add(min);
			values.add(p25);
			values.add(median);
			values.add(p75);
			values.add(p90);
			values.add(p95);
			values.add(max);
			return values.toArray((T[]) Array.newInstance(median.getClass(), 7));

		}
	}
}
