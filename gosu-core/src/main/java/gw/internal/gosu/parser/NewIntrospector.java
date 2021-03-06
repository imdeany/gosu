/*
 * Copyright 2012. Guidewire Software, Inc.
 */

package gw.internal.gosu.parser;

import gw.lang.reflect.gs.IGosuObject;
import gw.util.concurrent.LockingLazyVar;

import java.beans.BeanDescriptor;
import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.beans.PropertyVetoException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EventListener;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adapted from java.beans.Introspector. Fixes a bug involving covariant return
 * types (jdk 1.5) where a subtype's method is hidden by the super's method.
 */
public class NewIntrospector
{
  // Static Caches to speed up introspection.
  private static Map declaredMethodCache = new WeakHashMap( 100 );
  private static final Object DECLARED_METHODS_LOCK = new Object();

  private static final String GET_PREFIX = "get";
  private static final String SET_PREFIX = "set";
  private static final String IS_PREFIX = "is";

  private static Class<EventListener> eventListenerType = EventListener.class;


  private Class beanClass;

  // Methods maps from Method objects to MethodDescriptors
  private Map<String, GWMethodDescriptor> methods = new LinkedHashMap<String, GWMethodDescriptor>();

  // properties maps from String names to PropertyDescriptors
  private Map<String, PropertyDescriptor> properties = new TreeMap<String, PropertyDescriptor>();

  private HashMap<String, List<PropertyDescriptor>> pdStore;

  private static ConcurrentHashMap<Class, GenericBeanInfo> _beanInfoCache = new ConcurrentHashMap<Class, GenericBeanInfo>();

  private static LockingLazyVar<DeclaredMethodsAccessor> _declaredMethodsAccessor = new LockingLazyVar<DeclaredMethodsAccessor>() {
    @Override
    protected DeclaredMethodsAccessor init() {
      Boolean result = (Boolean)AccessController.doPrivileged( new PrivilegedAction()
      {
        public Object run()
        {
          try
          {
            Method m = Class.class.getDeclaredMethod( "privateGetDeclaredMethods", boolean.class );
            return true;
          }
          catch( Exception e )
          {
            return false;
          }
        }
      });
      if (Boolean.TRUE.equals(result)) {
        return new PrivateGetDeclaredMethodsAccessor();
      } else {
        return new PublicGetDeclaredMethodsAccessor();
      }
    }
  };

  /**
   * Introspect on a Java bean and learn all about its properties, exposed
   * methods, below a given "stop" point.
   * <p/>
   * If the BeanInfo class for a Java Bean has been previously Introspected
   * based on the same arguments, then the BeanInfo class is retrieved
   * from the BeanInfo cache.
   *
   *
   * @param beanClass The bean class to be analyzed.
   *
   * @throws java.beans.IntrospectionException
   *          if an exception occurs during
   *          introspection.
   */
  public static GenericBeanInfo getBeanInfo(Class beanClass)
  {
    GenericBeanInfo bi = _beanInfoCache.get(beanClass);
    if (bi == null) {
      try {
        bi = (new NewIntrospector( beanClass )).getBeanInfo();
        _beanInfoCache.put(beanClass, bi);
      } catch (IntrospectionException e) {
        throw new RuntimeException(e);
      }
    }
    return bi;

// Old behaviour: Make an independent copy of the BeanInfo.
//return new GenericBeanInfo(bi);
  }

  /**
   */
  public static String capitalizeFirstChar( String name )
  {
    if( name == null || name.length() == 0 )
    {
      return name;
    }
    if( name.startsWith( "_" ) )
    {
      return capitalizeFirstChar( name.substring( 1 ) );
    }
    char chars[] = name.toCharArray();
    chars[0] = Character.toUpperCase( chars[0] );
    return new String( chars );
  }


  /**
   * Flush all of the Introspector's internal caches.  This method is
   * not normally required.  It is normally only needed by advanced
   * tools that update existing "Class" objects in-place and need
   * to make the Introspector re-analyze existing Class objects.
   */

  public static void flushCaches()
  {
    declaredMethodCache.clear();
  }

  //======================================================================
  // 			Private implementation methods
  //======================================================================

  private NewIntrospector( Class beanClass )
    throws IntrospectionException
  {
    this.beanClass = beanClass;
  }

  /**
   * Constructs a GenericBeanInfo class from the state of the Introspector
   */
  private GenericBeanInfo getBeanInfo() throws IntrospectionException
  {

// the evaluation order here is import, as we evaluate the
// event sets and locate PropertyChangeListeners before we
// look for properties.
    BeanDescriptor bd = getTargetBeanDescriptor();
    GWMethodDescriptor mds[] = getTargetMethodInfo();
    PropertyDescriptor pds[] = getTargetPropertyInfo();
    return new GenericBeanInfo( bd, pds, mds );
  }

  /**
   * @return An array of PropertyDescriptors describing the editable
   *         properties supported by the target bean.
   */

  private PropertyDescriptor[] getTargetPropertyInfo() throws IntrospectionException
  {

    // Apply some reflection to the current class.
    getPropertiesFromMethods();
    processPropertyDescriptors();

    // Allocate and populate the result array.
    PropertyDescriptor result[] = new PropertyDescriptor[properties.size()];
    result = properties.values().toArray( result );

    return result;
  }

  private void getPropertiesFromMethods()
  {
    // First get an array of all the public methods at this level
    Method methodList[] = getDeclaredMethods(beanClass);

    // Now analyze each method.
    for( Method method : methodList )
    {
      if( method.isSynthetic() )
      {
        continue;
      }

      String name = method.getName();
      Class argTypes[] = method.getParameterTypes();
      Class resultType = method.getReturnType();
      int argCount = argTypes.length;
      PropertyDescriptor pd = null;

      try
      {

        if( argCount == 0 )
        {
          if( name.startsWith( GET_PREFIX ) )
          {
            // Simple getter
            pd = createPropertyDescriptor( capitalizeFirstChar( name.substring( 3 ) ),
                                         method, null );
          }
          else if( name.startsWith( IS_PREFIX ) && (resultType == boolean.class || resultType == Boolean.class)  )
          {
            // Boolean getter
            pd = createPropertyDescriptor( capitalizeFirstChar( name.substring( 2 ) ), method, null );
          }
        }
        else if( argCount == 1 )
        {
          // Simple setter
          if( resultType == void.class && name.startsWith( SET_PREFIX ) )
          {
            pd = new PropertyDescriptor( capitalizeFirstChar( name.substring( 3 ) ),
                                         null, method );
            if( throwsException( method, PropertyVetoException.class ) )
            {
              pd.setConstrained( true );
            }
          }
        }
      }
      catch( IntrospectionException ex )
      {
        // This happens if a PropertyDescriptor
        // constructor fins that the method violates details of the deisgn
        // pattern, e.g. by having an empty name, or a getter returning
        // void , or whatever.
        pd = null;
      }

      if( pd != null )
      {
        addPropertyDescriptor( pd );
      }
    }
  }

  /**
   * Adds the property descriptor to the list store.
   */
  private void addPropertyDescriptor( PropertyDescriptor pd )
  {
    if( pdStore == null )
    {
      pdStore = new HashMap<String, List<PropertyDescriptor>>();
    }

    String propName = pd.getName();
    List<PropertyDescriptor> list = pdStore.get( propName );
    if( list == null )
    {
      list = new ArrayList<PropertyDescriptor>();
      pdStore.put( propName, list );
    }
    for (int i = 0; i < list.size(); i++) {
      PropertyDescriptor otherPd = list.get(i);
      if (pd.getReadMethod() != null && otherPd.getReadMethod() != null) {
        if (pd.getReadMethod().getName().startsWith("is") && otherPd.getReadMethod().getName().startsWith("get")) {
          // do not overwrite a getter with an is
          return;
        }
        else if (pd.getReadMethod().getName().startsWith("get") && otherPd.getReadMethod().getName().startsWith("is")) {
          // overwrite boolean is with get.
          list.remove(i);
          break;
        }
      }
    }
    list.add( pd );
  }

  /**
   * Populates the property descriptor table by merging the
   * lists of Property descriptors.
   */
  private void processPropertyDescriptors() throws IntrospectionException
  {
    if( pdStore == null )
    {
      return;
    }
    List<PropertyDescriptor> list;

    PropertyDescriptor pd, gpd, spd;

    for( List<PropertyDescriptor> propertyDescriptors : pdStore.values() )
    {
      pd = null;
      gpd = null;
      spd = null;

      list = propertyDescriptors;

      // First pass. Find the latest getter method. Merge properties
      // of previous getter methods.
      for( Object aList1 : list )
      {
        pd = (PropertyDescriptor)aList1;
          if( pd.getReadMethod() != null && !pd.getReadMethod().isSynthetic() )
          {
            gpd = pd;
          }
      }

      // Second pass. Find the latest setter method which
      // has the same type as the getter method.
      for( Object aList : list )
      {
        pd = (PropertyDescriptor)aList;
          if( pd.getWriteMethod() != null && !pd.getWriteMethod().isSynthetic() )
          {
            if( gpd != null )
            {
              if( gpd.getPropertyType() == pd.getPropertyType() )
              {
                spd = pd;
              }
            }
            else
            {
              spd = pd;
            }
          }
      }

      // At this stage we should have either PDs or IPDs for the
      // representative getters and setters. The order at which the
      // property decrriptors are determined represent the
      // precedence of the property ordering.
      pd = null;
      if( gpd != null && spd != null )
      {
        pd = createPropertyDescriptor(gpd.getName(), gpd.getReadMethod(), spd.getWriteMethod());
      }
      else if( spd != null )
      {
        Method getterFromSuper = findGetterInSuper( beanClass, spd );
        if( getterFromSuper != null )
        {
          pd = createPropertyDescriptor( spd.getName(), getterFromSuper, spd.getWriteMethod() );
        }
        else
        {
          // simple setter
          pd = spd;
        }
      }
      else if( gpd != null )
      {
        Method setterFromSuper = findSetterInSuper( beanClass, gpd );
        if( setterFromSuper != null )
        {
          pd = createPropertyDescriptor( gpd.getName(), gpd.getReadMethod(), setterFromSuper );
        }
        else
        {
          // simple getter
          pd = gpd;
        }
      }

      if( pd != null )
      {
        properties.put( pd.getName(), pd );
      }
    }
  }

  private PropertyDescriptor createPropertyDescriptor(String name, Method readMethod, Method writeMethod) throws IntrospectionException {
    PropertyDescriptor pd;
    try
    {
      pd = new PropertyDescriptor( name, readMethod, writeMethod );
    }
    catch( IntrospectionException ie )
    {
      pd = new PropertyDescriptor( name, Object.class, null, null );
      // Handle case where the JVM checks whether or not the setter/getter is public (eg. websphere)
      try {
        createPropertyDescriptorForNonPublicMethods(readMethod, writeMethod, pd);
      } catch( NoSuchFieldException nsfe ) {
        pd = null;
      } catch (IllegalAccessException e) {
        pd = null;
      }
    }
    return pd;
  }

  private void createPropertyDescriptorForNonPublicMethods(Method readMethod, Method writeMethod, PropertyDescriptor pd) throws NoSuchFieldException, IllegalAccessException {
    Field field = PropertyDescriptor.class.getDeclaredField("getter");
    field.setAccessible( true );
    field.set( pd, readMethod );

    field = PropertyDescriptor.class.getDeclaredField("setter");
    field.setAccessible( true );
    field.set( pd, writeMethod );
  }

  private Method findSetterInSuper( Class<?> type, PropertyDescriptor gpd )
  {
    if( type == null )
    {
      return null;
    }

    boolean bStatic = Modifier.isStatic( gpd.getReadMethod().getModifiers() );

    Method setter = null;
    Method[] methods = getDeclaredMethods( type );

    for( int i = 0; i < methods.length; i++ )
    {
      Method m = methods[i];
      if( m.isSynthetic() || Modifier.isStatic( m.getModifiers() ) != bStatic )
      {
        continue;
      }
      if( m.getName().equals( SET_PREFIX + gpd.getName() ) )
      {
        //!! Note PropertyDescriptor enforces that the get and set types must match exactly -- it does not support covariance. Figures.
        //  if( m.getParameterTypes().length == 1 &&
        //      m.getParameterTypes()[0].isAssignableFrom( gpd.getPropertyType() ) )
        //  {
        //    if( setter == null || setter.getParameterTypes()[0].isAssignableFrom( m.getParameterTypes()[0] ) )
        //    {
        //      setter = m;
        //    }
        //  }

        if( m.getParameterTypes().length == 1 && m.getParameterTypes()[0] == gpd.getPropertyType() )
        {
          setter = m;
          break;
        }
      }
    }
    if( setter == null )
    {
      setter = findSetterInSuper(type.getSuperclass(), gpd);
      if( setter == null && (type.isInterface() || Modifier.isAbstract( type.getModifiers() )) )
      {
        for( Class iface : type.getInterfaces() )
        {
          setter = findSetterInSuper(iface, gpd);
        }
      }
    }
    return setter;
  }

  private Method findGetterInSuper( Class<?> type, PropertyDescriptor spd )
  {
    if( type == null )
    {
      return null;
    }

    boolean bStatic = Modifier.isStatic( spd.getWriteMethod().getModifiers() );

    Method getter = null;
    Method[] methods = getDeclaredMethods( type );

    for( int i = 0; i < methods.length; i++ )
    {
      Method m = methods[i];
      if( m.isSynthetic() || Modifier.isStatic( m.getModifiers() ) != bStatic )
      {
        continue;
      }
      if( m.getName().equals( GET_PREFIX + spd.getName() ) )
      {
        if( m.getParameterTypes().length == 0 && m.getReturnType() == spd.getPropertyType() )
        {
          getter = m;
          break;
        }
      }
    }
    if( getter == null )
    {
      getter = findGetterInSuper( type.getSuperclass(), spd );
      if( getter == null && (type.isInterface() || Modifier.isAbstract( type.getModifiers() )) )
      {
        for( Class iface : type.getInterfaces() )
        {
          getter = findGetterInSuper( iface, spd );
        }
      }
    }
    return getter;
  }

  /**
   * @return An array of MethodDescriptors describing the private
   *         methods supported by the target bean.
   */
  private GWMethodDescriptor[] getTargetMethodInfo() throws IntrospectionException
  {
    // First get an array of all the beans methods at this level
    Method methodList[] = getDeclaredMethods( beanClass );

    // Now analyze each method.
    for( Method method : methodList )
    {
      if( method.isSynthetic() )
      {
        continue;
      }
      GWMethodDescriptor md = new GWMethodDescriptor( method );
      addMethod( md );
    }

// Allocate and populate the result array.
    GWMethodDescriptor result[] = new GWMethodDescriptor[methods.size()];
    result = methods.values().toArray( result );

    return result;
  }

  private void addMethod( GWMethodDescriptor md )
  {
    // We have to be careful here to distinguish method by both name
    // and argument lists.
    // This method gets called a *lot, so we try to be efficient.

    String name = md.getName();

    GWMethodDescriptor old = methods.get(name);
    if( old == null )
    {
      // This is the common case.
      methods.put( name, md );
      return;
    }

    // We have a collision on method names.  This is rare.

    // Check if old and md have the same type.
    Class p1[] = md.getMethod().getParameterTypes();
    Class p2[] = old.getMethod().getParameterTypes();
    boolean match = false;
    if( p1.length == p2.length )
    {
      match = true;
      for( int i = 0; i < p1.length; i++ )
      {
        if( p1[i] != p2[i] )
        {
          match = false;
          break;
        }
      }
    }
    if( match )
    {
      methods.put( name, md );
      return;
    }

    // We have a collision on method names with different type signatures.
    // This is very rare.
    String longKey = makeQualifiedMethodName( md );
    methods.put( longKey, md );
  }

  private String makeQualifiedMethodName( GWMethodDescriptor md )
  {
    Method m = md.getMethod();
    StringBuilder sb = new StringBuilder();
    sb.append( m.getName() );
    sb.append( '=' );
    Class params[] = m.getParameterTypes();
    for( Class param : params )
    {
      sb.append( ':' );
      sb.append( param.getName() );
    }
    return sb.toString();
  }

  private BeanDescriptor getTargetBeanDescriptor()
  {
    // OK, fabricate a default BeanDescriptor.
    return (new BeanDescriptor( beanClass ));
  }


  public static Method[] getDeclaredMethods( Class clz )
  {
    // For anything within the GosuClassLoader, we want to make sure to 
    if ( IGosuObject.class.isAssignableFrom( clz ) ) {
      return clz.getDeclaredMethods();
    }

    // Looking up Class.getDeclaredMethods is relatively expensive,
    // so we cache the results.
    Method[] result;
    final Class fclz = clz;
    synchronized (DECLARED_METHODS_LOCK) {     
      result = (Method[])declaredMethodCache.get( fclz );
      if( result != null )
      {
        return result;
      }
    }

    result = _declaredMethodsAccessor.get().getDeclaredMethods(clz);
    Arrays.sort(result, new Comparator<Method>() {
      public int compare(Method o1, Method o2) {
        return o1.getName().compareTo(o2.getName());
      }
    });

    // Add it to the cache.
    synchronized (DECLARED_METHODS_LOCK) {
      declaredMethodCache.put( fclz, result );
    }
    return result;
  }

  private static interface DeclaredMethodsAccessor {
    Method[] getDeclaredMethods(Class clz);
  }

  private static class PrivateGetDeclaredMethodsAccessor implements DeclaredMethodsAccessor {
    @Override
    public Method[] getDeclaredMethods(final Class clz) {
      Method[] result = (Method[])AccessController.doPrivileged( new PrivilegedAction()
      {
        public Object run()
        {
          try
          {
            Method m = Class.class.getDeclaredMethod( "privateGetDeclaredMethods", boolean.class );
            m.setAccessible( true );
            return m.invoke( clz, false );
          }
          catch( Exception e )
          {
            System.out.println("WARNING cannot load methods of " + clz.getName() + ". " + e.getMessage());
            return new Method[0];
//            throw new RuntimeException( e );
          }
          // return fclz.getDeclaredMethods();
        }
      } );

      Method[] copy = new Method[result.length]; // copy so as not to mess up the Class' method offsets
      System.arraycopy( result, 0, copy, 0, copy.length );
      result = copy;
      return result;
    }
  }

  public static class PublicGetDeclaredMethodsAccessor implements DeclaredMethodsAccessor {
    @Override
    public Method[] getDeclaredMethods(Class clz) {
      return clz.getDeclaredMethods();
    }
  }

  //======================================================================
  // Package private support methods.
  //======================================================================

  /**
   * Return true iff the given method throws the given exception.
   */
  private boolean throwsException( Method method, Class exception )
  {
    Class exs[] = method.getExceptionTypes();
    for( Class ex : exs )
    {
      if( ex == exception )
      {
        return true;
      }
    }
    return false;
  }


} // end class Introspector