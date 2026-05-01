package scaleview.analysis.utils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class RansacRegressor {

	private static Random random = new Random();

	// RANDPERM(N,K) returns a vector of K unique values. This is sometimes
	// referred to as a K-permutation of 1:N or as sampling without replacement.
	private static Set<Integer> randPerm(int N, int K) {
		Set<Integer> res = new LinkedHashSet<>(); // unsorted set.
		while (res.size() < K) {
			res.add(random.nextInt(N)); // [0, number-1]
		}
		return res;
	}

	private static double norm(List<Double> vec) {
		return Math.sqrt(Math.pow(vec.get(0), 2) + Math.pow(vec.get(1), 2));
	}

	private static List<Integer> findLessThan(List<Double> distance, double threshDist) {
		List<Integer> res = new ArrayList<>();
		for (int i = 0; i < distance.size(); i++) {
			double dist = distance.get(i);
			if (Math.abs(dist) <= threshDist) {
				res.add(i);
			}
		}
		return res;
	}

	public static List<Double> perform(double[] dataY, double[] dataX, int iter, double threshDist,
			double inlierRatio) {
		double bestInNum = 0;
		double bestParameter1 = 0, bestParameter2 = 0;

		for (int i = 0; i < iter; i++) {
			Set<Integer> idx = randPerm(dataY.length, dataX.length);

			List<Double> sampleX = new ArrayList<>();
			List<Double> sampleY = new ArrayList<>();
			for (Integer idxVal : idx) {
				sampleX.add(dataX[idxVal]);
				sampleY.add(dataY[idxVal]);
			}

			List<Double> kLine = new ArrayList<>();
			kLine.add((double) (sampleX.get(1) - sampleX.get(0)));
			kLine.add(sampleY.get(1) - sampleY.get(0));

			List<Double> kLineNorm = new ArrayList<>();
			double norm = norm(kLine);
			kLineNorm.add(kLine.get(0) / norm);
			kLineNorm.add(kLine.get(1) / norm);

			List<Double> normVector = new ArrayList<>();
			normVector.add(-kLineNorm.get(1));
			normVector.add(kLineNorm.get(0));

			List<Double> distance = new ArrayList<>();
			for (int j = 0; j < dataY.length; j++) {
				double distTmp = normVector.get(0) * (dataX[j] - sampleX.get(0));
				distTmp += normVector.get(1) * (dataY[j] - sampleY.get(0));
				distance.add(distTmp);
			}

			List<Integer> inlierIdx = findLessThan(distance, threshDist);

			int inlierNum = inlierIdx.size();

			double parameter1 = 0;
			double parameter2 = 0;

			if ((inlierNum >= Math.round(inlierRatio * dataY.length)) && (inlierNum > bestInNum)) {
				bestInNum = inlierNum;
				parameter1 = (sampleY.get(1) - sampleY.get(0)) / (sampleX.get(1) - sampleX.get(0));
				parameter2 = sampleY.get(0) - parameter1 * sampleX.get(0);
				bestParameter1 = parameter1;
				bestParameter2 = parameter2;
			}
		}

		List<Double> res = new ArrayList<>();
		res.add(bestParameter1);
		res.add(bestParameter2);
		return res;
	}

}
