package scaleview.agent.util;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.IdentityHashMap;
import java.util.Map;

import scaleview.agent.stub.ProfilingStub;

public class SizeUtils {

    private static boolean SKIP_POOLED_OBJECTS = false;

    /**
     * Calculates size
     * 
     * @param obj
     *            object to calculate size of
     * @return object size
     */
    public static long shallowSizeOfObject(Object obj) {
        if (SKIP_POOLED_OBJECTS && isPooled(obj))
            return 0;
        return ProfilingStub.INSTRUMENTATION.getObjectSize(obj);
    }

    private static boolean isPooled(Object paramObject) {
        if ((paramObject instanceof Comparable)) {
            if ((paramObject instanceof Enum)) {
                return true;
            }
            if ((paramObject instanceof String)) {
                return paramObject == ((String) paramObject).intern();
            }
            if ((paramObject instanceof Boolean)) {
                return (paramObject == Boolean.TRUE) || (paramObject == Boolean.FALSE);
            }
            if ((paramObject instanceof Integer)) {
                return paramObject == Integer.valueOf(((Integer) paramObject).intValue());
            }
            if ((paramObject instanceof Short)) {
                return paramObject == Short.valueOf(((Short) paramObject).shortValue());
            }
            if ((paramObject instanceof Byte)) {
                return paramObject == Byte.valueOf(((Byte) paramObject).byteValue());
            }
            if ((paramObject instanceof Long)) {
                return paramObject == Long.valueOf(((Long) paramObject).longValue());
            }
            if ((paramObject instanceof Character)) {
                return paramObject == Character.valueOf(((Character) paramObject).charValue());
            }
        }
        return false;
    }

    /**
     * Calculates deep size
     * 
     * @param obj
     *            object to calculate size of
     * @return object deep size
     */
    public static long deepSizeOfObject(Object obj) {

        Map<Object, Object> previouslyVisited = new IdentityHashMap<Object, Object>();
        long result = deepSizeOf(obj, previouslyVisited);
        previouslyVisited.clear();
        return result;
    }

    private static boolean skipObject(Object obj, Map<Object, Object> previouslyVisited) {
        if (SKIP_POOLED_OBJECTS && isPooled(obj))
            return true;
        return (obj == null) || previouslyVisited == null || previouslyVisited.containsKey(obj);
    }

    private static long deepSizeOf(Object obj, Map<Object, Object> previouslyVisited) {
        if (skipObject(obj, previouslyVisited)) {
            return 0;
        }
        previouslyVisited.put(obj, null);

        long returnVal = 0;
        // get size of object + primitive variables + member pointers
        // for array header + len + if primitive total value for primitives
        returnVal += SizeUtils.shallowSizeOfObject(obj);

        // recursively call all array elements
        Class<?> objClass = obj.getClass();
        if (objClass == null)
            return 0;

        if (objClass.isArray()) {
            if (objClass.getName().length() != 2) {// primitive type arrays has length two, skip them (they included in
                                                   // the shallow size)
                int lengthOfArray = Array.getLength(obj);
                for (int i = 0; i < lengthOfArray; i++) {
                    returnVal += deepSizeOf(Array.get(obj, i), previouslyVisited);
                }
            }
        } else {
            // recursively call all fields of the object including the superclass fields
            do {
                Field[] objFields = objClass.getDeclaredFields();
                for (int i = 0; i < objFields.length; i++) {
                    if (!Modifier.isStatic(objFields[i].getModifiers())) {// skip statics
                        if (!objFields[i].getType().isPrimitive()) { // skip primitives
                            objFields[i].setAccessible(true);
                            Object tempObject = null;
                            try {
                                tempObject = objFields[i].get(obj);
                            } catch (IllegalArgumentException e) {
                                // TODO Auto-generated catch block

                            } catch (IllegalAccessException e) {
                                // TODO Auto-generated catch block

                            }
                            if (tempObject != null) {
                                returnVal += deepSizeOf(tempObject, previouslyVisited);
                            }
                        }
                    }
                }
                objClass = objClass.getSuperclass();
            } while (objClass != null);
        }
        return returnVal;
    }
}
