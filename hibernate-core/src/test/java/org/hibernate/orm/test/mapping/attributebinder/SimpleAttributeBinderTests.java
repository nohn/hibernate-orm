/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.orm.test.mapping.attributebinder;

import java.sql.Types;

import org.hibernate.mapping.BasicValue;
import org.hibernate.mapping.Property;

import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.DomainModelScope;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DomainModel( annotatedClasses = { System.class, YesNo.class } )
@SessionFactory
public class SimpleAttributeBinderTests {
	@Test
	public void verifyBootModel(DomainModelScope scope) {
		scope.withHierarchy( System.class, (descriptor) -> {
			final Property activeProp = descriptor.getProperty( "active" );
			final BasicValue activeMapping = (BasicValue) activeProp.getValue();

			assertThat( activeMapping.getJpaAttributeConverterDescriptor() ).isNotNull();

			final BasicValue.Resolution<?> resolution = activeMapping.resolve();

			assertThat( resolution.getDomainJavaDescriptor().getJavaType() ).isEqualTo( Boolean.class );
			assertThat( resolution.getRelationalJavaDescriptor().getJavaType() ).isEqualTo( Character.class );
			assertThat( resolution.getJdbcTypeDescriptor().getJdbcTypeCode() ).isEqualTo( Types.CHAR );
			assertThat( resolution.getValueConverter() ).isNotNull();
		} );
	}

	@Test
	public void basicTest(SessionFactoryScope scope) {
		scope.inTransaction( (session) -> {
			session.createQuery( "from System" ).list();
		} );
	}
}
