/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.orm.test.id.usertype;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.HibernateException;
import org.hibernate.annotations.CustomType;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.UserType;

import org.hibernate.testing.TestForIssue;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.junit.jupiter.api.Test;

@DomainModel(
		annotatedClasses = UserTypeNonComparableIdTest.SomeEntity.class
)
@SessionFactory
public class UserTypeNonComparableIdTest {

	@Test
	@TestForIssue(jiraKey = "HHH-8999")
	public void testUserTypeId(SessionFactoryScope scope) {
		SomeEntity e1 = new SomeEntity();
		SomeEntity e2 = new SomeEntity();
		scope.inTransaction(
				session -> {
					CustomId e1Id = new CustomId( 1L );
					e1.setCustomId( e1Id );
					CustomId e2Id = new CustomId( 2L );
					e2.setCustomId( e2Id );
					session.persist( e1 );
					session.persist( e2 );
				}
		);

		scope.inTransaction(
				session -> {
					session.delete( session.get( SomeEntity.class, e1.getCustomId() ) );
					session.delete( session.get( SomeEntity.class, e2.getCustomId() ) );
				}
		);
	}

	@Entity
	@Table(name = "some_entity")
	public static class SomeEntity {

		@Id
		@CustomType( CustomIdType.class )
		@Column(name = "id")
		private CustomId customId;

		public CustomId getCustomId() {
			return customId;
		}

		public void setCustomId(final CustomId customId) {
			this.customId = customId;
		}
	}

	public static class CustomId implements Serializable {

		private final Long value;

		public CustomId(final Long value) {
			this.value = value;
		}

		public Long getValue() {
			return value;
		}

		@Override
		public boolean equals(Object o) {
			if ( this == o ) {
				return true;
			}
			if ( o == null || getClass() != o.getClass() ) {
				return false;
			}

			CustomId customId = (CustomId) o;

			return !( value != null ? !value.equals( customId.value ) : customId.value != null );

		}

		@Override
		public int hashCode() {
			return value != null ? value.hashCode() : 0;
		}
	}

	public static class CustomIdType implements UserType<CustomId> {

		@Override
		public int[] sqlTypes() {
			return new int[] { Types.BIGINT };
		}

		@Override
		public CustomId nullSafeGet(ResultSet rs, int position, SharedSessionContractImplementor session, Object owner)
				throws SQLException {
			Long value = rs.getLong( position );

			return new CustomId( value );
		}

		@Override
		public void nullSafeSet(
				PreparedStatement preparedStatement,
				CustomId customId,
				int index,
				SharedSessionContractImplementor sessionImplementor) throws HibernateException, SQLException {
			if ( customId == null ) {
				preparedStatement.setNull( index, Types.BIGINT );
			}
			else {
				preparedStatement.setLong( index, customId.getValue() );
			}
		}

		@Override
		public Class returnedClass() {
			return CustomId.class;
		}

		@Override
		public boolean equals(Object x, Object y) throws HibernateException {
			return x.equals( y );
		}

		@Override
		public int hashCode(Object x) throws HibernateException {
			return x.hashCode();
		}

		@Override
		public Object deepCopy(Object value) throws HibernateException {
			return value;
		}

		@Override
		public boolean isMutable() {
			return true;
		}

		@Override
		public Serializable disassemble(Object value) throws HibernateException {
			return (Serializable) value;
		}

		@Override
		public Object assemble(Serializable cached, Object owner) throws HibernateException {
			return cached;
		}

		@Override
		public Object replace(Object original, Object target, Object owner) throws HibernateException {
			return original;
		}
	}
}
