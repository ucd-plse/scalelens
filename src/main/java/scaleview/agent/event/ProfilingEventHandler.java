package scaleview.agent.event;

import java.io.File;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Logger;

import scaleview.agent.command.CommandThread;
import scaleview.agent.util.AgentArgumentsParser;
import scaleview.agent.util.Logging;

public class ProfilingEventHandler {

	private static final Logger LOG = Logging.getLogger(ProfilingEventHandler.class.getName());

	private ProfilerEventHandlerConfig config;
	private File outputDirectory;;
	private ThreadFactory factory;
	private ThreadPoolExecutor executor;
	private List<Writer> openStreams;

	public ProfilingEventHandler(ProfilerEventHandlerConfig c) {
		config = c;
		if (config.isPersistent()) {
			outputDirectory = new File(config.getOutputDirectory());
			if (!outputDirectory.isDirectory()) {
				throw new IllegalArgumentException("[" + config.getOutputDirectory() + "] is not a valid directory");
			}
		}
		// store open streams
		openStreams = new ArrayList<>();
		// build the thread factory
		if (config.isPersistent()) {
			factory = new ProfilingThread.ProfilingThreadFactory(config.getName(), outputDirectory, openStreams, null,
					c.getFlushThreshold(), c.isEnabledFromStart());
		} else {
			// in this case, we need an specific type of writer
			factory = new ProfilingThread.ProfilingThreadFactory(config.getName(), outputDirectory, openStreams,
					config.getWriterClass(), c.getFlushThreshold());
		}
		// and create the thread pool
		executor = new ThreadPoolExecutor(config
				.getCoreThreads(), config.getMaxThreads(), 60, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(),
				factory) /*
							 * {
							 * 
							 * @Override
							 * protected void afterExecute(Runnable r, Throwable t) {
							 * super.afterExecute(r, t);
							 * if (t == null && r instanceof Future<?>) {
							 * try {
							 * Object result = ((Future<?>) r).get();
							 * }
							 * catch (CancellationException e) {
							 * t = e;
							 * }
							 * catch (ExecutionException e) {
							 * t = e.getCause();
							 * }
							 * catch (InterruptedException e) {
							 * t = e;
							 * }
							 * }
							 * if (t != null) Logging.exception(LOG, t, "Could not execute!");
							 * }
							 * }
							 */;
		// this should be it...
	}

	public <T> CompletableFuture<T> processCompletableEvent(final ProfilingEvent e)
			throws InterruptedException, ExecutionException {
		try {
			return CompletableFuture.supplyAsync(new InternalSupplier<T>(e.asProfilingTask()), executor);
		} catch (Exception ex) {
			Logging.exception(LOG, ex, "Execution rejected!");
			throw new RuntimeException(ex);
		}
	}

	public <T> Future<T> processEvent(ProfilingEvent e) throws InterruptedException, ExecutionException {
		if (filterEvent(e)) {
			try {
				return executor.submit(e.asProfilingTask());
			} catch (Exception ex) {
				LOG.severe("Execution rejected!: " + ex.getStackTrace().toString());
				return null;
			}
		}
		return null;
	}

	public <T> Future<T> processEventNoFilter(ProfilingEvent e) throws InterruptedException, ExecutionException {
		try {
			return executor.submit(e.asProfilingTask());
		} catch (Exception ex) {
			LOG.severe("Execution rejected!: " + ex.getStackTrace().toString());
			return null;
		}
	}

	private boolean filterEvent(ProfilingEvent e) {
		// filter by thread first...
		if (Thread.currentThread() instanceof ProfilingThread)
			return false;
		if (Thread.currentThread() instanceof CommandThread)
			return false;
		return true;
	}

	public void shutdown() throws InterruptedException {
		if (config.waitsForShutdown())
			shutdownBlocking();
		shutdownNonBlocking();
	}

	public void closeStreams() {
		for (Writer w : openStreams) {
			try {
				w.close();
			} catch (Exception e) {
			}
		}
	}

	public void flushStreams() {
		for (Writer w : openStreams) {
			try {
				w.flush();
			} catch (Exception e) {
				Logging.exception(LOG, e, "Execution rejected!");
			}
		}
	}

	private void shutdownBlocking() throws InterruptedException {
		executor.shutdown();
		try {
			if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
				executor.shutdownNow();
			}
		} catch (InterruptedException ex) {
			executor.shutdownNow();
			Thread.currentThread().interrupt();
		}
		closeStreams();

	}

	private void shutdownNonBlocking() {
		executor.shutdownNow();
		closeStreams();
	}

	public static final class ProfilerEventHandlerConfig {

		private String name;
		private int coreThreads = Runtime.getRuntime().availableProcessors();
		private int maxThreads = Runtime.getRuntime().availableProcessors();
		private String outputDirectory = "/tmp";
		private boolean blocks = false;
		private boolean persistent = true;
		private String writerClass = null;
		private int flushThreshold = 1000;
		private List<String> inclusions = new ArrayList<>();
		private List<String> exclusions = new ArrayList<>();
		private boolean enabledFromStart = false;

		public ProfilerEventHandlerConfig(String whole) {
			// break params
			String[] ps = whole.split(AgentArgumentsParser.ARG_ISEP);
			// foe each one...
			for (String p : ps) {
				String[] v = p.split(AgentArgumentsParser.ARG_TSEP);
				if (v.length == 2) {
					switch (v[0].trim()) {
						case "name":
							name = v[1].toLowerCase();
							break;
						case "coreThreads":
							coreThreads = Integer.valueOf(v[1]);
							break;
						case "maxThreads":
							maxThreads = Integer.valueOf(v[1]);
							break;
						case "outputDirectory":
							outputDirectory = v[1].trim();
							break;
						case "waitWhenShutDown":
							blocks = Boolean.valueOf(v[1]);
							break;
						case "persistent":
							persistent = Boolean.valueOf(v[1]);
							break;
						case "writerClassName":
							writerClass = v[1].trim();
							break;
						case "flushThreshold":
							flushThreshold = Integer.valueOf(v[1]);
							break;
						case "inclusions":
							String[] packages = v[1].split("#");
							for (String s : packages)
								inclusions.add(s);
							break;
						case "exclusions":
							String[] epackages = v[1].split("#");
							for (String s : epackages)
								exclusions.add(s);
							break;
						case "enabledFromStart":
							enabledFromStart = Boolean.valueOf(v[1]);
							break;
					}
				}
			}
			// make these two equal
			maxThreads = coreThreads;
		}

		public String getName() {
			return name;
		}

		public int getCoreThreads() {
			return coreThreads;
		}

		public int getMaxThreads() {
			return maxThreads;
		}

		public String getOutputDirectory() {
			return outputDirectory;
		}

		public boolean waitsForShutdown() {
			return blocks;
		}

		public boolean isPersistent() {
			return persistent;
		}

		public String getWriterClass() {
			return writerClass;
		}

		public int getFlushThreshold() {
			return flushThreshold;
		}

		public List<String> getInclusions() {
			return inclusions;
		}

		public List<String> getExclusions() {
			return exclusions;
		}

		public boolean isEnabledFromStart() {
			return enabledFromStart;
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("ProfilerEventHandlerConfig [name=").append(name).append(", coreThreads=")
					.append(coreThreads).append(", maxThreads=").append(maxThreads).append(", outputDirectory=")
					.append(outputDirectory).append(", blocks=").append(blocks).append(", persistent=")
					.append(persistent).append(", writerClass=").append(writerClass).append(", flushThreshold=")
					.append(flushThreshold).append(", inclusions=").append(inclusions).append("]")
					.append(", exclusions=").append(exclusions).append("]");
			return builder.toString();
		}

	}

	// just for internal use
	private static final class InternalSupplier<T> implements Supplier<T> {

		private ProfilingTask<T> task;

		InternalSupplier(ProfilingTask<T> t) {
			task = t;
		}

		@Override
		public T get() {
			try {
				return task.call();
			} catch (Exception e) {

				return null;
			}
		}

	}
}
