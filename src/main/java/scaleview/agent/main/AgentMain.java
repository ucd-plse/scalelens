package scaleview.agent.main;

import java.lang.instrument.Instrumentation;
import java.util.Map;
import java.util.logging.Logger;

import scaleview.agent.command.CommandThread;
import scaleview.agent.event.ProfilingEventHandler;
import scaleview.agent.transformer.LoopTransformer;
import scaleview.agent.transformer.MethodTransformer;
import scaleview.agent.stub.ProfilingStub;
import scaleview.agent.util.AgentArgumentsParser;
import scaleview.agent.util.Logging;

public class AgentMain {

	private static final Logger LOG = Logging.getLogger(AgentMain.class.getName());

	static {
		// make sure every class is loaded
		ProfilingEventHandler.class.getName();
		ProfilingStub.class.getName();
	}

	public static void premain(String agentArgs, Instrumentation inst) {
		Thread.currentThread().setName("ProfilerMain");
		LOG.info("Initializing agent, configuration is [" + agentArgs + "]");
		// parse here...
		Map<String, ProfilingEventHandler.ProfilerEventHandlerConfig> configs = AgentArgumentsParser
				.getProfilers(agentArgs);
		for (ProfilingEventHandler.ProfilerEventHandlerConfig c : configs.values()) {
			LOG.info(c.toString());
		}
		// now fill the stub...
		if (configs.containsKey(AgentArgumentsParser.ME_HAND)) {
			LOG.info("Adding [" + AgentArgumentsParser.ME_HAND + "] handler");
			ProfilingEventHandler.ProfilerEventHandlerConfig meConf = configs.get(AgentArgumentsParser.ME_HAND);
			ProfilingStub.METHOD_ENTRY = new ProfilingEventHandler(meConf);
			if (meConf.isEnabledFromStart())
				ProfilingStub.LE_ENABLED.set(true);
			ProfilingStub.INCLUSIONS.addAll(meConf.getInclusions());
			ProfilingStub.EXCLUSIONS.addAll(meConf.getExclusions());

			// and add the transformer
			inst.addTransformer(new MethodTransformer(ProfilingStub.EXCLUSIONS, ProfilingStub.INCLUSIONS), true);
		}
		// now fill the stub...
		if (configs.containsKey(AgentArgumentsParser.LOOP_HAND)) {
			LOG.info("Adding [" + AgentArgumentsParser.LOOP_HAND + "] handler");
			ProfilingEventHandler.ProfilerEventHandlerConfig meConf = configs.get(AgentArgumentsParser.LOOP_HAND);
			ProfilingStub.INCLUSIONS.addAll(meConf.getInclusions());
			ProfilingStub.EXCLUSIONS.addAll(meConf.getExclusions());

			// and add the transformer
			inst.addTransformer(new LoopTransformer(ProfilingStub.EXCLUSIONS, ProfilingStub.INCLUSIONS), true);
		}
		// Store the instrumentation
		ProfilingStub.INSTRUMENTATION = inst;
		// the command thread configuration
		CommandThread.CommandThreadConfig cmd = AgentArgumentsParser.getCommandThreadConfig(agentArgs);
		if (cmd == null) {
			LOG.info("Command Thread configuration is not present, so not thread will be started...");
		} else {
			LOG.info("Starting cmd thread...");
			LOG.info(cmd.toString());
			ProfilingStub.CMD_THREAD = new CommandThread(new CommandThread.CommandProcessingRunnable(), cmd);
			ProfilingStub.CMD_THREAD.start();
		}

		if (ProfilingStub.METHOD_ENTRY != null) {
			// add a shutdownhook, instance of this guy since i dont want it submitting
			// events
			Runtime.getRuntime().addShutdownHook(ProfilingStub.buildShutdownHookThread());
		}
		// and done...
	}

}
