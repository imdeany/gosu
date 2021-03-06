/*
 * Copyright 2012. Guidewire Software, Inc.
 */

package gw.lang.reflect.gs;

import gw.lang.reflect.java.IJavaType;


public interface IGosuClassLoader
{
  Class<?> findClass( String strName ) throws ClassNotFoundException;

  IJavaType getFunctionClassForArity(int length);

  void dumpAllClasses();

  Class loadClass(String className) throws ClassNotFoundException;

  ClassLoader getActualLoader();

  Class defineClass( String name, byte[] bytes );

  String getInterfaceMethodsClassName( ICompilableType gsClass );

  byte[] getBytes( ICompilableType gsClass, boolean compiledToUberModule );

  byte[] maybeDefineInterfaceMethodsClass( ICompilableType gosuClass );

  void assignParent( ClassLoader classLoader );
}
