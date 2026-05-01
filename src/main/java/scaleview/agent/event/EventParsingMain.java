package scaleview.agent.event;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.logging.Logger;

import org.apache.commons.math3.stat.correlation.SpearmansCorrelation;

import scaleview.analysis.io.CSVSummaryWriter;
import scaleview.analysis.types.Dimension;
import scaleview.analysis.types.SDLoop;
import scaleview.analysis.types.SortedMultiMap;
import scaleview.agent.event.ProfilingEvent.LoopEntryEvent;
import scaleview.agent.event.ProfilingEvent.LoopEntryEvent.CollapsedLoop;
import scaleview.agent.event.ProfilingEvent.LoopEntryEvent.LoopEntryEventId;
import scaleview.agent.event.ProfilingEvent.MethodEntryEvent;
import scaleview.agent.event.ProfilingEvent.MethodRegisterEvent;
import scaleview.agent.event.ProfilingEvent.SystemMethodRegisterEvent;
import scaleview.agent.util.Logging;

public class EventParsingMain {

	private static final Logger LOG = Logging.getLogger(EventParsingMain.class.getName());

	private static final String STATIC_INIT_NAME = "<clinit>";
	private static final String INIT_NAME = "<init>";
	private static final double LOW_CORRELATION = 0.8;
	private static final double LEAST_FOR_GROWTH_LOOP_PER = 0.25;
	private static final int LEAST_FOR_GROWTH_LOOP_POINTS = 2;
	private static final double FIRST_PART = 0.25;
	private static final double OTHER = -2;
	private static final double TOO_SMALL = -3;
	private static final double NO_DP = -4;
	private static final double NO_G_BEYOND = -5;

	public static void main(String... args) throws IOException, InterruptedException {
		long start = System.currentTimeMillis();
		Map<Long, MethodRegisterEvent> methods = new HashMap<>();
		Map<Long, SystemMethodRegisterEvent> systemMethods = new HashMap<>();
		try {
			String[] folders = args[0].split(",");
			String filter = args[1];
			String dimension = args[2];
			int limit = Integer.valueOf(args[3]);
			String mappingFile = args[4];

			// check the mapping file too
			if (!new File(mappingFile).exists()) {
				LOG.info("There is no mapping file, using default");
				mappingFile = null;
			}

			LOG.info("Parsing folders [" + Arrays.toString(folders) + "], filter=" + filter);
			if (filter.compareToIgnoreCase(LoopEntryEvent.EVT_PROF) == 0) {
				// parse all types of events...
				Map<String, List<ProfilingEvent>> allEvents = ProfilingEventParser.parseEventFilesInParallel(
						Arrays.asList(folders),
						MethodRegisterEvent.EVT_PROF, SystemMethodRegisterEvent.EVT_PROF, MethodEntryEvent.EVT_PROF,
						filter);

				List<ProfilingEvent> registerEvents = allEvents.get(MethodRegisterEvent.EVT_PROF);
				List<ProfilingEvent> systemRegisterEvents = allEvents.get(SystemMethodRegisterEvent.EVT_PROF);
				List<ProfilingEvent> entryEvents = allEvents.get(MethodEntryEvent.EVT_PROF);
				List<ProfilingEvent> events = allEvents.get(filter);

				for (ProfilingEvent e : registerEvents) {
					methods.put(((MethodRegisterEvent) e).getMethodNum(), ((MethodRegisterEvent) e));
				}
				for (ProfilingEvent e : systemRegisterEvents) {
					systemMethods.put(((SystemMethodRegisterEvent) e).getMethodNum(), ((SystemMethodRegisterEvent) e));
				}

				List<LoopEntryEvent> loopEntryEvents = new ArrayList<>();
				for (ProfilingEvent e : events)
					loopEntryEvents.add(((LoopEntryEvent) e));

				List<MethodEntryEvent> methodEntryEvents = new ArrayList<>();
				for (ProfilingEvent e : entryEvents)
					methodEntryEvents.add(((MethodEntryEvent) e));

				LOG.info("Got [" + methods.size() + "] MR, [" + systemMethods.size() + "] SR , ["
						+ loopEntryEvents.size() + "] " + filter +
						" and [" + methodEntryEvents.size() + "] E");

				LOG.info("Cleaning system methods...");
				cleanSystemMethods(methods, systemMethods, loopEntryEvents);
				LOG.info("Cleaning entry methods...");
				cleanMethodEntryEvents(methods, methodEntryEvents);
				// create the output dir
				File oDir = new File(dimension);
				if (oDir.exists() || !oDir.mkdirs()) {
					LOG.info("Could not create output directory, exiting...");
					System.exit(1);
				}
				processLoopTraces(methods, loopEntryEvents, dimension, oDir, limit, mappingFile);
				processEntryTraces(methods, methodEntryEvents, dimension, oDir, limit, mappingFile);

			} else {
				LOG.severe("Unkown event type +[" + filter + "]");
			}
		} finally {
			long time = System.currentTimeMillis() - start;
			LOG.info("Events processed in " + String.format("%.3f", time / 1000.0) + " seconds");
		}
	}

	public static boolean isInitializer(String name) {
		return name.compareTo(INIT_NAME) == 0 || name.compareTo(STATIC_INIT_NAME) == 0;
	}

	public static void cleanMethodEntryEvents(Map<Long, MethodRegisterEvent> methods, List<MethodEntryEvent> events) {
		int badEntries = 0;
		int initialSize = events.size();
		for (MethodEntryEvent e : events) {
			MethodRegisterEvent m = methods.get(e.getMethodId());
			if (m == null) {
				++badEntries;
				e.resetMethodId(-1);
			}
		}

		LOG.info("Got [" + badEntries + "] entry events with no methodId. Those events will be removed...");
		int finalSize = initialSize - badEntries;
		LOG.info("Ended with [" + finalSize + "] events...");
	}

	public static void cleanSystemMethods(Map<Long, MethodRegisterEvent> methods,
			Map<Long, SystemMethodRegisterEvent> systemMethods, List<LoopEntryEvent> events) {
		int notMapped = 0;
		int badLineNumbers = 0;
		int badMethodId = 0;
		int initialSize = events.size();
		Iterator<LoopEntryEvent> it = events.iterator();
		while (it.hasNext()) {
			LoopEntryEvent e = it.next();
			if (e.isShouldResolve() && e.getSystemMethod() != LoopEntryEvent.NO_ID_EVT) {
				SystemMethodRegisterEvent target = systemMethods.get(e.getSystemMethod());
				if (target != null) {
					long methodId = target.getAppMethod();
					int line = target.getLineNumber();
					if (line > 0) {
						e.resetMethodId(methodId);
						e.resetLineNumber(line);
					} else {
						MethodRegisterEvent original = methods.get(methodId);
						++badLineNumbers;
						if (original != null) {
							LOG.severe("Got bad line number when mapping " + original.getClassName() + "."
									+ original.getMethodName() + "[" + line + "]");
							// do not remove, its too expensive, instead just make it bad
							e.resetMethodId(-1);
						}
					}
				} else {
					// do not remove, its too expensive, instead just make it bad
					e.resetMethodId(-1);
				}
			} else {
				// lets see if this is clean, it coould be something we dont care about...
				if (e.getMethodId() == 0 && e.getSystemMethod() == 0) {
					++badMethodId;
					// do not remove, its too expensive, instead just make it bad
					e.resetMethodId(-1);
				}
			}
		}
		LOG.info("Got [" + notMapped + "] non matched methods, [" + badLineNumbers +
				"] bad line numbers and [" + badMethodId +
				"] bad Ids. Those events will be removed...");
		int finalSize = initialSize - (notMapped + badLineNumbers + badMethodId);
		LOG.info("Ended with [" + finalSize + "] events...");
	}

	public static void processEntryTraces(Map<Long, MethodRegisterEvent> methods, List<MethodEntryEvent> events,
			String dimension,
			File outputDir, int limit, String mappingFile) throws FileNotFoundException {
		LOG.info("Processing method execution count...");
		Map<Integer, List<MethodEntryEvent>> eventsByRun = new TreeMap<>();
		for (MethodEntryEvent e : events) {
			if (e.getRun() <= limit && e.getMethodId() != -1) {
				if (eventsByRun.containsKey(e.getRun())) {
					eventsByRun.get(e.getRun()).add(e);
				} else {
					List<MethodEntryEvent> list = new ArrayList<MethodEntryEvent>();
					list.add(e);
					eventsByRun.put(e.getRun(), list);
				}
			}
		}
		LOG.info("Run info");
		int maxRun = -1;
		int minRun = Integer.MAX_VALUE;
		Map<Integer, Integer> runMap = new HashMap<>();
		int current = 0;
		for (Entry<Integer, List<MethodEntryEvent>> byRun : eventsByRun.entrySet()) {
			LOG.info("run=" + byRun.getKey() + ", size=" + byRun.getValue().size());
			if (byRun.getKey() > maxRun)
				maxRun = byRun.getKey();
			if (byRun.getKey() < minRun)
				minRun = byRun.getKey();
			runMap.put(byRun.getKey(), current);
			++current;

		}

		Map<Integer, Map<CollapsedLoop, Integer>> iterationsByRun = new HashMap<>();

		// now by method, we count the iterations
		for (Entry<Integer, List<MethodEntryEvent>> byRun : eventsByRun.entrySet()) {

			// here filter by thread. we sum iterations for the same loop by the same thread
			Map<LoopEntryEventId, Integer> threadMap = new HashMap<>();

			LOG.info("Scale:" + byRun.getKey() + " : Initial method count  is " + byRun.getValue().size());

			for (MethodEntryEvent event : byRun.getValue()) {
				LoopEntryEventId id = new LoopEntryEventId(event.getMethodId(), 0, event.getThreadId());
				Integer count = threadMap.get(id);
				if (count == null) {
					threadMap.put(id, event.getCount());
				} else {
					threadMap.put(id, count + event.getCount());
				}
			}

			LOG.info("Scale:" + byRun.getKey() + " : Collapsed by thread method count  is " + threadMap.size());

			Map<CollapsedLoop, Integer> iterationsPerMethod = new HashMap<>();

			// now, leave the max thread here in case there are multiple
			for (Entry<LoopEntryEventId, Integer> byThread : threadMap.entrySet()) {
				// now we collapse by collapsed method
				CollapsedLoop key = new CollapsedLoop(byThread.getKey().getMethodId(), 0);
				Integer value = iterationsPerMethod.get(key);
				if (value != null) {
					// its here, is it the biggest??
					if (value > byThread.getValue()) {
						iterationsPerMethod.put(key, value);
					}
				} else {
					/// its not here, lets create it
					iterationsPerMethod.put(key, byThread.getValue());
				}
			}

			LOG.info("Scale:" + byRun.getKey() + " : Collapsed method count  is " + iterationsPerMethod.size());

			/*
			 * LOG.info("run=" + byRun.getKey() + ", loopCount=" +
			 * iterationsPerLoop.size());
			 * 
			 * for (Entry<CollapsedLoop, Integer> x : iterationsPerLoop.entrySet()) {
			 * LOG.info("l=" + x.getKey() + ", " + x.getValue());
			 * }
			 */

			// save
			iterationsByRun.put(byRun.getKey(), iterationsPerMethod);

		}

		// check the trend over the runs
		Map<LoopEntryEventId, double[]> methodTrends = new TreeMap<>();
		int minIterator = runMap.get(minRun);
		int maxIterator = runMap.get(maxRun);
		for (int i = minRun; i <= maxRun; ++i) {
			Map<CollapsedLoop, Integer> forThisRun = iterationsByRun.get(i);
			if (forThisRun == null)
				continue;
			int iteratorId = runMap.get(i);
			for (Entry<CollapsedLoop, Integer> loopEntry : forThisRun.entrySet()) {
				LoopEntryEventId loopId = new LoopEntryEventId(loopEntry.getKey().getMethodId(), 0, 0);
				if (methodTrends.containsKey(loopId)) {
					methodTrends.get(loopId)[iteratorId - minIterator] = loopEntry.getValue();
				} else {
					double[] values = new double[maxIterator - minIterator + 1];
					values[iteratorId - minIterator] = loopEntry.getValue();
					methodTrends.put(loopId, values);
				}
			}
		}

		doCorrelationMapping(methodTrends, methods, dimension, maxRun, outputDir, limit, mappingFile, "method");

	}

	public static void processLoopTraces(Map<Long, MethodRegisterEvent> methods, List<LoopEntryEvent> events,
			String dimension,
			File outputDir, int limit, String mappingFile) throws FileNotFoundException {
		LOG.info("Processing loop iteration count...");
		Map<Integer, List<LoopEntryEvent>> eventsByRun = new TreeMap<>();
		Map<Long, Long> loopByIteration = new HashMap<>();
		Map<Long, Integer> loopWithLineNumber = new HashMap<>();
		for (LoopEntryEvent e : events) {
			if (e.getRun() <= limit && e.getMethodId() != -1) {
				loopByIteration.put(e.getItId(), e.getMethodId());
				loopWithLineNumber.put(e.getItId(), e.getLineNumber());
				if (eventsByRun.containsKey(e.getRun())) {
					eventsByRun.get(e.getRun()).add(e);
				} else {
					List<LoopEntryEvent> list = new ArrayList<LoopEntryEvent>();
					list.add(e);
					eventsByRun.put(e.getRun(), list);
				}
			}
		}
		LOG.info("Run info");
		int maxRun = -1;
		int minRun = Integer.MAX_VALUE;
		Map<Integer, Integer> runMap = new HashMap<>();
		int current = 0;
		for (Entry<Integer, List<LoopEntryEvent>> byRun : eventsByRun.entrySet()) {
			LOG.info("run=" + byRun.getKey() + ", size=" + byRun.getValue().size());
			if (byRun.getKey() > maxRun)
				maxRun = byRun.getKey();
			if (byRun.getKey() < minRun)
				minRun = byRun.getKey();
			runMap.put(byRun.getKey(), current);
			++current;

		}

		Map<Integer, Map<CollapsedLoop, Integer>> iterationsByRun = new HashMap<>();

		// now by loop, we count the iterations
		for (Entry<Integer, List<LoopEntryEvent>> byRun : eventsByRun.entrySet()) {

			// here filter by thread. we sum iterations for the same loop by the same thread
			Map<LoopEntryEventId, Map<Long, Integer>> threadMap = new HashMap<>();

			LOG.info("Scale:" + byRun.getKey() + " : Initial loop count  is " + byRun.getValue().size());

			for (LoopEntryEvent event : byRun.getValue()) {
				LoopEntryEventId id = new LoopEntryEventId(event.getMethodId(), event.getLineNumber(), event.getItId());
				Map<Long, Integer> tMaps = threadMap.get(id);
				if (tMaps == null) {
					tMaps = new HashMap<Long, Integer>();
					tMaps.put(event.getThreadId(), event.getIterationCount());
					threadMap.put(id, tMaps);
				} else {
					if (tMaps.containsKey(event.getThreadId())) {
						tMaps.put(event.getThreadId(), tMaps.get(event.getThreadId()) + event.getIterationCount());
					} else {
						tMaps.put(event.getThreadId(), event.getIterationCount());
					}
				}
			}

			LOG.info("Scale:" + byRun.getKey() + " : Collapsed by loop count  is " + threadMap.size());

			Map<CollapsedLoop, Integer> iterationsPerLoop = new HashMap<>();

			// now, leave the max thread here in case there are multiple
			for (Entry<LoopEntryEventId, Map<Long, Integer>> byThread : threadMap.entrySet()) {
				// this first collapses by thread
				Map<Long, Integer> c = byThread.getValue();
				int max = 0;
				// find the max
				for (Entry<Long, Integer> e : c.entrySet()) {
					if (e.getValue() > max) {
						max = e.getValue();
					}
				}

				// now we collapse by collapsed loop
				CollapsedLoop key = new CollapsedLoop(byThread.getKey().getMethodId(),
						byThread.getKey().getLineNumber());
				Integer value = iterationsPerLoop.get(key);
				if (value != null) {
					// its here, is it the biggest??
					if (max > value) {
						iterationsPerLoop.put(key, max);
					}
				} else {
					/// its not here, lets create it
					iterationsPerLoop.put(key, max);
				}
			}

			LOG.info("Scale:" + byRun.getKey() + " : Collapsed by thread loop count  is " + iterationsPerLoop.size());

			/*
			 * LOG.info("run=" + byRun.getKey() + ", loopCount=" +
			 * iterationsPerLoop.size());
			 * 
			 * for (Entry<CollapsedLoop, Integer> x : iterationsPerLoop.entrySet()) {
			 * LOG.info("l=" + x.getKey() + ", " + x.getValue());
			 * }
			 */

			// save
			iterationsByRun.put(byRun.getKey(), iterationsPerLoop);

		}

		// check the trend over the runs
		Map<LoopEntryEventId, double[]> loopTrendsByRun = new TreeMap<>();
		int minIterator = runMap.get(minRun);
		int maxIterator = runMap.get(maxRun);
		for (int i = minRun; i <= maxRun; ++i) {
			Map<CollapsedLoop, Integer> forThisRun = iterationsByRun.get(i);
			if (forThisRun == null)
				continue;
			int iteratorId = runMap.get(i);
			for (Entry<CollapsedLoop, Integer> loopEntry : forThisRun.entrySet()) {
				LoopEntryEventId loopId = new LoopEntryEventId(loopEntry.getKey().getMethodId(),
						loopEntry.getKey().getLineNumber(), 0);
				if (loopTrendsByRun.containsKey(loopId)) {
					loopTrendsByRun.get(loopId)[iteratorId - minIterator] = loopEntry.getValue();
				} else {
					double[] values = new double[maxIterator - minIterator + 1];
					values[iteratorId - minIterator] = loopEntry.getValue();
					loopTrendsByRun.put(loopId, values);
				}
			}
		}

		doCorrelationMapping(loopTrendsByRun, methods, dimension, maxRun, outputDir, limit, mappingFile, "loop");

	}

	private static void doCorrelationMapping(Map<LoopEntryEventId, double[]> loopTrendsByRun,
			Map<Long, MethodRegisterEvent> methods, String dimension, int maxRun, File outputDir,
			int limit, String mappingFile, String summaryRoot) throws FileNotFoundException {
		SortedMultiMap<Double, SDLoop> sdSignatures = new SortedMultiMap<>(Comparator.reverseOrder());
		SortedMultiMap<Double, SDLoop> rejectedSdSignatures = new SortedMultiMap<>(Comparator.reverseOrder());
		int tooSmall = 0;
		int noGrowth = 0;
		int notPassLimit = 0;
		int other = 0;
		int good = 0;
		// check the growth trend
		for (Entry<LoopEntryEventId, double[]> loopTrend : loopTrendsByRun.entrySet()) {
			MethodRegisterEvent method = methods.get(loopTrend.getKey().getMethodId());
			if (method == null)
				continue;
			// first, if there are no iterations of at least the size of the cluster, filter
			Map<Double, Double> allPoints = pointsAsMap(loopTrend.getValue());
			Map<Double, Double> originalAllPoints = pointsAsMap(loopTrend.getValue());
			// now, lets get the growth only
			Map<Double, Double> growingPoints = preserveOnlyGrowth(loopTrend.getValue());
			Map<Double, Double> originalGrowingPoints = preserveOnlyGrowth(loopTrend.getValue());

			normmalizeDataPoints(allPoints);

			if (filterIfTooSmall(loopTrend.getValue(), Math.round(LEAST_FOR_GROWTH_LOOP_PER * limit))) {
				GrowthTrend small = trend(allPoints, dimension, false);
				SDLoop loop = small.asLoop(originalAllPoints, originalGrowingPoints, method,
						loopTrend.getKey().getLineNumber(), dimension);
				loop.getDimension().setSpearmanCorrelation(TOO_SMALL);
				rejectedSdSignatures.put(TOO_SMALL, loop);
				++tooSmall;
				continue;
			}
			if (growingPoints.size() <= LEAST_FOR_GROWTH_LOOP_POINTS) {
				GrowthTrend ng = trend(allPoints, dimension, false);
				SDLoop loop = ng.asLoop(originalAllPoints, originalGrowingPoints, method,
						loopTrend.getKey().getLineNumber(), dimension);
				loop.getDimension().setSpearmanCorrelation(NO_DP);
				rejectedSdSignatures.put(NO_DP, loop);
				++noGrowth;
				continue;
			}
			// and lets see where is the last data point here. If its not close to the end,
			// then why care?
			double intervalMax = maxRun;
			double intervalMin = Math.floor(FIRST_PART * intervalMax);
			boolean pointsAtArea = false;
			for (double p = intervalMin; p <= intervalMax; ++p) {
				if (growingPoints.containsKey(p)) {
					pointsAtArea = true;
					break;
				}
			}
			if (!pointsAtArea) {
				GrowthTrend bg = trend(allPoints, dimension, false);
				SDLoop loop = bg.asLoop(originalAllPoints, originalGrowingPoints, method,
						loopTrend.getKey().getLineNumber(), dimension);
				loop.getDimension().setSpearmanCorrelation(NO_G_BEYOND);
				rejectedSdSignatures.put(NO_G_BEYOND, loop);
				++notPassLimit;
				continue;
			}
			// if there are, normalize, this modifies in place
			normmalizeDataPoints(growingPoints);

			if (method != null) {
				GrowthTrend trend = trend(growingPoints, dimension, true);
				if (trend.spearmanCorrelation >= LOW_CORRELATION) {
					SDLoop sdLoop = trend.asLoop(originalAllPoints, originalGrowingPoints, method,
							loopTrend.getKey().getLineNumber(), dimension);
					LOG.info("Accepted [" + method.getClassName() + "." + method.getSignature() + ":"
							+ loopTrend.getKey().getLineNumber()
							+ String.format(" <spearman=%.5f>", trend.spearmanCorrelation) + "]");
					sdSignatures.put(trend.spearmanCorrelation, sdLoop);
					++good;
				} else {
					SDLoop loop = trend.asLoop(originalAllPoints, originalGrowingPoints, method,
							loopTrend.getKey().getLineNumber(), dimension);
					rejectedSdSignatures.put(OTHER, loop);
					++other;
				}
			}
		}

		int total = good + other + tooSmall + noGrowth + notPassLimit;
		double acceptedPer = 100.0 * (good * 1.0) / total;
		double tooSmallPer = 100.0 * (tooSmall * 1.0) / total;
		double noGrowthPer = 100.0 * (noGrowth * 1.0) / total;
		double notPassLimitPer = 100.0 * (notPassLimit * 1.0) / total;
		double otherPer = 100.0 * (other * 1.0) / total;
		String summaryString = String.format(
				"\nTotal=%d\nAccepted=%d(%.2f)\nSmallDataPoints=%d(%.2f)\nNoGrowthAtAll=%d(%.2f)\nNoGrowthBeyondFirstQuartile=%d(%.2f)\nLowCorrelation=%d(%.2f)",
				total, good, acceptedPer, tooSmall, tooSmallPer, noGrowth, noGrowthPer, notPassLimit, notPassLimitPer,
				other, otherPer);

		String pre = outputDir.getAbsolutePath() + File.separator + summaryRoot;
		if (!new File(pre).mkdirs()) {
			LOG.info("There was an error creating output directory...");
			System.exit(1);
		} else {
			// save the summary here
			String summ = pre + File.separator + "summary.log";
			PrintWriter pr = new PrintWriter(new File(summ));
			pr.println(summaryString);
			pr.close();
		}

		writeSummary(pre + File.separator + limit + ".csv", pre + File.separator + limit + "-data",
				sdSignatures.flatten(), mappingFile);
		writeSummary(pre + File.separator + "non-" + limit + ".csv", pre + File.separator + "non-" + limit + "-data",
				rejectedSdSignatures.flatten(), mappingFile);

		LOG.info(summaryString);
	}

	private static void writeSummary(String location, String dataPath, Collection<SDLoop> values,
			String columnMapping) {
		LOG.info("Writing summary to [" + location + "]");
		if (!new File(dataPath).mkdirs()) {
			LOG.info("Error while creating [" + dataPath + "], exiting...");
			System.exit(1);
		} else {
			try {
				CSVSummaryWriter writer = new CSVSummaryWriter(new PrintWriter(new File(location)), dataPath,
						columnMapping);
				writer.writeSummary(values);
			} catch (Exception e) {
				Logging.exception(LOG, e, "Exception while writing CSV summary");
			}
		}
	}

	private static Map<Double, Double> preserveOnlyGrowth(double[] points) {
		// this should be easy
		double current = points[0];
		Map<Double, Double> newPoints = new TreeMap<Double, Double>();
		newPoints.put(0.0, current);
		for (int i = 1; i < points.length; ++i) {
			if (points[i] - current > 0) {
				newPoints.put(i * 1.0, points[i]);
				current = points[i];
			}
		}
		return newPoints;
	}

	private static Map<Double, Double> pointsAsMap(double[] points) {
		// this should be easy
		Map<Double, Double> newPoints = new TreeMap<Double, Double>();
		for (int i = 0; i < points.length; ++i) {
			newPoints.put(i * 1.0, points[i]);
		}
		return newPoints;
	}

	private static void normmalizeDataPoints(Map<Double, Double> points) {
		// normalize trend
		double maxY = Double.MIN_VALUE;
		double minY = Double.MAX_VALUE;
		for (Entry<Double, Double> point : points.entrySet()) {
			if (point.getValue() > maxY)
				maxY = point.getValue();
			if (point.getValue() < minY)
				minY = point.getValue();
		}
		for (Entry<Double, Double> point : points.entrySet()) {
			points.put(point.getKey(), (point.getValue() - minY) / (minY != maxY ? maxY - minY : 1.0));
		}
	}

	private static double calculateCorrelation(double[] x, double[] y) {
		SpearmansCorrelation spearman = new SpearmansCorrelation();
		try {
			return spearman.correlation(x, y);
		} catch (Exception e) {
			LOG.info("Could not calculate correlation, returning default.");
			return -1;
		}
	}

	private static GrowthTrend trend(Map<Double, Double> points, String dimensionName,
			boolean tryMultiLinearRegression) {
		double[] xAxis = new double[points.size()];
		double[] growthYAxis = new double[points.size()];
		int pos = 0;
		for (Entry<Double, Double> point : points.entrySet()) {
			xAxis[pos] = point.getKey();
			growthYAxis[pos] = point.getValue();
			++pos;
		}
		// compute the correlation coefficient. This has to be done between the x axis
		// and the normalized points.
		double corr = calculateCorrelation(xAxis, growthYAxis);
		// String ransacEquationText = buildEquation("RC", dimensionName, slope,
		// intercept, rSquare, c4, c5, c6);
		// filter out by some cleaning up criteria here
		return new GrowthTrend(corr);
	}

	private static boolean filterIfTooSmall(double[] array, double threshold) {
		for (int i = 1; i < array.length; ++i) {
			if (array[i] >= threshold) {
				return false;
			}
		}
		return true;
	}

	private static class GrowthTrend {

		private double spearmanCorrelation;

		public GrowthTrend(double s) {
			this.spearmanCorrelation = s;
		}

		public SDLoop asLoop(Map<Double, Double> rawPoints, Map<Double, Double> growingPoints,
				MethodRegisterEvent method, int lineNumber, String dimensionName) {
			SDLoop sdLoop = new SDLoop();
			Dimension d = new Dimension();
			d.setSpearmanCorrelation(spearmanCorrelation);
			d.setGrowthPoints(growingPoints);
			d.setRawPoints(rawPoints);
			d.setName(dimensionName);
			sdLoop.setDimension(d);
			sdLoop.setClassName(method.getClassName());
			sdLoop.setLineNumber(lineNumber);
			sdLoop.setMethodSignature(method.getSignature());
			return sdLoop;
		}

	}

}
