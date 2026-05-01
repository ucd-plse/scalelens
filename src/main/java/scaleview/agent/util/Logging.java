package scaleview.agent.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public class Logging {

	private static final String LOG_FILE_PROP = "scaleview.logFile";

	// init the configuration in a static block...
	static {
		String logConfigurationFile = System.getProperty(LOG_FILE_PROP);
		// no configuration available...
		if (logConfigurationFile != null) {
			try {
				File configurationFile = new File(logConfigurationFile);
				if (configurationFile.exists()) {
					LogManager.getLogManager().readConfiguration(new FileInputStream(configurationFile));
				} else {
					System.err.println(
							"Log conf. [ " + logConfigurationFile + "] file was not found, defaulting to sys.err...");
				}
			} catch (IOException e) {
				System.err.println("IOException when configuring logging");
				e.printStackTrace(System.err);
			}
		}
	}

	public static void exception(Logger log, Exception e, String message) {
		log.severe(new StringBuilder("[").append(e.getClass().getName()).append("]")
				.append(" [").append(e.getMessage()).append("] ")
				.append(message).toString());
		int count = 0;
		for (StackTraceElement x : e.getStackTrace()) {
			if (count > 5)
				break;
			log.severe(new StringBuilder().append("at ").append(x.getClassName()).append(" ")
					.append(x.getMethodName()).append(" (").append(x.getLineNumber()).append(")").toString());
			++count;
		}
	}

	public static void exception(Logger log, Throwable e, String message) {
		log.severe(new StringBuilder("[").append(e.getClass().getName()).append("]")
				.append(" [").append(e.getMessage()).append("] ")
				.append(message).toString());
		int count = 0;
		for (StackTraceElement x : e.getStackTrace()) {
			if (count > 5)
				break;
			log.severe(new StringBuilder().append("at ").append(x.getClassName()).append(" ")
					.append(x.getMethodName()).append(" (").append(x.getLineNumber()).append(")").toString());
			++count;
		}
	}

	public static Logger getLogger(String className) {
		return Logger.getLogger(className);
	}

	public static class LogFormatter extends Formatter {

		SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

		@Override
		public String format(LogRecord record) {
			return new StringBuilder().append("[")
					.append(fmt.format(new Date(record.getMillis())))
					.append("] [").append(Thread.currentThread().getName()).append(" (")
					.append(Thread.currentThread().getId()).append(")")
					.append("] [").append(record.getLevel()).append("] [")
					.append(record.getLoggerName().lastIndexOf(".") != -1 ? record.getLoggerName()
							.substring(record.getLoggerName().lastIndexOf(".") + 1, record.getLoggerName().length())
							: record.getLoggerName())
					.append("] ")
					.append(record.getMessage()).append("\n").toString();
		}

	}

}
