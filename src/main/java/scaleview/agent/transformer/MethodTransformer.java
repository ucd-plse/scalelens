package scaleview.agent.transformer;

import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.reflect.Modifier;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.bytecode.BadBytecode;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.LineNumberAttribute;
import javassist.bytecode.Opcode;
import javassist.bytecode.analysis.ControlFlow;
import javassist.bytecode.analysis.ControlFlow.Block;
import scaleview.agent.event.ProfilingEvent;
import scaleview.agent.event.ProfilingEvent.MethodRegisterEvent;
import scaleview.agent.stub.ProfilingStub;
import scaleview.agent.util.Logging;
import scaleview.agent.util.Pair;

public class MethodTransformer extends AbstractTransformer implements ClassFileTransformer {

	private static final Logger LOG = Logging.getLogger(MethodTransformer.class.getName());

	private static final String MID_C = "@MID";
	private static final String LIN_C = "@LIN";
	private static final String SR_C = "@SRC";
	private static final String RMID_C = "@RMID";

	private static final ConcurrentMap<String, Integer> METHODS = new ConcurrentHashMap<>();

	private static final String PROF_LC_VAR = "__LM_ID_";
	private static final String PROF_LE_VAR = "__LE_ENABLED_";
	private static final String PROF_CR_VAR = "__CR_";
	private static final String PROF_CC_VAR = "__CC_";
	private static final String PROF_IT_COUNT = "@COUNTER";
	private static final String PROF_LOOP_COUNTER_PREF = "__LL_@NUM";
	private static final String PROF_LOOP_COUNTER_DECL = PROF_LOOP_COUNTER_PREF + " = 0;";
	// the following statements are inserted at the beginning of the method
	private static final String PROF_CC = String.format("%s = scaleview.agent.stub.ProfilingStub.getOrAddFromChain();",
			PROF_CC_VAR);
	private static final String PROF_MM = String.format(
			"if( %s ) scaleview.agent.stub.ProfilingStub.updateMethodExecutionForRun(%s);", PROF_LE_VAR, PROF_LC_VAR);
	private static final String PROF_LE = String.format("%s = scaleview.agent.stub.ProfilingStub.LE_ENABLED.get();",
			PROF_LE_VAR);
	private static final String PROF_CR = String.format("%s = scaleview.agent.stub.ProfilingStub.CURRENT_RUN.get();",
			PROF_CR_VAR);
	private static final String PROF_LM_CD_NOT_IN_PACKAGES = String.format(
			"%s = __LE_ENABLED_ ? scaleview.agent.stub.ProfilingStub.lookupUsingStackTrace() : 0L;", PROF_LC_VAR);
	private static final String PROF_LM_CD_IN_PACKAGES = String.format("%s = @NUML;", PROF_LC_VAR);

	// the following statements are inserted at the end of the method (right before
	// return)
	private static final String PROF_EVT_CALL_PRE = String.format("if( %s ) { ", PROF_LE_VAR);
	private static final String PROF_EVT_CALL_BODY = String.format(
			"scaleview.agent.stub.ProfilingStub.METHOD_ENTRY.processEvent(new scaleview.agent.event.ProfilingEvent.LoopEntryEvent(Thread.currentThread().getId(), %s, %s, %s, %s, %s, %s, %s));",
			MID_C, LIN_C, PROF_CR_VAR, PROF_CC_VAR, SR_C, RMID_C, PROF_IT_COUNT);
	private static final String PROF_EVT_CALL_CO = "scaleview.agent.stub.ProfilingStub.removeFromChain();}";

	private List<String> includedPackages = null;

	public MethodTransformer(List<String> excludedPackages, List<String> includedPackages) {
		super(excludedPackages);
		this.includedPackages = includedPackages;
		LOG.info("inclusions=" + this.includedPackages);
		LOG.info("exclusions=" + this.exclusions);
		LOG.info("exclusions=" + this.defaultExclusions);
	}

	@Override
	public byte[] transform(ClassLoader classLoader, String className, Class<?> classbject, ProtectionDomain domain,
			byte[] codeBuffer)
			throws IllegalClassFormatException {
		try {
			// not much to do in this case
			if (className == null) {
				return null;
			}
			// we need to do this, since this is the format that classpool recognizes...
			className = className.replaceAll("/", ".");
			CtClass theClass = getClassByName(className);
			int instrumented = 0;
			int total = 0;
			int error = 0;
			boolean success = false;
			// check if the class is something we want to instrument...
			if (theClass != null && !isClassExcluded(className) && !isClassExcludedByDefault(className)
					&& isInstrumentableClass(theClass)) {
				CtMethod[] classMethods = getDeclaredMethods(theClass);
				LOG.info("Found [" + (classMethods != null ? classMethods.length : 0) + "] methods in [" + className
						+ "], checking for targets...");
				// check if the class has methods...
				if (classMethods != null) {
					for (CtMethod method : classMethods) {
						// save the uninstrumented code
						CodeAttribute unmodified = null;
						try {
							// This does methods with loops...
							if (!method.isEmpty() &&
									!Modifier.isNative(method.getModifiers()) &&
									!Modifier.isAbstract(method.getModifiers())) {

								unmodified = (CodeAttribute) method.getMethodInfo().getCodeAttribute()
										.copy(method.getMethodInfo().getConstPool(), new HashMap<>());

								List<Integer> loops = findLoopsStart(method);
								List<Integer> returns = findReturns(method);
								Pair<Integer, Integer> ranges = findRanges(method);

								// register the method, cause from here we get an ID
								long methodId = registerMethod(method, ranges, includedPackages);
								// does it have loops? instrument for them...
								if (loops.size() > 0) {
									// we need to declare the necessary variables too. we do it cause there are
									// loops.
									declareBaseVariables(method, includedPackages, methodId);
									// we take care of the counters for the loops
									List<String> counterDeclarations = declareVariablesForLoops(method, loops);
									// insert the increment statements
									insertCounterIncrements(method, loops, counterDeclarations);
									// and then the return statements
									insertProfilingCalls(method, returns, counterDeclarations, loops, methodId,
											includedPackages);

									// done instrumenting
									try {
										method.getMethodInfo().getCodeAttribute().computeMaxStack();
										method.getMethodInfo().rebuildStackMap(ClassPool.getDefault());
									} catch (Exception ee) {
										LOG.warning(method.getLongName()
												+ ": Could not recompute stack for instrumented method...");
									}
									++instrumented;
								}

							}

						} catch (Exception e) {
							Logging.exception(LOG, e,
									"Exception when instrumenting method [" + method.getLongName() + "]");
							// there is no need to return here
							++error;
							// restore the code
							if (unmodified != null) {
								method.getMethodInfo().removeCodeAttribute();
								method.getMethodInfo().setCodeAttribute(unmodified);
								try {
									method.getMethodInfo().getCodeAttribute().computeMaxStack();
									method.getMethodInfo().rebuildStackMap(ClassPool.getDefault());
								} catch (Exception ee) {
									LOG.severe(method.getLongName()
											+ ": Could not recompute stack for reverted method...");
								}
							} else {
								LOG.severe(method.getLongName() + ": Could not restore code for this method...");
							}
						}
						++total;
					}
					// now get the code buffer and return it...
					try {
						// and done, go ahead...
						// saveGeneratedCode(theClass, "");
						String dir = System.getProperty("user.dir") + "/instrumented";
						LOG.info("Saving instrumented bytecode for class [" + className + "] to [" + dir + "]");
						try {
							saveGeneratedCode(theClass, dir);
						} catch (Exception e) {
							LOG.severe("Could not save instrumented bytecode for class [" + className + "] to [" + dir
									+ "]");
						}
						byte[] modifiedCode = theClass.toBytecode();
						theClass.detach();
						success = true;
						return modifiedCode;
					} catch (Exception e) {
						Logging.exception(LOG, e, "Exception when instrumenting class [" + theClass.getName() + "]");
					} finally {
						if (success) {
							LOG.info(className + ": Instrumented " + instrumented + " methods out of " + total + ", "
									+ error + " marked with errors");
						} else {
							LOG.info(className + ": No method was instrumented!");
						}
					}
				}
			}
			LOG.info("Ignoring [" + className + "]. isNull? = [" + (theClass == null) + "]");
			// dont care about other classes. We return null here because that is what
			// the docs say...
			return null;
		} catch (RuntimeException e) {
			Logging.exception(LOG, e, "Exception when instrumenting class...");
			throw e;
		}
	}

	private void saveGeneratedCode(CtClass klass, String dir) throws Exception {
		DataOutputStream out = new DataOutputStream(new FileOutputStream(dir + "/" + klass.getName() + ".class"));
		klass.getClassFile().write(out);
	}

	private int getLineNumber(CtMethod declaredMethod, int pos) {
		LineNumberAttribute lineNumberAttribute = (LineNumberAttribute) declaredMethod.getMethodInfo()
				.getCodeAttribute().getAttribute(LineNumberAttribute.tag);
		return lineNumberAttribute.toLineNumber(pos);
	}

	// this returns the lines where the loops start
	private List<Integer> findLoopsStart(CtMethod declaredMethod) throws BadBytecode, CannotCompileException {
		ControlFlow cf = new ControlFlow(declaredMethod);
		Block[] blocks = cf.basicBlocks();
		List<Integer> loops = new ArrayList<Integer>();
		for (Block b : blocks) {
			if (b.incomings() == 2 && b.exits() > 1) {
				loops.add(getLineNumber(declaredMethod, b.position()));
			}

		}

		return loops;
	}

	// this returns the lines where the loops start
	private List<Integer> findReturns(CtMethod declaredMethod) throws BadBytecode, CannotCompileException {
		List<Integer> returns = new ArrayList<Integer>();
		if (declaredMethod.getMethodInfo().getCodeAttribute() != null
				&& declaredMethod.getMethodInfo().getCodeAttribute().iterator() != null) {
			CodeIterator it = declaredMethod.getMethodInfo().getCodeAttribute().iterator();
			while (it.hasNext()) {
				int instruction = it.next();
				int opcode = it.byteAt(instruction);
				switch (opcode) {
					case Opcode.IRETURN: {
						returns.add(getLineNumber(declaredMethod, instruction));
					}

				}
			}
		}
		return returns;
	}

	// this returns the lines where the loops start
	private Pair<Integer, Integer> findRanges(CtMethod declaredMethod) throws BadBytecode, CannotCompileException {
		int start = declaredMethod.getMethodInfo().getLineNumber(0);
		int end = 0;
		if (declaredMethod.getMethodInfo().getCodeAttribute() != null
				&& declaredMethod.getMethodInfo().getCodeAttribute().iterator() != null) {
			CodeIterator it = declaredMethod.getMethodInfo().getCodeAttribute().iterator();
			while (it.hasNext()) {
				int instruction = it.next();
				end = getLineNumber(declaredMethod, instruction);
			}
		}
		return new Pair<Integer, Integer>(start, end);
	}

	private long registerMethod(CtMethod declaredMethod, Pair<Integer, Integer> ranges, List<String> packagePrefixes)
			throws InterruptedException, ExecutionException, NotFoundException {
		String methodCacheId = declaredMethod.getDeclaringClass().getName() + "." + declaredMethod.getName() + "."
				+ declaredMethod.getSignature();
		int newId = methodCacheId.hashCode();
		Integer at = METHODS.putIfAbsent(methodCacheId, newId);
		newId = at != null ? at : newId;
		boolean isFromSource = isInRelatedPackages(declaredMethod, packagePrefixes);
		// also save the deckaration for later
		if (ranges.getLeft() >= 0 &&
				ranges.getRight() >= 0 &&
				ranges.getRight() >= ranges.getLeft() &&
				isFromSource) {
			ProfilingStub.updateRangesForMethod(declaredMethod.getDeclaringClass().getName(), declaredMethod.getName(),
					newId, ranges);
		} else {
			if (isFromSource) {
				LOG.severe("Not mapping added for method " + declaredMethod.getDeclaringClass().getName() + "."
						+ declaredMethod.getName());
			}
		}

		// get the signature
		StringBuilder paramTypes = new StringBuilder();
		for (CtClass type : declaredMethod.getParameterTypes()) {
			paramTypes.append(type.getName()).append(",");
		}
		if (paramTypes.length() > 0)
			paramTypes.deleteCharAt(paramTypes.length() - 1);
		// and register a new method
		if (at == null) {
			ProfilingStub.METHOD_ENTRY.processEvent(
					new ProfilingEvent.MethodRegisterEvent(Thread.currentThread().getId(),
							declaredMethod.getDeclaringClass().getName(),
							declaredMethod.getName(),
							"(" + (paramTypes.length() > 0 ? paramTypes.toString() : MethodRegisterEvent.NO_ARGS) + ")",
							(long) newId));
		}

		return newId;
	}

	private boolean isInRelatedPackages(CtMethod declaredMethod, List<String> packagePrefixes)
			throws InterruptedException, ExecutionException, NotFoundException {
		String packagePrefix = declaredMethod.getDeclaringClass().getName();
		for (String prefix : packagePrefixes) {
			if (packagePrefix.startsWith(prefix)) {
				return true;
			}
		}
		return false;
	}

	private List<String> declareVariablesForLoops(CtMethod declaredMethod, List<Integer> loops)
			throws CannotCompileException {
		// we declare the variables for all the loops
		List<String> counterNames = new ArrayList<String>();
		for (Integer loop : loops) {
			String counterDeclaration = PROF_LOOP_COUNTER_DECL.replaceAll("@NUM", String.valueOf(loop));
			String variableName = PROF_LOOP_COUNTER_PREF.replaceAll("@NUM", String.valueOf(loop));
			counterNames.add(variableName);
			declaredMethod.addLocalVariable(variableName, CtClass.intType);
			declaredMethod.insertBefore(counterDeclaration);
		}
		return counterNames;
	}

	private void insertCounterIncrements(CtMethod declaredMethod, List<Integer> loops, List<String> vars)
			throws CannotCompileException {
		// we declare the variables for all the loops
		int current = 0;
		for (Integer loop : loops) {
			String variableName = vars.get(current);
			String variableCode = variableName + "++;";
			declaredMethod.insertAt(loop + 1, variableCode);
			++current;
		}
	}

	private void declareBaseVariables(CtMethod declaredMethod, List<String> prefixes, long methodId)
			throws CannotCompileException, InterruptedException, ExecutionException, NotFoundException {
		// to get the current run and to know if the loop profiling is enabled
		declaredMethod.addLocalVariable(PROF_CR_VAR, CtClass.intType);
		declaredMethod.insertBefore(PROF_CR);
		// to know the call chain
		declaredMethod.addLocalVariable(PROF_CC_VAR, CtClass.longType);
		declaredMethod.insertBefore(PROF_CC);
		// now, we also need the method id around
		declaredMethod.addLocalVariable(PROF_LC_VAR, CtClass.longType);
		if (isInRelatedPackages(declaredMethod, prefixes)) {
			// this case is easy
			declaredMethod.addLocalVariable(PROF_LE_VAR, CtClass.booleanType);
			declaredMethod.insertBefore(PROF_LE + PROF_MM);
			// here, we need to insert something simple.
			declaredMethod.insertBefore(PROF_LM_CD_IN_PACKAGES.replaceAll("@NUM", String.valueOf(methodId)));
		} else {
			// this one is more complex, so these two statements go together, one before the
			// other
			declaredMethod.addLocalVariable(PROF_LE_VAR, CtClass.booleanType);
			declaredMethod.insertBefore(PROF_LE + PROF_LM_CD_NOT_IN_PACKAGES);
		}
	}

	private void insertProfilingCalls(CtMethod declaredMethod, List<Integer> returnStatements,
			List<String> loopCounters,
			List<Integer> loops, long methodId, List<String> prefixes)
			throws CannotCompileException, InterruptedException, ExecutionException, NotFoundException {
		// build the statement
		int current = 0;
		String code = PROF_EVT_CALL_PRE;
		for (String c : loopCounters) {
			code += PROF_EVT_CALL_BODY.replaceAll(MID_C, PROF_LC_VAR)
					.replaceAll(LIN_C, String.valueOf(loops.get(current)))
					.replaceAll(SR_C, Boolean.toString(!isInRelatedPackages(declaredMethod, prefixes)))
					.replaceAll(PROF_IT_COUNT, c)
					.replaceAll(RMID_C, PROF_LC_VAR);
			++current;
		}
		code += PROF_EVT_CALL_CO;
		// now insert after every return statement
		if (returnStatements.size() > 0) {
			for (int r : returnStatements) {
				declaredMethod.insertAt(r, code);
			}
		} else {
			declaredMethod.insertAfter(code);
		}

	}

}
