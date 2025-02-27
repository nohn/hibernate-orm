/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.orm.test.query.hql;

import java.util.Calendar;
import java.util.List;

import org.hibernate.testing.orm.domain.gambit.SimpleEntity;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author Christian Beikov
 */
@DomainModel( annotatedClasses = SimpleEntity.class )
@SessionFactory
public class TupleTest {

	@Test
	public void testSelectTuple(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					List results = session.createQuery(
							"select o.id, (o.someString, o.someInteger), o.someLong from SimpleEntity o" )
							.list();
					assertThat( results.size(), is( 1 ) );
					Object[] result = (Object[]) results.get( 0 );
					assertThat( result[0], is( 1 ) );
					assertThat( result[1], instanceOf( Object[].class ) );
					Object[] tuple = (Object[]) result[1];
					assertThat( tuple[0], is( "aaa" ) );
					assertThat( tuple[1], is( Integer.MAX_VALUE ) );
					assertThat( result[2], is( Long.MAX_VALUE ) );
				}
		);
	}

	@BeforeEach
	public void setUp(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					SimpleEntity entity = new SimpleEntity(
							1,
							Calendar.getInstance().getTime(),
							null,
							Integer.MAX_VALUE,
							Long.MAX_VALUE,
							"aaa"
					);
					session.save( entity );
				}
		);
	}

	@AfterEach
	public void tearDown(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery( "delete SimpleEntity" ).executeUpdate();
				}
		);
	}
}
