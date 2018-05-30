package org.verdictdb.core.rewriter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.verdictdb.core.rewriter.AliasRenamingRules.countEstimateAliasName;
import static org.verdictdb.core.rewriter.AliasRenamingRules.countSubsampleAliasName;
import static org.verdictdb.core.rewriter.AliasRenamingRules.expectedErrorAliasName;
import static org.verdictdb.core.rewriter.AliasRenamingRules.expectedValueAliasName;
import static org.verdictdb.core.rewriter.AliasRenamingRules.sumEstimateAliasName;
import static org.verdictdb.core.rewriter.AliasRenamingRules.sumScaledCountAliasName;
import static org.verdictdb.core.rewriter.AliasRenamingRules.sumScaledSumAliasName;
import static org.verdictdb.core.rewriter.AliasRenamingRules.sumSquaredScaledCountAliasName;
import static org.verdictdb.core.rewriter.AliasRenamingRules.sumSquaredScaledSumAliasName;
import static org.verdictdb.core.rewriter.AliasRenamingRules.sumSubsampleSizeAliasName;

import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;
import org.verdictdb.core.resultset.AggregateFrame;
import org.verdictdb.core.resultset.AggregateMeasures;
import org.verdictdb.exception.ValueException;
import org.verdictdb.exception.VerdictDbException;

public class SingleResultSetRewriterTest {

  @Test
  public void testSanityCheck() throws VerdictDbException {
    String mysumAlias = "mysum";
    List<String> columnNames = Arrays.asList("group1", "group2",
        sumEstimateAliasName(mysumAlias),
        sumScaledSumAliasName(mysumAlias),
        sumSquaredScaledSumAliasName(mysumAlias),
        countSubsampleAliasName(),
        sumSubsampleSizeAliasName());
    AggregateFrame resultSet = new AggregateFrame(columnNames);

    SingleResultSetRewriter rewriter = new SingleResultSetRewriter(resultSet);
    List<String> nonaggColumns = Arrays.asList("group1", "group2");
    List<Pair<String, String>> aggColumns = Arrays.asList(Pair.of(mysumAlias, "sum"));
    AggregateFrame converted = rewriter.rewrite(nonaggColumns, aggColumns);
  }

  @Test(expected = ValueException.class)
  public void testSanityCheckFail() throws VerdictDbException {
    String mysumAlias = "mysum";
    List<String> columnNames = Arrays.asList("group1", "group2",
        sumEstimateAliasName(mysumAlias),
        sumScaledSumAliasName(mysumAlias),
        sumSquaredScaledSumAliasName(mysumAlias),
        countSubsampleAliasName(),
        sumSubsampleSizeAliasName());
    AggregateFrame resultSet = new AggregateFrame(columnNames);

    SingleResultSetRewriter rewriter = new SingleResultSetRewriter(resultSet);
    List<String> nonaggColumns = Arrays.asList("group1", "group2");
    List<Pair<String, String>> aggColumns = Arrays.asList(Pair.of(mysumAlias, "count"));
    AggregateFrame converted2 = rewriter.rewrite(nonaggColumns, aggColumns);
  }

  @Test
  public void testSumRewriting() throws ValueException {
    String mysumAlias = "mysum";
    List<String> attrNames = Arrays.asList(
        sumEstimateAliasName(mysumAlias),
        sumScaledSumAliasName(mysumAlias),
        sumSquaredScaledSumAliasName(mysumAlias),
        countSubsampleAliasName(),
        sumSubsampleSizeAliasName());
    List<Object> attrValues = Arrays.<Object>asList(1.0, 2.0, 3.0, 4, 5);
    AggregateMeasures measures = new AggregateMeasures(attrNames, attrValues);
    SingleResultSetRewriter rewriter = new SingleResultSetRewriter();
    AggregateMeasures rewrittenMeasures =
        rewriter.rewriteMeasures(measures, Arrays.asList(Pair.of(mysumAlias, "sum")));

    Object sumExpectedValue = rewrittenMeasures.getAttributeValue(expectedValueAliasName(mysumAlias));
    Object sumExpectedError = rewrittenMeasures.getAttributeValue(expectedErrorAliasName(mysumAlias));
    assertEquals(sumExpectedValue, 1.0);
    assertEquals(sumExpectedError, Math.sqrt((3.0 - 2*2.0 + 1.0*5) / ((float) 4*5)));
  }

  @Test
  public void testCountRewriting() throws ValueException {
    String mycountAlias = "mycount";
    List<String> attrNames = Arrays.asList(
        countEstimateAliasName(mycountAlias),
        sumScaledCountAliasName(mycountAlias),
        sumSquaredScaledCountAliasName(mycountAlias),
        countSubsampleAliasName(),
        sumSubsampleSizeAliasName());
    List<Object> attrValues = Arrays.<Object>asList(1.0, 2.0, 3.0, 4, 5);
    AggregateMeasures measures = new AggregateMeasures(attrNames, attrValues);
    SingleResultSetRewriter rewriter = new SingleResultSetRewriter();
    AggregateMeasures rewrittenMeasures =
        rewriter.rewriteMeasures(measures, Arrays.asList(Pair.of(mycountAlias, "count")));

    Object countExpectedValue = rewrittenMeasures.getAttributeValue(expectedValueAliasName(mycountAlias));
    Object countExpectedError = rewrittenMeasures.getAttributeValue(expectedErrorAliasName(mycountAlias));
    assertEquals(countExpectedValue, 1.0);
    assertEquals(countExpectedError, Math.sqrt((3.0 - 2*2.0 + 1.0*5) / ((float) 4*5)));
  }

  @Test
  public void testAvgRewriting() throws ValueException {
    String myavgAlias = "myavg";
    List<String> attrNames = Arrays.asList(
        sumEstimateAliasName(myavgAlias),
        sumScaledSumAliasName(myavgAlias),
        sumSquaredScaledSumAliasName(myavgAlias),
        countEstimateAliasName(myavgAlias),
        sumScaledCountAliasName(myavgAlias),
        sumSquaredScaledCountAliasName(myavgAlias),
        countSubsampleAliasName(),
        sumSubsampleSizeAliasName());
    List<Object> attrValues = Arrays.<Object>asList(7.0, 8.0, 9.0, 1.0, 2.0, 3.0, 4, 5);
    AggregateMeasures measures = new AggregateMeasures(attrNames, attrValues);
    SingleResultSetRewriter rewriter = new SingleResultSetRewriter();
    AggregateMeasures rewrittenMeasures =
        rewriter.rewriteMeasures(measures, Arrays.asList(Pair.of(myavgAlias, "avg")));

    Object avgExpectedValue = rewrittenMeasures.getAttributeValue(expectedValueAliasName(myavgAlias));
    Object avgExpectedError = rewrittenMeasures.getAttributeValue(expectedErrorAliasName(myavgAlias));
    assertEquals(avgExpectedValue, 7.0);
    double sum_var = (9.0 - 2*8.0 + 7.0*7.0*5) / ((float) 4*5);
    double count_var = (3.0 - 2*2.0 + 1.0*5) / ((float) 4*5);
    double eps = 1e-6;
    assertEquals(
        (double) avgExpectedError,
        Math.sqrt(Math.pow(7.0/1.0, 2) * (sum_var/Math.pow(7.0,2)+count_var/Math.pow(1.0,2))),
        eps);
  }

  @Test
  public void testResultSetRewriting() throws VerdictDbException {
    String mysumAlias = "mysum";
    List<String> columnNames = Arrays.asList(
        sumEstimateAliasName(mysumAlias),
        sumScaledSumAliasName(mysumAlias),
        sumSquaredScaledSumAliasName(mysumAlias),
        countSubsampleAliasName(),
        sumSubsampleSizeAliasName());
    AggregateFrame resultSet = new AggregateFrame(columnNames);

    List<Object> attrValues = Arrays.<Object>asList(1.0, 2.0, 3.0, 4, 5);
    AggregateMeasures measures = new AggregateMeasures(columnNames, attrValues);
    resultSet.addRow(measures);

    // rewriting
    SingleResultSetRewriter rewriter = new SingleResultSetRewriter(resultSet);
    List<String> nonaggColumns = Arrays.asList();
    List<Pair<String, String>> aggColumns = Arrays.asList(Pair.of(mysumAlias, "sum"));
    AggregateFrame converted = rewriter.rewrite(nonaggColumns, aggColumns);

    // assertions
    List<String> rewrittenColNames = converted.getColumnNames();
    assertTrue(rewrittenColNames.contains(expectedValueAliasName(mysumAlias)));
    assertTrue(rewrittenColNames.contains(expectedErrorAliasName(mysumAlias)));
    
    AggregateMeasures rewrittenMeasures = converted.getMeasures();
    Object sumExpectedValue = rewrittenMeasures.getAttributeValue(expectedValueAliasName(mysumAlias));
    Object sumExpectedError = rewrittenMeasures.getAttributeValue(expectedErrorAliasName(mysumAlias));
    assertEquals(sumExpectedValue, 1.0);
    assertEquals(sumExpectedError, Math.sqrt((3.0 - 2*2.0 + 1.0*5) / ((float) 4*5)));
  }

}
