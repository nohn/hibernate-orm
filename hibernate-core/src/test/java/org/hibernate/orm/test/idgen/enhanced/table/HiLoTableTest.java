/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.orm.test.idgen.enhanced.table;

import org.hibernate.id.enhanced.HiLoOptimizer;
import org.hibernate.id.enhanced.TableGenerator;
import org.hibernate.persister.entity.EntityPersister;

import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.instanceOf;
import static org.hibernate.id.IdentifierGeneratorHelper.BasicHolder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

@DomainModel( xmlMappings = "org/hibernate/orm/test/idgen/enhanced/table/HiLo.hbm.xml" )
@SessionFactory
public class HiLoTableTest {
	@Test
	public void testNormalBoundary(SessionFactoryScope scope) {
		final EntityPersister persister = scope.getSessionFactory().getEntityPersister( Entity.class.getName() );
		assertThat( persister.getIdentifierGenerator(), instanceOf( TableGenerator.class ) );

		final TableGenerator generator = (TableGenerator) persister.getIdentifierGenerator();
		assertThat( generator.getOptimizer(), instanceOf( HiLoOptimizer.class ) );

		final HiLoOptimizer optimizer = (HiLoOptimizer) generator.getOptimizer();
		final int increment = optimizer.getIncrementSize();

		scope.inTransaction(
				(s) -> {
					for ( int i = 0; i < increment; i++ ) {
						final Entity entity = new Entity( "" + ( i + 1 ) );
						s.save( entity );
						assertEquals( 1, generator.getTableAccessCount() ); // initialization
						assertEquals( 1, ( (BasicHolder) optimizer.getLastSourceValue() ).getActualLongValue() ); // initialization
						assertEquals( i + 1, ( (BasicHolder) optimizer.getLastValue() ).getActualLongValue() );
						assertEquals( increment + 1, ( (BasicHolder) optimizer.getHiValue() ).getActualLongValue() );
					}

					// now force a "clock over"
					final Entity entity = new Entity( "" + increment );
					s.save( entity );
					assertEquals( 2, generator.getTableAccessCount() ); // initialization
					assertEquals( 2, ( (BasicHolder) optimizer.getLastSourceValue() ).getActualLongValue() ); // initialization
					assertEquals( increment + 1, ( (BasicHolder) optimizer.getLastValue() ).getActualLongValue() );
					assertEquals( ( increment * 2 ) + 1, ( (BasicHolder) optimizer.getHiValue() ).getActualLongValue() );
				}
		);
	}

	@AfterEach
	public void cleanTestData(SessionFactoryScope scope) {
		scope.inTransaction(
				(session) -> session.createQuery( "delete Entity" ).executeUpdate()
		);
	}
}
