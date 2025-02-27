/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.orm.test.annotations.idclass.xml;


import org.hibernate.query.Query;

import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.FailureExpected;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A test for HHH-4282
 *
 * @author Hardy Ferentschik
 */
//@FailureExpected( jiraKey = "HHH-4282" )
@DomainModel(
		annotatedClasses = HabitatSpeciesLink.class,
		xmlMappings = "org/hibernate/orm/test/annotations/idclass/xml/HabitatSpeciesLink.xml"
)
@SessionFactory
public class IdClassXmlTest {
	@Test
	public void testEntityMappingPropertiesAreNotIgnored(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					HabitatSpeciesLink link = new HabitatSpeciesLink();
					link.setHabitatId( 1L );
					link.setSpeciesId( 1L );
					session.persist( link );

					Query q = session.getNamedQuery( "testQuery" );
					assertEquals( 1, q.list().size() );
				}
		);
	}
}
