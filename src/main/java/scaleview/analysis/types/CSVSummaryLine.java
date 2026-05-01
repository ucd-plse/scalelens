package scaleview.analysis.types;

public class CSVSummaryLine implements Comparable<CSVSummaryLine> {

	public static final String L_TYPE = "Loop";
	public static final String C_TYPE = "Cross";

	private String dimension;
	private String identifier;
	private int lineNumber;
	private int executionPercentage;
	private double slope;
	private String loopType;

	public CSVSummaryLine(String dimension, String identifier, int lineNumber, double slope) {
		this.dimension = dimension;
		this.identifier = identifier;
		this.lineNumber = lineNumber;
		this.slope = slope;
	}

	public CSVSummaryLine(String dimension, String identifier, int lineNumber, int executionPercentage, double slope) {
		this.dimension = dimension;
		this.identifier = identifier;
		this.lineNumber = lineNumber;
		this.executionPercentage = executionPercentage;
		this.slope = slope;
	}

	public CSVSummaryLine(String identifier, int lineNumber, String type) {
		this.identifier = identifier;
		this.lineNumber = lineNumber;
		this.loopType = type;
	}

	public String getDimension() {
		return dimension;
	}

	public String getIdentifier() {
		return identifier;
	}

	public int getLineNumber() {
		return lineNumber;
	}

	public int getExecutionPercentage() {
		return executionPercentage;
	}

	public double getSlope() {
		return slope;
	}

	public String getLoopType() {
		return loopType;
	}

	public static CSVSummaryLine fromString(String line, String separator, String[] idFilter) {
		String[] elements = line.split(separator);
		if (elements.length > 0) {
			if (elements.length > 3) {
				String d = elements[0];
				String i = elements[5];
				String l = elements[6];
				String p = elements.length > 7 ? elements[7] : "0";
				String s = elements[1];
				for (String filter : idFilter) {
					if (i.startsWith(filter)) {
						return new CSVSummaryLine(d, i, Integer.valueOf(l), Integer.valueOf(p), Double.valueOf(s));
					}
				}
			} else if (elements.length == 3) {
				String i = elements[0];
				String l = elements[1];
				String t = elements[2];
				for (String filter : idFilter) {
					if (i.startsWith(filter)) {
						return new CSVSummaryLine(i, Integer.valueOf(l), t);
					}
				}
			}
		}
		return null;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("CSVSummaryLine [dimension=").append(dimension).append(", identifier=").append(identifier)
				.append(", lineNumber=").append(lineNumber).append(", executionPercentage=").append(executionPercentage)
				.append(", slope=").append(slope).append(", loopType=").append(loopType).append("]");
		return builder.toString();
	}

	@Override
	public int compareTo(CSVSummaryLine arg0) {
		int idComp = identifier.compareTo(arg0.identifier);
		if (idComp == 0) {
			return lineNumber - arg0.lineNumber;
		}
		return idComp;
	}

	@Override
	public int hashCode() {
		return lineNumber;
	}

	@Override
	public boolean equals(Object o) {
		CSVSummaryLine other = (CSVSummaryLine) o;
		return compareTo(other) == 0;

	}
}
