package com.study.ioc.context.impl;

import com.study.ioc.context.ApplicationContext;
import com.study.ioc.entity.Bean;
import com.study.ioc.entity.BeanDefinition;
import com.study.ioc.exception.BeanInstantiationException;
import com.study.ioc.exception.NoSuchBeanDefinitionException;
import com.study.ioc.exception.NoUniqueBeanOfTypeException;
import com.study.ioc.exception.ProcessPostConstructException;
import com.study.ioc.reader.BeanDefinitionReader;
import com.study.ioc.reader.sax.XmlBeanDefinitionReader;

import java.lang.reflect.Method;
import java.util.*;

public class GenericApplicationContext implements ApplicationContext {

    private Map<String, Bean> beans;

    GenericApplicationContext() {
    }

    public GenericApplicationContext(String... paths) {
        this(new XmlBeanDefinitionReader(paths));
    }

    public GenericApplicationContext(BeanDefinitionReader definitionReader) {
        Map<String, BeanDefinition> beanDefinitions = definitionReader.getBeanDefinition();

        beans = createBeans(beanDefinitions);
        injectValueDependencies(beanDefinitions, beans);
        injectRefDependencies(beanDefinitions, beans);
    }

    @Override
    public Object getBean(String beanId) {
        Bean bean = beans.get(beanId);
        if (bean == null) {
            throw new NoSuchBeanDefinitionException(beanId, null, null);
        }
        return bean.getValue();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getBean(Class<T> clazz) {
        List<Bean> foundBeans = new ArrayList<>(1);
        for (Bean bean: beans.values()) {
            if (bean.getValue() != null && bean.getValue().getClass() == clazz) {
                foundBeans.add(bean);
            }
        }
        if (foundBeans.size() == 0) {
            throw new NoSuchBeanDefinitionException(null, clazz.getName(), null);
        }
        if (foundBeans.size() > 1) {
            throw new NoUniqueBeanOfTypeException("More than one Bean of class " + clazz.getName());
        }
        return (T) foundBeans.get(0).getValue();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getBean(String id, Class<T> clazz) {
        Bean bean = beans.get(id);
        if (bean == null) {
            throw new NoSuchBeanDefinitionException(id, clazz.getName(), null);
        }
        if (bean.getValue().getClass() != clazz) {
            throw new NoSuchBeanDefinitionException(id, clazz.getName(), bean.getValue().getClass().getName());
        }
        return (T) bean.getValue();
    }

    @Override
    public List<String> getBeanNames() {
        ArrayList<String> beanNames = new ArrayList<>(beans.size());
        beanNames.addAll(beans.keySet());
        return beanNames;
    }

    Map<String, Bean> createBeans(Map<String, BeanDefinition> beanDefinitionMap) {
        Map<String, Bean> beanNamesToBeans = new HashMap<>(beanDefinitionMap.size());
        for (BeanDefinition beanDefinition : beanDefinitionMap.values()) {
            try {
                Object beanObject = Class.forName(beanDefinition.getClassName()).getConstructor().newInstance();
                Bean bean = new Bean(beanDefinition.getId(), beanObject);
                beanNamesToBeans.put(bean.getId(), bean);
            } catch (Exception e) {
                throw new BeanInstantiationException("Error instantiating bean", e);
            }
        }
        return beanNamesToBeans;
    }

    void injectValueDependencies(Map<String, BeanDefinition> beanDefinitions, Map<String, Bean> beans) {
        for (Bean bean : beans.values()) {
            BeanDefinition beanDefinition = beanDefinitions.get(bean.getId());
            if (beanDefinition != null) { // todo it's possible but weird
                for (Map.Entry<String, String> property : beanDefinition.getValueDependencies().entrySet()) {
                    try {
                        final Object beanObject = bean.getValue();
                        Method setter = getPropertySetter(beanObject, property.getKey());
                        injectValue(beanObject, setter, property.getValue());
                    } catch (Exception e) {
                        throw new ProcessPostConstructException("Error setting property", e);
                    }
                }
            }
        }
    }

    void injectRefDependencies(Map<String, BeanDefinition> beanDefinitions, Map<String, Bean> beans) {
        for (Bean bean : beans.values()) {
            BeanDefinition beanDefinition = beanDefinitions.get(bean.getId());
            if (beanDefinition != null) { // todo it's possible but weird
                for (Map.Entry<String, String> property : beanDefinition.getRefDependencies().entrySet()) {
                    try {
                        final Object beanObject = bean.getValue();
                        Object dependency = beans.get(property.getValue()).getValue();
                        injectDependency(beanObject, getSetterName(property.getKey()), dependency);
                    } catch (Exception e) {
                        throw new ProcessPostConstructException("Error setting property", e);
                    }
                }
            }
        }
    }

    private Method getPropertySetter(Object beanObject, String propertyName) throws NoSuchMethodException {
        final String setterName = getSetterName(propertyName);
        try {
            return beanObject.getClass().getDeclaredMethod(setterName, String.class);
        } catch (NoSuchMethodException ignored) {}
        try {
            return beanObject.getClass().getDeclaredMethod(setterName, Integer.TYPE);
        } catch (NoSuchMethodException ignored) {}
        try {
            return beanObject.getClass().getDeclaredMethod(setterName, Double.TYPE);
        } catch (NoSuchMethodException ignored) {}
        try {
            return beanObject.getClass().getDeclaredMethod(setterName, Boolean.TYPE);
        } catch (NoSuchMethodException ignored) {}
        throw new NoSuchMethodException();
    }

    private String getSetterName(String fieldName) {
        return "set" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
    }

    void injectValue(Object object, Method classMethod, String propertyValue) throws ReflectiveOperationException {
        Class<?> parameterType = classMethod.getParameterTypes()[0];
        if (parameterType == String.class) {
            classMethod.invoke(object, propertyValue);
        } else if (parameterType == Integer.TYPE) {
            classMethod.invoke(object, Integer.parseInt(propertyValue));
        } else if (parameterType == Double.TYPE) {
            classMethod.invoke(object, Double.parseDouble(propertyValue));
        }
    }

    void injectDependency(Object object, String setterName, Object dependencyObject) throws ReflectiveOperationException {
        for (Method declaredMethod : object.getClass().getDeclaredMethods()) {
            if (declaredMethod.getName().equals(setterName)) {
                declaredMethod.invoke(object, dependencyObject);
                return;
            }
        }
    }

    void setBeans(Map<String, Bean> beans) {
        this.beans = beans;
    }
}
