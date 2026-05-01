package scaleview.agent.transformer;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtMethod;

public abstract class AbstractTransformer {

	protected Set<String> exclusions = new HashSet<>();
	protected Set<String> defaultExclusions;

	public AbstractTransformer(List<String> e) {
		exclusions.addAll(e);
		defaultExclusions = new HashSet<>();
		defaultExclusions.add("jdk");
		defaultExclusions.add("javax");
		defaultExclusions.add("scaleview");
		defaultExclusions.add("javassist");
		defaultExclusions.add("java.security");
		defaultExclusions.add("java.util.Properties");
		defaultExclusions.add("java.io.PrintWriter");
		defaultExclusions.add("java.util.concurrent.CompletableFuture");
		defaultExclusions.add("java.util.jar");
		defaultExclusions.add("sun");
		defaultExclusions.add("java.lang.String");
		defaultExclusions.add("org.slf4j");
		defaultExclusions.add("java.lang.Class");
		defaultExclusions.add("com.sum");
		
	}

	public boolean isAssignableFrom(CtClass c1, CtClass c2) {
		return c2.subclassOf(c1);
	}

	public CtClass getClassByName(String className) {
		ClassPool classPool = ClassPool.getDefault();
		try {
			return classPool.get(className);
		} catch (Exception e) {
			return null;
		}
	}

	public CtMethod[] getDeclaredMethods(CtClass klass) {
		return klass.getDeclaredMethods();
	}

	public CtConstructor[] getDeclaredConstructors(CtClass klass) {
		return klass.getDeclaredConstructors();
	}

	public CtMethod[] getDeclaredMethods(String className) {
		CtClass theClass = getClassByName(className);
		if (theClass != null)
			return getDeclaredMethods(theClass);
		return null;
	}

	public boolean isClassExcluded(String className) {
		for (String e : exclusions) {
			if (className.startsWith(e))
				return true;
		}
		return false;
	}

	public boolean isClassExcludedByDefault(String className) {
		for (String e : defaultExclusions) {
			if (className.startsWith(e))
				return true;
		}
		return false;
	}

	public boolean isInstrumentableClass(CtClass klass) {
		return !klass.isAnnotation() &&
				!klass.isArray() &&
				!klass.isPrimitive() &&
				!klass.isInterface();
	}

	public boolean isStaticInnerClass(CtClass klass) {
		return Modifier.isStatic(klass.getModifiers()) && klass.getName().contains("$");
	}
}
