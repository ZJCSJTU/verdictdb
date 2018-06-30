package org.verdictdb.core;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Table-specific information
 * @author Yongjoo Park
 *
 */
public class ScrambleMetaForTable {

  // key
  String schemaName;

  String tableName;

  // aggregation block
  String aggregationBlockColumn;        // agg block number (0 to count-1)

  int aggregationBlockCount;            // agg block total count

  // tier
  String tierColumn;
  
  int numberOfTiers;
  
  /**
   * The probability mass function of the sizes of the aggregation blocks for a tier.
   * The key is the id of a tier (e.g., 0, 1, ..., 3), and the list is the cumulative distribution.
   * The length of the cumulative distribution must be equal to aggregationBlockCount.
   */
  Map<Integer, List<Double>> cumulativeMassDistributionPerTier = new HashMap<>();
  
  // subsample column
  String subsampleColumn;

  public ScrambleMetaForTable() {}
  
  public String getSchemaName() {
    return schemaName;
  }

  public void setSchemaName(String schemaName) {
    this.schemaName = schemaName;
  }

  public String getTableName() {
    return tableName;
  }

  public void setTableName(String tableName) {
    this.tableName = tableName;
  }

  public String getAggregationBlockColumn() {
    return aggregationBlockColumn;
  }

  public void setAggregationBlockColumn(String aggregationBlockColumn) {
    this.aggregationBlockColumn = aggregationBlockColumn;
  }

  public int getAggregationBlockCount() {
    return aggregationBlockCount;
  }

  public void setAggregationBlockCount(int aggregationBlockCount) {
    this.aggregationBlockCount = aggregationBlockCount;
  }

  public String getTierColumn() {
    return tierColumn;
  }

  public void setTierColumn(String tierColumn) {
    this.tierColumn = tierColumn;
  }

  public String getSubsampleColumn() {
    return subsampleColumn;
  }
  
  public List<Double> getCumulativeProbabilityDistribution(int tier) {
    return cumulativeMassDistributionPerTier.get(tier);
  }

  public void setSubsampleColumn(String subsampleColumn) {
    this.subsampleColumn = subsampleColumn;
  }
  
}

