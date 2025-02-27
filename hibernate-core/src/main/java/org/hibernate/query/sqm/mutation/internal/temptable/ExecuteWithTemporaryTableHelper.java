/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.sqm.mutation.internal.temptable;

import java.util.function.Function;

import org.hibernate.LockMode;
import org.hibernate.LockOptions;
import org.hibernate.boot.TempTableDdlTransactionHandling;
import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.temptable.TemporaryTable;
import org.hibernate.dialect.temptable.TemporaryTableColumn;
import org.hibernate.dialect.temptable.TemporaryTableHelper;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.hibernate.engine.jdbc.spi.JdbcServices;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.engine.transaction.spi.IsolationDelegate;
import org.hibernate.metamodel.mapping.BasicValuedMapping;
import org.hibernate.metamodel.mapping.EntityMappingType;
import org.hibernate.metamodel.mapping.ModelPart;
import org.hibernate.query.ComparisonOperator;
import org.hibernate.query.NavigablePath;
import org.hibernate.query.sqm.mutation.internal.MultiTableSqmMutationConverter;
import org.hibernate.sql.ast.SqlAstJoinType;
import org.hibernate.sql.ast.SqlAstTranslatorFactory;
import org.hibernate.sql.ast.spi.SqlExpressionResolver;
import org.hibernate.sql.ast.tree.expression.ColumnReference;
import org.hibernate.sql.ast.tree.expression.QueryLiteral;
import org.hibernate.sql.ast.tree.from.StandardTableGroup;
import org.hibernate.sql.ast.tree.from.TableGroup;
import org.hibernate.sql.ast.tree.from.TableReference;
import org.hibernate.sql.ast.tree.insert.InsertStatement;
import org.hibernate.sql.ast.tree.predicate.ComparisonPredicate;
import org.hibernate.sql.ast.tree.predicate.Predicate;
import org.hibernate.sql.ast.tree.select.QuerySpec;
import org.hibernate.sql.exec.spi.ExecutionContext;
import org.hibernate.sql.exec.spi.JdbcInsert;
import org.hibernate.sql.exec.spi.JdbcParameterBindings;
import org.hibernate.sql.results.internal.SqlSelectionImpl;

/**
 * @author Steve Ebersole
 */
@SuppressWarnings("WeakerAccess")
public final class ExecuteWithTemporaryTableHelper {
	private ExecuteWithTemporaryTableHelper() {
	}

	public static int saveMatchingIdsIntoIdTable(
			MultiTableSqmMutationConverter sqmConverter,
			Predicate suppliedPredicate,
			TemporaryTable idTable,
			Function<SharedSessionContractImplementor, String> sessionUidAccess,
			JdbcParameterBindings jdbcParameterBindings,
			ExecutionContext executionContext) {
		final SessionFactoryImplementor factory = executionContext.getSession().getFactory();

		final TableGroup mutatingTableGroup = sqmConverter.getMutatingTableGroup();

		assert mutatingTableGroup.getModelPart() instanceof EntityMappingType;
		final EntityMappingType mutatingEntityDescriptor = (EntityMappingType) mutatingTableGroup.getModelPart();

		final TableReference idTableReference = new TableReference(
				idTable.getTableExpression(),
				null,
				false,
				factory
		);
		final InsertStatement idTableInsert = new InsertStatement( idTableReference );

		for ( int i = 0; i < idTable.getColumns().size(); i++ ) {
			final TemporaryTableColumn column = idTable.getColumns().get( i );
			idTableInsert.addTargetColumnReferences(
					new ColumnReference(
							idTableReference,
							column.getColumnName(),
							// id columns cannot be formulas and cannot have custom read and write expressions
							false,
							null,
							null,
							column.getJdbcMapping(),
							factory
					)
			);
		}

		final QuerySpec matchingIdSelection = new QuerySpec( true, 1 );
		idTableInsert.setSourceSelectStatement( matchingIdSelection );

		matchingIdSelection.getFromClause().addRoot( mutatingTableGroup );

		mutatingEntityDescriptor.getIdentifierMapping().forEachSelectable(
				(jdbcPosition, selection) -> {
					final TableReference tableReference = mutatingTableGroup.resolveTableReference(
							mutatingTableGroup.getNavigablePath(),
							selection.getContainingTableExpression()
					);
					matchingIdSelection.getSelectClause().addSqlSelection(
							new SqlSelectionImpl(
									jdbcPosition,
									jdbcPosition + 1,
									sqmConverter.getSqlExpressionResolver().resolveSqlExpression(
											SqlExpressionResolver.createColumnReferenceKey(
													tableReference,
													selection.getSelectionExpression()
											),
											sqlAstProcessingState -> new ColumnReference(
													tableReference,
													selection,
													factory
											)
									)
							)
					);
				}
		);

		if ( idTable.getSessionUidColumn() != null ) {
			final int jdbcPosition = matchingIdSelection.getSelectClause().getSqlSelections().size();
			matchingIdSelection.getSelectClause().addSqlSelection(
					new SqlSelectionImpl(
							jdbcPosition,
							jdbcPosition + 1,
							new QueryLiteral<>(
									sessionUidAccess.apply( executionContext.getSession() ),
									(BasicValuedMapping) idTable.getSessionUidColumn().getJdbcMapping()
							)
					)
			);
		}

		matchingIdSelection.applyPredicate( suppliedPredicate );
		return saveIntoTemporaryTable( idTableInsert, jdbcParameterBindings, executionContext );
	}

	public static int saveIntoTemporaryTable(
			InsertStatement temporaryTableInsert,
			JdbcParameterBindings jdbcParameterBindings,
			ExecutionContext executionContext) {
		final SessionFactoryImplementor factory = executionContext.getSession().getFactory();
		final JdbcServices jdbcServices = factory.getJdbcServices();
		final JdbcEnvironment jdbcEnvironment = jdbcServices.getJdbcEnvironment();
		final SqlAstTranslatorFactory sqlAstTranslatorFactory = jdbcEnvironment.getSqlAstTranslatorFactory();
		final LockOptions lockOptions = executionContext.getQueryOptions().getLockOptions();
		final LockMode lockMode = lockOptions.getLockMode();
		// Acquire a WRITE lock for the rows that are about to be modified
		lockOptions.setLockMode( LockMode.WRITE );
		// Visit the table joins and reset the lock mode if we encounter OUTER joins that are not supported
		if ( temporaryTableInsert.getSourceSelectStatement() != null
				&& !jdbcEnvironment.getDialect().supportsOuterJoinForUpdate() ) {
			temporaryTableInsert.getSourceSelectStatement().forEachQuerySpec(
					querySpec -> {
						querySpec.getFromClause().visitTableJoins(
								tableJoin -> {
									if ( tableJoin.getJoinType() != SqlAstJoinType.INNER ) {
										lockOptions.setLockMode( lockMode );
									}
								}
						);
					}
			);
		}
		final JdbcInsert jdbcInsert = sqlAstTranslatorFactory.buildInsertTranslator( factory, temporaryTableInsert )
				.translate( jdbcParameterBindings, executionContext.getQueryOptions() );
		lockOptions.setLockMode( lockMode );

		return jdbcServices.getJdbcMutationExecutor().execute(
				jdbcInsert,
				jdbcParameterBindings,
				sql -> executionContext.getSession()
						.getJdbcCoordinator()
						.getStatementPreparer()
						.prepareStatement( sql ),
				(integer, preparedStatement) -> {},
				executionContext
		);
	}

	public static QuerySpec createIdTableSelectQuerySpec(
			TemporaryTable idTable,
			Function<SharedSessionContractImplementor, String> sessionUidAccess,
			EntityMappingType entityDescriptor,
			ExecutionContext executionContext) {
		return createIdTableSelectQuerySpec( idTable, null, sessionUidAccess, entityDescriptor, executionContext );
	}

	public static QuerySpec createIdTableSelectQuerySpec(
			TemporaryTable idTable,
			ModelPart fkModelPart,
			Function<SharedSessionContractImplementor, String> sessionUidAccess,
			EntityMappingType entityDescriptor,
			ExecutionContext executionContext) {
		final QuerySpec querySpec = new QuerySpec( false );

		final TableReference idTableReference = new TableReference(
				idTable.getTableExpression(),
				TemporaryTable.DEFAULT_ALIAS,
				true,
				executionContext.getSession().getFactory()
		);
		final TableGroup idTableGroup = new StandardTableGroup(
				true,
				new NavigablePath( idTableReference.getTableExpression() ),
				entityDescriptor,
				null,
				idTableReference,
				null,
				executionContext.getSession().getFactory()
		);

		querySpec.getFromClause().addRoot( idTableGroup );

		applyIdTableSelections( querySpec, idTableReference, idTable, fkModelPart, executionContext );
		applyIdTableRestrictions( querySpec, idTableReference, idTable, sessionUidAccess, executionContext );

		return querySpec;
	}

	private static void applyIdTableSelections(
			QuerySpec querySpec,
			TableReference tableReference,
			TemporaryTable idTable,
			ModelPart fkModelPart,
			ExecutionContext executionContext) {
		if ( fkModelPart == null ) {
			final int size = idTable.getEntityDescriptor().getIdentifierMapping().getJdbcTypeCount();
			for ( int i = 0; i < size; i++ ) {
				final TemporaryTableColumn temporaryTableColumn = idTable.getColumns().get( i );
				if ( temporaryTableColumn != idTable.getSessionUidColumn() ) {
					querySpec.getSelectClause().addSqlSelection(
							new SqlSelectionImpl(
									i + 1,
									i,
									new ColumnReference(
											tableReference,
											temporaryTableColumn.getColumnName(),
											false,
											null,
											null,
											temporaryTableColumn.getJdbcMapping(),
											executionContext.getSession().getFactory()
									)
							)
					);
				}
			}
		}
		else {
			fkModelPart.forEachSelectable(
					(i, selectableMapping) -> {
						querySpec.getSelectClause().addSqlSelection(
								new SqlSelectionImpl(
										i + 1,
										i,
										new ColumnReference(
												tableReference,
												selectableMapping.getSelectionExpression(),
												false,
												null,
												null,
												selectableMapping.getJdbcMapping(),
												executionContext.getSession().getFactory()
										)
								)
						);
					}
			);
		}
	}

	private static void applyIdTableRestrictions(
			QuerySpec querySpec,
			TableReference idTableReference,
			TemporaryTable idTable,
			Function<SharedSessionContractImplementor, String> sessionUidAccess,
			ExecutionContext executionContext) {
		if ( idTable.getSessionUidColumn() != null ) {
			querySpec.applyPredicate(
					new ComparisonPredicate(
							new ColumnReference(
									idTableReference,
									idTable.getSessionUidColumn().getColumnName(),
									false,
									null,
									null,
									idTable.getSessionUidColumn().getJdbcMapping(),
									executionContext.getSession().getFactory()
							),
							ComparisonOperator.EQUAL,
							new QueryLiteral<>(
									sessionUidAccess.apply( executionContext.getSession() ),
									(BasicValuedMapping) idTable.getSessionUidColumn().getJdbcMapping()
							)
					)
			);
		}
	}

	public static void performBeforeTemporaryTableUseActions(
			TemporaryTable temporaryTable,
			ExecutionContext executionContext) {
		final SessionFactoryImplementor factory = executionContext.getSession().getFactory();
		final Dialect dialect = factory.getJdbcServices().getDialect();
		if ( dialect.getTemporaryTableBeforeUseAction() == BeforeUseAction.CREATE ) {
			final TemporaryTableHelper.TemporaryTableCreationWork temporaryTableCreationWork = new TemporaryTableHelper.TemporaryTableCreationWork(
					temporaryTable,
					factory
			);

			final TempTableDdlTransactionHandling ddlTransactionHandling = dialect.getTemporaryTableDdlTransactionHandling();
			if ( ddlTransactionHandling == TempTableDdlTransactionHandling.NONE ) {
				executionContext.getSession().doWork( temporaryTableCreationWork );
			}
			else {
				final IsolationDelegate isolationDelegate = executionContext.getSession()
						.getJdbcCoordinator()
						.getJdbcSessionOwner()
						.getTransactionCoordinator()
						.createIsolationDelegate();
				isolationDelegate.delegateWork( temporaryTableCreationWork, ddlTransactionHandling == TempTableDdlTransactionHandling.ISOLATE_AND_TRANSACT );
			}
		}
	}

	public static void performAfterTemporaryTableUseActions(
			TemporaryTable temporaryTable,
			Function<SharedSessionContractImplementor, String> sessionUidAccess,
			ExecutionContext executionContext) {
		final SessionFactoryImplementor factory = executionContext.getSession().getFactory();
		final Dialect dialect = factory.getJdbcServices().getDialect();
		switch ( dialect.getTemporaryTableAfterUseAction() ) {
			case CLEAN:
				TemporaryTableHelper.cleanTemporaryTableRows(
						temporaryTable,
						dialect.getTemporaryTableExporter(),
						sessionUidAccess,
						executionContext.getSession()
				);
				break;
			case DROP:
				final TemporaryTableHelper.TemporaryTableDropWork temporaryTableDropWork = new TemporaryTableHelper.TemporaryTableDropWork(
						temporaryTable,
						factory
				);

				final TempTableDdlTransactionHandling ddlTransactionHandling = dialect.getTemporaryTableDdlTransactionHandling();
				if ( ddlTransactionHandling == TempTableDdlTransactionHandling.NONE ) {
					executionContext.getSession().doWork( temporaryTableDropWork );
				}
				else {
					final IsolationDelegate isolationDelegate = executionContext.getSession()
							.getJdbcCoordinator()
							.getJdbcSessionOwner()
							.getTransactionCoordinator()
							.createIsolationDelegate();
					isolationDelegate.delegateWork(
							temporaryTableDropWork,
							ddlTransactionHandling == TempTableDdlTransactionHandling.ISOLATE_AND_TRANSACT
					);
				}
		}
	}
}
