/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intel.ssg.dcst.panthera.parse.sql;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Stack;

import org.antlr.runtime.ANTLRStringStream;
import org.antlr.runtime.CharStream;
import org.antlr.runtime.RecognitionException;
import org.antlr.runtime.Token;
import org.antlr.runtime.TokenRewriteStream;
import org.antlr.runtime.TokenStream;
import org.antlr.runtime.tree.CommonTreeAdaptor;
import org.antlr.runtime.tree.TreeAdaptor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.ql.Context;
import org.apache.hadoop.hive.ql.parse.ASTNode;

import com.intel.ssg.dcst.panthera.parse.ql.ParseException;
import com.intel.ssg.dcst.panthera.parse.ql.HiveParseDriver;
import com.intel.ssg.dcst.panthera.parse.sql.BehaviorDiffException;
import com.intel.ssg.dcst.panthera.parse.sql.HiveASTChecker;
import com.intel.ssg.dcst.panthera.parse.sql.HiveParseException;
import com.intel.ssg.dcst.panthera.parse.sql.SqlParseException;
import com.intel.ssg.dcst.panthera.parse.sql.SqlXlateException;

import br.com.porcelli.parser.plsql.PLSQLLexer;
import br.com.porcelli.parser.plsql.PantheraParser;
import br.com.porcelli.parser.plsql.PantheraParser_PLSQLParser;

/**
 *
 * A Class to parse SQL queries into SQL AST.
 * Includes Sql Lexer and Parser.
 * Similar to Hive's ParseDriver.
 * parse() method is the entry point of SQL parsing.
 *
 */
public class SqlParseDriver {
  private static final Log LOG = LogFactory.getLog("hive.ql.parse.sql.SqlParseDriver");
  private final HiveConf conf;
  private final HiveParseDriver parseDriver;

  public SqlParseDriver(HiveConf conf) {
    this.conf = conf;
    parseDriver = new HiveParseDriver();
  }

  public SqlParseDriver() {
    this.conf = new HiveConf();
    parseDriver = new HiveParseDriver();
  }

  /**
   *
   * Sql Lexer.
   *
   */
  public class SqlLexer extends PLSQLLexer {

    private final ArrayList<ParseError> errors;

    public SqlLexer() {
      super();
      errors = new ArrayList<ParseError>();
    }

    public SqlLexer(CharStream input) {
      super(input);
      errors = new ArrayList<ParseError>();
    }

    @Override
    public void displayRecognitionError(String[] tokenNames,
        RecognitionException e) {

      errors.add(new ParseError(this, e, tokenNames));
    }

    public ArrayList<ParseError> getErrors() {
      return errors;
    }

  }

  /**
   *
   * Sql Parser.
   *
   */
  public class SqlParser extends PantheraParser {

    private final ArrayList<ParseError> errors;

    public SqlParser(TokenStream input) {
      super(input);
      errors = new ArrayList<ParseError>();
    }

    @Override
    public void displayRecognitionError(String[] tokenNames,
        RecognitionException e) {
      errors.add(new ParseError(this, e, tokenNames));
    }

    public ArrayList<ParseError> getErrors() {
      return errors;
    }

  }

  /**
   * Tree adaptor for making antlr return SqlASTNodes instead of CommonTree nodes
   */
  static final TreeAdaptor adaptor = new CommonTreeAdaptor() {
    /**
     * Creates an SqlASTNode for the given token. The SqlASTNode is a wrapper around
     * Hive's ASTNode class thus can use all Hive's existing walking utilities.
     *
     * @param payload
     *          The token.
     * @return Object (which is actually an SqlASTNode) for the token.
     */
    @Override
    public Object create(Token payload) {
      return new SqlASTNode(payload);
    }

    @Override
    public Object dupNode(Object t) {
      if (t == null) {
        return null;
      }
      return create(((SqlASTNode) t).token);
    }

    @Override
    public Object errorNode(TokenStream input, Token start, Token stop,
        RecognitionException e)
    {
      SqlASTErrorNode t = new SqlASTErrorNode(input, start, stop, e);
      return t;
    }
  };

  /**
   * A pre-parse stage to convert the characters in query command (exclude
   * those in quotes) to lower-case, as our SQL Parser does not recognize
   * upper-case keywords. It does no harm to Hive as Hive is case-insensitive.
   *
   * @param command
   *          input query command
   * @return the command with all chars turned to lower case except
   *         those in quotes
   */
  protected String preparse(String command) {
    Character tag = '\'';
    Stack<Character> singleQuotes = new Stack<Character>();
    Stack<Character> doubleQuotes = new Stack<Character>();
    char[] chars = filterDot(command).toCharArray();
    for (int i = 0; i < chars.length; i++) {
      char c = chars[i];
      if (c == '\'' && (i>0?chars[i-1] != '\\':true) ) {
        singleQuotes.push(tag);
      } else if  (c == '\"' && (i>0?chars[i-1] != '\\':true) ){
        doubleQuotes.push(tag);
      }
      if (singleQuotes.size() % 2 == 0 &&
          doubleQuotes.size() % 2 == 0 ) {
        // if not inside quotes, convert to lower case
        chars[i] = Character.toLowerCase(c);
      }
    }
    return new String(chars);
  }
/**
 * @deprecated
 * filter number's last dot, just a negative patch for plsql parser.
 * @param sql
 * @return
 */

  @Deprecated
  private String filterDot(String sql) {

    String[] sa = sql.split(" ");
    boolean b = false;
    for (int i = 0; i < sa.length; i++) {
      String token = sa[i];
      if (!token.contains(".")) {
        continue;
      }
      try {
        Double.valueOf(token.trim());
        if (token.charAt(token.length() - 1) == '.') {
          sa[i] = token.substring(0, token.length() - 1);
          b = true;
        }
      } catch (Exception e) {

      }
    }
    if (b) {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < sa.length; i++) {
        sb.append(sa[i] + " ");
      }
      String r = sb.toString();
      return r.substring(0, r.length() - 1);
    }
    return sql;

  }

  /**
   * Translate from SQL Command string to Hive AST.
   * This comes in two stages:
   * 1. SQL command -> SQL AST (SqlLexer & SqlParser)(using antlr)
   * 2. SQL AST -> Hive AST (SqlASTTranslator)
   *
   * @param command
   *          input SQL string
   * @param ctx
   *          - pass context
   * @return result Hive AST
   * @throws SqlParseException
   *           exception thrown during lexer & parser stage
   * @throws SqlXlateException
   *           exception thrown during translation from SQL AST to Hive AST
   */
  public ASTNode sqlParse(String command, Context ctx) throws HiveParseException, SqlParseException, SqlXlateException {

    LOG.info("Input SQL Command :" + command);
    // pre-parse phase
    command = preparse(command);
    LOG.info("Pre-Parsing Completed");
    // Lexing phase
    SqlLexer lexer = new SqlLexer(new ANTLRStringStream(command));

    TokenRewriteStream tokens = new TokenRewriteStream(lexer);
    //TODO as TokenRewriteStream is under antlr33 while Hive tokenstream is under antlr
    //we can not set the token stream into ctx. This may lead to error when creating view
    //Fix this later.
    //if (ctx != null) {
    //  ctx.setTokenRewriteStream(tokens);
    //}
    // Parsing phase
    SqlParser parser = new SqlParser(tokens);
    parser.setTreeAdaptor(adaptor);

    // //the root grammar is "seq_of_statements"
    PantheraParser_PLSQLParser.seq_of_statements_return r = null;

    try {
      r = parser.seq_of_statements();
    } catch (org.antlr.runtime.RecognitionException e) {
      LOG.error("Error when trying SQLParser:" + e.toString());
      throw new SqlParseException("ParseError at line " + e.line + ":" + e.charPositionInLine + " when trying SQLParser");
    } catch (Exception e) {
      throw new SqlParseException("Unknown parse Error, check your input please.");
    }

    // check if the query is a SELECT Statement or EXPLAIN Statement, only Select and Explain are supported.
    SqlXlateUtil.checkPantheraSupportQueries(r.getTree());

    //check the tree
    SqlASTChecker checker = new SqlASTChecker();
    try {
      checker.checkSqlAST(r.getTree(), command);
    } catch (SqlXlateException e) {
      LOG.error("SQL parse Error :" + e.toString());
      e.outputException(command);
      throw e;
    }
    LOG.info("Parsing Completed.");

    // Translate phase
    SqlASTNode sqlAST = (SqlASTNode) r.getTree();
    LOG.info("SQL AST before translation : " + sqlAST.toStringTree().replace('(', '[').replace(')', ']'));
    SqlASTTranslator trans = null;
    ASTNode hiveAST = null;
    try {
      trans = new SqlASTTranslator(this.conf);
      hiveAST = trans.translate(sqlAST);
    } catch (SqlXlateException e) {
      LOG.error("SQL transform error: " + e.toString());
      e.outputException(command);
      throw e;
    } catch (Exception e) {
      LOG.error("Panthera encountered a known bug");
      throw new SqlParseException("Panthera encountered a known bug");
    }

    LOG.info("Hive AST after translation : " + hiveAST.toStringTree());
    LOG.info("Translation Completed.");
    return hiveAST;
  }

  public ASTNode parse(String command) throws ParseException {
    try {
      return parse(command, null);
    } catch (ParseException parseException) {
      throw parseException;
    } catch (Exception otherException) {
      throw new ParseException(otherException.getMessage());
    }
  }
  
  /**
   * Parse queries.
   * 1, check modes. 3 Modes totally: Hive mode, Panthera mode, Auto mode.
   * 2, Hive mode only try Hive, Panthera mode only try Panthera, Auto mode will try Hive first, if there is
   *    exception, then try Panthera.
   *
   * @param command
   * @param ctx
   * @return
   * @throws HiveParseException
   * @throws ParseException
   * @throws SqlParseException
   * @throws SqlXlateException
   */
  public ASTNode parse(String command, Context ctx) throws HiveParseException, ParseException, SqlParseException, SqlXlateException {
    String hivePantheraMode = conf.get("hive.panthera.mode", "on");
    LOG.debug("hive.ql.mode config is : \"" + hivePantheraMode + "\"");
    if (hivePantheraMode.equalsIgnoreCase("hive")) {
      // hive mode
      LOG.info("Using Hive Mode, will run queries on Hive directly.");
      return parseDriver.parse(command, ctx);
    } else if (hivePantheraMode.equalsIgnoreCase("panthera")) {
      // panthera mode
      LOG.info("Using Panthera Mode, will run queries on Panthera.");
      return sqlParse(command, ctx);
    }
    // else auto mode
    LOG.info("Using Auto Mode, will run queries try Hive first and if fail will try Panthera.");
    ASTNode hiveASTNode = null;
    try {
      // parse query on Hive first.
      LOG.info("Parse query with Hive...");
      hiveASTNode = parseDriver.parse(command, ctx);
    } catch (Exception e) {
      // if failed parse with Hive, then try panthera.
      LOG.info("Parse failed with Hive, Try to Parse with Panthera...");
      return sqlParse(command, ctx);
    }
    //check the hive tree
    HiveASTChecker checker = new HiveASTChecker();
    try {
      checker.checkHiveAST(hiveASTNode, command);
    } catch (BehaviorDiffException e) {
      // if get features has different behavior with Hive and Sql_92, then use Panthera(Sql_92).
      LOG.info("Query has feature that has different behavior with Hive and Sql_92, will try to parase with panthera...");
      return sqlParse(command, ctx);
    }
    return hiveASTNode;
  }
}
