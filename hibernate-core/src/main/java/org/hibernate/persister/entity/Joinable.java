/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.persister.entity;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.hibernate.Filter;
import org.hibernate.MappingException;
import org.hibernate.sql.ast.tree.from.TableGroup;

/**
 * Anything that can be loaded by outer join - namely
 * persisters for classes or collections.
 *
 * @author Gavin King
 */
public interface Joinable {
	//should this interface extend PropertyMapping?

	/**
	 * An identifying name; a class name or collection role name.
	 */
	public String getName();
	/**
	 * The table to join to.
	 */
	public String getTableName();

	/**
	 * The columns to join on
	 */
	public String[] getKeyColumnNames();

	/**
	 * Get the where clause filter, given a query alias and considering enabled session filters
	 */
	public default String filterFragment(String alias, Map<String, Filter> enabledFilters) throws MappingException {
		return filterFragment( alias, enabledFilters, Collections.emptySet() );
	}

	/**
	 * Get the where clause filter, given a query alias and considering enabled session filters
	 */
	public String filterFragment(String alias, Map<String, Filter> enabledFilters, Set<String> treatAsDeclarations) throws MappingException;

	public String filterFragment(
			TableGroup tableGroup,
			Map<String, Filter> enabledFilters,
			Set<String> treatAsDeclarations,
			boolean useIdentificationVariable) throws MappingException;

	public String oneToManyFilterFragment(String alias) throws MappingException;

	public String oneToManyFilterFragment(String alias, Set<String> treatAsDeclarations);

	/**
	 * Is this instance actually a CollectionPersister?
	 */
	public boolean isCollection();

	/**
	 * Very, very, very ugly...
	 *
	 * @return Does this persister "consume" entity column aliases in the result
	 * set?
	 */
	public boolean consumesEntityAlias();

	/**
	 * Very, very, very ugly...
	 *
	 * @return Does this persister "consume" collection column aliases in the result
	 * set?
	 */
	public boolean consumesCollectionAlias();
}
