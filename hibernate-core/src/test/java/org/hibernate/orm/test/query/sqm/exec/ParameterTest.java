/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.orm.test.query.sqm.exec;

import org.hibernate.testing.orm.domain.StandardDomainModel;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.junit.jupiter.api.Test;

/**
 * @author Steve Ebersole
 */
@DomainModel( standardModels = StandardDomainModel.RETAIL )
@SessionFactory
public class ParameterTest {
	@Test
	public void testReusedNamedParam(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery( "from SalesAssociate p where p.name.familiarName = :name or p.name.familyName = :name" )
							.setParameter( "name", "a name" )
							.list();
				}
		);
	}

	@Test
	public void testReusedOrdinalParam(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery( "from SalesAssociate p where p.name.familiarName = ?1 or p.name.familyName = ?1" )
							.setParameter( 1, "a name" )
							.list();
				}
		);
	}
}
