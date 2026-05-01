package scaleview.analysis.types;

import org.apache.commons.math3.ml.clustering.Clusterable;

public class ClusteredMethod implements Clusterable {

	private String dimensionName;
	private String identifier;
	private double[] points;

	public ClusteredMethod(String dimensionName, String identifier, double[] points) {
		this.dimensionName = dimensionName;
		this.identifier = identifier;
		this.points = points;
	}

	public String getDimensionName() {
		return dimensionName;
	}

	public String getIdentifier() {
		return identifier;
	}

	@Override
	public double[] getPoint() {
		return points;
	}

}
