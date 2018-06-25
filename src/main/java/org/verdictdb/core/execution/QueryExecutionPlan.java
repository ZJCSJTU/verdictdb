/*
 * Copyright 2018 University of Michigan
 * 
 * You must contact Barzan Mozafari (mozafari@umich.edu) or Yongjoo Park (pyongjoo@umich.edu) to discuss
 * how you could use, modify, or distribute this code. By default, this code is not open-sourced and we do
 * not license this code.
 */

package org.verdictdb.core.execution;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.commons.lang3.tuple.Pair;
import org.verdictdb.connection.DbmsConnection;
import org.verdictdb.core.execution.ola.AggExecutionNodeBlock;
import org.verdictdb.core.query.SelectQuery;
import org.verdictdb.core.rewriter.ScrambleMeta;
import org.verdictdb.exception.VerdictDBTypeException;
import org.verdictdb.exception.VerdictDBValueException;
import org.verdictdb.exception.VerdictDBException;
import org.verdictdb.sql.syntax.SyntaxAbstract;

public class QueryExecutionPlan {
  
  SelectQuery query;
  
  ScrambleMeta scrambleMeta;
  
  QueryExecutionNode root;
  
  String scratchpadSchemaName;
  
//  PostProcessor postProcessor;
  
//  /**
//   * 
//   * @param queryString A select query
//   * @throws UnexpectedTypeException 
//   */
//  public AggQueryExecutionPlan(DbmsConnection conn, SyntaxAbstract syntax, String queryString) throws VerdictDbException {
//    this(conn, syntax, (SelectQueryOp) new NonValidatingSQLParser().toRelation(queryString));
//  }
  
  static final int serialNum = ThreadLocalRandom.current().nextInt(0, 1000000);
  
  static int identifierNum = 0;

  static int tempTableNameNum = 0;
  
  public static String generateUniqueIdentifier() {
    return String.format("verdictdbtemptable_%d_%d", serialNum, identifierNum++);
  }

  public Pair<String, String> generateTempTableName() {
    return Pair.of(scratchpadSchemaName, String.format("verdictdbtemptable_%d", tempTableNameNum++));
  }

  /**
   * 
   * @param query  A well-formed select query object
   * @throws VerdictDBValueException 
   * @throws VerdictDBException 
   */
  public QueryExecutionPlan(
      DbmsConnection conn, 
      SyntaxAbstract syntax, 
      ScrambleMeta scrambleMeta, 
      SelectQuery query,
      String scratchpadSchemaName) throws VerdictDBException {
    this.scrambleMeta = scrambleMeta;
    if (!query.isAggregateQuery()) {
      throw new VerdictDBTypeException(query);
    }
    this.query = query;
    this.scratchpadSchemaName = scratchpadSchemaName;
    this.root = makePlan(query);
  }
  
  public String getScratchpadSchemaName() {
    return scratchpadSchemaName;
  }
  
  /** 
   * Creates a tree in which each node is AggQueryExecutionNode. Each AggQueryExecutionNode corresponds to
   * an aggregate query, whether it is the main query or a subquery.
   * 
   * 1. Restrict the aggregate subqueries to appear only in the where clause.
   * 2. If an aggregate subquery appears in the where clause, the subquery itself should be a single
   *    AggQueryExecutionNode even if it contains another aggregate subqueries within it.
   * 3. Except for the root nodes, all other nodes are not approximated.
   * 4. AggQueryExecutionNode must not include any correlated predicates.
   * 5. The results of intermediate AggQueryExecutionNode should be stored as a materialized view.
   * 
   * @param conn
   * @param query
   * @return Pair of roots of the tree and post-processing interface.
   * @throws VerdictDBValueException 
   * @throws VerdictDBTypeException 
   */

  QueryExecutionNode makePlan(SelectQuery query) throws VerdictDBException {
    return SelectAllExecutionNode.create(query, this.scratchpadSchemaName);
  }

  /**
   *
   * @param root The root execution node of ALL nodes (i.e., not just the top agg node)
   * @return
   * @throws VerdictDBException
   */
  QueryExecutionNode makeAsyncronousAggIfAvailable(QueryExecutionNode root) throws VerdictDBException {
    List<AggExecutionNodeBlock> aggBlocks = root.identifyTopAggBlocks();

//    List<QueryExecutionNode> newNodes = new ArrayList<>();
//    for (QueryExecutionNode node : topAggNodes) {
//      QueryExecutionNode newNode = null;
//      if (((AggExecutionNode) node).doesContainScrambledTablesInDescendants(scrambleMeta)) {
//        newNode = ((AggExecutionNode) node).toAsyncAgg(scrambleMeta);
//      } else {
//        newNode = node;
//      }
//      newNodes.add(newNode);
//    }

    // converted nodes should be used in place of the original nodes.
    for (int i = 0; i < aggBlocks.size(); i++) {
      AggExecutionNodeBlock nodeBlock = aggBlocks.get(i);
      QueryExecutionNode oldNode = nodeBlock.getBlockRootNode();
      QueryExecutionNode newNode = nodeBlock.convertToProgressiveAgg(scrambleMeta);

      List<QueryExecutionNode> parents = oldNode.getParents();
      for (QueryExecutionNode parent : parents) {
        List<QueryExecutionNode> parentDependants = parent.getDependents();
        int idx = parentDependants.indexOf(oldNode);
        parentDependants.remove(idx);
        parentDependants.add(idx, newNode);
      }
    }

    return root;
  }
  
  public void execute(DbmsConnection conn) {
    // execute roots
    
    // after executions are all finished.
    cleanUp();
  }
  
  // clean up any intermediate materialized tables
  void cleanUp() {
    tempTableNameNum = 0;
  }
  static void resetTempTableNameNum() {tempTableNameNum = 0;}

}
