/*
 * Copyright 2012. Guidewire Software, Inc.
 */

package gw.internal.gosu.parser;

import gw.fs.IFile;
import gw.fs.IResource;
import gw.lang.reflect.INamespaceType;
import gw.lang.reflect.IType;
import gw.lang.reflect.ITypeLoader;
import gw.lang.reflect.RefreshRequest;
import gw.lang.reflect.RefreshKind;
import gw.lang.reflect.module.ITypeLoaderStack;

import java.util.List;

/**
 */
public interface ITypeLoaderStackInternal extends ITypeLoaderStack {

  List<ITypeLoader> getTypeLoaders();

  void clearErrorTypes();

  INamespaceType getNamespaceType( String strName );

  IType getIntrinsicTypeFromObject( Object object );

  IType getTypeByFullNameIfValid( String fullyQualifiedName );

  IFile getResource(String strResourceName);

  void refresh();

  void refresh(IResource file, RefreshKind refreshKind);

  void clearFromCaches(RefreshRequest typesToClear);
}
