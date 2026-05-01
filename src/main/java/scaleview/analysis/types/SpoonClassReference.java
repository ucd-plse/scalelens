package scaleview.analysis.types;

import java.util.HashSet;
import java.util.Set;

import scaleview.analysis.utils.AnalysisUtils;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtField;
import spoon.reflect.reference.CtTypeReference;

public class SpoonClassReference extends SpoonReference {

	private CtClass klass;
	private SpoonReferenceContainer<SpoonClassReference> subClasses;
	private SpoonReferenceContainer<SpoonMethodReference> methods;
	private SpoonReferenceContainer<SpoonFieldReference> fields;

	public SpoonClassReference(CtClass c) {
		super(c.getQualifiedName());
		klass = c;
		methods = new SpoonReferenceContainer<>();
		subClasses = new SpoonReferenceContainer<>();
		fields = new SpoonReferenceContainer<>();
	}

	public static void resolveClassHierarchy(SpoonReferenceContainer<SpoonClassReference> classes) {
		for (SpoonClassReference klass : classes)
			klass.processClassHierarchy(classes);
	}

	private boolean processClassHierarchy(SpoonReferenceContainer<SpoonClassReference> classes) {
		// lets collect and then filter
		Set<SpoonClassReference> thisSupers = new HashSet<>();
		// super classes
		if (klass.getSuperclass() != null) {
			SpoonClassReference s = classes.getValue(klass.getSuperclass().getQualifiedName());
			if (s != null && s != this) {
				s.subClasses.putValue(this);
				thisSupers.add(s);
			}
		}
		// super interfaces
		if (klass.getSuperInterfaces() != null) {
			for (CtTypeReference ref : klass.getSuperInterfaces()) {
				SpoonClassReference si = classes.getValue(ref.getQualifiedName());
				if (si != null && si != this) {
					si.subClasses.putValue(this);
					thisSupers.add(si);
				}
			}
		}
		// and done, now we can continue with the supers?
		if (thisSupers.size() > 0) {
			for (SpoonClassReference ref : thisSupers) {
				ref.processClassHierarchy(classes);
			}
			return true;
		} else {
			// done
			return true;
		}
	}

	public String getClassId() {
		return id;
	}

	public CtClass getKlass() {
		return klass;
	}

	public SpoonReferenceContainer<SpoonMethodReference> getMethods() {
		return methods;
	}

	public SpoonReferenceContainer<SpoonClassReference> getSubClasses() {
		return subClasses;
	}

	public SpoonReferenceContainer<SpoonFieldReference> getFields() {
		return fields;
	}

	public static class SpoonFieldReference extends SpoonReference {

		private CtField field;
		private SpoonClassReference declaringClass;

		public SpoonFieldReference(CtField field, SpoonClassReference declaringClass) {
			super(declaringClass.getKlass().getQualifiedName() + AnalysisUtils.ID_SEP_FIELD + field.getSimpleName());
			this.field = field;
			this.declaringClass = declaringClass;
		}

		public boolean isSDField(Set<SDField> sdFields) {
			for (SDField f : sdFields) {
				if (f.getRelatedFieldId().compareToIgnoreCase(
						SDField.getRelatedFieldId(declaringClass.getKlass().getQualifiedName(),
								field.getSimpleName())) == 0) {
					return true;
				}
			}
			return false;
		}

		public String getFieldId() {
			return id;
		}

		public CtField getField() {
			return field;
		}

		public SpoonClassReference getDeclaringClass() {
			return declaringClass;
		}

	}

}
