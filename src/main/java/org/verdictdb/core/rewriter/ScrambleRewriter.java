package org.verdictdb.core.rewriter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.verdictdb.core.logical_query.SelectItem;
import org.apache.commons.lang3.tuple.Pair;
import org.verdictdb.core.logical_query.AbstractRelation;
import org.verdictdb.core.logical_query.AliasReference;
import org.verdictdb.core.logical_query.AliasedColumn;
import org.verdictdb.core.logical_query.AsteriskColumn;
import org.verdictdb.core.logical_query.BaseColumn;
import org.verdictdb.core.logical_query.BaseTable;
import org.verdictdb.core.logical_query.ColumnOp;
import org.verdictdb.core.logical_query.ConstantColumn;
import org.verdictdb.core.logical_query.GroupingAttribute;
import org.verdictdb.core.logical_query.SelectQueryOp;
import org.verdictdb.core.logical_query.UnnamedColumn;
import org.verdictdb.exception.UnexpectedTypeException;
import org.verdictdb.exception.ValueException;
import org.verdictdb.exception.VerdictDbException;

/**
 * AQP rewriter for partitioned tables. A sampling probability column must exist.
 * 
 * @author Yongjoo Park
 *
 */
public class ScrambleRewriter {
    
    ScrambleMeta scrambleMeta;
    
    int nextAliasNumber = 1;
    
    public ScrambleRewriter(ScrambleMeta scrambleMeta) {
        this.scrambleMeta = scrambleMeta;
    }
    
    String generateNextAliasName() {
        String aliasName = "verdictalias" + nextAliasNumber;
        nextAliasNumber += 1;
        return aliasName;
    }
    
    void initializeAliasNameSequence() {
        nextAliasNumber = 1;
    }
    
    String generateMeanEstimateAliasName(String aliasName) {
        return aliasName;
    }
    
    String generateStdEstimateAliasName(String aliasName) {
        return "std_" + aliasName;
    }
    
    /**
     * Assuming the root is an aggregate query, rewriting performs the following.
     * 1. Recursively converts a select query (or a base table) into an alternative expression.
     * 2. If a base table is not a scrambled table, it returns the same base table.
     * 3. If a base table is a scrambled table, it performs:
     *    a. converts it into a select-star query (with a certain alias name).
     *    b. register the select-start query into the 'selectAllScrambled' list.
     *    c. register the information of the select-star query into 'scrambleMeta':
     *       (1) inclusionProbabilityColumn
     *       (2) inclusionProbBlockDiffColumn
     *       (3) subsampleColumn
     * 4. If a select query is not a scrambled table, it returns the same select query.
     * 5. If a select query is a scrambled table, it performs:
     *    a. adds the following three columns to the select list and to 'scrambleMeta':
     *       (1) inclusionProbabilityColumn
     *       (2) inclusionProbBlockDiffColumn
     *       (3) subsampleColumn
     *       
     * At the root, 
     * 
     * Current Limitations:
     * 1. Only handles the query with a single aggregate (sub)query
     * 2. Only handles the query that the first select list is the aggregate query.
     * 
     * @param relation
     * @return
     * @throws VerdictDbException 
     */
    public List<AbstractRelation> rewrite(AbstractRelation relation) throws VerdictDbException {
//        UnnamedColumn subsampleColumn = deriveSubsampleIdColumn(relation);
//        AggregationBlockSequence blockSeq = generateAggregationBlockSequence(relation);
//        List<AbstractRelation> rewritten = rewriteNotIncludingMaterialization(relation, subsampleColumn, blockSeq);

//        return rewritten;
        if (!(relation instanceof SelectQueryOp)) {
            throw new UnexpectedTypeException("Unexpected argument type: " + relation.getClass().toString());
        }
        
        BlockAggregationInRewriting inRewriting = rewriteWithoutMaterialization(relation);
        AbstractRelation rewrittenWithoutBlocking = inRewriting.getTopRelation();
        List<RelationBlockInfo> relationBlocks = inRewriting.getBlockAggregateInfo();
        List<AbstractRelation> rewritten = new ArrayList<>();
        
        if (relationBlocks.size() == 0) {
            rewritten.add(rewrittenWithoutBlocking);
        }
        else if (relationBlocks.size() == 1) {
            RelationBlockInfo relationBlock = relationBlocks.get(0);
            for (int k = 0; k < relationBlock.getAggBlockCount(); k++) {
                SelectQueryOp relToBlock = relationBlock.getRelationToBlock();
                relToBlock.clearFilters();
                ColumnOp blockPredicate = relationBlock.getAggBlockPredicate(k);
                relToBlock.addFilterByAnd(blockPredicate);
                AbstractRelation rewritten_k = deepcopySelectQuery((SelectQueryOp) rewrittenWithoutBlocking);
                rewritten.add(rewritten_k);
            }
            
        }
        else if (relationBlocks.size() == 2) {
            throw new ValueException("Not implemented yet.");
        }
        else {
            throw new ValueException("A maximum of two scrambled tables are allowed.");
        }
        
        return rewritten;
    }
    
    SelectQueryOp deepcopySelectQuery(SelectQueryOp relation) {
        SelectQueryOp sel = new SelectQueryOp();
        for (SelectItem c : relation.getSelectList()) {
            sel.addSelectItem(c);
        }
        for (AbstractRelation r : relation.getFromList()) {
            if (r instanceof SelectQueryOp) {
                sel.addTableSource(deepcopySelectQuery((SelectQueryOp) r));
            } else {
                sel.addTableSource(r);
            }
        }
        if (relation.getFilter().isPresent()) {
            sel.addFilterByAnd(relation.getFilter().get());
        }
        for (GroupingAttribute a : relation.getGroupby()) {
            sel.addGroupby(a);
        }
        if (relation.getAliasName().isPresent()) {
            sel.setAliasName(relation.getAliasName().get());
        }
        return sel;
    }
    
    /**
     * Rewrite a given query into AQP-enabled form. The rewritten queries do not include any "create table ..."
     * parts.
     * 
     * "select other_groups, sum(price) from ... group by other_groups;" is converted to
     * "select other_groups,
     *         sum(sub_sum_est) as mean_estimate,
     *         std(sub_sum_est*sqrt(subsample_size)) / sqrt(sum(subsample_size)) as std_estimate
     *  from (select other_groups,
     *               sum(price / prob) as sub_sum_est,
     *               sum(case 1 when price is not null else 0 end) as subsample_size
     *        from ...
     *        group by sid, other_groups) as sub_table
     *  group by other_groups;"
     *  
     * "select other_groups, avg(price) from ... group by other_groups;" is converted to
     * "select other_groups,
     *         sum(sub_sum_est) / sum(sub_count_est) as mean_estimate,
     *         std(sub_sum_est/sub_count_est*sqrt(subsample_size)) / sqrt(sum(subsample_size)) as std_estimate
     *  from (select other_groups,
     *               sum(price / prob) as sub_sum_est,
     *               sum(case 1 when price is not null else 0 end / prob) as sub_count_est,
     *               sum(case 1 when price is not null else 0 end) as subsample_size
     *        from ...
     *        group by sid, other_groups) as sub_table
     *  group by other_groups;"
     *  This is based on the self-normalized estimator.
     *  https://statweb.stanford.edu/~owen/mc/Ch-var-is.pdf
     *  
     * "select other_groups, count(*) from ... group by other_groups;" is converted to
     * "select other_groups,
     *         sum(sub_count_est) as mean_estimate,
     *         sum(sub_count_est*sqrt(subsample_size)) / sqrt(sum(subsample_size)) as std_estimate
     *  from (select other_groups,
     *               sum(1 / prob) as sub_count_est,
     *               count(*) as subsample_size
     *        from ...
     *        group by sid, other_groups) as sub_table
     *  group by other_groups;"
     * 
     * @param relation
     * @param partitionNumber
     * @return
     * @throws VerdictDbException 
     */
//    List<AbstractRelation> rewriteNotIncludingMaterialization(
//            AbstractRelation relation,
//            UnnamedColumn subsampleId,
//            AggregationBlockSequence blockSeq)
//    throws VerdictDbException {
//        
//        // must be some select query.
//        if (!(relation instanceof SelectQueryOp)) {
//            throw new UnexpectedTypeException("Unexpected relation type: " + relation.getClass().toString());
//        }
//        
//        List<AbstractRelation> rewrittenQueries = new ArrayList<>();
//        for (int k = 0; k < blockSeq.getBlockCount(); k++) {
//            AggregationBlock aggblock = blockSeq.getBlock(k);
//            AbstractRelation rewritten = rewriteSingleAggregationBlock(relation, subsampleId, aggblock);
//            rewrittenQueries.add(rewritten);
//        }
//        return rewrittenQueries;
//    }
    
    BlockAggregationInRewriting rewriteWithoutMaterialization(AbstractRelation relation) {
        if (relation instanceof BaseTable) {
            BaseTable base = (BaseTable) relation;
            String baseSchemaName = base.getSchemaName();
            String baseTableName = base.getTableName();
            if (scrambleMeta.isScrambled(baseSchemaName, baseTableName)) {
                int aggBlockCount = scrambleMeta.getAggregationBlockCount(base.getSchemaName(), base.getTableName());
                String aliasName = generateNextAliasName();
                String aggblockColumnName = scrambleMeta.getAggregationBlockColumn(baseSchemaName, baseTableName);
                BaseColumn aggBlockColumn = new BaseColumn(base.getTableSourceAlias(), aggblockColumnName);
                BlockAggregationInRewriting rewritingInfo =
                        BlockAggregationInRewriting.initializeWithScrambled(base, aggBlockColumn, aggBlockCount, aliasName);
                
                // set meta info
                scrambleMeta.insertScrambleMetaEntry(
                        aliasName,
                        scrambleMeta.getAggregationBlockColumn(baseSchemaName, baseTableName),
                        scrambleMeta.getInclusionProbabilityColumn(baseSchemaName, baseTableName),
                        scrambleMeta.getInclusionProbabilityBlockDifferenceColumn(baseSchemaName, baseTableName),
                        scrambleMeta.getSubsampleColumn(baseSchemaName, baseTableName),
                        scrambleMeta.getAggregationBlockCount(baseSchemaName, baseTableName));
                
                return rewritingInfo;
            }
            else {
                BlockAggregationInRewriting rewritingInfo = BlockAggregationInRewriting.initializeWithRegular(base);
                return rewritingInfo;
            }
        }
        else if (relation instanceof SelectQueryOp) {
            SelectQueryOp sel = (SelectQueryOp) relation;
            for (AbstractRelation source : sel.getFromList()) {
                BlockAggregationInRewriting sourceRewritten = rewriteWithoutMaterialization(source);
            }
            
            if (!isAggregateQuery(relation)) {
                
            }
            else {
                
            }
        }
        return null;
    }
    
    boolean isAggregateQuery(AbstractRelation relation) {
        if (!(relation instanceof SelectQueryOp)) {
            return false;
        }
        SelectQueryOp sel = (SelectQueryOp) relation;
        List<SelectItem> fromList = sel.getSelectList();
        for (SelectItem item : fromList) {
            if (item instanceof ColumnOp) {
                ColumnOp col = (ColumnOp) item;
                if (isColumnOpAggregate(col)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    boolean isColumnOpAggregate(ColumnOp col) {
        if (col.getOpType().equals("avg") ||
            col.getOpType().equals("sum") ||
            col.getOpType().equals("count")) {
            return true;
        }
        boolean aggExists = false;
        List<UnnamedColumn> ops = col.getOperands();
        for (UnnamedColumn c : ops) {
            if (c instanceof ColumnOp) {
                if (isColumnOpAggregate((ColumnOp) c)) {
                    aggExists = true;
                    break;
                }
            }
        }
        return aggExists;
    }
    
//    AbstractRelation rewriteSingleAggregationBlock(
//            AbstractRelation relation,
//            UnnamedColumn subsampleId,
//            AggregationBlock aggblock)
//    throws VerdictDbException {
//        initializeAliasNameSequence();
//        SelectQueryOp rewrittenOuter = new SelectQueryOp();
//        SelectQueryOp rewrittenInner = new SelectQueryOp();
//        String innerTableAliasName = generateNextAliasName();
//        rewrittenInner.setAliasName(innerTableAliasName);
//        SelectQueryOp sel = (SelectQueryOp) relation;
//        List<SelectItem> selectList = sel.getSelectList();
//        List<SelectItem> newInnerSelectList = new ArrayList<>();
//        List<SelectItem> newOuterSelectList = new ArrayList<>();
//        List<GroupingAttribute> groupbyList = sel.getGroupby();
//        List<GroupingAttribute> newInnerGroupbyList = new ArrayList<>();
//        List<GroupingAttribute> newOuterGroupbyList = new ArrayList<>();
//        // The pair is (innerAliasName, outerAliasName)
//        List<Pair<UnnamedColumn, Pair<String, String>>> innerNonaggregateSelectItemToAliases = new ArrayList<>();
//        for (SelectItem item : selectList) {
//            if (!(item instanceof AliasedColumn)) {
//                throw new UnexpectedTypeException("The following select item is not aliased: " + item.toString());
//            }
//            
//            UnnamedColumn c = ((AliasedColumn) item).getColumn();
//            String aliasName = ((AliasedColumn) item).getAliasName();
//            
//            if (c instanceof BaseColumn) {
//                String aliasForBase = generateNextAliasName();
//                newInnerSelectList.add(new AliasedColumn(c, aliasForBase));
//                newOuterSelectList.add(new AliasedColumn(new BaseColumn(innerTableAliasName, aliasForBase), aliasName));
//                innerNonaggregateSelectItemToAliases.add(Pair.of(c, Pair.of(aliasForBase, aliasName)));
//            }
//            else if (c instanceof ColumnOp) {
//                ColumnOp col = (ColumnOp) c;
//                if (col.getOpType().equals("sum")) {
//                    String aliasForSubSumEst = generateNextAliasName();
//                    String aliasForSubsampleSize = generateNextAliasName();
//                    String aliasForMeanEstimate = generateMeanEstimateAliasName(aliasName);
//                    String aliasForStdEstimate = generateStdEstimateAliasName(aliasName);
//                    
//                    UnnamedColumn op = col.getOperand();        // argument within the sum function
//                    UnnamedColumn probCol = deriveInclusionProbabilityColumnFromSource(sel.getFromList(), aggblock);
//                    ColumnOp newCol = ColumnOp.sum(ColumnOp.divide(op, probCol));
//                    newInnerSelectList.add(new AliasedColumn(newCol, aliasForSubSumEst));
//                    ColumnOp oneIfNotNull = ColumnOp.casewhenelse(
//                            ConstantColumn.valueOf(1),
//                            ColumnOp.notnull(op),
//                            ConstantColumn.valueOf(0));
//                    newInnerSelectList.add(
//                        new AliasedColumn(
//                            ColumnOp.sum(oneIfNotNull),
//                            aliasForSubsampleSize));
//                    newOuterSelectList.add(
//                        new AliasedColumn(ColumnOp.sum(new BaseColumn(innerTableAliasName, aliasForSubSumEst)),
//                                          aliasForMeanEstimate));
//                    newOuterSelectList.add(
//                        new AliasedColumn(
//                            ColumnOp.divide(
//                                ColumnOp.std(
//                                    ColumnOp.multiply(
//                                        new BaseColumn(innerTableAliasName, aliasForSubSumEst),
//                                        ColumnOp.sqrt(new BaseColumn(innerTableAliasName, aliasForSubsampleSize)))),
//                                ColumnOp.sqrt(
//                                    ColumnOp.sum(new BaseColumn(innerTableAliasName, aliasForSubsampleSize)))),
//                            aliasForStdEstimate));
//                }
//                else if (col.getOpType().equals("count")) {
//                    String aliasForSubCountEst = generateNextAliasName();
//                    String aliasForSubsampleSize = generateNextAliasName();
//                    String aliasForMeanEstimate = generateMeanEstimateAliasName(aliasName);
//                    String aliasForStdEstimate = generateStdEstimateAliasName(aliasName);
//                    
//                    // inner
//                    UnnamedColumn probCol = deriveInclusionProbabilityColumnFromSource(sel.getFromList(), aggblock);
//                    ColumnOp newCol = ColumnOp.sum(ColumnOp.divide(ConstantColumn.valueOf(1), probCol));
//                    newInnerSelectList.add(new AliasedColumn(newCol, aliasForSubCountEst));
//                    newInnerSelectList.add(
//                        new AliasedColumn(
//                            ColumnOp.count(),
//                            aliasForSubsampleSize));
//                    // outer
//                    newOuterSelectList.add(
//                        new AliasedColumn(ColumnOp.sum(new BaseColumn(innerTableAliasName, aliasForSubCountEst)),
//                                          aliasForMeanEstimate));
//                    newOuterSelectList.add(
//                        new AliasedColumn(
//                            ColumnOp.divide(
//                                ColumnOp.std(
//                                    ColumnOp.multiply(
//                                        new BaseColumn(innerTableAliasName, aliasForSubCountEst),
//                                        ColumnOp.sqrt(new BaseColumn(innerTableAliasName, aliasForSubsampleSize)))),
//                                ColumnOp.sqrt(
//                                    ColumnOp.sum(new BaseColumn(innerTableAliasName, aliasForSubsampleSize)))),
//                            aliasForStdEstimate));
//                }
//                else if (col.getOpType().equals("avg")) {
//                    String aliasForSubSumEst = generateNextAliasName();
//                    String aliasForSubCountEst = generateNextAliasName();
//                    String aliasForSubsampleSize = generateNextAliasName();
//                    String aliasForMeanEstimate = generateMeanEstimateAliasName(aliasName);
//                    String aliasForStdEstimate = generateStdEstimateAliasName(aliasName);
//                    
//                    // inner
//                    UnnamedColumn op = col.getOperand();        // argument within the avg function
//                    UnnamedColumn probCol = deriveInclusionProbabilityColumnFromSource(sel.getFromList(), aggblock);
//                    ColumnOp newCol = ColumnOp.sum(ColumnOp.divide(op, probCol));
//                    newInnerSelectList.add(new AliasedColumn(newCol, aliasForSubSumEst));
//                    ColumnOp oneIfNotNull = ColumnOp.casewhenelse(
//                            ConstantColumn.valueOf(1),
//                            ColumnOp.notnull(op),
//                            ConstantColumn.valueOf(0));
//                    newInnerSelectList.add(
//                        new AliasedColumn(
//                            ColumnOp.sum(ColumnOp.divide(oneIfNotNull, probCol)),
//                            aliasForSubCountEst));
//                    newInnerSelectList.add(
//                        new AliasedColumn(
//                            ColumnOp.sum(oneIfNotNull),
//                            aliasForSubsampleSize));
//                    // outer
//                    newOuterSelectList.add(
//                        new AliasedColumn(
//                            ColumnOp.divide(
//                                ColumnOp.sum(new BaseColumn(innerTableAliasName, aliasForSubSumEst)),
//                                ColumnOp.sum(new BaseColumn(innerTableAliasName, aliasForSubCountEst))),
//                            aliasForMeanEstimate));
//                    newOuterSelectList.add(
//                        new AliasedColumn(
//                            ColumnOp.divide(
//                                ColumnOp.std(
//                                    ColumnOp.multiply(
//                                        ColumnOp.divide(
//                                            new BaseColumn(innerTableAliasName, aliasForSubSumEst),
//                                            new BaseColumn(innerTableAliasName, aliasForSubCountEst)),
//                                        ColumnOp.sqrt(new BaseColumn(innerTableAliasName, aliasForSubsampleSize)))),
//                                ColumnOp.sqrt(
//                                    ColumnOp.sum(new BaseColumn(innerTableAliasName, aliasForSubsampleSize)))),
//                            aliasForStdEstimate));
//                }
//                else {
//                    throw new UnexpectedTypeException("Not implemented yet.");
//                }
//            }
//            else {
//                throw new UnexpectedTypeException("Unexpected column type: " + c.getClass().toString());
//            }
//        }
//        
//        for (GroupingAttribute a : groupbyList) {
//            boolean added = false;
//            for (Pair<UnnamedColumn, Pair<String, String>> pair : innerNonaggregateSelectItemToAliases) {
//                UnnamedColumn col = pair.getLeft();
//                String innerAlias = pair.getRight().getLeft();
//                String outerAlias = pair.getRight().getRight();
//                
//                // when grouping attribute is a base column
//                if (a.equals(col)) {
//                    newInnerGroupbyList.add(new AliasReference(innerAlias));
//                    newOuterGroupbyList.add(new AliasReference(outerAlias));
//                    added = true;
//                }
//                // when grouping attribute is the alias to an select item
//                else if (a instanceof AliasReference && ((AliasReference) a).getAliasName().equals(outerAlias)) {
//                    newInnerGroupbyList.add(new AliasReference(innerAlias));
//                    newOuterGroupbyList.add(new AliasReference(outerAlias));
//                    added = true;
//                }
//            }
//            if (added == false) {
//                newInnerGroupbyList.add(a);
//            }
//        }
//        newInnerGroupbyList.add(subsampleId);
//        
//        // inner query
//        for (SelectItem c : newInnerSelectList) {
//            rewrittenInner.addSelectItem(c);
//        }
//        for (AbstractRelation r : sel.getFromList()) {
//            rewrittenInner.addTableSource(r);
//        }
//        if (sel.getFilter().isPresent()) {
//            rewrittenInner.addFilterByAnd(sel.getFilter().get());
//        }
//        UnnamedColumn aggBlockPredicate = generateAggBlockPredicate(sel.getFromList(), aggblock);
//        rewrittenInner.addFilterByAnd(aggBlockPredicate);
//        for (GroupingAttribute a : newInnerGroupbyList) {
//            rewrittenInner.addGroupby(a);
//        }
//        
//        // outer query
//        for (SelectItem c : newOuterSelectList) {
//            rewrittenOuter.addSelectItem(c);
//        }
//        rewrittenOuter.addTableSource(rewrittenInner);
//        for (GroupingAttribute a : newOuterGroupbyList) {
//            rewrittenOuter.addGroupby(a);
//        }
//        
//        return rewrittenOuter;
//    }
    
    
    
//    UnnamedColumn generateAggBlockPredicate(
//            List<AbstractRelation> fromList,
//            AggregationBlock aggblock)
//    throws VerdictDbException {
//        List<UnnamedColumn> partitionPredicates = new ArrayList<>();
//        for (AbstractRelation source : fromList) {
//            if (!(source instanceof BaseTable)) {
//                throw new UnexpectedTypeException("Unexpected source type: " + source.getClass().toString());
//            }
//            
//            BaseTable base = (BaseTable) source;
//            String colName = scrambleMeta.getPartitionColumn(base.getSchemaName(), base.getTableName());
//            if (colName == null) {
//                continue;
//            }
//            if (partitionPredicates.size() > 0) {
//                throw new ValueException("Only a single table can be a scrambled table.");
//            }
//            String aliasName = base.getTableSourceAlias();
//            Pair<Integer, Integer> startEndIndices = aggblock.getBlockIndexOf(base.getSchemaName(), base.getTableName());
//            int startIndex = startEndIndices.getLeft();
//            int endIndex = startEndIndices.getRight();
//            if (startIndex == endIndex) {
//                return ColumnOp.equal(new BaseColumn(aliasName, colName), ConstantColumn.valueOf(endIndex));
//            } else {
//                throw new ValueException("Multiple aggregation blocks are not allowed.");
//            }
//        }
//        throw new ValueException("Empty fromList is not expected.");
//    }
    
    UnnamedColumn deriveSubsampleIdColumn(AbstractRelation relation) throws VerdictDbException {
        if (!(relation instanceof SelectQueryOp)) {
            throw new UnexpectedTypeException("Unexpected relation type: " + relation.getClass().toString());
        }
        
        List<UnnamedColumn> subsampleColumns = new ArrayList<>();
        SelectQueryOp sel = (SelectQueryOp) relation;
        List<AbstractRelation> fromList = sel.getFromList();
        for (AbstractRelation r : fromList) {
            Optional<UnnamedColumn> c = subsampleColumnOfSource(r);
            if (!c.isPresent()) {
                continue;
            }
            if (subsampleColumns.size() > 0) {
                throw new ValueException("Only a single table can be a scrambled table.");
            }
            subsampleColumns.add(c.get());
        }
        
        return subsampleColumns.get(0);
    }
    
    Optional<UnnamedColumn> subsampleColumnOfSource(AbstractRelation source) throws VerdictDbException {
        if (source instanceof BaseTable) {
            BaseTable base = (BaseTable) source;
            String colName = scrambleMeta.getSubsampleColumn(base.getSchemaName(), base.getTableName());
            if (colName == null) {
                return Optional.empty();
            }
            String aliasName = base.getTableSourceAlias();
            BaseColumn col = new BaseColumn(aliasName, colName);
            return Optional.<UnnamedColumn>of(col);
        }
        else if (source instanceof SelectQueryOp) {
            Optional<String> aliasName = ((SelectQueryOp) source).getAliasName();
            if (!aliasName.isPresent()) {
                return Optional.empty();
            }
            String colName = scrambleMeta.getSubsampleColumn(aliasName.get());
            BaseColumn col = new BaseColumn(aliasName.get(), colName);
            return Optional.<UnnamedColumn>of(col);
        }
        else {
            throw new UnexpectedTypeException("Unexpected source type: " + source.getClass().toString()); 
        }
    }
    
//    List<String> partitionAttributeValuesOfSource(AbstractRelation source) throws UnexpectedTypeException {
//        if (source instanceof BaseTable) {
//            BaseTable base = (BaseTable) source;
//            String schemaName = base.getSchemaName();
//            String tableName = base.getTableName();
//            return scrambleMeta.getPartitionAttributes(schemaName, tableName);
//        }
//        else {
//            throw new UnexpectedTypeException("Not implemented yet.");
//        }
//        
//    }
    
//    ColumnOp derivePartitionFilter(AbstractRelation relation, int partitionNumber) throws UnexpectedTypeException {
//        AbstractColumn partCol = derivePartitionColumn(relation);
//        String partitionValue = derivePartitionValue(relation, partitionNumber);
//        return ColumnOp.equal(partCol, ConstantColumn.valueOf(partitionValue));
//    }
    
    Optional<UnnamedColumn> partitionColumnOfSource(AbstractRelation source) throws UnexpectedTypeException {
        if (source instanceof BaseTable) {
            BaseTable base = (BaseTable) source;
            String colName = scrambleMeta.getAggregationBlockColumn(base.getSchemaName(), base.getTableName());
            String aliasName = base.getTableSourceAlias();
            BaseColumn col = new BaseColumn(aliasName, colName);
            return Optional.<UnnamedColumn>of(col);
        }
        else {
            throw new UnexpectedTypeException("Not implemented yet.");
        }
    }
    
//    int derivePartitionCount(AbstractRelation relation) throws UnexpectedTypeException {
//        if (!(relation instanceof SelectQueryOp)) {
//            throw new UnexpectedTypeException("Unexpected relation type: " + relation.getClass().toString());
//        }
//        // TODO: partition count should be modified to handle the joins of multiple tables.
//        SelectQueryOp sel = (SelectQueryOp) relation;
//        List<AbstractRelation> fromList = sel.getFromList();
//        int partCount = 0;
//        for (AbstractRelation r : fromList) {
//            int c = partitionCountOfSource(r);
//            if (partCount == 0) {
//                partCount = c;
//            }
//            else {
//                partCount = partCount * c;
//            }
//        }
//        return partCount;
//    }
    
//    int partitionCountOfSource(AbstractRelation source) throws UnexpectedTypeException {
//        if (source instanceof BaseTable) {
//            BaseTable tab = (BaseTable) source;
//            return scrambleMeta.getPartitionCount(tab.getSchemaName(), tab.getTableName());
//        }
//        else {
//            throw new UnexpectedTypeException("Not implemented yet.");
//        }
//    }
    
    /**
     * Obtains the inclusion probability expression needed for computing the aggregates within the given
     * relation.
     * 
     * @param relation
     * @return
     * @throws VerdictDbException 
     */
//    UnnamedColumn deriveInclusionProbabilityColumnFromSource(
//            List<AbstractRelation> fromList,
//            AggregationBlock aggblock)
//    throws VerdictDbException {
//        UnnamedColumn incProbCol = null;
//        for (AbstractRelation r : fromList) {
//            Optional<UnnamedColumn> c = inclusionProbabilityColumnOfSingleSource(r, aggblock);
//            if (!c.isPresent()) {
//                continue;
//            }
//            if (incProbCol == null) {
//                incProbCol = c.get();
//            }
//            else {
//                incProbCol = new ColumnOp("multiply", Arrays.asList(incProbCol, c.get()));
//            }
//        }
//        return incProbCol;
//    }
    
//    Optional<UnnamedColumn> inclusionProbabilityColumnOfSingleSource(
//            AbstractRelation source,
//            AggregationBlock aggblock)
//    throws VerdictDbException {
//        if (source instanceof BaseTable) {
//            BaseTable base = (BaseTable) source;
//            String incProbCol = scrambleMeta.getInclusionProbabilityColumn(base.getSchemaName(), base.getTableName());
//            String incProbDiff = scrambleMeta.getInclusionProbabilityBlockDifferenceColumn(base.getSchemaName(), base.getTableName());
//            if (incProbCol == null) {
//                return Optional.empty();
//            }
//            Pair<Integer, Integer> startEndIndices = aggblock.getBlockIndexOf(base.getSchemaName(), base.getTableName());
//            String aliasName = base.getTableSourceAlias();
//            int startIndex = startEndIndices.getLeft();
//            int endIndex = startEndIndices.getRight();
//            if (startIndex == endIndex) {
//                ColumnOp col = ColumnOp.add(
//                        new BaseColumn(aliasName, incProbCol),
//                        ColumnOp.multiply(new BaseColumn(aliasName, incProbDiff), ConstantColumn.valueOf(endIndex)));
//                return Optional.<UnnamedColumn>of(col);
//            } else {
//                throw new ValueException("Multiple aggregation blocks cannot be used.");
//            }
//        }
//        else {
//            throw new UnexpectedTypeException("Derived tables cannot be used."); 
//        }
//    }
    
//    AggregationBlockSequence generateAggregationBlockSequence(AbstractRelation relation) throws VerdictDbException {
//        if (!(relation instanceof SelectQueryOp)) {
//            throw new UnexpectedTypeException("Unexpected argument type: " + relation.getClass().toString());
//        }
//        
//        SelectQueryOp sel = (SelectQueryOp) relation;
//        List<BaseTable> sourceTables = new ArrayList<>();
//        for (AbstractRelation a : sel.getFromList()) {
//            if (!(a instanceof BaseTable)) {
//                throw new UnexpectedTypeException("Unexpected source type: " + a.getClass().toString());
//            }
//            BaseTable base = (BaseTable) a;
//            String col = scrambleMeta.getPartitionColumn(base.getSchemaName(), base.getTableName());
//            if (col != null) {
//                sourceTables.add(base);
//            }
//        }
//        
//        if (sourceTables.size() > 1) {
//            throw new ValueException("More than one scrambled table exists.");
//        }
//        BaseTable base = sourceTables.get(0);
//        int aggBlockCount = scrambleMeta.getAggregationBlockCount(base.getSchemaName(), base.getTableName());
//        AggregationBlockSequence seq = new AggregationBlockSequence();
//        seq.addScrambledTable(base);
//        for (int k = 0; k < aggBlockCount; k++) {
//            seq.addNoJoinBlock(k);
//        }
//        
//        return seq;
//    }

}

/**
 * Stores information about how to partition (or block-aggregate) one or multiple scrambled
 * tables.
 * 
 * @author Yongjoo Park
 *
 */
class BlockAggregationInRewriting {
    
    AbstractRelation topRelation;
    
    // the size of this list is the number of scrambled tables (i.e., one or two).
    List<RelationBlockInfo> blockAggregateInfo = new ArrayList<>();
    
    public BlockAggregationInRewriting() {};
    
    public static BlockAggregationInRewriting initializeWithRegular(BaseTable base) {
        BlockAggregationInRewriting inRewriting = new BlockAggregationInRewriting();
        inRewriting.setTopRelation(base);
        return inRewriting;
    }
    
    public static BlockAggregationInRewriting initializeWithScrambled(
            BaseTable base, BaseColumn aggBlockColumn, int aggBlockCount, String aliasName) {
        BlockAggregationInRewriting inRewriting = new BlockAggregationInRewriting();
        SelectQueryOp converted = SelectQueryOp.getSelectQueryOp(Arrays.<SelectItem>asList(new AsteriskColumn()), base);
        converted.setAliasName(aliasName);
        inRewriting.setTopRelation(converted);
        inRewriting.getBlockAggregateInfo().add(new RelationBlockInfo(converted, aggBlockColumn, aggBlockCount));
        return inRewriting;
    }
    
    public AbstractRelation getTopRelation() {
        return topRelation;
    }
    
    void setTopRelation(AbstractRelation topRelation) {
        this.topRelation = topRelation;
    }
    
    public List<RelationBlockInfo> getBlockAggregateInfo() {
        return blockAggregateInfo;
    }
}


class RelationBlockInfo {
    
    SelectQueryOp relationToBlock;       // select * from baseTable;
    
    List<AggregationBlock> aggregationBlocks = new ArrayList<>();       // partition values
    
    public RelationBlockInfo(SelectQueryOp selectAllBaseTable, BaseColumn aggBlockColumn, int blockCount) {
        relationToBlock = selectAllBaseTable;
        for (int k = 0; k < blockCount; k++) {
            aggregationBlocks.add(new AggregationBlock(aggBlockColumn, k));
        }
    }
    
    public SelectQueryOp getRelationToBlock() {
        return relationToBlock;
    }
    
    public int getAggBlockCount() {
        return aggregationBlocks.size();
    }
    
    public ColumnOp getAggBlockPredicate(int aggBlockNumber) {
        return aggregationBlocks.get(aggBlockNumber).asPredicate();
    }
}


//class AggregationBlockSequence {
//    
//    List<BaseTable> scrambledTables = new ArrayList<>();
//    
//    List<AggregationBlock> blocks = new ArrayList<>();
//    
//    public AggregationBlock getBlock(int number) {
//        return blocks.get(number);
//    }
//    
//    public List<BaseTable> getScrambledTables() {
//        return scrambledTables;
//    }
//    
//    public void addScrambledTable(BaseTable table) {
//        scrambledTables.add(table);
//    }
//    
//    public void addNoJoinBlock(int blockIndex) {
//        blocks.add(new AggregationBlock(blockIndex, scrambledTables));
//    }
//    
//    public int getBlockCount() {
//        return blocks.size();
//    }
//}


class AggregationBlock {
    
//    List<BaseTable> scrambledTables;
    
//    /**
//     * Each element, i.e., Pair<Integer, Integer>, is the start and end indices of an aggregation block.
//     */
//    List<Pair<Integer, Integer>> blockStartEndIndices = new ArrayList<>();
    
    BaseColumn aggBlockColumn;
    
    int startIndex = 0;
    
    int endIndex = 0;
    
    long blockSize = 0;
    
    public AggregationBlock(BaseColumn aggBlockColumn, int blockIndex) {
        this.aggBlockColumn = aggBlockColumn;
        startIndex = blockIndex;
        endIndex = blockIndex;
    }
    
    public AggregationBlock(BaseColumn aggBlockColumn, int blockStartIndex, int blockEndIndex) {
        this.aggBlockColumn = aggBlockColumn;
        this.startIndex = blockStartIndex;
        this.endIndex = blockEndIndex;
    }
    
    public ColumnOp asPredicate() {
        if (startIndex == endIndex) {
            ColumnOp predicate = ColumnOp.equal(aggBlockColumn, ConstantColumn.valueOf(startIndex));
            return predicate;
        }
        else {
            ColumnOp predicate = ColumnOp.and(
                    ColumnOp.notlessthan(aggBlockColumn, ConstantColumn.valueOf(startIndex)),
                    ColumnOp.notgreaterthan(aggBlockColumn, ConstantColumn.valueOf(endIndex)));
            return predicate;
        }
    }
    
//    public int getNoJoinBlockIndex() {
//        return blockStartEndIndices.get(0).getLeft();
//    }
//    
//    public Pair<Integer, Integer> getBlockIndexOf(String schemaName, String tableName) {
//        int order = 0;
//        for (BaseTable base : scrambledTables) {
//            if (base.getSchemaName().equals(schemaName) && base.getTableName().equals(tableName)) {
//                break;
//            } else {
//                order += 1;
//            }
//        }
//        // in case it does not match anything
//        if (order >= scrambledTables.size()) {
//            return null;
//        }
//        return blockStartEndIndices.get(order);
//    }
//    
//    public List<Pair<Integer, Integer>> getDoubleJoinBlockStartEndIndices() {
//        return Arrays.asList(
//                Pair.of(blockStartEndIndices.get(0).getLeft(), blockStartEndIndices.get(0).getRight()),
//                Pair.of(blockStartEndIndices.get(1).getLeft(), blockStartEndIndices.get(1).getRight()));
//    }
}

