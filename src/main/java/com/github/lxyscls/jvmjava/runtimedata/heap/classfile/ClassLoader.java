/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.github.lxyscls.jvmjava.runtimedata.heap.classfile;

import com.github.lxyscls.jvmjava.classfile.ClassFile;
import com.github.lxyscls.jvmjava.classfile.ClassReader;
import com.github.lxyscls.jvmjava.classpath.ClassPath;
import com.github.lxyscls.jvmjava.runtimedata.heap.classfile.constant.ConstantPool;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author sk-xinyilong
 */
public class ClassLoader {
    private final ClassPath cp;
    private final Map<String, Jclass> classMap;
    
    public ClassLoader(ClassPath cp) {
        this.cp = cp;
        classMap = new HashMap<>();
    }
    
    public Jclass loadClass(String name) throws IOException {
        if (classMap.containsKey(name)) {
            return classMap.get(name);
        }
        return loadNonArrayClass(name);
    }
    
    private Jclass loadNonArrayClass(String name) throws IOException {
        Jclass cls = defineClass(this.cp.readClass(name));
        link(cls);
        classMap.put(cls.getClassName(), cls);
        return cls;
    }
    
    private Jclass defineClass(byte[] data) throws IOException {
        Jclass cls = new Jclass(new ClassFile(new ClassReader(data)));
        cls.setClassLoader(this);
        resolveSuperClass(cls);
        resolveInterfaces(cls);
        return cls;
    }

    private void link(Jclass cls) {
        verify(cls);
        prepare(cls);
    }

    private void resolveSuperClass(Jclass cls) throws IOException {
        if (!cls.getClassName().equals("java/lang/Object")) {
            cls.setSuperClass(cls.getClassLoader().loadClass(cls.getSuperClassName()));
        }
    }

    private void resolveInterfaces(Jclass cls) throws IOException {
        String[] interfaceNames = cls.getInterfaceNames();
        Jclass[] interfaces = new Jclass[interfaceNames.length];
        for (int i = 0; i < interfaces.length; i++) {
            interfaces[i] = cls.getClassLoader().loadClass(interfaceNames[i]);
        }
        cls.setInterfaceClasses(interfaces);
    }

    private void verify(Jclass cls) {
    }

    private void prepare(Jclass cls) {
        calcInstanceFieldsSlotIds(cls);
        clacStaticFieldsSlotIds(cls);
        allocAndInitStaticVars(cls);
    }

    private void calcInstanceFieldsSlotIds(Jclass cls) {
        int slotId = 0;
        if (cls.getSuperClass() != null) {
            slotId += cls.getSuperClass().getInstanceFieldCount();
        }
        for (Field field : cls.getFields()) {
            if (!field.isStatic()) {
                field.setSlotId(slotId);
                slotId += 1;
                if (field.isLongOrDouble()) {
                    slotId += 1;
                }
            }
        }
        cls.setInstanceFieldCount(slotId);
    }

    private void clacStaticFieldsSlotIds(Jclass cls) {
        int slotId = 0;
        for (Field field : cls.getFields()) {
            if (field.isStatic()) {
                field.setSlotId(slotId);
                slotId += 1;
                if (field.isLongOrDouble()) {
                    slotId += 1;
                }
            }
        }
        cls.setStaticFieldCount(slotId);
    }

    private void allocAndInitStaticVars(Jclass cls) {
        cls.setStaticVars(new Object[cls.getStaticFieldCount()]);
        for (Field field: cls.getFields()) {
            if (field.isStatic() && field.isFinal()) {
                initStaticFinalVar(cls, field);
            }
        }
    }

    private void initStaticFinalVar(Jclass cls, Field field) {
        Object[] staticVars = cls.getStaticVars();
        ConstantPool cp = cls.getConstantPool();
        int constantValueIndex = field.getConstantValueIndex();
        if (constantValueIndex > 0) {
            staticVars[field.getSlotId()] = cp.getConst(constantValueIndex);
        }
    }
}