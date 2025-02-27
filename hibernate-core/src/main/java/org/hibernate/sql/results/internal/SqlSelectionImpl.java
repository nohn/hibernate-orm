/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.sql.results.internal;

import java.util.Objects;

import org.hibernate.metamodel.mapping.JdbcMappingContainer;
import org.hibernate.metamodel.mapping.SqlExpressable;
import org.hibernate.sql.ast.SqlAstWalker;
import org.hibernate.sql.ast.spi.SqlExpressionAccess;
import org.hibernate.sql.ast.spi.SqlSelection;
import org.hibernate.sql.ast.tree.expression.Expression;
import org.hibernate.type.descriptor.ValueExtractor;

/**
 * @asciidoc
 *
 * ```
 * @Entity
 * class MyEntity {
 *     ...
 *     @Column ( name = "the_column", ... )
 *     public String getTheColumn() { ... }
 *
 *     @Convert ( ... )
 *     @Column ( name = "the_column", ... )
 *     ConvertedType getTheConvertedColumn() { ... }
 *
 * }
 * ```
 *
 * @author Steve Ebersole
 */
public class SqlSelectionImpl implements SqlSelection, SqlExpressionAccess {
	private final int jdbcPosition;
	private final int valuesArrayPosition;
	private final Expression sqlExpression;

	public SqlSelectionImpl(int jdbcPosition, int valuesArrayPosition, Expression sqlExpression) {
		this.jdbcPosition = jdbcPosition;
		this.valuesArrayPosition = valuesArrayPosition;
		this.sqlExpression = sqlExpression;
	}

	@Override
	public Expression getExpression() {
		return sqlExpression;
	}

	@Override
	public ValueExtractor getJdbcValueExtractor() {
		return ( (SqlExpressable) sqlExpression.getExpressionType() ).getJdbcMapping().getJdbcValueExtractor();
//		return jdbcValueExtractor;
//		return jdbcMapping.getJdbcValueExtractor();
	}

	@Override
	public int getJdbcResultSetIndex() {
		return jdbcPosition;
	}

	@Override
	public int getValuesArrayPosition() {
		return valuesArrayPosition;
	}

	@Override
	public JdbcMappingContainer getExpressionType() {
		return getExpression().getExpressionType();
	}

	@Override
	public void accept(SqlAstWalker interpreter) {
		sqlExpression.accept( interpreter );
	}

	@Override
	public boolean equals(Object o) {
		if ( this == o ) {
			return true;
		}
		if ( o == null || getClass() != o.getClass() ) {
			return false;
		}
		SqlSelectionImpl that = (SqlSelectionImpl) o;
		return jdbcPosition == that.jdbcPosition &&
				valuesArrayPosition == that.valuesArrayPosition &&
				Objects.equals( sqlExpression, that.sqlExpression );
	}

	@Override
	public int hashCode() {
		return Objects.hash( jdbcPosition, valuesArrayPosition, sqlExpression );
	}

	@Override
	public Expression getSqlExpression() {
		return sqlExpression;
	}
}
