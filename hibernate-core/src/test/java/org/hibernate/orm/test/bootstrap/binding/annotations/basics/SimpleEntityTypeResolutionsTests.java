/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.orm.test.bootstrap.binding.annotations.basics;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Date;
import java.util.Iterator;

import jakarta.persistence.TemporalType;

import org.hibernate.SessionFactory;
import org.hibernate.mapping.BasicValue;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Property;
import org.hibernate.type.descriptor.java.IntegerJavaTypeDescriptor;
import org.hibernate.type.descriptor.jdbc.IntegerJdbcType;

import org.hibernate.testing.orm.domain.gambit.SimpleEntity;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.DomainModelScope;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Steve Ebersole
 */
@DomainModel( annotatedClasses = SimpleEntity.class )
public class SimpleEntityTypeResolutionsTests {
	@Test
	public void testIt(DomainModelScope scope) {
		final PersistentClass simpleEntityBinding = scope.getDomainModel().getEntityBinding( SimpleEntity.class.getName() );

		{
			final BasicValue identifier = (BasicValue) simpleEntityBinding.getIdentifier();
			assertThat( identifier.isValid( scope.getDomainModel() ), is( true ) );
			final BasicValue.Resolution<?> resolution = identifier.resolve();
			assertSame( IntegerJavaTypeDescriptor.INSTANCE, resolution.getDomainJavaDescriptor() );
			assertSame( IntegerJdbcType.INSTANCE, resolution.getJdbcTypeDescriptor() );
			assertThat( resolution.getJdbcMapping(), sameInstance( resolution.getLegacyResolvedBasicType() ) );
		}

		final Iterator<Property> itr = simpleEntityBinding.getPropertyIterator();
		while ( itr.hasNext() ) {
			final Property property = itr.next();
			final BasicValue propertyValue = (BasicValue) property.getValue();
			assertThat( propertyValue.isValid( scope.getDomainModel() ), is( true ) );

			final BasicValue.Resolution<?> propertyResolution = propertyValue.resolve();

			switch ( property.getName() ) {
				case "someDate": {
					assertThat(
							propertyResolution.getDomainJavaDescriptor().getJavaTypeClass(),
							sameInstance( Timestamp.class )
					);
					assertThat( propertyValue.getTemporalPrecision(), is( TemporalType.TIMESTAMP ) );
					break;
				}
				case "someInstant": {
					assertThat(
							propertyResolution.getDomainJavaDescriptor().getJavaTypeClass(),
							sameInstance( Instant.class )
					);
					assertThat( propertyValue.getTemporalPrecision(), nullValue() );
					break;
				}
				case "someInteger": {
					assertThat(
							propertyResolution.getDomainJavaDescriptor().getJavaTypeClass(),
							sameInstance( Integer.class )
					);
					break;
				}
				case "someLong": {
					assertThat(
							propertyResolution.getDomainJavaDescriptor().getJavaTypeClass(),
							sameInstance( Long.class )
					);
					break;
				}
				case "someString": {
					assertThat(
							propertyResolution.getDomainJavaDescriptor().getJavaTypeClass(),
							sameInstance( String.class )
					);
					break;
				}
				default: {
					fail( "Unexpected property : " + property.getName() );
				}
			}
		}

		try ( final SessionFactory sf = scope.getDomainModel().buildSessionFactory() ) {

		}
	}
}
