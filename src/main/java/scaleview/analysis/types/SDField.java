package scaleview.analysis.types;

import com.google.gson.annotations.SerializedName;

import scaleview.analysis.utils.AnalysisUtils;
import spoon.reflect.declaration.CtField;

public class SDField {

	@SerializedName("className")
	private String className;
	@SerializedName("fieldName")
	private String fieldName;
	@SerializedName("dimension")
	private Dimension dimension;

	public String getClassName() {
		return className;
	}

	public void setClassName(String className) {
		this.className = className;
	}

	public String getFieldName() {
		return fieldName;
	}

	public void setFieldName(String fieldName) {
		this.fieldName = fieldName;
	}

	public Dimension getDimension() {
		return dimension;
	}

	public void setDimension(Dimension dimension) {
		this.dimension = dimension;
	}

	public String getRelatedFieldId() {
		return className + AnalysisUtils.ID_SEP_FIELD + fieldName;
	}

	public SDField() {
	}

	public SDField(CtField field, String declaringClassName) {
		this.className = declaringClassName;
		this.fieldName = field.getSimpleName();
	}

	public static String getRelatedFieldId(String klassName, String name) {
		return klassName + AnalysisUtils.ID_SEP_FIELD + name;
	}

	@Override
	public int hashCode() {
		return className.hashCode() * fieldName.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof SDField) {
			return ((SDField) obj).className.compareTo(className) == 0 &&
					((SDField) obj).fieldName.compareTo(fieldName) == 0 &&
					((SDField) obj).dimension.equals(dimension);
		}
		return false;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("SDField [className=").append(className).append(", fieldName=").append(fieldName)
				.append(", dimension=").append(dimension).append("]");
		return builder.toString();
	}

}
