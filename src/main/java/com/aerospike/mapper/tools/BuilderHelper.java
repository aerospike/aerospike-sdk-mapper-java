package com.aerospike.mapper.tools;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.constraints.NotNull;

import org.apache.commons.lang3.StringUtils;

import com.aerospike.client.sdk.AerospikeException;
import com.aerospike.mapper.tools.configuration.BinConfig;
import com.aerospike.mapper.tools.configuration.ClassConfig;
import com.aerospike.mapper.tools.configuration.Configuration;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

/**
 * Shared utilities for builder classes that configure
 * {@link ClassCache} via YAML or programmatic class loading.
 */
final class BuilderHelper {

    private BuilderHelper() {}

    static Yaml createYamlParser() {
        Constructor constructor = new Constructor(Configuration.class, new LoaderOptions());

        TypeDescription configDesc = new TypeDescription(Configuration.class);
        configDesc.addPropertyParameters("classes", ClassConfig.class);
        constructor.addTypeDescription(configDesc);

        TypeDescription classConfigDesc = new TypeDescription(ClassConfig.class);
        classConfigDesc.substituteProperty("class", String.class, "getClassName", "setClassName");
        classConfigDesc.addPropertyParameters("bins", BinConfig.class);
        constructor.addTypeDescription(classConfigDesc);

        return new Yaml(constructor);
    }

    static void loadConfiguration(@NotNull Configuration configuration, boolean allowsInvalid, Class<?> logSource) {
        if (configuration == null) {
            throw new AerospikeException("YAML configuration is empty or invalid");
        }
        for (ClassConfig config : configuration.getClasses()) {
            try {
                String name = config.getClassName();
                if (StringUtils.isBlank(name)) {
                    throw new AerospikeException("Class with blank name in configuration file");
                } else {
                    try {
                        Class.forName(config.getClassName());
                    } catch (ClassNotFoundException e) {
                        throw new AerospikeException("Cannot find a class with name " + name);
                    }
                }
            } catch (RuntimeException re) {
                if (allowsInvalid) {
                    System.getLogger(logSource.getName())
                            .log(System.Logger.Level.WARNING, "Ignoring issue with configuration: " + re.getMessage());
                } else {
                    throw re;
                }
            }
        }
        ClassCache.getInstance().addConfiguration(configuration);
    }

    static String getPackageName(Class<?> clazz) {
        Class<?> c = clazz.isArray() ? clazz.getComponentType() : clazz;
        if (c.isPrimitive()) {
            return "java.lang";
        }
        String cn = c.getName();
        int dot = cn.lastIndexOf('.');
        return (dot != -1) ? cn.substring(0, dot).intern() : "";
    }

    static Set<Class<?>> findAllClassesUsingClassLoader(String packageName) {
        InputStream stream = ClassLoader.getSystemClassLoader()
                .getResourceAsStream(packageName.replaceAll("[.]", "/"));
        if (stream == null) {
            return Set.of();
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            return reader.lines()
                    .filter(line -> line.endsWith(".class"))
                    .map(line -> getClassForName(line, packageName))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            return Set.of();
        }
    }

    static Class<?> getClassForName(String className, String packageName) {
        try {
            return Class.forName(packageName + "." + className.substring(0, className.lastIndexOf('.')));
        } catch (ClassNotFoundException ignored) {
        }
        return null;
    }
}
