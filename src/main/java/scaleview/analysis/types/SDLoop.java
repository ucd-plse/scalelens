package scaleview.analysis.types;

import com.google.gson.annotations.SerializedName;

import scaleview.analysis.utils.AnalysisUtils;

public class SDLoop {

	@SerializedName("className")
	private String className;
	@SerializedName("methodSignature")
	private String methodSignature;
	@SerializedName("lineNumber")
	private int lineNumber;
	@SerializedName("dimension")
	private Dimension dimension;

	public String getClassName() {
		return className;
	}

	public void setClassName(String className) {
		this.className = className;
	}

	public String getMethodSignature() {
		return methodSignature;
	}

	public void setMethodSignature(String methodSignature) {
		this.methodSignature = methodSignature;
	}

	public int getLineNumber() {
		return lineNumber;
	}

	public void setLineNumber(int lineNumber) {
		this.lineNumber = lineNumber;
	}

	public Dimension getDimension() {
		return dimension;
	}

	public void setDimension(Dimension dimension) {
		this.dimension = dimension;
	}

	public String getRelatedMethodId() {
		return className + AnalysisUtils.ID_SEP + methodSignature;
	}

	@Override
	public int hashCode() {
		return className.hashCode() * methodSignature.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof SDLoop) {
			return ((SDLoop) obj).className.compareTo(className) == 0 &&
					((SDLoop) obj).methodSignature.compareTo(methodSignature) == 0 &&
					((SDLoop) obj).lineNumber == lineNumber &&
					((SDLoop) obj).dimension.equals(dimension);
		}
		return false;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("SDLoop [className=").append(className).append(", methodSignature=").append(methodSignature)
				.append(", lineNumber=").append(lineNumber).append(", dimension=").append(dimension).append("]");
		return builder.toString();
	}

}
