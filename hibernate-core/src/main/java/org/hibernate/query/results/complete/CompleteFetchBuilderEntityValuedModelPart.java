/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.results.complete;

import java.util.List;
import java.util.function.BiFunction;

import org.hibernate.engine.FetchTiming;
import org.hibernate.query.NavigablePath;
import org.hibernate.query.results.DomainResultCreationStateImpl;
import org.hibernate.query.results.SqlSelectionImpl;
import org.hibernate.query.results.dynamic.DynamicFetchBuilderLegacy;
import org.hibernate.sql.ast.spi.SqlExpressionResolver;
import org.hibernate.sql.ast.tree.from.TableGroup;
import org.hibernate.sql.ast.tree.from.TableReference;
import org.hibernate.sql.results.graph.DomainResultCreationState;
import org.hibernate.sql.results.graph.Fetch;
import org.hibernate.sql.results.graph.FetchParent;
import org.hibernate.sql.results.graph.entity.EntityValuedFetchable;
import org.hibernate.sql.results.jdbc.spi.JdbcValuesMetadata;

import static org.hibernate.query.results.ResultsHelper.impl;
import static org.hibernate.query.results.ResultsHelper.jdbcPositionToValuesArrayPosition;

/**
 * CompleteFetchBuilder for entity-valued ModelParts
 *
 * @author Christian Beikov
 */
public class CompleteFetchBuilderEntityValuedModelPart
		implements CompleteFetchBuilder, ModelPartReferenceEntity {
	private final NavigablePath navigablePath;
	private final EntityValuedFetchable modelPart;
	private final List<String> columnAliases;

	public CompleteFetchBuilderEntityValuedModelPart(
			NavigablePath navigablePath,
			EntityValuedFetchable modelPart,
			List<String> columnAliases) {
		this.navigablePath = navigablePath;
		this.modelPart = modelPart;
		this.columnAliases = columnAliases;
	}

	@Override
	public NavigablePath getNavigablePath() {
		return navigablePath;
	}

	@Override
	public EntityValuedFetchable getReferencedPart() {
		return modelPart;
	}

	@Override
	public Fetch buildFetch(
			FetchParent parent,
			NavigablePath fetchPath,
			JdbcValuesMetadata jdbcResultsMetadata,
			BiFunction<String, String, DynamicFetchBuilderLegacy> legacyFetchResolver,
			DomainResultCreationState domainResultCreationState) {
		assert fetchPath.equals( navigablePath );
		final DomainResultCreationStateImpl creationStateImpl = impl( domainResultCreationState );

		final TableGroup tableGroup = creationStateImpl.getFromClauseAccess().getTableGroup( navigablePath.getParent() );
		modelPart.forEachSelectable(
				(selectionIndex, selectableMapping) -> {
					final TableReference tableReference = tableGroup.resolveTableReference( navigablePath, selectableMapping.getContainingTableExpression() );
					final String mappedColumn = selectableMapping.getSelectionExpression();
					final String columnAlias = columnAliases.get( selectionIndex );
					creationStateImpl.resolveSqlSelection(
							creationStateImpl.resolveSqlExpression(
									SqlExpressionResolver.createColumnReferenceKey( tableReference, mappedColumn ),
									processingState -> {
										final int jdbcPosition = jdbcResultsMetadata.resolveColumnPosition( columnAlias );
										final int valuesArrayPosition = jdbcPositionToValuesArrayPosition( jdbcPosition );
										return new SqlSelectionImpl( valuesArrayPosition, selectableMapping.getJdbcMapping() );
									}
							),
							modelPart.getJavaTypeDescriptor(),
							creationStateImpl.getSessionFactory().getTypeConfiguration()
					);
				}
		);

		return parent.generateFetchableFetch(
				modelPart,
				fetchPath,
				FetchTiming.DELAYED,
				true,
				null,
				domainResultCreationState
		);
	}

}
