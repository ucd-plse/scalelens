package scaleview.analysis.processor;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.factory.TypeFactory;
import spoon.reflect.reference.CtTypeReference;
import scaleview.agent.util.Logging;
import scaleview.analysis.scanner.MethodBodyScanner;
import scaleview.analysis.scanner.MethodBodyScanner.MethodLoopCounterScanner;
import scaleview.analysis.types.CSVSummaryLine;
import scaleview.analysis.types.SpoonClassReference;
import scaleview.analysis.types.SpoonMethodReference;
import scaleview.analysis.types.SpoonReference.SpoonReferenceContainer;
import scaleview.analysis.utils.AnalysisUtils;

public class GeneralStatsProcessor extends AbstractProcessor<CtClass> {

	private static final Logger LOG = Logging.getLogger(GeneralStatsProcessor.class.getName());

	public static final String LOG_LOOP_LINE = "try{ String __name=\"__NAME\"; java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileOutputStream(new java.io.File(\"inst_loops.txt\"),true));pw.println(__name);pw.close();} catch(Exception e) {}";

	private static final boolean INSTRUMENT_LOOPS = Boolean.valueOf(System.getProperty("doInstrument", "false"));

	private SpoonReferenceContainer<SpoonClassReference> applicationClasses = new SpoonReferenceContainer<>();
	private SpoonReferenceContainer<SpoonMethodReference> applicationMethods = new SpoonReferenceContainer<>();
	private List<Integer> methodPerClass = new ArrayList<>();
	private List<Integer> fieldPerClass = new ArrayList<>();
	private List<Integer> methodPerTopLevelClass = new ArrayList<>();
	private List<Integer> fieldPerTopLevelClass = new ArrayList<>();
	private List<Integer> loopPerMethod = new ArrayList<>();
	private List<Integer> loopPerMethodInTopLevelClass = new ArrayList<>();
	private Set<CSVSummaryLine> loopsInApplication = new TreeSet<>();
	private int totalFieldCount = 0;
	private int totalFieldTopLevelCount = 0;
	private int topLevelClassCount = 0;
	private int numMethodsWithBody = 0;
	private int numLoops = 0;
	private int numLoopsInTopLevelClasses = 0;
	private int numMethodsWithLoops = 0;
	private int numMethodsInTopLevelClassesWithLoops = 0;
	private int numFELoops = 0;
	private int numFELoopsInTopLevelClasses = 0;
	private int numWHLoops = 0;
	private int numWHLoopsInTopLevelClasses = 0;
	private int numDOLoops = 0;
	private int numDOLoopsInTopLevelClasses = 0;
	private int numFOLoops = 0;
	private int numFOLoopsInTopLevelClasses = 0;
	private int numMethodsReturningArrays = 0;
	private int numMethodsReturningCols = 0;
	private int nonInstrumented = 0;

	@Override
	public void process(CtClass element) {

		// I done care about enums
		if (element.isEnum()) {
			return;
		}
		// instatiate the class
		SpoonClassReference klass = new SpoonClassReference(element);
		if (element.isTopLevel())
			++topLevelClassCount;
		// collect class methods too
		int count = 0;
		int countTopLevel = 0;
		for (CtMethod method : (Set<CtMethod>) element.getMethods()) {
			// we can also map dependencies while doing this
			SpoonMethodReference mm = new SpoonMethodReference(klass, method);
			applicationMethods.putValue(mm);
			klass.getMethods().putValue(mm);
			++count;
			if (element.isTopLevel())
				++countTopLevel;
			// lets see here too
			try {
				checkMethodReturnType(method);
			} catch (Exception e) {
				// Logging.exception(LOG, e, "Exception when checking return type for [" +
				// method.getSignature() + "]");
			}
			// do the loops here too
			MethodLoopCounterScanner scanner = MethodBodyScanner.countLoops(mm, INSTRUMENT_LOOPS,
					klass.getKlass().getQualifiedName(), method.getSimpleName());
			if (scanner != null) {
				loopPerMethod.add(scanner.getTotalLoops());
				numLoops += scanner.getTotalLoops();
				numFELoops += scanner.getForEachLoopCount();
				numWHLoops += scanner.getWhileLoopCount();
				numDOLoops += scanner.getDoLoopCount();
				numFOLoops += scanner.getForLoopCount();
				nonInstrumented += scanner.getNonInstrumented();
				if (scanner.getTotalLoops() > 0) {
					++numMethodsWithLoops;

				}
				if (element.isTopLevel()) {
					loopPerMethodInTopLevelClass.add(scanner.getTotalLoops());
					numLoopsInTopLevelClasses += scanner.getTotalLoops();
					numFELoopsInTopLevelClasses += scanner.getForEachLoopCount();
					numWHLoopsInTopLevelClasses += scanner.getWhileLoopCount();
					numDOLoopsInTopLevelClasses += scanner.getDoLoopCount();
					numFOLoopsInTopLevelClasses += scanner.getForLoopCount();
					if (scanner.getTotalLoops() > 0) {
						++numMethodsInTopLevelClassesWithLoops;
					}
				}
				++numMethodsWithBody;
			}
			// and collect per class loops here
			Set<CSVSummaryLine> collectedLoops = MethodBodyScanner.collectLoops(klass, mm);
			if (collectedLoops != null) {
				loopsInApplication.addAll(collectedLoops);
			}

		}
		// count here
		methodPerClass.add(count);
		if (element.isTopLevel())
			methodPerTopLevelClass.add(countTopLevel);
		// save the fields too
		count = 0;
		countTopLevel = 0;
		/*
		 * for(CtFieldReference field : element.getDeclaredFields()) {
		 * klass.getFields().putValue(new SpoonFieldReference(field.getDeclaration(),
		 * klass));
		 * ++count;
		 * ++totalFieldCount;
		 * if(element.isTopLevel()) {
		 * ++totalFieldTopLevelCount;
		 * ++countTopLevel;
		 * }
		 * }
		 * fieldPerClass.add(count);
		 * if(element.isTopLevel()) fieldPerTopLevelClass.add(countTopLevel);
		 */
		// and store here too...
		applicationClasses.putValue(klass);
	}

	@Override
	public void processingDone() {
		LOG.info("Getting some stats");
		printStats();
		// printQuickStats();
		// print the number of loops collected
		LOG.info(String.format("Found [%d] different loops", loopsInApplication.size()));
		// save them into a file
		try {
			PrintWriter p = new PrintWriter(new File("loops.csv"));
			for (CSVSummaryLine loop : loopsInApplication) {
				p.println(String.format("%s,%d,%s", loop.getIdentifier(), loop.getLineNumber(), loop.getLoopType()));
			}
			p.close();
		} catch (Exception e) {
		}

		// instrument too
		if (INSTRUMENT_LOOPS) {
			LOG.info("Instrumenting...");
			Map<String, CtCompilationUnit> units = new HashMap<>();
			for (SpoonClassReference k : applicationClasses) {
				if (k.getKlass().getPosition().isValidPosition()) {
					CtCompilationUnit unit = k.getKlass().getPosition().getCompilationUnit();
					units.put(unit.getFile().getAbsolutePath(), unit);
				}
			}
			for (Entry<String, CtCompilationUnit> uu : units.entrySet()) {
				try {
					PrintWriter p = new PrintWriter(uu.getValue().getFile());
					LOG.info("Saving instrumented version of [" + uu.getValue().getFile().getAbsolutePath() + "]");

					// ForceFullyQualifiedProcessor fp = new ForceFullyQualifiedProcessor();
					/// fp.process(uu.getValue());
					String source = uu.getValue().prettyprint();
					String noStupidImports = source.replaceAll("import [a-zA-Z]+[0-9]*[;]", "");
					noStupidImports = noStupidImports.replaceAll("import [A-Z][a-zA-Z.]+[;]", "");
					noStupidImports = noStupidImports.replaceAll("[<][>][;]", ";");
					p.print(noStupidImports);
					p.close();

					// String source = uu.getValue().prettyprint();
					// String noStupidImports = source.replaceAll("import [a-zA-Z]+[0-9]*[;]", "");
					// noStupidImports = noStupidImports.replaceAll("import [a-zA-Z.]+[<][>][;]",
					// "");
					// noStupidImports = noStupidImports.replaceAll("import [A-Z][a-zA-Z.]+[;]",
					// "");
					// p.print(noStupidImports);
					// p.close();
				} catch (Exception e) {
					LOG.info(e.toString());
				}
			}
		}

		// done
		LOG.info("Done, exiting...");
		System.exit(0);

	}

	private void checkMethodReturnType(CtMethod mt) throws Exception {
		CtTypeReference rt = mt.getType();
		// is an array?
		if (rt.isArray())
			++numMethodsReturningArrays;
		// is a collection or map
		if (rt.getTypeDeclaration() != null) {
			if (rt.getTypeDeclaration().isSubtypeOf(new TypeFactory().COLLECTION))
				++numMethodsReturningCols;
			else if (rt.getTypeDeclaration().isSubtypeOf(new TypeFactory().MAP))
				++numMethodsReturningCols;
		}
	}

	// this is for quick copy pasting
	void printQuickStats() {

		StringBuilder bld = new StringBuilder().append(applicationClasses.size()).append(",")
				.append(applicationMethods.size() - numMethodsWithBody).append(",")
				.append(numMethodsWithBody).append(",")
				.append(numMethodsWithLoops).append(",")
				.append(numMethodsReturningArrays + numMethodsReturningCols).append(",")
				.append(0).append(",")
				.append(numFELoops).append(",")
				.append(numWHLoops).append(",")
				.append(numDOLoops).append(",")
				.append(numFOLoops).append(",")
				.append(numFELoops + numWHLoops + numDOLoops + numFOLoops);
		LOG.info(bld.toString());

	}

	private void printStats() {

		LOG.info("numClasses=" + applicationClasses.size());
		LOG.info("numTopLevelClasses=" + topLevelClassCount);
		LOG.info("numMethods=" + applicationMethods.size());
		LOG.info("numMethodsWithBody=" + numMethodsWithBody);
		LOG.info("numFields=" + totalFieldCount);
		LOG.info("numTopLevelClassFields=" + totalFieldTopLevelCount);
		LOG.info("numLoops=" + numLoops);
		LOG.info("numTopLevelClassLoops=" + numLoopsInTopLevelClasses);
		LOG.info("numFELoops=" + numFELoops);
		LOG.info("numFELoopsInTopLevelClasses=" + numFELoopsInTopLevelClasses);
		LOG.info("numWHLoops=" + numWHLoops);
		LOG.info("numWHLoopsInTopLevelClasses=" + numWHLoopsInTopLevelClasses);
		LOG.info("numDOLoops=" + numDOLoops);
		LOG.info("numDOLoopsInTopLevelClasses=" + numDOLoopsInTopLevelClasses);
		LOG.info("numFOLoops=" + numFOLoops);
		LOG.info("numFOLoopsInTopLevelClasses=" + numFOLoopsInTopLevelClasses);

		LOG.info("numMethodsWithLoops=" + numMethodsWithLoops);
		LOG.info("numMethodsWithLoopsTopLevelClass=" + numMethodsInTopLevelClassesWithLoops);
		// get stats for this methods
		Integer[] methodStats = AnalysisUtils.statsFromList(methodPerClass, true).toArray();
		LOG.info("perClassMethodStats[min, p25, median, p75, p90, p95, max]=" + Arrays.toString(methodStats));
		// top level classes
		Integer[] methodTopLevelStats = AnalysisUtils.statsFromList(methodPerTopLevelClass, true).toArray();
		LOG.info("perTopLevelClassMethodStats[min, p25, median, p75, p90, p95, max]="
				+ Arrays.toString(methodTopLevelStats));
		// for fields per class
		// Integer [] fieldStats = AnalysisUtils.statsFromList(fieldPerClass,
		// true).toArray();
		// LOG.info("perClassFieldStats[min, p25, median, p75, p90, p95, max]=" +
		// Arrays.toString(fieldStats));
		// top level
		// Integer [] fieldTopLevelStats =
		// AnalysisUtils.statsFromList(fieldPerTopLevelClass, true).toArray();
		// LOG.info("perTopLevelClassFieldStats[min, p25, median, p75, p90, p95, max]="
		// + Arrays.toString(fieldTopLevelStats));
		// for loop per class
		Integer[] loopStats = AnalysisUtils.statsFromList(loopPerMethod, true).toArray();
		LOG.info("perMethodLoopStats[min, p25, median, p75, p90, p95, max]=" + Arrays.toString(loopStats));
		// top level
		Integer[] loopTopLevelStats = AnalysisUtils.statsFromList(loopPerMethodInTopLevelClass, true).toArray();
		LOG.info("perTopLevelClassMethodLoopStats[min, p25, median, p75, p90, p95, max]="
				+ Arrays.toString(loopTopLevelStats));

		LOG.info("nonInstrumented=" + nonInstrumented);

	}

}
