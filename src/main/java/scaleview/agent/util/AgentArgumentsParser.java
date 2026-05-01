package scaleview.agent.util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import scaleview.agent.command.CommandThread;
import scaleview.agent.event.ProfilingEventHandler;

public class AgentArgumentsParser {

	public static final String ARG_LSEP = ";";
	public static final String ARG_VSEP = ":";
	public static final String ARG_ISEP = ",";
	public static final String ARG_TSEP = "=";
	public static final String HAND_OPT = "profiler";
	public static final String EX_OPT = "exclusions";
	public static final String ME_HAND = "methods";
	public static final String MS_HAND = "heapmeasure";
	public static final String CMD_OPT = "commandThread";
	public static final String LOOP_HAND = "loops";

	public static Map<String, ProfilingEventHandler.ProfilerEventHandlerConfig> getProfilers(String wholeConfig) {
		Map<String, ProfilingEventHandler.ProfilerEventHandlerConfig> handlers = new HashMap<>();
		String[] pieces = wholeConfig.split(ARG_LSEP);
		for (String piece : pieces) {
			String[] conf = piece.split(ARG_VSEP);
			if (conf.length == 2 && conf[0].compareToIgnoreCase(HAND_OPT) == 0) {
				ProfilingEventHandler.ProfilerEventHandlerConfig h = new ProfilingEventHandler.ProfilerEventHandlerConfig(
						conf[1]);
				handlers.put(h.getName(), h);
			}
		}
		return handlers;
	}

	public static Set<String> getExclusions(String wholeConfig) {
		Set<String> ex = new HashSet<>();
		String[] pieces = wholeConfig.split(ARG_LSEP);
		for (String piece : pieces) {
			String[] conf = piece.split(ARG_VSEP);
			if (conf.length == 2 && conf[0].compareToIgnoreCase(EX_OPT) == 0) {
				String[] allExclusions = conf[1].split(ARG_ISEP);
				for (String e : allExclusions)
					ex.add(e);
			}
		}
		return ex;
	}

	public static CommandThread.CommandThreadConfig getCommandThreadConfig(String wholeConfig) {
		String[] pieces = wholeConfig.split(ARG_LSEP);
		for (String piece : pieces) {
			String[] conf = piece.split(ARG_VSEP);
			if (conf.length == 2 && conf[0].compareToIgnoreCase(CMD_OPT) == 0) {
				return new CommandThread.CommandThreadConfig(conf[1]);
			}
		}
		return null;
	}

}
