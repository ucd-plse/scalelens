package scaleview.analysis.processor;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtFieldReference;
import scaleview.agent.util.Logging;
import scaleview.analysis.scanner.MethodBodyScanner;
import scaleview.analysis.types.AppMethod;
import scaleview.analysis.types.SDField;
import scaleview.analysis.types.SDLoop;
import scaleview.analysis.types.SpoonClassReference;
import scaleview.analysis.types.SpoonClassReference.SpoonFieldReference;
import scaleview.analysis.types.SpoonMethodReference;
import scaleview.analysis.types.SpoonReference.SpoonReferenceContainer;
import scaleview.analysis.utils.AnalysisUtils;

public class SDEntryPointProcessor extends AbstractProcessor<CtClass> {

	private static final Logger LOG = Logging.getLogger(SDEntryPointProcessor.class.getName());
	private static final String ENTRY_PROP = "scaleview.entryPointJsonPath";
	private static final String INPUT__PROP = "scaleview.inputJsonPath";
	private static final String OUTPUT__PROP = "scaleview.outputJsonPath";

	private SDEntryPointProcessorConfig config;
	private SDEntryPointProcessorInput input;
	private Set<String> entrypointClasses = new HashSet<>();
	private Set<String> entrypointSignatures = new HashSet<>();
	private SpoonReferenceContainer<SpoonMethodReference> analysisEntryPoints = new SpoonReferenceContainer<>();
	private SpoonReferenceContainer<SpoonClassReference> applicationClasses = new SpoonReferenceContainer<>();
	private SpoonReferenceContainer<SpoonMethodReference> applicationMethods = new SpoonReferenceContainer<>();
	private boolean findEntryPoints = false;
	private Set<SDLoop> matches = new HashSet<>();

	public SDEntryPointProcessor() {
		// here, we load the processor configuration. This is not mandatory
		String epJsonPath = System.getProperty(ENTRY_PROP, null);
		if (epJsonPath != null) {
			try {
				LOG.info("Loading configuration from [" + epJsonPath + "]");
				config = new Gson().fromJson(new FileReader(new File(epJsonPath)), SDEntryPointProcessorConfig.class);
				LOG.info(config.toString());
				for (AppMethod p : config.getEntryPoints()) {
					entrypointClasses.add(p.getClassName());
					entrypointSignatures.add(p.getMethodSignature());
				}
			} catch (IOException ie) {
				Logging.exception(LOG, ie, "Could not read [" + epJsonPath + "]");
			}
		} else {
			LOG.warning("Entrypoints not found, will process everything");
			findEntryPoints = true;
		}
		// load the input too, this is necessary
		String iJsonPath = System.getProperty(INPUT__PROP, null);
		if (iJsonPath != null) {
			try {
				LOG.info("Loading input from [" + iJsonPath + "]");
				input = new Gson().fromJson(new FileReader(new File(iJsonPath)), SDEntryPointProcessorInput.class);
				LOG.info(input.toString());
			} catch (IOException ie) {
				throw new RuntimeException(ie);
			}
		} else {
			throw new IllegalArgumentException("[" + INPUT__PROP + "] is not set");
		}
	}

	@Override
	public void process(CtClass element) {
		// instatiate the class
		SpoonClassReference klass = new SpoonClassReference(element);
		// collect entry points
		List<CtMethod> entryPoints;
		if (findEntryPoints == false && couldBeEntryPointClass(element)) {
			if (entrypointClasses.size() > 0) {
				if ((entryPoints = filterEntryPointsFromClass(element)) != null) {
					for (CtMethod ep : entryPoints) {
						String name = AnalysisUtils.getMethodId(ep);
						LOG.info("Marking [" + name + "] as entrypoint");
						analysisEntryPoints.putValue(new SpoonMethodReference(name, klass, ep));
					}
				}
			} else {
				findEntryPoints = true;
			}
		}
		// collect these methods here, grouping them for quick search
		for (CtMethod method : (Set<CtMethod>) element.getMethods()) {
			// we can also map dependencies while doing this
			SpoonMethodReference mm = new SpoonMethodReference(klass, method);
			for (SDLoop loop : input.sdloops) {
				if (AnalysisUtils.inSameMethod(loop, mm)) {
					LOG.info("Method [" + mm.getMethodId() + "] marked as dependent on dimension ["
							+ loop.getDimension().getName() + "]");
					if (mm.getDimensions().containsKey(loop.getDimension())) {
						mm.getDimensions().put(loop.getDimension(), mm.getDimensions().get(loop.getDimension()) + 1);
					} else {
						mm.getDimensions().put(loop.getDimension(), 1);
					}
					matches.add(loop);
				}
			}
			// and also just store...
			applicationMethods.putValue(mm);
			klass.getMethods().putValue(mm);
		}
		// save the fields too
		for (CtFieldReference field : element.getDeclaredFields()) {
			klass.getFields().putValue(new SpoonFieldReference(field.getDeclaration(), klass));
		}
		// and store here too...
		applicationClasses.putValue(klass);
	}

	@Override
	public void processingDone() {
		LOG.info("Collected [" + applicationClasses.size() + "] application classes");
		LOG.info("Collected [" + applicationMethods.size() + "] application methods");
		MethodBodyScanner.checkSDFields(applicationMethods, input.sdfields);
		if (matches.size() > 0) {
			// this is still part of setup, here we will find subclasses
			LOG.info("Matching classes and subclasses");
			SpoonClassReference.resolveClassHierarchy(applicationClasses);
			// and here we will find implementations of abstract methods...
			LOG.info("Matching concrete method implementations");
			for (SpoonMethodReference ref : applicationMethods) {
				ref.findConcreteImplementations(applicationMethods);
			}
			// check for entry points
			if (!findEntryPoints) {
				LOG.info("[" + analysisEntryPoints.size() + "] methods marked as entrypoints");
			} else {
				LOG.info("No predefined entrypoints, looking for candidates");
				// who are these candidates? There are methods that are not invoked by any other
				// method, those could be entry points
				analysisEntryPoints = MethodBodyScanner.collectPossibleEntryPoints(applicationMethods);
			}
			// now we have candidates, we need to see what is what, lets try to find entry
			// points
			SpoonReferenceContainer<SpoonMethodReference> dependents = MethodBodyScanner
					.collectSDEntryPoints(analysisEntryPoints, applicationMethods);
			if (dependents.size() > 0) {
				LOG.info("Showing [" + dependents.size() + "] dependent methods:");
				Set<AppMethod> deps = new HashSet<>();
				for (SpoonMethodReference dep : dependents) {
					AppMethod mm = dep.asAppMethod();
					LOG.info(mm.toString());
					deps.add(mm);
				}
				// save the output somewhere
				String outputName = System.getProperty(OUTPUT__PROP) != null ? System.getProperty(OUTPUT__PROP)
						: (String.valueOf(new Random().nextInt(1000000)) + ".json");
				LOG.info("Writing results to [" + outputName + "]");
				try {
					AnalysisUtils.toJsonFile(deps, Set.class, outputName);
				} catch (IOException ie) {
					Logging.exception(LOG, ie, "Error when saving output!");
				}
			} else {
				LOG.warning(
						"With the provided input, NO DEPENDENCIES WERE FOUND. This might be an issue on your configuration.");
			}
			LOG.info("Done, exiting...");
			System.exit(0);
		} else {
			LOG.severe("No sdloop was matched in this codebase, cannot continue");
			System.exit(1);
		}
	}

	private List<CtMethod> filterEntryPointsFromClass(CtClass klass) {
		// this cast is supposed to be safe...
		if (entrypointClasses.contains(klass.getQualifiedName())) {
			List<CtMethod> methods = new ArrayList<>();
			for (CtMethod method : (Set<CtMethod>) klass.getMethods()) {
				if (AnalysisUtils.hasBody(method)) {
					if (entrypointSignatures.contains(method.getSignature())) {
						methods.add(method);
					}
				}
			}
			return methods.size() > 0 ? methods : null;
		}
		return null;

	}

	private boolean couldBeEntryPointClass(CtClass klass) {
		return klass != null && !klass.isInterface() && klass.getMethods().size() > 0;
	}

	public static class SDEntryPointProcessorConfig {

		@SerializedName("entryPoints")
		List<AppMethod> entryPoints;

		public List<AppMethod> getEntryPoints() {
			return entryPoints;
		}

		public void setEntryPoints(List<AppMethod> entryPoints) {
			this.entryPoints = entryPoints;
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("SDEntryPointProcessorConfig [entryPoints=").append(entryPoints).append("]");
			return builder.toString();
		}
	}

	public static class SDEntryPointProcessorInput {

		@SerializedName("sdloops")
		Set<SDLoop> sdloops;
		@SerializedName("sdfields")
		Set<SDField> sdfields;

		public Set<SDLoop> getSdloops() {
			return sdloops;
		}

		public void setSdloops(Set<SDLoop> sdloops) {
			this.sdloops = sdloops;
		}

		public Set<SDField> getSdfields() {
			return sdfields;
		}

		public void setSdfields(Set<SDField> sdfields) {
			this.sdfields = sdfields;
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("SDEntryPointProcessorInput [sdloops=").append(sdloops).append(", sdfields=")
					.append(sdfields).append("]");
			return builder.toString();
		}

	}

}
