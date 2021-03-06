/*
 * Copyright 2012. Guidewire Software, Inc.
 */

package gw.internal.gosu.parser;

import gw.internal.gosu.parser.expressions.EvalExpression;
import gw.internal.gosu.template.TemplateTokenizerInstructor;
import gw.lang.parser.ICapturedSymbol;
import gw.lang.parser.IFileContext;
import gw.lang.parser.IGosuProgramParser;
import gw.lang.parser.IParseResult;
import gw.lang.parser.IParseTree;
import gw.lang.parser.IParsedElement;
import gw.lang.parser.ISymbolTable;
import gw.lang.parser.ITypeUsesMap;
import gw.lang.parser.ParseResult;
import gw.lang.parser.ParserOptions;
import gw.lang.parser.exceptions.ParseResultsException;
import gw.lang.parser.statements.IClassStatement;
import gw.lang.reflect.IType;
import gw.lang.reflect.TypeSystem;
import gw.lang.reflect.gs.ClassType;
import gw.lang.reflect.gs.GosuClassTypeLoader;
import gw.lang.reflect.gs.IGosuClass;
import gw.lang.reflect.gs.IGosuProgram;
import gw.lang.reflect.gs.StringSourceFileHandle;
import gw.util.GosuStringUtil;
import gw.util.fingerprint.FP64;

import java.util.List;

/**
 */
public class GosuProgramParser implements IGosuProgramParser
{
  private static int g_iIndex;

  public IParseResult parseEval( String strSource, List<ICapturedSymbol> captured, IType enclosingClass, IParsedElement ctxElem, ISymbolTable extSyms )
  {
    TypeSystem.lock();
    try
    {
      IGosuProgramInternal program;

//      program = ((EvalExpression)evalExpr).getCachedProgram( strSource );
//      if( program != null )
//      {
//        program.isValid();
//        return new ParseResult( program );
//      }

      String typeName = enclosingClass.getName() + '.' + IGosuProgram.NAME_PREFIX + "eval_" + getEvalExprLocationOffset( ctxElem ) + "_" + GosuStringUtil.getSHA1String(strSource);
      StringSourceFileHandle sfh = new StringSourceFileHandle(typeName, strSource, false, ClassType.Eval );
      sfh.setParentType( enclosingClass.getName() );
      ITypeUsesMap typeUsedMap = getTypeUsedMapFrom( ctxElem );
      if( typeUsedMap != null )
      {
        sfh.setTypeUsesMap( typeUsedMap );
      }
      if( ctxElem instanceof EvalExpression )
      {
        sfh.setCapturedTypeVars( ((EvalExpression)ctxElem).getCapturedTypeVars() );
      }
      program = (IGosuProgramInternal)GosuClassTypeLoader.getDefaultClassLoader().makeNewClass( sfh, null );
      program.setEnclosingEvalExpression( ctxElem );
      if( captured != null )
      {
        for( ICapturedSymbol sym : captured )
        {
          program.getParseInfo().addCapturedSymbolSilent( sym );
        }
      }
      sfh.setExternalSymbols( extSyms );
      program.isValid();
//      ((EvalExpression)evalExpr).cacheProgram( strSource, program );
      return new ParseResult( program );
    }
    finally
    {
      TypeSystem.unlock();
    }
  }

  private ITypeUsesMap getTypeUsedMapFrom( IParsedElement ctxElem ) {
    if( ctxElem instanceof EvalExpression ) {
      return ((EvalExpression)ctxElem).getTypeUsesMap();
    }
    IGosuClass gsClass = ctxElem.getGosuClass();
    if( gsClass != null ) {
      return ((IGosuClass)TypeLord.getOuterMostEnclosingClass( gsClass )).getTypeUsesMap().copy();
    }
    return null;
  }

  private int getEvalExprLocationOffset( IParsedElement evalExpr ) {
    IParseTree location = evalExpr.getLocation();
    return location != null ? location.getOffset() : getIndex();
  }

  private String getIndex(String strSource) {
    FP64 fp = new FP64(strSource);
    long rawFingerprint = fp.getRawFingerprint();
    if (rawFingerprint < 0) {
      return "n" + (-rawFingerprint);
    } else {
      return "p" + rawFingerprint;
    }
  }

  @Override
  public IParseResult parseExpressionOnly( String strSource, ISymbolTable symTable, ParserOptions options ) throws ParseResultsException
  {
    return parseExpressionOrProgram( strSource, symTable, options );
  }

  @Override
  public IParseResult parseProgramOnly( String strSource, ISymbolTable symTable, ParserOptions options ) throws ParseResultsException
  {
    return parseExpressionOrProgram( strSource, symTable, options );
  }

  @Override
  public IParseResult parseExpressionOrProgram( String strSource, ISymbolTable symTable, ParserOptions options ) throws ParseResultsException
  {
    TypeSystem.lock();
    try
    {
      int index = getIndex();
      String name = null;
      IFileContext fileContext = options.getFileContext();
      if( fileContext != null )
      {
        name = fileContext.getClassName();
      }

      if( name == null )
      {
        name = IGosuProgram.PACKAGE + '.' + IGosuProgram.NAME_PREFIX + index;
      }

      if( fileContext != null && fileContext.getContextString() != null )
      {
        name += "_" + fileContext.getContextString();
      }

      StringSourceFileHandle sfh = new StringSourceFileHandle( name, strSource, false, ClassType.Program );
      if( fileContext != null )
      {
        sfh.setFilePath(fileContext.getFilePath());
      }
      sfh.setParentType( options.getEnclosingType() );
      sfh.setTypeUsesMap( options.getTypeUsesMap() );
      GosuClassTypeLoader defaultClassLoader = GosuClassTypeLoader.getDefaultClassLoader();
      IGosuProgramInternal program = (IGosuProgramInternal) defaultClassLoader.makeNewClass(sfh, symTable);
      if( options.getParser() != null )
      {
        program.setEditorParser( (GosuParser)options.getParser() );
        program.setCreateEditorParser( options.getParser().isEditorParser() );
      }
      program.setGenRootExprAccess( options.isBGenRootExprAccess() );
      program.setExpectedReturnType( options.getExpectedType() );
      program.setTokenizerInstructor( options.getTi() );
      program.setSuperType( options.getSuperType() );
      program.setAdditionalDFSDecls( options.getAdditionalDFSDecls() );
      program.setStatementsOnly( options.isStatementsOnly() );
      program.setThrowaway( options.isThrowawayProgram() );
      program.setAllowUses( options.allowUses() );
      program.setCtxInferenceMgr( options.getCtxInferenceMgr() );
      if( options.getScriptPartId() != null )
      {
        program.setContextType( options.getScriptPartId().getContainingType() );
      }

      if( !program.isValid() )
      {
        throw program.getParseResultsException();
      }

      if( options.getParser() != null )
      {
        try
        {
          IClassStatement classStatement = program.getClassStatement();
          IParsedElement parent = classStatement.getParent();
          IParsedElement pe = parent != null ? parent : classStatement;
          ((GosuParser)options.getParser()).verifyParsedElement(pe);
        }
        catch( ParseResultsException pre )
        {
          program.setParseResultsException( pre );
        }
      }

      return new ParseResult( program );
    }
    finally
    {
      TypeSystem.unlock();
    }
  }

  public IParseResult parseTemplate( String strSource, ISymbolTable symTable, ParserOptions options ) throws ParseResultsException
  {
    TypeSystem.lock();
    try
    {
      int index = getIndex();
      String name = null;
      if( options.getFileContext() != null )
      {
        name = options.getFileContext().getClassName();
      }

      if( name == null )
      {
        name = IGosuProgram.PACKAGE + '.' + IGosuProgram.NAME_PREFIX + index;
      }

      if( options.getFileContext() != null && options.getFileContext().getContextString() != null )
      {
        name += "_" + options.getFileContext().getContextString();
      }

      StringSourceFileHandle sfh = new StringSourceFileHandle( name, strSource, false, ClassType.Template );
      if( options.getFileContext() != null )
      {
        sfh.setFilePath(options.getFileContext().getFilePath());
      }
      sfh.setTypeUsesMap( options.getTypeUsesMap() );
      IGosuProgramInternal program = (IGosuProgramInternal)GosuClassTypeLoader.getDefaultClassLoader().makeNewClass( sfh, symTable );
      if( options.getParser() != null )
      {
        program.setEditorParser( (GosuParser)options.getParser() );
        program.setCreateEditorParser( options.getParser().isEditorParser() );
      }
      program.setGenRootExprAccess( options.isBGenRootExprAccess() );
      program.setExpectedReturnType( options.getExpectedType() );
      program.setTokenizerInstructor( options.getTi() );
      program.setSuperType( options.getSuperType() );
      program.setAdditionalDFSDecls( options.getAdditionalDFSDecls() );
      program.setStatementsOnly( options.isStatementsOnly() );
      program.setThrowaway( options.isThrowawayProgram() );
      program.setAllowUses( options.allowUses() );
      program.setCtxInferenceMgr( options.getCtxInferenceMgr() );
      if( options.getScriptPartId() != null )
      {
        program.setContextType( options.getScriptPartId().getContainingType() );
      }
      program.setTokenizerInstructor( new TemplateTokenizerInstructor( options.getParser().getTokenizer() ) );

      if( !program.isValid() )
      {
        throw program.getParseResultsException();
      }

      if( options.getParser() != null )
      {
        try
        {
          IParsedElement pe = program.getClassStatement().getParent();
          pe = pe == null ? program.getClassStatement() : pe;
          ((GosuParser)options.getParser()).verifyParsedElement( pe );
        }
        catch( ParseResultsException pre )
        {
          program.setParseResultsException( pre );
        }
      }

      return new ParseResult( program );
    }
    finally
    {
      TypeSystem.unlock();
    }
  }

  synchronized static private int getIndex()
  {
    return g_iIndex++;
  }
}
