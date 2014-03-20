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

import org.antlr.runtime.tree.CommonTree;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.hive.ql.parse.ASTNode;
import org.apache.hadoop.hive.ql.parse.HiveParser;


public class HiveASTChecker {
  private static final Log LOG = LogFactory.getLog("hive.ql.parse.sql.SqlParseDriver");

  /**
   * check whether the AST Tree has some features that have different in Hive and Sql_92.
   * if there is, then throw BehaviorDiffException.
   * @param tree
   * @param originCmd
   *        the origin query
   * @throws SqlParseException
   * @throws SqlXlateException
   *         currently, only ID start with PantheraConstants.PANTHERA_PREFIX would throw SqlXlateException
   * @throws BehaviorException
   *         currently, only "order by Integer" will throw BehaviorDiffException.
   */
  public void checkHiveAST(Object tree, String originCmd) throws SqlXlateException, BehaviorDiffException {
    if (tree instanceof ASTNode) {
      if (((CommonTree) tree).getType() == HiveParser.Identifier
          //ID begin with PantheraConstants.PANTHERA_PREFIX
          && ((CommonTree) tree).getText().startsWith(PantheraConstants.PANTHERA_PREFIX)) {
        //when SqlXlateException encountered, Panthera would not use Hive to run the queries again
        throw new SqlXlateException((CommonTree) tree, "Table/Column name/alias begin with \"" +
            PantheraConstants.PANTHERA_PREFIX + "\" is reserved by " + PantheraConstants.PANTHERA_ASE);
      }
      checkDiffBehavior((CommonTree) tree);
      for (int i=0; i < ((ASTNode) tree).getChildCount(); i++) {
        checkHiveAST( ((ASTNode) tree).getChild(i), originCmd);
      }
    }
  }

  /**
   * check whether there if features has different behavior, if there is, throw exception and
   * use Panthera.
   * @param tree
   * @throws BehaviorDiffException
   */
  private void checkDiffBehavior(CommonTree tree) throws BehaviorDiffException {
    switch(tree.getType()) {
    case HiveParser.Number:
      if (tree.getParent().getParent().getType() == HiveParser.TOK_ORDERBY) {
        throw new BehaviorDiffException("order by Integer, different behavior in Hive and SQL_92, will try panthera");
      }
    default:
    }
    return ;
  }
}
