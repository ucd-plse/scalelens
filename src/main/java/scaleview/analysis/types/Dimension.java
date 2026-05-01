package scaleview.analysis.types;

import java.util.Map;

import com.google.gson.annotations.SerializedName;

public class Dimension {

	@SerializedName("name")
	private String name;
	@SerializedName("growthSlope")
	private double growthSlope;
	@SerializedName("equation")
	private String equation;
	@SerializedName("xAxis")
	private double[] xAxis;
	@SerializedName("yAxis")
	private double[] yAxis;
	@SerializedName("multiRegressionYaxis")
	private double[] multiRegressionYaxis;
	@SerializedName("growthYAxis")
	private double[] growthYAxis;
	@SerializedName("rSquare")
	private double rSquare;
	@SerializedName("spearmanCorrelation")
	private double spearmanCorrelation;
	@SerializedName("details")
	private String details;
	@SerializedName("experimentSize")
	private int experimentSize;
	@SerializedName("growthPoints")
	private Map<Double, Double> growthPoints;
	@SerializedName("rawPoints")
	private Map<Double, Double> rawPoints;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public double getGrowthSlope() {
		return growthSlope;
	}

	public void setGrowthSlope(double growthSlope) {
		this.growthSlope = growthSlope;
	}

	public String getEquation() {
		return equation;
	}

	public void setEquation(String equation) {
		this.equation = equation;
	}

	public double[] getxAxis() {
		return xAxis;
	}

	public void setxAxis(double[] xAxis) {
		this.xAxis = xAxis;
	}

	public double[] getyAxis() {
		return yAxis;
	}

	public void setyAxis(double[] yAxis) {
		this.yAxis = yAxis;
	}

	public double[] getGrowthYAxis() {
		return growthYAxis;
	}

	public void setGrowthYAxis(double[] growthYAxis) {
		this.growthYAxis = growthYAxis;
	}

	public double getrSquare() {
		return rSquare;
	}

	public void setrSquare(double rSquare) {
		this.rSquare = rSquare;
	}

	public double getSpearmanCorrelation() {
		return spearmanCorrelation;
	}

	public void setSpearmanCorrelation(double spearmanCorrelation) {
		this.spearmanCorrelation = spearmanCorrelation;
	}

	public double[] getMultiRegressionYaxis() {
		return multiRegressionYaxis;
	}

	public void setMultiRegressionYaxis(double[] multiRegressionYaxis) {
		this.multiRegressionYaxis = multiRegressionYaxis;
	}

	public String getDetails() {
		return details;
	}

	public void setDetails(String details) {
		this.details = details;
	}

	public int getExperimentSize() {
		return experimentSize;
	}

	public void setExperimentSize(int experimentSize) {
		this.experimentSize = experimentSize;
	}

	public Map<Double, Double> getGrowthPoints() {
		return growthPoints;
	}

	public void setGrowthPoints(Map<Double, Double> growthPoints) {
		this.growthPoints = growthPoints;
	}

	public Map<Double, Double> getRawPoints() {
		return rawPoints;
	}

	public void setRawPoints(Map<Double, Double> growthPoints) {
		this.rawPoints = growthPoints;
	}

	@Override
	public int hashCode() {
		return name.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof Dimension) {
			return ((Dimension) obj).name.compareTo(name) == 0;
		}
		return false;
	}

}
