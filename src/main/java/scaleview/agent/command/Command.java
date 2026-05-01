package scaleview.agent.command;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class Command {

	private static final String CMD_PROP = "cmd";
	private static final String CMD_PROF_LE = "heapMeasure";
	private static final String CMD_PROF_SD = "shutdown";
	public static final String HM_RUN_PROP = "runId";
	public static final String HM_GCB_PROP = "gcBefore";
	public static final String HM_ELP_PROP = "enableLoopProfiling";

	private Properties values;

	public Command(File commandFile) throws IOException {
		if (commandFile.exists()) {
			values = new Properties();
			values.load(new FileInputStream(commandFile));
			if (values.get(CMD_PROP) == null) {
				throw new IllegalArgumentException(
						"File [" + commandFile.getAbsolutePath() + "] does not contain command (" + CMD_PROP + ")");
			}
		} else {
			throw new IllegalArgumentException("File [" + commandFile.getAbsolutePath() + "] does not exist!");
		}
	}

	public String getCmd() {
		if (values.containsKey(CMD_PROP)) {
			return values.getProperty(CMD_PROP);
		}
		return null;
	}

	public Integer getInt(String name) {
		if (values.containsKey(name)) {
			return Integer.valueOf(values.getProperty(name));
		}
		return null;
	}

	public Boolean getBoolean(String name) {
		if (values.containsKey(name)) {
			return Boolean.valueOf(values.getProperty(name));
		}
		return null;
	}

	public boolean isShutdownCommand() {
		return ((String) values.get(CMD_PROP)).compareTo(CMD_PROF_SD) == 0;
	}

	public boolean isMeasureCommand() {
		return ((String) values.get(CMD_PROP)).compareTo(CMD_PROF_LE) == 0;
	}

}
