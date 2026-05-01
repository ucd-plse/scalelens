package scaleview.analysis.types;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import scaleview.analysis.utils.AnalysisUtils;
import spoon.reflect.declaration.CtMethod;

public class SpoonMethodReference extends SpoonReference {

	private SpoonClassReference declaringCLass;
	private CtMethod method;
	private SpoonReferenceContainer<SpoonMethodReference> concreteImplementations;
	private Map<Dimension, Integer> dimensions;

	public SpoonMethodReference(SpoonClassReference klass, CtMethod m) {
		super(AnalysisUtils.getMethodId(m));
		method = m;
		declaringCLass = klass;
		dimensions = new HashMap<>();
		concreteImplementations = new SpoonReferenceContainer<>();
	}

	public SpoonMethodReference(String n, SpoonClassReference klass, CtMethod m) {
		super(n);
		method = m;
		declaringCLass = klass;
		dimensions = new HashMap<>();
		concreteImplementations = new SpoonReferenceContainer<>();
	}

	public AppMethod asAppMethod() {
		AppMethod mm = new AppMethod();
		mm.setClassName(method.getDeclaringType().getQualifiedName());
		mm.setMethodSignature(method.getSignature());
		for (Entry<Dimension, Integer> dentry : dimensions.entrySet())
			mm.getDimensions().put(dentry.getKey(), dentry.getValue());
		mm.setLineNumber(method.getPosition().getLine());
		return mm;
	}

	public boolean findConcreteImplementations(SpoonReferenceContainer<SpoonMethodReference> methods) {
		if (method.isAbstract()) {
			for (SpoonClassReference subc : declaringCLass.getSubClasses()) {
				for (SpoonMethodReference mm : subc.getMethods()) {
					if (!mm.method.isAbstract() && mm.method.isOverriding(method)) {
						concreteImplementations.putValue(mm);
					}
				}
			}
		}
		return true;
	}

	public String getMethodId() {
		return id;
	}

	public CtMethod getMethod() {
		return method;
	}

	public SpoonReferenceContainer<SpoonMethodReference> getConcreteImplementations() {
		return concreteImplementations;
	}

	public Map<Dimension, Integer> getDimensions() {
		return dimensions;
	}

}
