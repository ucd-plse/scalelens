package scaleview.agent.event;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import scaleview.agent.event.ProfilingEvent.LoopEntryEvent;
import scaleview.agent.event.ProfilingEvent.MethodEntryEvent;
import scaleview.agent.event.ProfilingEvent.MethodRegisterEvent;
import scaleview.agent.event.ProfilingEvent.SystemMethodRegisterEvent;
import scaleview.agent.util.Logging;

public class ProfilingEventParser {

	private static final Logger LOG = Logging.getLogger(ProfilingEventParser.class.getName());

	public static List<ProfilingEvent> parseEventFiles(List<String> folders, String filter) throws IOException {
		List<ProfilingEvent> events = new ArrayList<>();
		for (String folder : folders) {
			for (File file : new File(folder).listFiles()) {
				try {
					events.addAll(parseEventFile(file, filter));
				} catch (Exception e) {
					Logging.exception(LOG, e, "Exception when processing file [" + file.getAbsolutePath() + "]");
				}
			}
		}
		return events;
	}

	public static Map<String, List<ProfilingEvent>> parseEventFilesInParallel(List<String> folders, String... filters)
			throws IOException, InterruptedException {
		final Map<String, List<ProfilingEvent>> merged = new HashMap<>();
		for (String filterName : filters) {
			merged.put(filterName, new ArrayList<>());
		}
		for (String folder : folders) {
			int numFiles = new File(folder).listFiles().length;
			CountDownLatch latch = new CountDownLatch(numFiles);
			for (File file : new File(folder).listFiles()) {
				new Thread() {
					@Override
					public void run() {
						try {
							LOG.info("Going to parse file [" + file.getAbsolutePath() + "]");
							parseEventFiles(file.getAbsolutePath(), merged, latch, filters);
							LOG.info("Done merging events...");
						} catch (IOException e) {
							throw new RuntimeException(e);
						}
					}
				}.start();
			}
			latch.await(Integer.MAX_VALUE, TimeUnit.SECONDS);
		}
		return merged;
	}

	public static void parseEventFiles(String file, Map<String, List<ProfilingEvent>> merged, CountDownLatch latch,
			String... filters) throws IOException {
		File eventFile = new File(file);
		try {
			Map<String, List<ProfilingEvent>> theseOnes = parseAllEventsFromFile(eventFile, filters);
			synchronized (merged) {
				for (String key : theseOnes.keySet()) {
					merged.get(key).addAll(theseOnes.get(key));
				}
			}
			latch.countDown();
		} catch (Exception e) {
			Logging.exception(LOG, e, "Exception when processing file [" + eventFile.getAbsolutePath() + "]");
		}
	}

	public static List<ProfilingEvent> parseEventFiles(String file, String filter, CountDownLatch latch)
			throws IOException {
		List<ProfilingEvent> events = new ArrayList<>();
		File eventFile = new File(file);
		try {
			events.addAll(parseEventFile(eventFile, filter));
			latch.countDown();
		} catch (Exception e) {
			Logging.exception(LOG, e, "Exception when processing file [" + eventFile.getAbsolutePath() + "]");
		}
		return events;
	}

	private static Map<String, List<ProfilingEvent>> parseAllEventsFromFile(File eventFile, String... filters)
			throws IOException {
		Map<String, List<ProfilingEvent>> eventMap = new HashMap<>();
		for (String filterName : filters) {
			eventMap.put(filterName, new ArrayList<>());
		}
		FileReader rdr = null;
		BufferedReader brd = null;
		try {
			rdr = new FileReader(eventFile);
			brd = new BufferedReader(rdr, 1024 * 1024 * 250);
			String line;
			while ((line = brd.readLine()) != null) {
				try {
					ProfilingEvent e = parseFromLine(line);
					if (e != null) {
						eventMap.get(e.getFilterName()).add(e);
					}
				} catch (Exception e) {
					Logging.exception(LOG, e, "Exception processing line [" + line + "]");
				}
			}
			return eventMap;
		} catch (IOException e) {
			Logging.exception(LOG, e, "Could not read [" + eventFile.getAbsolutePath() + "]");
			return null;
		} finally {
			if (rdr != null)
				rdr.close();
			if (brd != null)
				brd.close();
		}
	}

	private static List<ProfilingEvent> parseEventFile(File eventFile, String filter) throws IOException {
		FileReader rdr = null;
		BufferedReader brd = null;
		List<ProfilingEvent> events = new ArrayList<>();
		try {
			rdr = new FileReader(eventFile);
			brd = new BufferedReader(rdr, 1024 * 1024 * 250);
			String line;
			while ((line = brd.readLine()) != null) {
				try {
					ProfilingEvent e = parseFromLine(line, filter);
					if (e != null) {
						events.add(e);
					}
				} catch (Exception e) {
					Logging.exception(LOG, e, "Exception processing line [" + line + "]");
				}
			}
			return events;
		} catch (IOException e) {
			Logging.exception(LOG, e, "Could not read [" + eventFile.getAbsolutePath() + "]");
			return null;
		} finally {
			if (rdr != null)
				rdr.close();
			if (brd != null)
				brd.close();
		}
	}

	public static void parseAndFixRegistrations(List<String> eventFolders, String outputFile) throws IOException {
		File output = new File(outputFile);
		PrintWriter pr = new PrintWriter(output);
		Map<String, MethodRegisterEvent> registrations = new HashMap<>();
		for (String folder : eventFolders) {
			File ff = new File(folder);
			parseAndFixRegistrations(ff, registrations, pr);
		}
		// and now print the regs
		for (MethodRegisterEvent e : registrations.values()) {
			pr.println(e.formatEvent().toString());
		}
		// done..
		pr.close();
	}

	private static void parseAndFixRegistrations(File eventFolder, Map<String, MethodRegisterEvent> registrations,
			PrintWriter merged) throws IOException {
		List<ProfilingEvent> methodRegistrations = parseEventFiles(Arrays.asList(eventFolder.getAbsolutePath()),
				MethodRegisterEvent.EVT_PROF);
		List<ProfilingEvent> loopEntries = parseEventFiles(Arrays.asList(eventFolder.getAbsolutePath()),
				LoopEntryEvent.EVT_PROF);
		Map<Long, Long> mapping = new HashMap<>();
		for (ProfilingEvent evt : methodRegistrations) {
			MethodRegisterEvent e = (MethodRegisterEvent) evt;
			if (!registrations.containsKey(e.getSignature())) {
				registrations.put(e.getSignature(), e);
				mapping.put(e.getMethodNum(), registrations.get(e.getSignature()).getMethodNum());
			} else {
				long methodNum = registrations.get(e.getSignature()).getMethodNum();
				mapping.put(methodNum, e.getMethodNum());
			}
		}
		// now fix the loop entries
		for (ProfilingEvent evt : loopEntries) {
			LoopEntryEvent e = (LoopEntryEvent) evt;
			long methodNum = e.getMethodId();
			if (mapping.containsKey(methodNum)) {
				e.resetMethodId(methodNum);
				merged.println(e.formatEvent().toString());
			}
		}
		// done, method registrations will go at the end...
	}

	private static ProfilingEvent parseFromLine(String line, String filter) {
		if (line.startsWith(filter)) {
			if (line.startsWith(MethodEntryEvent.EVT_PROF)) {
				return new MethodEntryEvent(-1).fromString(line);
			} else if (line.startsWith(MethodRegisterEvent.EVT_PROF)) {
				return new MethodRegisterEvent(-1).fromString(line);
			} else if (line.startsWith(LoopEntryEvent.EVT_PROF)) {
				return new LoopEntryEvent(-1).fromString(line);
			} else if (line.startsWith(SystemMethodRegisterEvent.EVT_PROF)) {
				return new SystemMethodRegisterEvent(-1).fromString(line);
			}
		}
		return null;
	}

	private static ProfilingEvent parseFromLine(String line) {
		if (line.startsWith(MethodEntryEvent.EVT_PROF)) {
			return new MethodEntryEvent(-1).fromString(line);
		} else if (line.startsWith(MethodRegisterEvent.EVT_PROF)) {
			return new MethodRegisterEvent(-1).fromString(line);
		} else if (line.startsWith(LoopEntryEvent.EVT_PROF)) {
			return new LoopEntryEvent(-1).fromString(line);
		} else if (line.startsWith(SystemMethodRegisterEvent.EVT_PROF)) {
			return new SystemMethodRegisterEvent(-1).fromString(line);
		}
		return null;
	}

}
