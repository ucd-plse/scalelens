package scaleview.agent.command;

import java.io.File;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Logger;

import scaleview.agent.event.ProfilingEvent;
import scaleview.agent.stub.ProfilingStub;
import scaleview.agent.util.AgentArgumentsParser;
import scaleview.agent.util.Logging;

public class CommandThread extends Thread {

	private static final Logger LOG = Logging.getLogger(CommandThread.class.getName());
	private static final String CMD_FILE_NAME = "command";

	private File commandDirectory;
	private File commandFile;
	private int sleepTime;
	private CommandProcessingRunnable task;

	public CommandThread(CommandProcessingRunnable t, CommandThreadConfig config) {
		super(t, "commandThread");
		task = t;
		File cmdDir = new File(config.getCommandDirectory());
		if (!cmdDir.exists()) {
			throw new IllegalArgumentException("[" + config.getCommandDirectory() + "] is not a valid directory");
		} else {
			commandDirectory = cmdDir;
			sleepTime = config.getSleepTime();
		}
		setDaemon(true);
	}

	public void shutdown() {
		LOG.severe("Shutting down...");
		task.keepRunning = false;
	}

	// just a place holder class
	public static class MethodDumpThread extends Thread {
	}

	public static void dumpMethods(final Map<Long, Map<Long, Integer>> methodEntryCounts,
			int runId, CountDownLatch latch) {
		// we will fire a new thread for this...
		MethodDumpThread dump = new MethodDumpThread() {
			@Override
			public void run() {
				try {
					// now we can write all the method entry events
					for (Entry<Long, Map<Long, Integer>> events : methodEntryCounts.entrySet()) {
						// I dont need to merge per thread now, but this events
						// should maybe be grouped by ID and NOT per thread
						long threadId = events.getKey();
						Map<Long, Integer> entries = events.getValue();
						for (Entry<Long, Integer> entry : entries.entrySet()) {
							ProfilingStub.METHOD_ENTRY.processEvent(
									new ProfilingEvent.MethodEntryEvent(threadId, entry.getKey(),
											runId, entry.getValue()));
						}
					}
				} catch (Exception e) {
					// THis is bad and has to be reported...
					Logging.exception(LOG, e, "Error while dumpning trace");
				} finally {
					if (latch != null)
						latch.countDown();
				}
			}
		};
		// and launch
		dump.start();
	}

	public static class CommandProcessingRunnable implements Runnable {

		private volatile boolean keepRunning = true;

		@Override
		public void run() {
			boolean shutdown = false;
			if (Thread.currentThread() instanceof CommandThread) {
				CommandThread thisThread = (CommandThread) Thread.currentThread();
				LOG.info("Starting command Thread [" + Thread.currentThread().getId() + "], directory=["
						+ thisThread.commandDirectory.getAbsolutePath() +
						"], sleepTime=" + thisThread.sleepTime + " ms");
				while (keepRunning) {
					try {
						String cmdFileName = thisThread.commandDirectory.getAbsolutePath() + File.separator
								+ CMD_FILE_NAME;
						LOG.info("Looking for file [" + cmdFileName + "]");
						thisThread.commandFile = new File(cmdFileName);
						if (thisThread.commandFile.exists()) {
							// process the command here...
							Command cmd = new Command(thisThread.commandFile);
							if (cmd.isShutdownCommand()) {
								// shutwdown this thread and everything...
								LOG.info("Shutting down system...");
								keepRunning = false;
								shutdown = true;
								break;
							} else if (cmd.isMeasureCommand()) {
								// get the required parameters
								Integer run = cmd.getInt(Command.HM_RUN_PROP);
								Boolean gcBefore = cmd.getBoolean(Command.HM_GCB_PROP);
								Boolean enableLoopProfiling = cmd.getBoolean(Command.HM_ELP_PROP);
								if (run == null || gcBefore == null || enableLoopProfiling == null) {
									LOG.severe("Measurement was requested without properties. Add [" +
											Command.HM_RUN_PROP + " (int) and " + Command.HM_GCB_PROP
											+ " (boolean) to the command file.");
								} else {
									// switch first
									final Map<Long, Map<Long, Integer>> methodEntryCounts = ProfilingStub
											.switchMethodExectionData();
									ProfilingStub.LE_ENABLED.set(enableLoopProfiling);
									ProfilingStub.CURRENT_RUN.set(run);
									// and now dump all methods...
									dumpMethods(methodEntryCounts, run - 1, null);

								}
							} else {
								LOG.info("Did not recognize [" + cmd.getCmd() + "] as a command...");
							}
						} else {
							LOG.info("No command was found...");
							thisThread.commandFile = null;
						}
						// wait here...
						LOG.info("Sleeping [" + thisThread.sleepTime + "] ms");
						try {
							Thread.sleep(thisThread.sleepTime);
						} catch (Exception e) {
							Logging.exception(LOG, e, "Interrupted while sleeping!");
							break;
						}

					} catch (Exception e) {
						Logging.exception(LOG, e, "Exception when processing command file");
					} finally {
						if (thisThread.commandFile != null && thisThread.commandFile.exists()) {
							try {
								boolean del = thisThread.commandFile.delete();
								LOG.info("Deleted command file=" + del);
							} catch (Throwable t) {
								Logging.exception(LOG, t, "Exception when deleting command file");
							}
						}
						thisThread.commandFile = null;
					}
				}
			} else {
				LOG.severe("This thread is not an instance of CommandThread, exiting...");
				return;
			}
			LOG.severe("Command Thread exiting...");
			if (shutdown) {
				LOG.severe("Shutting down system due to shut down command...");
				System.exit(0);
			}
		}

	}

	public static class CommandThreadConfig {

		private String commandDirectory;
		private int sleepTime;

		public CommandThreadConfig(String whole) {
			// break params
			String[] ps = whole.split(AgentArgumentsParser.ARG_ISEP);
			// foe each one...
			for (String p : ps) {
				String[] v = p.split(AgentArgumentsParser.ARG_TSEP);
				if (v.length == 2) {
					switch (v[0].trim()) {
						case "commandDirectory":
							commandDirectory = v[1].trim();
							break;
						case "sleepTime":
							sleepTime = Integer.valueOf(v[1]);
							break;
					}
				}
			}
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("CommandThreadConfig [commandDirectory=").append(commandDirectory).append(", sleepTime=")
					.append(sleepTime).append("]");
			return builder.toString();
		}

		public String getCommandDirectory() {
			return commandDirectory;
		}

		public int getSleepTime() {
			return sleepTime;
		}

	}
}
