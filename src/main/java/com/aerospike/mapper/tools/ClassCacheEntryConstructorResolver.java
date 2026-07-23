package com.aerospike.mapper.tools;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.aerospike.client.sdk.AerospikeException;
import com.aerospike.mapper.annotations.AerospikeConstructor;
import com.aerospike.mapper.annotations.ParamFrom;
import com.aerospike.mapper.tools.DeferredObjectLoader.DeferredObject;

final class ClassCacheEntryConstructorResolver<T> {

    private final ClassCacheEntry<T> entry;
    private String[] constructorParamBins = new String[0];
    private Object[] constructorParamDefaults = new Object[0];
    private Constructor<T> constructor;
    private Method factoryConstructorMethod;
    private FactoryMethodType factoryConstructorType;

    private enum FactoryMethodType {
        NO_PARAMS, CLASS, MAP, CLASS_MAP
    }

    ClassCacheEntryConstructorResolver(ClassCacheEntry<T> entry) {
        this.entry = entry;
    }

    void resolve() {
        Method factoryMethod = findConstructorFactoryMethod();
        if (!entry.getRawClassInternal().isInterface()) {
            if (factoryMethod == null) {
                findConstructor();
            } else {
                setConstructorFactoryMethod(factoryMethod);
            }
        }
    }

    private boolean validateFactoryMethod(Method method) {
        if ((method.getModifiers() & Modifier.STATIC) == Modifier.STATIC
                && entry.getFactoryMethodNameInternal().equals(method.getName())) {
            Parameter[] params = method.getParameters();
            return switch (params.length) {
                case 0 -> true;
                case 1 -> Class.class.isAssignableFrom(params[0].getType())
                        || Map.class.isAssignableFrom(params[0].getType());
                case 2 -> Class.class.isAssignableFrom(params[0].getType())
                        && Map.class.isAssignableFrom(params[1].getType());
                default -> false;
            };
        }
        return false;
    }

    private Method findConstructorFactoryMethod() {
        if (!StringUtils.isBlank(entry.getFactoryClassNameInternal())
                || !StringUtils.isBlank(entry.getFactoryMethodNameInternal())) {
            if (StringUtils.isBlank(entry.getFactoryClassNameInternal())) {
                throw new AerospikeException(
                        String.format("Missing factoryClass definition when factoryMethod is specified on class %s",
                                entry.getRawClassInternal().getSimpleName()));
            }
            if (StringUtils.isBlank(entry.getFactoryMethodNameInternal())) {
                throw new AerospikeException(
                        String.format("Missing factoryMethod definition when factoryClass is specified on class %s",
                                entry.getRawClassInternal().getSimpleName()));
            }
            try {
                Class<?> factoryClazzType = Class.forName(entry.getFactoryClassNameInternal());
                Method foundMethod = null;
                for (Method method : factoryClazzType.getDeclaredMethods()) {
                    if (validateFactoryMethod(method)) {
                        if (foundMethod != null) {
                            throw new AerospikeException(String.format(
                                    "Factory Class %s defines at least 2 valid factory methods (%s, %s) as a factory for class %s",
                                    entry.getFactoryClassNameInternal(), foundMethod, method,
                                    entry.getRawClassInternal().getSimpleName()));
                        }
                        foundMethod = method;
                    }
                }
                if (foundMethod == null) {
                    throw new AerospikeException(String.format("Class %s specified a factory class of %s and a factory"
                            + " method of %s, but no valid method with that name exists on the class. A valid"
                            + " method must be static, can take no parameters, a single Class parameter, a single"
                            + " Map parameter, or a Class and a Map parameter, and must return an object which is"
                            + " either an ancestor, descendant or equal to %s",
                            entry.getRawClassInternal().getSimpleName(), entry.getFactoryClassNameInternal(),
                            entry.getFactoryMethodNameInternal(), entry.getRawClassInternal().getSimpleName()));
                }
                return foundMethod;
            } catch (ClassNotFoundException cnfe) {
                throw new AerospikeException(String.format("Factory class %s for class %s cannot be loaded",
                        entry.getFactoryClassNameInternal(), entry.getRawClassInternal().getSimpleName()));
            }
        }
        return null;
    }

    private void setConstructorFactoryMethod(Method method) {
        this.factoryConstructorMethod = method;
        this.factoryConstructorMethod.setAccessible(true);

        this.factoryConstructorType = switch (method.getParameterCount()) {
            case 0 -> FactoryMethodType.NO_PARAMS;
            case 2 -> FactoryMethodType.CLASS_MAP;
            case 1 -> Class.class.isAssignableFrom(method.getParameters()[0].getType())
                    ? FactoryMethodType.CLASS
                    : FactoryMethodType.MAP;
            default -> throw new AerospikeException("Unexpected factory method signature: " + method);
        };
    }

    @SuppressWarnings("unchecked")
    private void findConstructor() {
        Constructor<?>[] constructors = entry.getRawClassInternal().getDeclaredConstructors();
        if (constructors.length == 0) {
            throw new AerospikeException(String.format(
                    "Class %s has no constructors and hence cannot be mapped to Aerospike",
                    entry.getRawClassInternal().getSimpleName()));
        }
        Constructor<?> desiredConstructor = null;
        Constructor<?> noArgConstructor = null;
        if (constructors.length == 1) {
            desiredConstructor = constructors[0];
        } else {
            for (Constructor<?> thisConstructor : constructors) {
                if (thisConstructor.getParameters().length == 0) {
                    noArgConstructor = thisConstructor;
                }
                AerospikeConstructor aerospikeConstructor = thisConstructor.getAnnotation(AerospikeConstructor.class);
                if (aerospikeConstructor != null) {
                    if (desiredConstructor != null) {
                        throw new AerospikeException(String.format(
                                "Class %s has multiple constructors annotated with @AerospikeConstructor. Only one constructor can be so annotated.",
                                entry.getRawClassInternal().getSimpleName()));
                    }
                    desiredConstructor = thisConstructor;
                }
            }
        }
        if (desiredConstructor == null && noArgConstructor != null) {
            constructorParamBins = new String[0];
            desiredConstructor = noArgConstructor;
        }

        if (desiredConstructor == null) {
            throw new AerospikeException(String.format("Class %s has neither a no-arg constructor, "
                    + "nor a constructor annotated with @AerospikeConstructor so cannot be mapped to Aerospike.",
                    entry.getRawClassInternal().getSimpleName()));
        }

        Parameter[] params = desiredConstructor.getParameters();
        this.constructorParamBins = new String[params.length];
        this.constructorParamDefaults = new Object[params.length];

        Map<String, ValueType> allValues = new HashMap<>();
        for (ClassCacheEntry<?> current = entry; current != null; current = current.getSuperClazzInternal()) {
            allValues.putAll(current.getValuesInternal());
        }

        int count = 0;
        for (Parameter thisParam : params) {
            count++;
            boolean isFromAnnotation = false;
            String binName = thisParam.getName();
            ParamFrom parameterDetails = thisParam.getAnnotation(ParamFrom.class);

            if (parameterDetails != null) {
                binName = parameterDetails.value();
                isFromAnnotation = true;
            }

            if (!allValues.containsKey(binName)) {
                String valueList = String.join(",", entry.getValuesInternal().keySet());
                String message = String.format("Class %s has a preferred constructor of %s. However, parameter %d is "
                        + "mapped to bin \"%s\" %s which is not one of the values on the class, which are: %s%s",
                        entry.getRawClassInternal().getSimpleName(), desiredConstructor, count, binName,
                        isFromAnnotation ? "via the @ParamFrom annotation" : "via the argument name", valueList,
                        (!isFromAnnotation && binName.startsWith("arg"))
                                ? ". Did you forget to specify '-parameters' to javac when building?"
                                : "");
                throw new AerospikeException(message);
            }
            Class<?> type = thisParam.getType();
            if (!type.isAssignableFrom(allValues.get(binName).getType())) {
                throw new AerospikeException(String.format(
                        "Class %s has a preferred constructor of %s. However, parameter %s is of type %s but assigned from bin \"%s\" of type %s. These types are incompatible.",
                        entry.getRawClassInternal().getSimpleName(), desiredConstructor, count, type, binName,
                        allValues.get(binName).getType()));
            }
            constructorParamBins[count - 1] = binName;
            constructorParamDefaults[count - 1] = PrimitiveDefaults.getDefaultValue(thisParam.getType());
        }
        this.constructor = (Constructor<T>) desiredConstructor;
        this.constructor.setAccessible(true);
    }

    @SuppressWarnings("unchecked")
    T instantiate(Map<String, Object> javaValuesMap, HydrationContext ctx) throws ReflectiveOperationException {
        T result;
        if (factoryConstructorMethod != null) {
            Object[] args = switch (factoryConstructorType) {
                case NO_PARAMS -> new Object[0];
                case CLASS -> new Object[] { entry.getRawClassInternal() };
                case MAP -> new Object[] { javaValuesMap };
                case CLASS_MAP -> new Object[] { entry.getRawClassInternal(), javaValuesMap };
            };
            result = (T) factoryConstructorMethod.invoke(null, args);
        } else {
            Object[] args = new Object[constructorParamBins.length];
            for (int i = 0; i < constructorParamBins.length; i++) {
                if (javaValuesMap.containsKey(constructorParamBins[i])) {
                    args[i] = resolveDeferredValue(javaValuesMap.get(constructorParamBins[i]), ctx);
                } else {
                    args[i] = constructorParamDefaults[i];
                }
                javaValuesMap.remove(constructorParamBins[i]);
            }
            result = constructor.newInstance(args);
        }
        ctx.cacheCurrentObject(result);

        for (String field : javaValuesMap.keySet()) {
            ValueType value = entry.getValuesInternal().get(field);
            Object object = javaValuesMap.get(field);
            if (object == null && value.getType().isPrimitive()) {
                object = PrimitiveDefaults.getDefaultValue(value.getType());
            }
            value.set(result, object, ctx);
        }
        return result;
    }

    private Object resolveDeferredValue(Object value, HydrationContext ctx) {
        if (!(value instanceof DeferredObject deferred)) {
            return value;
        }
        if (deferred.isDigest()) {
            return entry.getEngineInternal().readFromDigest(ctx, deferred.getType(), (byte[]) deferred.getKey(), false)
                    .orElse(null);
        }
        return entry.getEngineInternal().read(ctx, deferred.getType(), deferred.getKey(), false).orElse(null);
    }
}