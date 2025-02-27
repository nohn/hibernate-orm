/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.userguide.mapping.basic;

import java.sql.Types;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.metamodel.MappingMetamodel;
import org.hibernate.metamodel.mapping.JdbcMapping;
import org.hibernate.metamodel.mapping.internal.BasicAttributeMapping;
import org.hibernate.persister.entity.EntityPersister;

import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.isOneOf;

/**
 * @author Steve Ebersole
 */
@DomainModel( annotatedClasses = OffsetDateTimeMappingTests.EntityWithOffsetDateTime.class )
@SessionFactory
public class OffsetDateTimeMappingTests {

	@Test
	public void verifyMappings(SessionFactoryScope scope) {
		final MappingMetamodel domainModel = scope.getSessionFactory().getDomainModel();
		final EntityPersister entityDescriptor = domainModel.findEntityDescriptor( EntityWithOffsetDateTime.class );

		final BasicAttributeMapping attributeMapping = (BasicAttributeMapping) entityDescriptor.findAttributeMapping( "offsetDateTime" );
		final JdbcMapping jdbcMapping = attributeMapping.getJdbcMapping();
		assertThat( jdbcMapping.getJavaTypeDescriptor().getJavaTypeClass(), equalTo( OffsetDateTime.class ) );
		assertThat( jdbcMapping.getJdbcTypeDescriptor().getJdbcTypeCode(), isOneOf( Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE ) );

		scope.inTransaction(
				(session) -> {
					session.persist( new EntityWithOffsetDateTime( 1, OffsetDateTime.now() ) );
				}
		);

		scope.inTransaction(
				(session) -> session.find( EntityWithOffsetDateTime.class, 1 )
		);
	}

	@Entity( name = "EntityWithOffsetDateTime" )
	@Table( name = "EntityWithOffsetDateTime" )
	public static class EntityWithOffsetDateTime {
		@Id
		private Integer id;

		//tag::basic-OffsetDateTime-example[]
		// mapped as TIMESTAMP or TIMESTAMP_WITH_TIMEZONE
		private OffsetDateTime offsetDateTime;
		//end::basic-OffsetDateTime-example[]

		public EntityWithOffsetDateTime() {
		}

		public EntityWithOffsetDateTime(Integer id, OffsetDateTime offsetDateTime) {
			this.id = id;
			this.offsetDateTime = offsetDateTime;
		}
	}
}
