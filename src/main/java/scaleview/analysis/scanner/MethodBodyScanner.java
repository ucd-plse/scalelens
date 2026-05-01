package scaleview.analysis.scanner;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;
import java.util.regex.Matcher;

import scaleview.analysis.processor.GeneralStatsProcessor;
import spoon.Launcher;
import spoon.reflect.code.CtCodeSnippetStatement;
import spoon.reflect.code.CtDo;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtFor;
import spoon.reflect.code.CtForEach;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLoop;
import spoon.reflect.code.CtWhile;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.CtScanner;
import scaleview.agent.util.Logging;
import scaleview.analysis.types.CSVSummaryLine;
import scaleview.analysis.types.Dimension;
import scaleview.analysis.types.SDField;
import scaleview.analysis.types.SDLoop;
import scaleview.analysis.types.SpoonClassReference;
import scaleview.analysis.types.SpoonMethodReference;
import scaleview.analysis.types.SpoonReference.SpoonReferenceContainer;
import scaleview.analysis.utils.AnalysisUtils;

public class MethodBodyScanner {

	private static final Logger LOG = Logging.getLogger(MethodBodyScanner.class.getName());

	public static SpoonReferenceContainer<SpoonMethodReference> collectPossibleEntryPoints(
			SpoonReferenceContainer<SpoonMethodReference> applicationMethods) {
		Set<String> invokedMethods = new HashSet<>();
		SpoonReferenceContainer<SpoonMethodReference> possibleEntryPoints = new SpoonReferenceContainer<>();
		LOG.info("Collecting references from [" + applicationMethods.size() + "] methods");
		for (SpoonMethodReference method : applicationMethods) {
			if (AnalysisUtils.hasBody(method.getMethod())) {
				method.getMethod().getBody().accept(new MethodInvocationScanner(invokedMethods));
			}
		}
		LOG.info("Collected [" + invokedMethods.size() + "] calls, looking for possible candidates");
		for (SpoonMethodReference appMethodEntry : applicationMethods) {
			if (!invokedMethods.contains(appMethodEntry.getMethodId())) {
				possibleEntryPoints.putValue(appMethodEntry);
			}
		}
		LOG.info("Done, collected [" + possibleEntryPoints.size() + "] candidates");
		return possibleEntryPoints;
	}

	public static SpoonReferenceContainer<SpoonMethodReference> collectTestEntryPoints(Set<String> testDirectories,
			Set<String> testAnnotations) {
		SpoonReferenceContainer<SpoonMethodReference> possibleEntryPoints = new SpoonReferenceContainer<>();
		LOG.info("Collecting entryPoints from " + testDirectories.toString() + " looking for annotations: "
				+ (testAnnotations != null ? testAnnotations.toString() : "none"));
		Launcher launcher = new Launcher();
		for (String res : testDirectories)
			launcher.addInputResource(res);
		launcher.buildModel();
		// Now get the types
		LOG.info("Parsing test classes and methods");
		for (CtType<?> type : launcher.getModel().getAllTypes()) {
			type.accept(new CtScanner() {
				@Override
				public void visitCtClass(CtClass theClass) {
					SpoonClassReference klass = new SpoonClassReference(theClass);
					for (CtMethod method : (Set<CtMethod>) theClass.getMethods()) {
						// we can also map dependencies while doing this
						SpoonMethodReference mm = new SpoonMethodReference(klass, method);
						if (testAnnotations != null && testAnnotations.size() > 0) {
							for (CtAnnotation<?> annotation : method.getAnnotations()) {
								if (testAnnotations.contains(annotation.getAnnotationType().getQualifiedName())) {
									possibleEntryPoints.putValue(mm);
								}
							}
						} else {
							possibleEntryPoints.putValue(mm);
						}
					}
				}
			});
		}
		LOG.info("Done, found [" + possibleEntryPoints.size() + "] test methods");
		return possibleEntryPoints;
	}

	public static MethodLoopCounterScanner countLoops(SpoonMethodReference method, boolean instrument, String className,
			String methodName) {
		if (AnalysisUtils.hasBody(method.getMethod())) {
			MethodLoopCounterScanner scanner = new MethodLoopCounterScanner(className, methodName, instrument);
			method.getMethod().getBody().accept(scanner);
			return scanner;
		}
		return null;
	}

	public static Set<CSVSummaryLine> collectLoops(SpoonClassReference klass, SpoonMethodReference method) {
		if (AnalysisUtils.hasBody(method.getMethod())) {
			MethodLoopCollectorScanner scanner = new MethodLoopCollectorScanner(klass.getKlass().getQualifiedName(),
					method.getMethod().getSimpleName());
			method.getMethod().getBody().accept(scanner);
			return scanner.getLoopLocations();
		}
		return null;
	}

	public static SpoonReferenceContainer<SpoonMethodReference> collectSDEntryPoints(
			SpoonReferenceContainer<SpoonMethodReference> candidates,
			SpoonReferenceContainer<SpoonMethodReference> applicationMethods) {

		SpoonReferenceContainer<SpoonMethodReference> sdEntryPoints = new SpoonReferenceContainer<>();
		LOG.info("Looking for SD entrypoints, [" + candidates.size() + "] initial candidates");
		for (SpoonMethodReference candidate : candidates) {
			Set<String> exclusions = new HashSet<>();
			exclusions.add(candidate.getId());
			if (checkDimensionalDependencies(candidate, candidate, applicationMethods, exclusions)) {
				sdEntryPoints.putValue(candidate);
			}
		}
		LOG.info("Done, found [" + sdEntryPoints.size() + "] candidates");
		return sdEntryPoints;
	}

	public static void checkSDFields(SpoonReferenceContainer<SpoonMethodReference> applicationMethods,
			Set<SDField> fields) {
		LOG.info("Checking sdfields in [" + applicationMethods.size() + "] methods");
		for (SpoonMethodReference method : applicationMethods) {
			if (AnalysisUtils.hasBody(method.getMethod())) {
				method.getMethod().getBody().accept(new MethodFieldAccessScanner(fields));
			}
		}
	}

	private static boolean checkDimensionalDependencies(SpoonMethodReference entryPoint,
			SpoonMethodReference currentTarget,
			SpoonReferenceContainer<SpoonMethodReference> applicationMethods, Set<String> exclusions) {
		// so the candidate is the entrypoint, the question is, is there any method in
		// its call
		// path that has a dimensional dependency?
		Set<String> invokedMethods = new HashSet<>();
		if (AnalysisUtils.hasBody(currentTarget.getMethod())) {
			currentTarget.getMethod().getBody().accept(new MethodInvocationScanner(invokedMethods, exclusions));
			boolean result = false;
			// now, lets lookup the methods here..
			for (String id : invokedMethods) {
				SpoonMethodReference target = applicationMethods.getValue(id);
				if (target != null) {
					exclusions.add(target.getId());
					if (target.getDimensions() != null && target.getDimensions().size() > 0) {
						for (Entry<Dimension, Integer> dentry : target.getDimensions().entrySet()) {
							if (entryPoint.getDimensions().containsKey(dentry.getKey())) {
								entryPoint.getDimensions().put(dentry.getKey(),
										entryPoint.getDimensions().get(dentry.getKey()) + 1);
							} else {
								entryPoint.getDimensions().put(dentry.getKey(), 1);
							}
						}
						result = true;
					}
					result |= checkDimensionalDependencies(entryPoint, target, applicationMethods, exclusions);
				} else {
					// LOG.info("method [" + id + "] not found...");
				}
			}
			// and done
			return result;
		}
		// does not have a body, so its abstract. Lets follow the concrete
		// implementations if possible
		else {
			boolean result = false;
			for (SpoonMethodReference ci : currentTarget.getConcreteImplementations()) {
				exclusions.add(ci.getId());
				if (ci.getDimensions() != null && ci.getDimensions().size() > 0) {
					for (Entry<Dimension, Integer> dentry : ci.getDimensions().entrySet()) {
						if (entryPoint.getDimensions().containsKey(dentry.getKey())) {
							entryPoint.getDimensions().put(dentry.getKey(),
									entryPoint.getDimensions().get(dentry.getKey()) + 1);
						} else {
							entryPoint.getDimensions().put(dentry.getKey(), 1);
						}
					}
					result = true;
				}
				result |= checkDimensionalDependencies(entryPoint, ci, applicationMethods, exclusions);
			}
			return result;
		}
	}

	private static class MethodInvocationScanner extends CtScanner {

		private Set<String> collectedMethods;
		private Set<String> exclusions;

		public MethodInvocationScanner(Set<String> cm) {
			collectedMethods = cm;
			exclusions = null;
		}

		public MethodInvocationScanner(Set<String> cm, Set<String> e) {
			collectedMethods = cm;
			exclusions = e;
		}

		@Override
		public void visitCtInvocation(CtInvocation invocation) {
			CtExecutableReference reference = invocation.getExecutable();
			if (reference != null && reference.getDeclaringType() != null) {
				String id = AnalysisUtils.getMethodId(reference);
				if (exclusions == null) {
					collectedMethods.add(id);
				} else {
					if (!exclusions.contains(id))
						collectedMethods.add(id);
				}

			}
		}
	}

	private static class MethodSDInvocationScanner extends CtScanner {

		private Set<String> collectedMethods;
		private Set<String> exclusions;
		private List<SDLoop> sdloops;
		private List<Dimension> current = new ArrayList<>();

		public MethodSDInvocationScanner(Set<String> cm, Set<String> e, List<SDLoop> l) {
			collectedMethods = cm;
			exclusions = e;
			sdloops = l;
		}

		private boolean isLoop(CtElement element) {
			return (element instanceof CtForEach || element instanceof CtWhile || element instanceof CtDo
					|| element instanceof CtFor);
		}

		private void pushDimensions(CtElement element) {
			if (!isLoop(element))
				return;
			for (SDLoop loo : sdloops) {
				if (loo.getLineNumber() == element.getPosition().getLine()) {
					current.add(loo.getDimension());
				}
			}
		}

		private void popDimensions(CtElement element) {
			if (!isLoop(element) && current.size() > 0)
				return;
			current.remove(current.size() - 1);
		}

		@Override
		public void visitCtForEach(CtForEach loop) {
			pushDimensions(loop);
		}

		@Override
		public void visitCtWhile(CtWhile loop) {
			pushDimensions(loop);
		}

		@Override
		public void visitCtDo(CtDo loop) {
			pushDimensions(loop);
		}

		@Override
		public void visitCtFor(CtFor loop) {
			pushDimensions(loop);
		}

		@Override
		protected void exit(CtElement loop) {
			popDimensions(loop);
		}

		@Override
		public void visitCtInvocation(CtInvocation invocation) {
			CtExecutableReference reference = invocation.getExecutable();
			if (reference != null && reference.getDeclaringType() != null) {
				String id = AnalysisUtils.getMethodId(reference);
				if (exclusions == null) {
					collectedMethods.add(id);
				} else {
					if (!exclusions.contains(id))
						collectedMethods.add(id);
				}

			}
		}
	}

	public static class MethodLoopCollectorScanner extends CtScanner {

		private static final String TYPE_F = "For";
		private static final String TYPE_W = "While";
		private static final String TYPE_D = "DoWhile";
		private static final String TYPE_E = "ForEach";

		Set<CSVSummaryLine> loopLocations = new TreeSet<>();
		private String className;
		private String methodName;

		public MethodLoopCollectorScanner(String c, String m) {
			className = c;
			methodName = m;
		}

		@Override
		public void visitCtForEach(CtForEach loop) {
			int line = loop.getPosition().getLine();
			CSVSummaryLine element = new CSVSummaryLine(className + "." + methodName, line, TYPE_E);
			loopLocations.add(element);
		}

		@Override
		public void visitCtWhile(CtWhile loop) {
			int line = loop.getPosition().getLine();
			CSVSummaryLine element = new CSVSummaryLine(className + "." + methodName, line, TYPE_W);
			loopLocations.add(element);
		}

		@Override
		public void visitCtDo(CtDo loop) {
			int line = loop.getPosition().getLine();
			CSVSummaryLine element = new CSVSummaryLine(className + "." + methodName, line, TYPE_D);
			loopLocations.add(element);
		}

		@Override
		public void visitCtFor(CtFor loop) {
			int line = loop.getPosition().getLine();
			CSVSummaryLine element = new CSVSummaryLine(className + "." + methodName, line, TYPE_F);
			loopLocations.add(element);
		}

		public Set<CSVSummaryLine> getLoopLocations() {
			return loopLocations;
		}

	}

	public static class MethodLoopCounterScanner extends CtScanner {

		private int forEachLoopCount = 0;
		private int whileLoopCount = 0;
		private int doLoopCount = 0;
		private int forLoopCount = 0;
		private boolean instrument = false;
		private String className = null;
		private String methodName = null;
		private int nonInstrumented = 0;

		MethodLoopCounterScanner() {
		}

		MethodLoopCounterScanner(String className, String methodName, boolean instrument) {
			this.instrument = instrument;
			this.className = className;
			this.methodName = methodName;
		}

		@Override
		public void visitCtForEach(CtForEach loop) {
			int line = loop.getPosition().getLine();
			++forEachLoopCount;
			instrumentLoop(loop, line);
			super.visitCtForEach(loop);
		}

		@Override
		public void visitCtWhile(CtWhile loop) {
			int line = loop.getPosition().getLine();
			++whileLoopCount;
			instrumentLoop(loop, line);
			super.visitCtWhile(loop);
		}

		@Override
		public void visitCtDo(CtDo loop) {
			int line = loop.getPosition().getLine();
			++doLoopCount;
			instrumentLoop(loop, line);
			super.visitCtDo(loop);
		}

		@Override
		public void visitCtFor(CtFor loop) {
			int line = loop.getPosition().getLine();
			++forLoopCount;
			instrumentLoop(loop, line);
			super.visitCtFor(loop);
		}

		private void instrumentLoop(CtLoop loop, int line) {
			if (instrument) {
				try {
					String name = className + "." + methodName + "." + line;
					String codeSnippet = GeneralStatsProcessor.LOG_LOOP_LINE.replaceAll("__NAME",
							Matcher.quoteReplacement(name));
					CtCodeSnippetStatement statementInLoop = loop.getFactory().Code()
							.createCodeSnippetStatement(codeSnippet);
					loop.getBody().insertBefore(statementInLoop);
					// System.out.println(loop.prettyprint());
				} catch (Exception e) {
					++nonInstrumented;
					Logging.exception(LOG, e, loop.prettyprint());
				}
			}

		}

		public int getTotalLoops() {
			return forEachLoopCount + whileLoopCount + doLoopCount + forLoopCount;
		}

		public int getForEachLoopCount() {
			return forEachLoopCount;
		}

		public int getWhileLoopCount() {
			return whileLoopCount;
		}

		public int getDoLoopCount() {
			return doLoopCount;
		}

		public int getForLoopCount() {
			return forLoopCount;
		}

		public int getNonInstrumented() {
			return nonInstrumented;
		}
	}

	private static class MethodFieldAccessScanner extends CtScanner {

		private Set<SDField> fields;

		public MethodFieldAccessScanner(Set<SDField> fields) {
			this.fields = fields;
		}

		private boolean lookup(String className, String name) {
			for (SDField field : fields) {
				if (field.getRelatedFieldId().compareToIgnoreCase(SDField.getRelatedFieldId(className, name)) == 0) {
					return true;
				}
			}
			return false;
		}

		@Override
		public void visitCtFieldRead(CtFieldRead read) {
			String[] fullName = read.getVariable().getQualifiedName().split(AnalysisUtils.ID_SEP_FIELD);
			if (lookup(fullName[0], fullName[1])) {
				LOG.info(read.getVariable().getQualifiedName());
				CtMethod parent = findParent(read);
				LOG.info(parent.getSignature());
				LOG.info(parent.getBody().prettyprint());
				LOG.info("-----");
			}
		}

		private CtMethod findParent(CtFieldRead read) {
			CtElement parent = read;
			while (parent != null) {
				parent = parent.getParent();
				if (parent instanceof CtMethod) {
					return (CtMethod) parent;
				}
			}
			return null;
		}

	}

}
