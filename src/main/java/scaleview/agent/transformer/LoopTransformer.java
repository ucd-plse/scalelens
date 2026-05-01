package scaleview.agent.transformer;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.reflect.Modifier;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;
import java.util.regex.Matcher;

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
import scaleview.agent.util.Logging;
import scaleview.agent.util.Pair;

public class LoopTransformer extends AbstractTransformer implements ClassFileTransformer {

	private static final Logger LOG = Logging.getLogger(LoopTransformer.class.getName());
	private static final String DECL = "@VAR = true;";
	private static final String CODE = "try{ if (@VAR) { String __name=\"__NAME\"; java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileOutputStream(new java.io.File(\"inst_loops.txt\"),true));pw.println(__name);pw.close(); @VAR = false; }} catch(Exception e) {}";

	private List<String> includedPackages = null;

	public LoopTransformer(List<String> excludedPackages, List<String> includedPackages) {
		super(excludedPackages);
		this.includedPackages = includedPackages;
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
						if (!isInRelatedPackages(method, includedPackages)) {
							continue;
						}
						// save the uninstrumented code
						CodeAttribute unmodified = null;
						try {
							// This does methods with loops...
							if (!method.isEmpty() &&
									!Modifier.isNative(method.getModifiers()) &&
									!Modifier.isAbstract(method.getModifiers())) {
								unmodified = (CodeAttribute) method.getMethodInfo().getCodeAttribute()
										.copy(method.getMethodInfo().getConstPool(), new HashMap<>());

								Pair<Integer, List<Pair<Integer, Integer>>> locations = lookForLoops(method);
								int count = locations.getRight().size();

								if (count > 0) {
									for (Pair<Integer, Integer> location : locations.getRight()) {
										// this will insert the code at the bottom of the loop...
										String name = className + "." + method.getName() + "." + location.getRight();
										String code = CODE.replaceAll("__NAME", Matcher.quoteReplacement(name))
												.replaceAll("@VAR", "__L" + count);
										insertLoopProfiling(method, location.getLeft(), location.getRight(),
												includedPackages, code, count);
										--count;
									}
								}
							}
							try {
								method.getMethodInfo().getCodeAttribute().computeMaxStack();
								method.getMethodInfo().rebuildStackMap(ClassPool.getDefault());
							} catch (Exception ee) {
								LOG.warning(method.getLongName()
										+ ": Could not recompute stack for instrumented method...");
							}
							++instrumented;
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
		} catch (RuntimeException | InterruptedException | ExecutionException | NotFoundException e) {
			Logging.exception(LOG, e, "Exception when instrumenting class...");
			throw new RuntimeException(e);
		}
	}

	private Pair<Integer, List<Pair<Integer, Integer>>> lookForLoops(CtMethod declaredMethod)
			throws BadBytecode, CannotCompileException {
		int minLine = Integer.MAX_VALUE;
		List<Pair<Integer, Integer>> locations = new ArrayList<>();
		if (declaredMethod.getMethodInfo().getCodeAttribute() != null
				&& declaredMethod.getMethodInfo().getCodeAttribute().iterator() != null) {
			CodeIterator it = declaredMethod.getMethodInfo().getCodeAttribute().iterator();
			LineNumberAttribute lineNumberAttribute = (LineNumberAttribute) declaredMethod.getMethodInfo()
					.getCodeAttribute().getAttribute(LineNumberAttribute.tag);
			while (it.hasNext()) {
				int instruction = it.next();
				int opcode = it.byteAt(instruction);
				switch (opcode) {
					case Opcode.IF_ACMPEQ:
					case Opcode.IF_ACMPNE:
					case Opcode.IF_ICMPEQ:
					case Opcode.IF_ICMPGE:
					case Opcode.IF_ICMPGT:
					case Opcode.IF_ICMPLE:
					case Opcode.IF_ICMPLT:
					case Opcode.IF_ICMPNE:
					case Opcode.IFEQ:
					case Opcode.IFGE:
					case Opcode.IFGT:
					case Opcode.IFLE:
					case Opcode.IFLT:
					case Opcode.IFNE:
					case Opcode.IFNONNULL:
					case Opcode.IFNULL:
					case Opcode.GOTO:
						int offset = it.s16bitAt(instruction + 1);
						if (offset < 0) {
							// it gives me the location of the first instruction
							// of the loop, thus we need to insert an instruction just after this one
							if (lineNumberAttribute != null) {
								locations.add(new Pair(lineNumberAttribute.toLineNumber(instruction),
										lineNumberAttribute.toLineNumber(offset + instruction)));
								if (locations.get(locations.size() - 1).getRight() < minLine)
									minLine = locations.get(locations.size() - 1).getRight();
							}
						}
						break;
				}
			}
		}
		return new Pair(minLine, locations);
	}

	private boolean isInRelatedPackages(CtMethod declaredMethod, List<String> packagePrefixes)
			throws InterruptedException, ExecutionException, NotFoundException {
		String packagePrefix = declaredMethod.getDeclaringClass().getPackageName();
		for (String prefix : packagePrefixes) {
			if (packagePrefix.startsWith(prefix)) {
				return true;
			}
		}
		return false;
	}

	private void insertLoopProfiling(CtMethod declaredMethod, int insertLocation, int line, List<String> prefixes,
			String code, int id)
			throws BadBytecode, CannotCompileException, InterruptedException, ExecutionException, NotFoundException {
		// local variable first
		declaredMethod.addLocalVariable("__L" + id, CtClass.booleanType);
		declaredMethod.insertBefore(DECL.replaceAll("@VAR", "__L" + id));
		declaredMethod.insertAt(insertLocation, code);
	}

}
