package scaleview.analysis.io;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import scaleview.analysis.types.SDLoop;

public class CSVSummaryWriter extends SummaryWriter<SDLoop> {

	private static final String SEPARATOR = ",";

	private String dataPath;
	private String columnMappingFile = null;

	public CSVSummaryWriter(PrintWriter writer, String dataPath, String columnMappingFile) {
		super(writer);
		this.dataPath = dataPath;
		this.columnMappingFile = columnMappingFile;
	}

	private Map<Integer, String> parseColumnMap(String file, int numColumns) throws IOException {
		Map<Integer, String> mappedColumns = new TreeMap<>();
		if (file != null) {
			File f = new File(file);
			BufferedReader rdr = new BufferedReader(new FileReader(f));
			String line = null;
			while ((line = rdr.readLine()) != null) {
				String[] p = line.split(SEPARATOR);
				int id = Integer.valueOf(p[0]);
				StringBuilder rest = new StringBuilder();
				for (int i = 1; i < p.length; ++i) {
					rest.append(p[i]);
					if (i != p.length - 1)
						rest.append(SEPARATOR);
				}
				mappedColumns.put(id, rest.toString());
			}
			rdr.close();
		} else {
			for (int i = 0; i <= numColumns; ++i) {
				mappedColumns.put(i, String.valueOf(i));
			}
		}
		return mappedColumns;
	}

	@Override
	public void writeSummary(Collection<SDLoop> summary) throws IOException {
		Map<Integer, String> cols = parseColumnMap(columnMappingFile, getNumColumns(summary));
		writeHeader();
		// here we just write the whole csv
		for (SDLoop loop : summary) {
			writeLoop(loop);
			String gd = dataPath + File.separator + loop.getClassName() + "."
					+ loop.getMethodSignature().substring(0, loop.getMethodSignature().indexOf("(")) + "."
					+ loop.getLineNumber() + ".dat";
			String rd = dataPath + File.separator + loop.getClassName() + "."
					+ loop.getMethodSignature().substring(0, loop.getMethodSignature().indexOf("(")) + "."
					+ loop.getLineNumber() + ".raw";
			if (loop.getDimension().getGrowthPoints() != null) {
				writeDataFile(gd, loop.getDimension().getGrowthPoints(), cols);
			}
			if (loop.getDimension().getRawPoints() != null) {
				writeDataFile(rd, loop.getDimension().getRawPoints(), cols);
			}
		}
		closeWriter();
	}

	private int getNumColumns(Collection<SDLoop> summary) {
		for (SDLoop loop : summary) {
			if (loop.getDimension() != null && loop.getDimension().getRawPoints() != null)
				return loop.getDimension().getRawPoints().size();
		}
		return 0;
	}

	private void writeDataFile(String fileName, Map<Double, Double> values, Map<Integer, String> columnMapping)
			throws FileNotFoundException {
		File newDataFile = new File(fileName);
		PrintWriter pr = new PrintWriter(newDataFile);
		for (Entry<Double, Double> v : values.entrySet()) {
			pr.println(String.format("%s%s%d", columnMapping.get((v.getKey().intValue())), SEPARATOR,
					v.getValue().intValue()));
		}
		pr.close();
	}

	private void writeHeader() {
		StringBuilder line = new StringBuilder();
		line.append("Dimension");
		line.append(SEPARATOR);
		line.append("SpearmanCorrelation");
		line.append(SEPARATOR);
		line.append("Method");
		line.append(SEPARATOR);
		line.append("Line Number");
		writer.println(line.toString());
	}

	private void writeLoop(SDLoop loop) {
		StringBuilder line = new StringBuilder();
		line.append(loop.getDimension().getName());
		line.append(SEPARATOR);
		line.append(String.format("%.5f", loop.getDimension().getSpearmanCorrelation()));
		line.append(SEPARATOR);
		line.append(loop.getClassName() + "."
				+ loop.getMethodSignature().substring(0, loop.getMethodSignature().indexOf("(")));
		line.append(SEPARATOR);
		line.append(loop.getLineNumber());
		writer.println(line.toString());
	}

}
