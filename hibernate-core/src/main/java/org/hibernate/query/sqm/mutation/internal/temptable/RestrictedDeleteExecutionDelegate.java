/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.sqm.mutation.internal.temptable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.hibernate.dialect.temptable.TemporaryTable;
import org.hibernate.engine.jdbc.spi.JdbcServices;
import org.hibernate.engine.spi.LoadQueryInfluencers;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.internal.FilterHelper;
import org.hibernate.internal.util.MutableBoolean;
import org.hibernate.internal.util.MutableInteger;
import org.hibernate.metamodel.mapping.EntityIdentifierMapping;
import org.hibernate.metamodel.mapping.EntityMappingType;
import org.hibernate.metamodel.mapping.ForeignKeyDescriptor;
import org.hibernate.metamodel.mapping.MappingModelExpressable;
import org.hibernate.metamodel.mapping.MappingModelHelper;
import org.hibernate.metamodel.mapping.SelectableConsumer;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.persister.entity.Joinable;
import org.hibernate.query.spi.DomainQueryExecutionContext;
import org.hibernate.query.spi.QueryOptions;
import org.hibernate.query.spi.QueryParameterBindings;
import org.hibernate.query.sqm.internal.DomainParameterXref;
import org.hibernate.query.sqm.internal.SqmJdbcExecutionContextAdapter;
import org.hibernate.query.sqm.internal.SqmUtil;
import org.hibernate.query.sqm.mutation.internal.MultiTableSqmMutationConverter;
import org.hibernate.query.sqm.mutation.internal.SqmMutationStrategyHelper;
import org.hibernate.query.sqm.tree.delete.SqmDeleteStatement;
import org.hibernate.query.sqm.tree.expression.SqmParameter;
import org.hibernate.sql.ast.spi.SqlAstTreeHelper;
import org.hibernate.sql.ast.spi.SqlExpressionResolver;
import org.hibernate.sql.ast.tree.delete.DeleteStatement;
import org.hibernate.sql.ast.tree.expression.ColumnReference;
import org.hibernate.sql.ast.tree.expression.Expression;
import org.hibernate.sql.ast.tree.expression.JdbcParameter;
import org.hibernate.sql.ast.tree.expression.SqlTuple;
import org.hibernate.sql.ast.tree.from.TableGroup;
import org.hibernate.sql.ast.tree.from.TableReference;
import org.hibernate.sql.ast.tree.from.UnionTableReference;
import org.hibernate.sql.ast.tree.predicate.FilterPredicate;
import org.hibernate.sql.ast.tree.predicate.InSubQueryPredicate;
import org.hibernate.sql.ast.tree.predicate.Predicate;
import org.hibernate.sql.ast.tree.select.QuerySpec;
import org.hibernate.sql.exec.spi.ExecutionContext;
import org.hibernate.sql.exec.spi.JdbcDelete;
import org.hibernate.sql.exec.spi.JdbcParameterBindings;

import org.jboss.logging.Logger;

/**
 * @author Steve Ebersole
 */
public class RestrictedDeleteExecutionDelegate implements TableBasedDeleteHandler.ExecutionDelegate {
	private static final Logger log = Logger.getLogger( RestrictedDeleteExecutionDelegate.class );

	private final EntityMappingType entityDescriptor;
	private final TemporaryTable idTable;
	private final SqmDeleteStatement<?> sqmDelete;
	private final DomainParameterXref domainParameterXref;
	private final SessionFactoryImplementor sessionFactory;

	private final Function<SharedSessionContractImplementor,String> sessionUidAccess;
	private final MultiTableSqmMutationConverter converter;

	@SuppressWarnings("WeakerAccess")
	public RestrictedDeleteExecutionDelegate(
			EntityMappingType entityDescriptor,
			TemporaryTable idTable,
			SqmDeleteStatement<?> sqmDelete,
			DomainParameterXref domainParameterXref,
			Function<SharedSessionContractImplementor, String> sessionUidAccess,
			QueryOptions queryOptions,
			LoadQueryInfluencers loadQueryInfluencers,
			QueryParameterBindings queryParameterBindings,
			SessionFactoryImplementor sessionFactory) {
		this.entityDescriptor = entityDescriptor;
		this.idTable = idTable;
		this.sqmDelete = sqmDelete;
		this.domainParameterXref = domainParameterXref;
		this.sessionUidAccess = sessionUidAccess;
		this.sessionFactory = sessionFactory;
		this.converter = new MultiTableSqmMutationConverter(
				entityDescriptor,
				sqmDelete,
				sqmDelete.getTarget(),
				domainParameterXref,
				queryOptions,
				loadQueryInfluencers,
				queryParameterBindings,
				sessionFactory
		);
	}

	@Override
	public int execute(DomainQueryExecutionContext executionContext) {
		final EntityPersister entityDescriptor = sessionFactory.getDomainModel().getEntityDescriptor( sqmDelete.getTarget().getEntityName() );
		final String hierarchyRootTableName = ( (Joinable) entityDescriptor ).getTableName();

		final TableGroup deletingTableGroup = converter.getMutatingTableGroup();

		final TableReference hierarchyRootTableReference = deletingTableGroup.resolveTableReference(
				deletingTableGroup.getNavigablePath(),
				hierarchyRootTableName
		);
		assert hierarchyRootTableReference != null;

		final Map<SqmParameter, List<List<JdbcParameter>>> parameterResolutions;
		final Map<SqmParameter, MappingModelExpressable> paramTypeResolutions;

		if ( domainParameterXref.getSqmParameterCount() == 0 ) {
			parameterResolutions = Collections.emptyMap();
			paramTypeResolutions = Collections.emptyMap();
		}
		else {
			parameterResolutions = new IdentityHashMap<>();
			paramTypeResolutions = new LinkedHashMap<>();
		}

		// Use the converter to interpret the where-clause.  We do this for 2 reasons:
		//		1) the resolved Predicate is ultimately the base for applying restriction to the deletes
		//		2) we also inspect each ColumnReference that is part of the where-clause to see which
		//			table it comes from.  if all of the referenced columns (if any at all) are from the root table
		//			we can perform all of the deletes without using an id-table
		final MutableBoolean needsIdTableWrapper = new MutableBoolean( false );
		Predicate predicate = converter.visitWhereClause(
				sqmDelete.getWhereClause(),
				columnReference -> {
					if ( ! hierarchyRootTableReference.getIdentificationVariable().equals( columnReference.getQualifier() ) ) {
						needsIdTableWrapper.setValue( true );
					}
				},
				(sqmParameter, mappingType, jdbcParameters) -> {
					parameterResolutions.computeIfAbsent(
							sqmParameter,
							k -> new ArrayList<>( 1 )
					).add( jdbcParameters );
					paramTypeResolutions.put( sqmParameter, mappingType );
				}
		);

		final FilterPredicate filterPredicate = FilterHelper.createFilterPredicate(
				executionContext.getSession().getLoadQueryInfluencers(),
				(Joinable) entityDescriptor,
				deletingTableGroup
		);
		if ( filterPredicate != null ) {
			needsIdTableWrapper.setValue( true );
			predicate = SqlAstTreeHelper.combinePredicates( predicate, filterPredicate );
		}
		converter.pruneTableGroupJoins();

		// We need an id table if we want to delete from an intermediate table to avoid FK violations
		// The intermediate table has a FK to the root table, so we can't delete from the root table first
		// Deleting from the intermediate table first also isn't possible,
		// because that is the source for deletion in other tables, hence we need an id table
		final boolean needsIdTable = needsIdTableWrapper.getValue()
				|| entityDescriptor != entityDescriptor.getRootEntityDescriptor();

		final SqmJdbcExecutionContextAdapter executionContextAdapter = SqmJdbcExecutionContextAdapter.omittingLockingAndPaging( executionContext );

		if ( needsIdTable ) {
			return executeWithIdTable(
					predicate,
					deletingTableGroup,
					parameterResolutions,
					paramTypeResolutions,
					executionContextAdapter
			);
		}
		else {
			return executeWithoutIdTable(
					predicate,
					deletingTableGroup,
					parameterResolutions,
					paramTypeResolutions,
					converter.getSqlExpressionResolver(),
					executionContextAdapter
			);
		}
	}

	private int executeWithoutIdTable(
			Predicate suppliedPredicate,
			TableGroup tableGroup,
			Map<SqmParameter, List<List<JdbcParameter>>> restrictionSqmParameterResolutions,
			Map<SqmParameter, MappingModelExpressable> paramTypeResolutions,
			SqlExpressionResolver sqlExpressionResolver,
			ExecutionContext executionContext) {
		assert entityDescriptor == entityDescriptor.getRootEntityDescriptor();

		final EntityPersister rootEntityPersister = entityDescriptor.getEntityPersister();
		final String rootTableName = ( (Joinable) rootEntityPersister ).getTableName();
		final TableReference rootTableReference = tableGroup.resolveTableReference(
				tableGroup.getNavigablePath(),
				rootTableName
		);

		final QuerySpec matchingIdSubQuerySpec = ExecuteWithoutIdTableHelper.createIdMatchingSubQuerySpec(
				tableGroup.getNavigablePath(),
				rootTableReference,
				suppliedPredicate,
				rootEntityPersister,
				sqlExpressionResolver,
				sessionFactory
		);

		final JdbcParameterBindings jdbcParameterBindings = SqmUtil.createJdbcParameterBindings(
				executionContext.getQueryParameterBindings(),
				domainParameterXref,
				SqmUtil.generateJdbcParamsXref(
						domainParameterXref,
						() -> restrictionSqmParameterResolutions
				),
				sessionFactory.getDomainModel(),
				navigablePath -> tableGroup,
				paramTypeResolutions::get,
				executionContext.getSession()
		);

		SqmMutationStrategyHelper.cleanUpCollectionTables(
				entityDescriptor,
				(tableReference, attributeMapping) -> {
					// No need for a predicate if there is no supplied predicate i.e. this is a full cleanup
					if ( suppliedPredicate == null ) {
						return null;
					}
					final ForeignKeyDescriptor fkDescriptor = attributeMapping.getKeyDescriptor();
					final QuerySpec idSelectFkSubQuery;
					// todo (6.0): based on the location of the attribute mapping, we could prune the table group of the subquery
					if ( fkDescriptor.getTargetPart() instanceof EntityIdentifierMapping ) {
						idSelectFkSubQuery = matchingIdSubQuerySpec;
					}
					else {
						idSelectFkSubQuery = ExecuteWithoutIdTableHelper.createIdMatchingSubQuerySpec(
								tableGroup.getNavigablePath(),
								rootTableReference,
								suppliedPredicate,
								rootEntityPersister,
								sqlExpressionResolver,
								sessionFactory
						);
					}
					return new InSubQueryPredicate(
							MappingModelHelper.buildColumnReferenceExpression(
									fkDescriptor,
									null,
									sessionFactory
							),
							idSelectFkSubQuery,
							false
					);

				},
				jdbcParameterBindings,
				executionContext
		);

		if ( rootTableReference instanceof UnionTableReference ) {
			final MutableInteger rows = new MutableInteger();
			entityDescriptor.visitConstraintOrderedTables(
					(tableExpression, tableKeyColumnVisitationSupplier) -> {
						final TableReference tableReference = new TableReference(
								tableExpression,
								tableGroup.getPrimaryTableReference().getIdentificationVariable(),
								false,
								sessionFactory
						);
						final QuerySpec idMatchingSubQuerySpec;
						// No need for a predicate if there is no supplied predicate i.e. this is a full cleanup
						if ( suppliedPredicate == null ) {
							idMatchingSubQuerySpec = null;
						}
						else {
							idMatchingSubQuerySpec = matchingIdSubQuerySpec;
						}
						rows.plus(
								deleteFromNonRootTableWithoutIdTable(
										tableReference,
										tableKeyColumnVisitationSupplier,
										sqlExpressionResolver,
										tableGroup,
										idMatchingSubQuerySpec,
										jdbcParameterBindings,
										executionContext
								)
						);
					}
			);
			return rows.get();
		}
		else {
			entityDescriptor.visitConstraintOrderedTables(
					(tableExpression, tableKeyColumnVisitationSupplier) -> {
						if ( !tableExpression.equals( rootTableName ) ) {
							final TableReference tableReference = tableGroup.getTableReference(
									tableGroup.getNavigablePath(),
									tableExpression,
									true,
									true
							);
							final QuerySpec idMatchingSubQuerySpec;
							// No need for a predicate if there is no supplied predicate i.e. this is a full cleanup
							if ( suppliedPredicate == null ) {
								idMatchingSubQuerySpec = null;
							}
							else {
								idMatchingSubQuerySpec = matchingIdSubQuerySpec;
							}
							deleteFromNonRootTableWithoutIdTable(
									tableReference,
									tableKeyColumnVisitationSupplier,
									sqlExpressionResolver,
									tableGroup,
									idMatchingSubQuerySpec,
									jdbcParameterBindings,
									executionContext
							);
						}
					}
			);

			return deleteFromRootTableWithoutIdTable(
					rootTableReference,
					suppliedPredicate,
					jdbcParameterBindings,
					executionContext
			);
		}
	}

	private int deleteFromRootTableWithoutIdTable(
			TableReference rootTableReference,
			Predicate predicate,
			JdbcParameterBindings jdbcParameterBindings,
			ExecutionContext executionContext) {
		return executeSqlDelete(
				new DeleteStatement( rootTableReference, predicate ),
				jdbcParameterBindings,
				executionContext
		);
	}

	private int deleteFromNonRootTableWithoutIdTable(
			TableReference targetTableReference,
			Supplier<Consumer<SelectableConsumer>> tableKeyColumnVisitationSupplier,
			SqlExpressionResolver sqlExpressionResolver,
			TableGroup rootTableGroup,
			QuerySpec matchingIdSubQuerySpec,
			JdbcParameterBindings jdbcParameterBindings,
			ExecutionContext executionContext) {
		assert targetTableReference != null;
		log.tracef( "deleteFromNonRootTable - %s", targetTableReference.getTableExpression() );

		final TableReference deleteTableReference = new TableReference(
				targetTableReference.getTableExpression(),
				DeleteStatement.DEFAULT_ALIAS,
				true,
				sessionFactory
		);
		final Predicate tableDeletePredicate;
		if ( matchingIdSubQuerySpec == null ) {
			tableDeletePredicate = null;
		}
		else {
			/*
			 * delete from sub_table
			 * where sub_id in (
			 * 		select root_id from root_table
			 * 		where {predicate}
			 * )
			 */

			/*
			 * Create the `sub_id` reference as the LHS of the in-subquery predicate
			 */
			final List<ColumnReference> deletingTableColumnRefs = new ArrayList<>();
			tableKeyColumnVisitationSupplier.get().accept(
					(columnIndex, selection) -> {
						assert deleteTableReference.getTableReference( selection.getContainingTableExpression() ) != null;

						final Expression expression = sqlExpressionResolver.resolveSqlExpression(
								SqlExpressionResolver.createColumnReferenceKey( deleteTableReference, selection.getSelectionExpression() ),
								sqlAstProcessingState -> new ColumnReference(
										deleteTableReference,
										selection,
										sessionFactory
								)
						);

						deletingTableColumnRefs.add( (ColumnReference) expression );
					}
			);

			final Expression deletingTableColumnRefsExpression;
			if ( deletingTableColumnRefs.size() == 1 ) {
				deletingTableColumnRefsExpression = deletingTableColumnRefs.get( 0 );
			}
			else {
				deletingTableColumnRefsExpression = new SqlTuple( deletingTableColumnRefs, entityDescriptor.getIdentifierMapping() );
			}

			tableDeletePredicate = new InSubQueryPredicate(
					deletingTableColumnRefsExpression,
					matchingIdSubQuerySpec,
					false
			);
		}

		final DeleteStatement sqlAstDelete = new DeleteStatement( deleteTableReference, tableDeletePredicate );
		final int rows = executeSqlDelete(
				sqlAstDelete,
				jdbcParameterBindings,
				executionContext
		);
		log.debugf( "deleteFromNonRootTable - `%s` : %s rows", targetTableReference, rows );
		return rows;
	}


	private static int executeSqlDelete(
			DeleteStatement sqlAst,
			JdbcParameterBindings jdbcParameterBindings,
			ExecutionContext executionContext) {
		final SessionFactoryImplementor factory = executionContext.getSession().getFactory();

		final JdbcServices jdbcServices = factory.getJdbcServices();

		final JdbcDelete jdbcDelete = jdbcServices.getJdbcEnvironment()
				.getSqlAstTranslatorFactory()
				.buildDeleteTranslator( factory, sqlAst )
				.translate( jdbcParameterBindings, executionContext.getQueryOptions() );

		return jdbcServices.getJdbcMutationExecutor().execute(
				jdbcDelete,
				jdbcParameterBindings,
				sql -> executionContext.getSession()
						.getJdbcCoordinator()
						.getStatementPreparer()
						.prepareStatement( sql ),
				(integer, preparedStatement) -> {},
				executionContext
		);
	}

	private int executeWithIdTable(
			Predicate predicate,
			TableGroup deletingTableGroup,
			Map<SqmParameter, List<List<JdbcParameter>>> restrictionSqmParameterResolutions,
			Map<SqmParameter, MappingModelExpressable> paramTypeResolutions,
			ExecutionContext executionContext) {
		final JdbcParameterBindings jdbcParameterBindings = SqmUtil.createJdbcParameterBindings(
				executionContext.getQueryParameterBindings(),
				domainParameterXref,
				SqmUtil.generateJdbcParamsXref(
						domainParameterXref,
						() -> restrictionSqmParameterResolutions
				),
				sessionFactory.getDomainModel(),
				navigablePath -> deletingTableGroup,
				paramTypeResolutions::get,
				executionContext.getSession()
		);

		ExecuteWithTemporaryTableHelper.performBeforeTemporaryTableUseActions(
				idTable,
				executionContext
		);

		try {
			return executeUsingIdTable( predicate, executionContext, jdbcParameterBindings );
		}
		finally {
			ExecuteWithTemporaryTableHelper.performAfterTemporaryTableUseActions(
					idTable,
					sessionUidAccess,
					executionContext
			);
		}
	}

	private int executeUsingIdTable(
			Predicate predicate,
			ExecutionContext executionContext,
			JdbcParameterBindings jdbcParameterBindings) {
		final int rows = ExecuteWithTemporaryTableHelper.saveMatchingIdsIntoIdTable(
				converter,
				predicate,
				idTable,
				sessionUidAccess,
				jdbcParameterBindings,
				executionContext
		);

		final QuerySpec idTableIdentifierSubQuery = ExecuteWithTemporaryTableHelper.createIdTableSelectQuerySpec(
				idTable,
				sessionUidAccess,
				entityDescriptor,
				executionContext
		);

		SqmMutationStrategyHelper.cleanUpCollectionTables(
				entityDescriptor,
				(tableReference, attributeMapping) -> {
					final ForeignKeyDescriptor fkDescriptor = attributeMapping.getKeyDescriptor();
					final QuerySpec idTableFkSubQuery;
					if ( fkDescriptor.getTargetPart() instanceof EntityIdentifierMapping ) {
						idTableFkSubQuery = idTableIdentifierSubQuery;
					}
					else {
						idTableFkSubQuery = ExecuteWithTemporaryTableHelper.createIdTableSelectQuerySpec(
								idTable,
								fkDescriptor.getTargetPart(),
								sessionUidAccess,
								entityDescriptor,
								executionContext
						);
					}
					return new InSubQueryPredicate(
							MappingModelHelper.buildColumnReferenceExpression(
									fkDescriptor,
									null,
									sessionFactory
							),
							idTableFkSubQuery,
							false
					);

				},
				JdbcParameterBindings.NO_BINDINGS,
				executionContext
		);

		entityDescriptor.visitConstraintOrderedTables(
				(tableExpression, tableKeyColumnVisitationSupplier) -> deleteFromTableUsingIdTable(
						tableExpression,
						tableKeyColumnVisitationSupplier,
						idTableIdentifierSubQuery,
						executionContext
				)
		);

		return rows;
	}

	private void deleteFromTableUsingIdTable(
			String tableExpression,
			Supplier<Consumer<SelectableConsumer>> tableKeyColumnVisitationSupplier,
			QuerySpec idTableSubQuery,
			ExecutionContext executionContext) {
		log.tracef( "deleteFromTableUsingIdTable - %s", tableExpression );

		final SessionFactoryImplementor factory = executionContext.getSession().getFactory();

		final TableKeyExpressionCollector keyColumnCollector = new TableKeyExpressionCollector( entityDescriptor );
		final TableReference targetTable = new TableReference(
				tableExpression,
				DeleteStatement.DEFAULT_ALIAS,
				true,
				factory
		);

		tableKeyColumnVisitationSupplier.get().accept(
				(columnIndex, selection) -> {
					assert selection.getContainingTableExpression().equals( tableExpression );
					assert ! selection.isFormula();
					assert selection.getCustomReadExpression() == null;
					assert selection.getCustomWriteExpression() == null;

					keyColumnCollector.apply(
							new ColumnReference(
									targetTable,
									selection,
									factory
							)
					);
				}
		);

		final InSubQueryPredicate predicate = new InSubQueryPredicate(
				keyColumnCollector.buildKeyExpression(),
				idTableSubQuery,
				false
		);

		executeSqlDelete(
				new DeleteStatement( targetTable, predicate ),
				JdbcParameterBindings.NO_BINDINGS,
				executionContext
		);
	}

}
