package org.verdictdb.core.execution;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;
import org.verdictdb.connection.DbmsConnection;
import org.verdictdb.core.ScrambleMeta;
import org.verdictdb.core.query.AliasReference;
import org.verdictdb.core.query.AliasedColumn;
import org.verdictdb.core.query.AsteriskColumn;
import org.verdictdb.core.query.BaseColumn;
import org.verdictdb.core.query.BaseTable;
import org.verdictdb.core.query.ColumnOp;
import org.verdictdb.core.query.SelectItem;
import org.verdictdb.core.query.SelectQuery;
import org.verdictdb.core.scramble.Scrambler;
import org.verdictdb.exception.VerdictDBValueException;

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
  public void testDoesContainScrambledTableFlatQuery() throws VerdictDBValueException {
    BaseTable base = new BaseTable("myschema", "mytable", "t");
    SelectQuery query = SelectQuery.create(
        Arrays.<SelectItem>asList(
            new ColumnOp("count", new BaseColumn("t", "mycolumn1"))),
        base);
    
    DbmsConnection conn = null;
    String schemaName = "newschema";
    String tableName = "newtable";
    ScrambleMeta scrambleMeta = generateTestScrambleMeta();
    QueryExecutionNode node = AggExecutionNode.create(new QueryExecutionPlan("newschema"), query);
    assertTrue(node.doesContainScrambledTablesInDescendants(scrambleMeta));
  }
  
  @Test
  public void testDoesContainScrambledTableNestedQuery() throws VerdictDBValueException {
    BaseTable base = new BaseTable("myschema", "mytable", "t");
    SelectQuery innerRelation = SelectQuery.create(
        Arrays.<SelectItem>asList(
            new BaseColumn("t", "mygroup"),
            new AliasedColumn(new ColumnOp("avg", new BaseColumn("t", "mycolumn1")), "myavg")),
        base);
    innerRelation.addGroupby(new AliasReference("mygroup"));
    innerRelation.setAliasName("s");
    SelectQuery query = SelectQuery.create(
        Arrays.<SelectItem>asList(new AsteriskColumn()),
        innerRelation);
    
    DbmsConnection conn = null;
    String schemaName = "newschema";
    String tableName = "newtable";
    ScrambleMeta scrambleMeta = generateTestScrambleMeta();
    QueryExecutionNode node = AggExecutionNode.create(new QueryExecutionPlan("newschema"), query);
    assertFalse(node.doesContainScrambledTablesInDescendants(scrambleMeta));
  }
  
//  @Test
//  public void testIdentifyTopAggNodes() {
//    DbmsConnection conn = null;
//    
//    BaseTable base = new BaseTable("myschema", "temptable", "t");
//    SelectQuery aggQuery = SelectQuery.create(
//        Arrays.<SelectItem>asList(
//            new BaseColumn("t", "mygroup"),
//            new AliasedColumn(new ColumnOp("avg", new BaseColumn("t", "mycolumn1")), "myavg")),
//        base);
//    aggQuery.addGroupby(new AliasReference("mygroup"));
//    aggQuery.setAliasName("s");
//    SelectQuery projectionQuery = SelectQuery.create(
//        Arrays.<SelectItem>asList(new AsteriskColumn()),
//        new BaseTable("myschema", "temptable2", "t"));
//    
//    QueryExecutionNode dep = AggExecutionNode.create(aggQuery, "newschema");
//    QueryExecutionNode root = ProjectionExecutionNode.create(projectionQuery, "newschema");
//    root.addDependency(dep);
//    
//    List<AggExecutionNodeBlock> topAggNodes = new ArrayList<>();
//    root.identifyTopAggBlocks(topAggNodes);
//    
//    assertEquals(dep, topAggNodes.get(0).getBlockRootNode());
//  }

}
