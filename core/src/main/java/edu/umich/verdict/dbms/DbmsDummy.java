/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.umich.verdict.dbms;

import java.sql.ResultSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.spark.sql.DataFrame;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import edu.umich.verdict.VerdictContext;
import edu.umich.verdict.datatypes.SampleParam;
import edu.umich.verdict.datatypes.TableUniqueName;
import edu.umich.verdict.exceptions.VerdictException;

public class DbmsDummy extends Dbms {

<<<<<<< HEAD
	public DbmsDummy(VerdictContext vc) throws VerdictException {
		super(vc, "dummy");
	}
	
	@Override
	public boolean execute(String sql) throws VerdictException {
		return false;
	}

	@Override
	public ResultSet getResultSet() {
		return null;
	}

	@Override
	public DataFrame getDataFrame() {
		return null;
	}

	@Override
	public void executeUpdate(String sql) throws VerdictException {
		
	}

	@Override
	public void changeDatabase(String schemaName) throws VerdictException {
	}

	@Override
	public List<Pair<String, String>> getAllTableAndColumns(String schemaName) throws VerdictException {
		return null;
	}

	@Override
	public void deleteEntry(TableUniqueName tableName, List<Pair<String, String>> colAndValues)
			throws VerdictException {
	}

	@Override
	public void insertEntry(TableUniqueName tableName, List<Object> values) throws VerdictException {
	}

	@Override
	public long getTableSize(TableUniqueName tableName) throws VerdictException {
		return 0;
	}

	@Override
	public void createMetaTablesInDMBS(TableUniqueName originalTableName, TableUniqueName sizeTableName,
			TableUniqueName nameTableName) throws VerdictException {
	}

	@Override
	public boolean doesMetaTablesExist(String schemaName) throws VerdictException {
		return false;
	}

	@Override
	public String modOfHash(String col, int mod) {
		return null;
	}

	@Override
	public void close() throws VerdictException {
	}

	@Override
	public List<String> getTables(String schema) throws VerdictException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<String, String> getColumns(TableUniqueName table) throws VerdictException {
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public String getQuoteString() {
        return "`";
=======
    public DbmsDummy(VerdictContext vc) throws VerdictException {
        super(vc, "dummy");
    }

    @Override
    public boolean execute(String sql) throws VerdictException {
        return false;
    }

    @Override
    public ResultSet getResultSet() {
        return null;
    }

    @Override
    public DataFrame getDataFrame() {
        return null;
    }

    @Override
    public void executeUpdate(String sql) throws VerdictException {

    }

    @Override
    public void changeDatabase(String schemaName) throws VerdictException {
    }

    @Override
    public List<Pair<String, String>> getAllTableAndColumns(String schemaName) throws VerdictException {
        return null;
    }

    @Override
    public void deleteEntry(TableUniqueName tableName, List<Pair<String, String>> colAndValues)
            throws VerdictException {
    }

    @Override
    public void insertEntry(TableUniqueName tableName, List<Object> values) throws VerdictException {
    }

    @Override
    public long getTableSize(TableUniqueName tableName) throws VerdictException {
        return 0;
    }

    @Override
    public void createMetaTablesInDMBS(TableUniqueName originalTableName, TableUniqueName sizeTableName,
            TableUniqueName nameTableName) throws VerdictException {
    }

    @Override
    public boolean doesMetaTablesExist(String schemaName) throws VerdictException {
        return false;
    }

    @Override
    public String modOfHash(String col, int mod) {
        return null;
    }

    @Override
    public void close() throws VerdictException {
    }

    @Override
    public List<String> getTables(String schema) throws VerdictException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Map<String, String> getColumns(TableUniqueName table) throws VerdictException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getQuoteString() {
        return null;
    }

    @Override
    public Set<String> getDatabases() throws VerdictException {
        return new HashSet<String>();
    }

    @Override
    public String modOfRand(int mod) {
        return null;
    }

    @Override
    protected String randomPartitionColumn() {
        return null;
    }

    @Override
    protected String randomNumberExpression(SampleParam param) {
        return null;
    }

    @Override
    public Dataset<Row> getDataset() {
        // TODO Auto-generated method stub
        return null;
>>>>>>> origin/spark2
    }

}
