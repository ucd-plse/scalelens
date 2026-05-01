package scaleview.agent.event;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.List;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Logger;

import scaleview.agent.util.Logging;

public class ProfilingThread extends Thread {

	private static final Logger LOG = Logging.getLogger(ProfilingThread.class.getName());

	private Writer writer;
	private int eventCount;
	private int flushThreashold;

	public ProfilingThread(Runnable task, String name, int ft) {
		super(task, name);
		flushThreashold = ft;
		eventCount = 0;
		setDaemon(true);

	}

	public void setWriter(Writer w) {
		writer = w;
	}

	public Writer getWriter() {
		return writer;
	}

	public void checkForFlush() {
		++eventCount;
		if (flushThreashold <= eventCount) {
			eventCount = 0;
			try {
				writer.flush();
			} catch (Exception e) {
				Logging.exception(LOG, e, "Exception when flushing...");
			}
		}
	}

	public static final class ProfilingThreadFactory implements ThreadFactory {

		private int index = 0;
		private String poolName;
		private File outputDirectory;
		private List<Writer> openStreams;
		private String writerClass;
		private int flushThreashold;
		private boolean fromStart = false;

		public ProfilingThreadFactory(String pname, File opath, List<Writer> o, String w, int ft) {
			poolName = pname;
			outputDirectory = opath;
			openStreams = o;
			writerClass = w;
			flushThreashold = ft;
		}

		public ProfilingThreadFactory(String pname, File opath, List<Writer> o, String w, int ft, boolean start) {
			poolName = pname;
			outputDirectory = opath;
			openStreams = o;
			writerClass = w;
			flushThreashold = ft;
			fromStart = true;
		}

		private String newThreadName(int index) {
			return poolName + "-" + index;
		}

		private Writer newWriter(int index) throws IOException {
			if (writerClass == null) {
				if (fromStart) {
					return new PrintWriter(
							new FileWriter(outputDirectory.getAbsolutePath() + File.separator + index, true));
				} else {
					return new PrintWriter(new File(outputDirectory.getAbsolutePath() + File.separator + index));
				}

			} else {
				try {
					return (Writer) Class.forName(writerClass).newInstance();
				} catch (Exception e) {
					throw new IOException(e);
				}
			}
		}

		@Override
		public Thread newThread(Runnable task) {
			int i = ++index;
			ProfilingThread t = new ProfilingThread(task, newThreadName(i), flushThreashold);
			try {
				Writer w = newWriter(i);
				t.setWriter(w);
				openStreams.add(w);
			} catch (IOException e) {
				throw new IllegalArgumentException(e);
			}
			return t;
		}
	}

}
