/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.jpa.test.procedure;

import jakarta.persistence.EntityManager;

import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.jpa.test.BaseEntityManagerFunctionalTestCase;
import org.hibernate.procedure.internal.NamedCallableQueryMementoImpl;
import org.hibernate.procedure.spi.ParameterStrategy;
import org.hibernate.type.IntegerType;
import org.hibernate.type.LongType;
import org.hibernate.type.StringType;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author Strong Liu <stliu@hibernate.org>
 */
public abstract class AbstractStoredProcedureTest extends BaseEntityManagerFunctionalTestCase {
	@Test
	public void testNamedStoredProcedureBinding() {
		EntityManager em = getOrCreateEntityManager();
		SessionFactoryImplementor sf = em.getEntityManagerFactory().unwrap( SessionFactoryImplementor.class );
		final NamedCallableQueryMementoImpl m1 = (NamedCallableQueryMementoImpl) sf.getNamedQueryRepository()
				.getNamedProcedureCallMemento( "s1" );
		assertNotNull( m1 );
		assertEquals( "p1", m1.getProcedureName() );
		assertEquals( ParameterStrategy.NAMED, m1.getParameterStrategy() );
		List<NamedCallableQueryMementoImpl.ParameterMemento> list = m1.getParameterDeclarations();
		assertEquals( 2, list.size() );
		NamedCallableQueryMementoImpl.ParameterMemento memento = list.get( 0 );
		assertEquals( "p11", memento.getName() );
		assertEquals( jakarta.persistence.ParameterMode.IN, memento.getMode() );
		assertEquals( IntegerType.INSTANCE, memento.getHibernateType() );
		assertEquals( Integer.class, memento.getType() );

		memento = list.get( 1 );
		assertEquals( "p12", memento.getName() );
		assertEquals( jakarta.persistence.ParameterMode.IN, memento.getMode() );
		assertEquals( IntegerType.INSTANCE, memento.getHibernateType() );
		assertEquals( Integer.class, memento.getType() );



		final NamedCallableQueryMementoImpl m2 = (NamedCallableQueryMementoImpl) sf.getNamedQueryRepository()
				.getNamedProcedureCallMemento( "s2" );
		assertNotNull( m2 );
		assertEquals( "p2", m2.getProcedureName() );
		assertEquals( ParameterStrategy.POSITIONAL, m2.getParameterStrategy() );
		list = m2.getParameterDeclarations();

		memento = list.get( 0 );
		assertEquals( Integer.valueOf( 1 ), memento.getPosition() );
		assertEquals( jakarta.persistence.ParameterMode.INOUT, memento.getMode() );
		assertEquals( StringType.INSTANCE, memento.getHibernateType() );
		assertEquals( String.class, memento.getType() );

		memento = list.get( 1 );
		assertEquals( Integer.valueOf( 2 ), memento.getPosition() );
		assertEquals( jakarta.persistence.ParameterMode.INOUT, memento.getMode() );
		assertEquals( LongType.INSTANCE, memento.getHibernateType() );
		assertEquals( Long.class, memento.getType() );

	}
}
