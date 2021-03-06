/*
 * Copyright 2012. Guidewire Software, Inc.
 */

package gw.lang;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target( { } )
@Inherited
@java.lang.Deprecated
@Deprecated("This annotation is deprecated and will be ignored if you add it to a new file. Do NOT use.")
public @interface PublishedType
{
  String fromType();
  String toType();
}