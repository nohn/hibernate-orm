/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.spatial.dialect.sqlserver;

import java.io.Serializable;
import java.util.Map;

import org.hibernate.boot.model.TypeContributions;
import org.hibernate.query.sqm.function.SqmFunctionDescriptor;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.spatial.SpatialDialect;
import org.hibernate.spatial.SpatialFunction;
import org.hibernate.spatial.SpatialRelation;

/**
 * Created by Karel Maesen, Geovise BVBA on 19/09/2018.
 */
class SqlServerSupport implements SpatialDialect, Serializable {

	private SqlServerFunctions functions = new SqlServerFunctions();

	Iterable<? extends Map.Entry<String, SqmFunctionDescriptor>> functionsToRegister() {
		return functions;
	}

	void contributeTypes(TypeContributions typeContributions, ServiceRegistry serviceRegistry) {
	}


	public String getSpatialRelateSQL(String columnName, int spatialRelation) {
		final String stfunction;
		switch ( spatialRelation ) {
			case SpatialRelation.WITHIN:
				stfunction = "STWithin";
				break;
			case SpatialRelation.CONTAINS:
				stfunction = "STContains";
				break;
			case SpatialRelation.CROSSES:
				stfunction = "STCrosses";
				break;
			case SpatialRelation.OVERLAPS:
				stfunction = "STOverlaps";
				break;
			case SpatialRelation.DISJOINT:
				stfunction = "STDisjoint";
				break;
			case SpatialRelation.INTERSECTS:
				stfunction = "STIntersects";
				break;
			case SpatialRelation.TOUCHES:
				stfunction = "STTouches";
				break;
			case SpatialRelation.EQUALS:
				stfunction = "STEquals";
				break;
			default:
				throw new IllegalArgumentException(
						"Spatial relation is not known by this dialect"
				);
		}

		return columnName + "." + stfunction + "(?) = 1";
	}


	public String getSpatialFilterExpression(String columnName) {
		return columnName + ".Filter(?) = 1";
	}


	public String getSpatialAggregateSQL(String columnName, int aggregation) {
		throw new UnsupportedOperationException( "No spatial aggregate SQL functions." );
	}


	public String getDWithinSQL(String columnName) {
		throw new UnsupportedOperationException( "SQL Server has no DWithin function." );
	}


	public String getHavingSridSQL(String columnName) {
		return columnName + ".STSrid = (?)";
	}


	public String getIsEmptySQL(String columnName, boolean isEmpty) {
		final String base = "(" + columnName + ".STIsEmpty() ";
		return isEmpty ? base + " = 1 )" : base + " = 0 )";
	}


	public boolean supportsFiltering() {
		return true;
	}


	public boolean supports(SpatialFunction function) {
		return ( functions.get( function.toString() ) != null );
	}

}

