/*
 * Copyright 2012. Guidewire Software, Inc.
 */

package gw.internal.gosu.parser.statements;

import gw.internal.gosu.parser.DynamicPropertySymbol;
import gw.lang.reflect.IFeatureInfo;
import gw.lang.parser.CaseInsensitiveCharSequence;
import gw.lang.parser.statements.IPropertyStatement;
import gw.lang.parser.statements.ITerminalStatement;
import gw.internal.gosu.parser.Statement;

/**
 */
public class PropertyStatement extends Statement implements IPropertyStatement
{
  private FunctionStatement _propertyGetterOrSetter;
  private DynamicPropertySymbol _dps;

  public PropertyStatement( FunctionStatement propertyGetterOrSetter, DynamicPropertySymbol dps )
  {
    _propertyGetterOrSetter = propertyGetterOrSetter;
    _dps = dps;
  }

  public FunctionStatement getPropertyGetterOrSetter()
  {
    return _propertyGetterOrSetter;
  }

  public DynamicPropertySymbol getDps()
  {
    return _dps;
  }

  public Object execute()
  {
    // NoOp
    return Statement.VOID_RETURN_VALUE;
  }

  @Override
  public ITerminalStatement getLeastSignificantTerminalStatement()
  {
    return null;
  }

  @Override
  public boolean isNoOp()
  {
    return true;
  }

  @Override
  public String toString()
  {
    return "";
  }

  @Override
  public String getFunctionName()
  {
    return getDps().getDisplayName();
  }

  //***** Delegate to FunctionStatement *****//

  @Override
  public int getNameOffset( CaseInsensitiveCharSequence identifierName )
  {
    return _propertyGetterOrSetter.getNameOffset( identifierName );
  }
  @Override
  public void setNameOffset( int iOffset, CaseInsensitiveCharSequence identifierName )
  {
    _propertyGetterOrSetter.setNameOffset( iOffset, identifierName );
  }

  public boolean declares( CaseInsensitiveCharSequence identifierName )
  {
    return _propertyGetterOrSetter.declares( identifierName ) ||
           _propertyGetterOrSetter.declares( CaseInsensitiveCharSequence.get( "@" + identifierName.toString() + "()" ) );
  }

  public String[] getDeclarations() {
    return new String[] {_propertyGetterOrSetter.getDynamicFunctionSymbol().getDisplayName().replace("@", "")};
  }

  private IFeatureInfo findOwningFeatureInfoOfDeclaredSymbols(CaseInsensitiveCharSequence identifierName)
  {
    return _propertyGetterOrSetter.findOwningFeatureInfoOfDeclaredSymbols( identifierName );
  }

}
