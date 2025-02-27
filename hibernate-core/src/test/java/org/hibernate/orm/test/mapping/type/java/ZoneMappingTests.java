/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.orm.test.mapping.type.java;

import java.sql.Types;
import java.time.Year;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;

import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.metamodel.mapping.internal.BasicAttributeMapping;
import org.hibernate.persister.entity.EntityPersister;

import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.JiraKey;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;

import org.junit.jupiter.api.Test;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import static org.assertj.core.api.Assertions.assertThat;

@JiraKey( "HHH-13393" )
@DomainModel( annotatedClasses = ZoneMappingTests.ZoneMappingTestEntity.class )
@SessionFactory
public class ZoneMappingTests {

	@Test
	public void basicAssertions(SessionFactoryScope scope) {
		final SessionFactoryImplementor sessionFactory = scope.getSessionFactory();
		final EntityPersister entityDescriptor = sessionFactory.getMetamodel().entityPersister( ZoneMappingTestEntity.class );

		{
			final BasicAttributeMapping zoneIdAttribute = (BasicAttributeMapping) entityDescriptor.findAttributeMapping( "zoneId" );
			assertThat( zoneIdAttribute.getJdbcMapping().getJdbcTypeDescriptor().getJdbcTypeCode() ).isEqualTo( Types.VARCHAR );
			assertThat( zoneIdAttribute.getJdbcMapping().getJavaTypeDescriptor().getJavaTypeClass() ).isEqualTo( ZoneId.class );
		}

		{
			final BasicAttributeMapping zoneOffsetAttribute = (BasicAttributeMapping) entityDescriptor.findAttributeMapping( "zoneOffset" );
			assertThat( zoneOffsetAttribute.getJdbcMapping().getJdbcTypeDescriptor().getJdbcTypeCode() ).isEqualTo( Types.VARCHAR );
			assertThat( zoneOffsetAttribute.getJdbcMapping().getJavaTypeDescriptor().getJavaTypeClass() ).isEqualTo( ZoneOffset.class );
		}
	}

	@Test
	public void testUsage(SessionFactoryScope scope) {
		final ZoneMappingTestEntity entity = new ZoneMappingTestEntity( 1, "one", ZoneId.systemDefault(), ZoneOffset.UTC );
		final ZoneMappingTestEntity entity2 = new ZoneMappingTestEntity( 2, "two", ZoneId.systemDefault(), ZoneOffset.ofHours( 0 ) );
		final ZoneMappingTestEntity entity3 = new ZoneMappingTestEntity( 3, "three", ZoneId.systemDefault(), ZoneOffset.ofHours( -10 ) );

		scope.inTransaction( (session) -> {
			session.persist( entity );
			session.persist( entity2 );
			session.persist( entity3 );
		} );

		try {
			scope.inTransaction( (session) -> {
				session.createQuery( "from ZoneMappingTestEntity" ).list();
			});
		}
		finally {
			scope.inTransaction( (session) -> {
				session.createQuery( "delete ZoneMappingTestEntity" ).executeUpdate();
			});
		}
	}


	@Entity( name = "ZoneMappingTestEntity" )
	@Table( name = "zone_map_test_entity" )
	public static class ZoneMappingTestEntity {
		private Integer id;

		private String name;

		private ZoneId zoneId;
		private Set<ZoneId> zoneIds = new HashSet<>();

		private ZoneOffset zoneOffset;
		private Set<ZoneOffset> zoneOffsets = new HashSet<>();

		public ZoneMappingTestEntity() {
		}

		public ZoneMappingTestEntity(Integer id, String name, ZoneId zoneId, ZoneOffset zoneOffset) {
			this.id = id;
			this.name = name;
			this.zoneId = zoneId;
			this.zoneOffset = zoneOffset;
		}

		@Id
		public Integer getId() {
			return id;
		}

		public void setId(Integer id) {
			this.id = id;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public ZoneId getZoneId() {
			return zoneId;
		}

		public void setZoneId(ZoneId zoneId) {
			this.zoneId = zoneId;
		}

		@ElementCollection
		@CollectionTable( name = "zone_ids", joinColumns = @JoinColumn( name = "entity_id" ) )
		@Column( name = "zone_id" )
		public Set<ZoneId> getZoneIds() {
			return zoneIds;
		}

		public void setZoneIds(Set<ZoneId> zoneIds) {
			this.zoneIds = zoneIds;
		}

		public ZoneOffset getZoneOffset() {
			return zoneOffset;
		}

		public void setZoneOffset(ZoneOffset zoneOffset) {
			this.zoneOffset = zoneOffset;
		}

		@ElementCollection
		@CollectionTable( name = "zone_offsets", joinColumns = @JoinColumn( name = "entity_id" ) )
		@Column( name = "zone_offset" )
		public Set<ZoneOffset> getZoneOffsets() {
			return zoneOffsets;
		}

		public void setZoneOffsets(Set<ZoneOffset> zoneOffsets) {
			this.zoneOffsets = zoneOffsets;
		}
	}
}
