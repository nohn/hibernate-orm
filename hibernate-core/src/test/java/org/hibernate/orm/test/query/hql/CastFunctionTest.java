/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.orm.test.query.hql;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import org.hibernate.dialect.DerbyDialect;
import org.hibernate.dialect.Dialect;

import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.junit.jupiter.api.Test;

/**
 * @author Steve Ebersole
 */
@DomainModel( annotatedClasses = CastFunctionTest.MyEntity.class )
@SessionFactory
public class CastFunctionTest {
	@Test
	public void testStringCasting(SessionFactoryScope scope) {
		scope.inTransaction(
				(session) -> {
					final Dialect dialect = session.getFactory().getJdbcServices().getDialect();
					if ( DerbyDialect.class.isInstance( dialect ) ) {

						// the conversion from DOUBLE to VARCHAR is not supported by Derby,
						// using the short name
						session.createQuery( "select cast(char(e.theLostNumber) as string) from MyEntity e" ).list();
						// using the java class name
						session.createQuery( "select cast(char(e.theLostNumber) as java.lang.String) from MyEntity e" ).list();
						// using the fqn Hibernate Type name
						session.createQuery( "select cast(char(e.theLostNumber) as org.hibernate.type.StringType) from MyEntity e" )
								.list();
					}
					else {
						// using the short name
						session.createQuery( "select cast(e.theLostNumber as string) from MyEntity e" ).list();
						// using the java class name
						session.createQuery( "select cast(e.theLostNumber as java.lang.String) from MyEntity e" ).list();
						// using the fqn Hibernate Type name
						session.createQuery( "select cast(e.theLostNumber as org.hibernate.type.StringType) from MyEntity e" )
								.list();
					}
				}
		);
	}

	@Test
	public void testIntegerCasting(SessionFactoryScope scope) {
		scope.inTransaction(
				(session) -> {
					// using the short name
					session.createQuery( "select cast(e.theLostNumber as integer) from MyEntity e" ).list();
					// using the java class name (primitive)
					session.createQuery( "select cast(e.theLostNumber as int) from MyEntity e" ).list();
					// using the java class name
					session.createQuery( "select cast(e.theLostNumber as java.lang.Integer) from MyEntity e" ).list();
					// using the fqn Hibernate Type name
					session.createQuery( "select cast(e.theLostNumber as org.hibernate.type.IntegerType) from MyEntity e" ).list();
				}
		);
	}

	@Test
	public void testLongCasting(SessionFactoryScope scope) {
		scope.inTransaction(
				(session) -> {
					// using the short name (also the primitive name)
					session.createQuery( "select cast(e.theLostNumber as long) from MyEntity e" ).list();
					// using the java class name
					session.createQuery( "select cast(e.theLostNumber as java.lang.Long) from MyEntity e" ).list();
					// using the fqn Hibernate Type name
					session.createQuery( "select cast(e.theLostNumber as org.hibernate.type.LongType) from MyEntity e" ).list();
				}
		);
	}

	@Test
	public void testFloatCasting(SessionFactoryScope scope) {
		scope.inTransaction(
				(session) -> {
					// using the short name (also the primitive name)
					session.createQuery( "select cast(e.theLostNumber as float) from MyEntity e" ).list();
					// using the java class name
					session.createQuery( "select cast(e.theLostNumber as java.lang.Float) from MyEntity e" ).list();
					// using the fqn Hibernate Type name
					session.createQuery( "select cast(e.theLostNumber as org.hibernate.type.FloatType) from MyEntity e" ).list();

				}
		);
	}

	@Entity( name="MyEntity" )
	public static class MyEntity {
		@Id
		private Integer id;
		private String name;
		private Double theLostNumber;
	}

}
