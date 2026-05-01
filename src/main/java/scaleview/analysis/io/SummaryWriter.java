package scaleview.analysis.io;

import java.io.PrintWriter;
import java.io.IOException;
import java.util.Collection;

public abstract class SummaryWriter<T> {

	protected PrintWriter writer;

	public SummaryWriter(PrintWriter writer) {
		this.writer = writer;
	}

	public void closeWriter() throws IOException {
		writer.close();
	}

	public abstract void writeSummary(Collection<T> summary) throws IOException;

}
