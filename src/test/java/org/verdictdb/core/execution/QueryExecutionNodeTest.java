package org.verdictdb.core.execution;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.verdictdb.connection.DbmsConnection;
import org.verdictdb.core.query.AliasReference;
import org.verdictdb.core.query.AliasedColumn;
import org.verdictdb.core.query.AsteriskColumn;
import org.verdictdb.core.query.BaseColumn;
import org.verdictdb.core.query.BaseTable;
import org.verdictdb.core.query.ColumnOp;
import org.verdictdb.core.query.SelectItem;
import org.verdictdb.core.query.SelectQuery;
import org.verdictdb.core.rewriter.ScrambleMeta;
import org.verdictdb.core.scramble.Scrambler;

public class QueryExecutionNodeTest {
  
  ScrambleMeta generateTestScrambleMeta() {
    int aggblockCount = 2;
    ScrambleMeta meta = new ScrambleMeta();
    meta.insertScrambleMetaEntry("myschema", "mytable",
        Scrambler.getAggregationBlockColumn(),
        Scrambler.getSubsampleColumn(),
        Scrambler.getTierColumn(),
        aggblockCount);
    return meta;
  }

  @Test
  public void testDoesContainScrambledTableFlatQuery() {
    BaseTable base = new BaseTable("myschema", "mytable", "t");
    SelectQuery query = SelectQuery.getSelectQueryOp(
        Arrays.<SelectItem>asList(
            new ColumnOp("count", new BaseColumn("t", "mycolumn1"))),
        base);
    
    DbmsConnection conn = null;
    String schemaName = "newschema";
    String tableName = "newtable";
    ScrambleMeta scrambleMeta = generateTestScrambleMeta();
    QueryExecutionNode node = new AggExecutionNode(conn, schemaName, tableName, query);
    assertTrue(node.doesContainScrambledTablesInDescendants(scrambleMeta));
  }
  
  @Test
  public void testDoesContainScrambledTableNestedQuery() {
    BaseTable base = new BaseTable("myschema", "mytable", "t");
    SelectQuery innerRelation = SelectQuery.getSelectQueryOp(
        Arrays.<SelectItem>asList(
            new BaseColumn("t", "mygroup"),
            new AliasedColumn(new ColumnOp("avg", new BaseColumn("t", "mycolumn1")), "myavg")),
        base);
    innerRelation.addGroupby(new AliasReference("mygroup"));
    innerRelation.setAliasName("s");
    SelectQuery query = SelectQuery.getSelectQueryOp(
        Arrays.<SelectItem>asList(new AsteriskColumn()),
        innerRelation);
    
    DbmsConnection conn = null;
    String schemaName = "newschema";
    String tableName = "newtable";
    ScrambleMeta scrambleMeta = generateTestScrambleMeta();
    QueryExecutionNode node = new AggExecutionNode(conn, schemaName, tableName, query);
    assertFalse(node.doesContainScrambledTablesInDescendants(scrambleMeta));
  }
  
  @Test
  public void testIdentifyTopAggNodes() {
    DbmsConnection conn = null;
    
    BaseTable base = new BaseTable("myschema", "temptable", "t");
    SelectQuery aggQuery = SelectQuery.getSelectQueryOp(
        Arrays.<SelectItem>asList(
            new BaseColumn("t", "mygroup"),
            new AliasedColumn(new ColumnOp("avg", new BaseColumn("t", "mycolumn1")), "myavg")),
        base);
    aggQuery.addGroupby(new AliasReference("mygroup"));
    aggQuery.setAliasName("s");
    SelectQuery projectionQuery = SelectQuery.getSelectQueryOp(
        Arrays.<SelectItem>asList(new AsteriskColumn()),
        new BaseTable("myschema", "temptable2", "t"));
    
    QueryExecutionNode dep = new AggExecutionNode(conn, "myschema", "temptable2", aggQuery);
    QueryExecutionNode root = new ProjectionExecutionNode(conn, projectionQuery);
    root.addDependency(dep);
    
    List<QueryExecutionNode> topAggNodes = new ArrayList<>();
    root.identifyTopAggNodes(topAggNodes);
    
    assertEquals(dep, topAggNodes.get(0));
  }

}