package scaleview.analysis.types;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.annotations.SerializedName;

import scaleview.analysis.utils.AnalysisUtils;

public class AppMethod {

	@SerializedName("className")
	private String className;
	@SerializedName("methodSignature")
	private String methodSignature;
	@SerializedName("dimensions")
	private Map<Dimension, Integer> dimensions = new HashMap<>();
	@SerializedName("lineNumber")
	private int lineNumber;

	public String getClassName() {
		return className;
	}

	public String getMethodSignature() {
		return methodSignature;
	}

	public void setClassName(String className) {
		this.className = className;
	}

	public void setMethodSignature(String methodSignature) {
		this.methodSignature = methodSignature;
	}

	public Map<Dimension, Integer> getDimensions() {
		return dimensions;
	}

	public void setDimensions(Map<Dimension, Integer> dimensions) {
		this.dimensions = dimensions;
	}

	public int getLineNumber() {
		return lineNumber;
	}

	public void setLineNumber(int lineNumber) {
		this.lineNumber = lineNumber;
	}

	@Override
	public int hashCode() {
		return AnalysisUtils.getMethodId(className, methodSignature).hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof AppMethod) {
			return ((AppMethod) obj).className.compareTo(className) == 0
					&& ((AppMethod) obj).methodSignature.compareTo(methodSignature) == 0;
		}
		return false;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("AppMethod [className=").append(className).append(", methodSignature=").append(methodSignature)
				.append(", dimensions=").append(dimensions).append(", lineNumber=").append(lineNumber).append("]");
		return builder.toString();
	}

}
