/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.results.dynamic;

/**
 * Contract for handling Hibernate's legacy way of representing fetches through
 * {@link org.hibernate.query.NativeQuery#addFetch}, {@link org.hibernate.query.NativeQuery#addJoin},
 * `hbm.xml` mappings, etc
 *
 * @see org.hibernate.query.results.DomainResultCreationStateImpl#getLegacyFetchResolver()
 *
 * @author Steve Ebersole
 */
@FunctionalInterface
public interface LegacyFetchResolver {
	DynamicFetchBuilderLegacy resolve(String ownerTableAlias, String fetchedPartPath);
}
