/*
 *    Copyright 2018 University of Michigan
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package org.verdictdb.sqlsyntax;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import com.google.common.collect.Lists;

public class MysqlSyntax extends SqlSyntax {

  @Override
  public Collection<String> getCandidateJDBCDriverClassNames() {
    List<String> candidates = Lists.newArrayList("com.mysql.jdbc.Driver");
    return candidates;
  }

  @Override
  public boolean doesSupportTablePartitioning() {
    return true;
  }

  @Override
  public void dropTable(String schema, String tablename) {}

  @Override
  public int getColumnNameColumnIndex() {
    return 0;
  }

  @Override
  public String getFallbackDefaultSchema() {
    return "test";
  }

  @Override
  public String getColumnsCommand(String schema, String table) {
    return "show columns in " + quoteName(table) + " in " + quoteName(schema);
  }

  @Override
  public int getColumnTypeColumnIndex() {
    return 1;
  }

  @Override
  public String getPartitionByInCreateTable(
      List<String> partitionColumns, List<Integer> partitionCounts) {

    for (int count : partitionCounts) {
      if (count == 0) {
        return "";
      }
    }

    StringBuilder sql = new StringBuilder();
    sql.append("partition by list columns (");

    // use a single column
    for (int i = 0; i < partitionColumns.size(); i++) {
      if (i != 0) {
        sql.append(", ");
      }
      sql.append(String.format("`%s`", partitionColumns.get(i)));
    }
    sql.append(") (");

    // add list
    List<Integer> currentPart = new ArrayList<>(Collections.nCopies(partitionCounts.size(), 0));
    int partNum = 0;
    while (true) {
      //      System.out.println(sql.toString());
      //      System.out.println(partitionCounts);

      if (partNum != 0) {
        sql.append(", ");
      }
      sql.append(String.format("partition p%d values in (", partNum));
      for (int i = 0; i < currentPart.size(); i++) {
        if (i == 0) {
          if (currentPart.size() > 1) {
            sql.append("(");
          }
        } else {
          sql.append(",");
        }

        sql.append(String.format("%d", currentPart.get(i)));

        if (i == currentPart.size() - 1 && currentPart.size() > 1) {
          sql.append(")");
        }
      }
      sql.append(")");

      // increase currentPart by one
      boolean carry = true;
      for (int j = partitionCounts.size() - 1; j >= 0; j--) {
        if (carry) {
          currentPart.set(j, currentPart.get(j) + 1);
          carry = false;
        }
        if (currentPart.get(j).equals(partitionCounts.get(j))) {
          carry = true;
          currentPart.set(j, 0);
        }
      }

      // overflow
      if (carry) {
        break;
      }

      partNum += 1;
    }

    sql.append(")");
    return sql.toString();
  }

  @Override
  public String getPartitionCommand(String schema, String table) {
    return "SELECT DISTINCT REPLACE(PARTITION_EXPRESSION, '`', '') FROM INFORMATION_SCHEMA.PARTITIONS "
        + "WHERE TABLE_NAME='"
        + table
        + "' AND TABLE_SCHEMA='"
        + schema
        + "'";
  }

  @Override
  public String getQuoteString() {
    // https://dev.mysql.com/doc/refman/8.0/en/identifiers.html
    return "`";
  }

  @Override
  public String getSchemaCommand() {
    return "show schemas";
  }

  @Override
  public int getSchemaNameColumnIndex() {
    return 0;
  }

  @Override
  public String getTableCommand(String schema) {
    return "show tables in " + quoteName(schema);
  }

  @Override
  public int getTableNameColumnIndex() {
    return 0;
  }

  @Override
  public String randFunction() {
    return "rand()";
  }

  @Override
  public String rowNumberFunction(String partition) {
    if (partition.equals("")) {
      return "ROW_NUMBER() OVER ()";
    } else {
      return String.format("ROW_NUMBER() OVER (PARTITION BY %s)", partition);
    }
  }

  @Override
  public boolean isAsRequiredBeforeSelectInCreateTable() {
    return false;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (obj == this) {
      return true;
    }
    if (obj.getClass() != getClass()) {
      return false;
    }
    return true;
  }

  @Override
  public String getApproximateCountDistinct(String column) {
    return String.format("count(distinct %s)", column);
  }

  @Override
  public String getPrimaryKey(String schema, String table) {
    return String.format("SHOW KEYS FROM %s.%s WHERE Key_name = 'PRIMARY'", schema, table);
  }

  /**
   * The following query returns 9.6757 (9.6757 / 100 = 0.0968):
   * 
   * select stddev(c)
   * from (
   *     select v, count(*) as c
   *     from (
   *         select conv(substr(md5(value), 1, 8), 16, 10) % 100 as v
   *         from mytable
   *     ) t1
   *     group by v
   * ) t2;
   * 
   * where mytable contains the integers from 0 to 10000.
   * 
   * Note that the stddev of rand() is sqrt(0.01 * 0.99) = 0.09949874371.
   */
  @Override
  public String hashFunction(String column) {
    String f = String.format(
        "(conv(substr(md5(%s), 1, 8), 16, 10) %% %d) / %d",
        column, hashPrecision, hashPrecision);
    return f;
  }


}
