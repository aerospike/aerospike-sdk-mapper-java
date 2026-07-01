package com.aerospike.mapper.tools;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import com.aerospike.mapper.annotations.AerospikeRecord;
import com.aerospike.mapper.tools.configuration.ClassConfig;
import com.aerospike.mapper.tools.configuration.Configuration;
import com.aerospike.mapper.tools.utils.TypeUtils;
import org.yaml.snakeyaml.Yaml;

// TODO: Remove once ReactiveAeroMapper is migrated to MappingEngine + MappingSession
public abstract class AbstractBuilder<T extends IBaseAeroMapper> {
    private final T mapper;
    private List<Class<?>> classesToPreload = null;

    protected AbstractBuilder(T mapper) {
        this.mapper = mapper;
    }
    /**
     * Add in a custom type converter. The converter must have methods which implement the ToAerospike and FromAerospike annotation.
     *
     * @param converter The custom converter
     * @return this object
     */
    public AbstractBuilder<T> addConverter(Object converter) {
        GenericTypeMapper typeMapper = new GenericTypeMapper(converter);
        TypeUtils.addTypeMapper(typeMapper.getMappedClass(), typeMapper);

        return this;
    }

    public AbstractBuilder<T> preLoadClasses(Class<?>... clazzes) {
        if (classesToPreload == null) {
            classesToPreload = new ArrayList<>();
        }
        classesToPreload.addAll(Arrays.asList(clazzes));
        return this;
    }

    public String getPackageName(Class<?> clazz) {
        return BuilderHelper.getPackageName(clazz);
    }

    public AbstractBuilder<T> preLoadClassesFromPackage(Class<?> classInPackage) {
        return preLoadClassesFromPackage(getPackageName(classInPackage));
    }

    public AbstractBuilder<T> preLoadClassesFromPackage(String thePackage) {
        Set<Class<?>> clazzes = BuilderHelper.findAllClassesUsingClassLoader(thePackage);
        for (Class<?> thisClazz : clazzes) {
            if (thisClazz.getAnnotation(AerospikeRecord.class) != null) {
                this.preLoadClass(thisClazz);
            }
        }
        return this;
    }

    public AbstractBuilder<T> preLoadClass(Class<?> clazz) {
        if (classesToPreload == null) {
            classesToPreload = new ArrayList<>();
        }
        classesToPreload.add(clazz);
        return this;
    }

    public AbstractBuilder<T> withConfigurationFile(File file) throws IOException {
        return this.withConfigurationFile(file, false);
    }

    public AbstractBuilder<T> withConfigurationFile(File file, boolean allowsInvalid) throws IOException {
        try (InputStream fis = new FileInputStream(file)) {
            return this.withConfigurationFile(fis, allowsInvalid);
        }
    }

    public AbstractBuilder<T> withConfigurationFile(InputStream ios) throws IOException {
        return this.withConfigurationFile(ios, false);
    }

    public AbstractBuilder<T> withConfigurationFile(InputStream ios, boolean allowsInvalid) throws IOException {
        Yaml yaml = BuilderHelper.createYamlParser();
        Configuration configuration = yaml.load(ios);
        BuilderHelper.loadConfiguration(configuration, allowsInvalid, AbstractBuilder.class);
        return this;
    }

    public AbstractBuilder<T> withConfiguration(String configurationYaml) {
        return this.withConfiguration(configurationYaml, false);
    }

    public AbstractBuilder<T> withConfiguration(String configurationYaml, boolean allowsInvalid) {
        Yaml yaml = BuilderHelper.createYamlParser();
        Configuration configuration = yaml.load(configurationYaml);
        BuilderHelper.loadConfiguration(configuration, allowsInvalid, AbstractBuilder.class);
        return this;
    }

    public AbstractBuilder<T> withClassConfigurations(ClassConfig classConfig, ClassConfig ...classConfigs) {
        Configuration configuration = new Configuration();
        configuration.add(classConfig);
        for (ClassConfig thisConfig : classConfigs) {
            configuration.add(thisConfig);
        }
        ClassCache.getInstance().addConfiguration(configuration);
        return this;
    }

    public T build() {
        if (classesToPreload != null) {
            for (Class<?> clazz : classesToPreload) {
                ClassCache.getInstance().loadClass(clazz, this.mapper);
            }
        }
        return this.mapper;
    }
}
