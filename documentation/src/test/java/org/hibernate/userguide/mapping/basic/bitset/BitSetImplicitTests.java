/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.userguide.mapping.basic.bitset;

import java.sql.Types;
import java.util.BitSet;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.metamodel.MappingMetamodel;
import org.hibernate.metamodel.mapping.internal.BasicAttributeMapping;
import org.hibernate.persister.entity.EntityPersister;

import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

/**
 * Tests for using BitSet without any mapping details
 *
 * @author Steve Ebersole
 */
@DomainModel( annotatedClasses = BitSetImplicitTests.Product.class )
@SessionFactory
public class BitSetImplicitTests {

	@Test
	public void verifyMappings(SessionFactoryScope scope) {
		final SessionFactoryImplementor sessionFactory = scope.getSessionFactory();
		final MappingMetamodel domainModel = sessionFactory.getDomainModel();

		final EntityPersister entityDescriptor = domainModel.findEntityDescriptor( Product.class );
		final BasicAttributeMapping attributeMapping = (BasicAttributeMapping) entityDescriptor.findAttributeMapping( "bitSet" );

		assertThat( attributeMapping.getJavaTypeDescriptor().getJavaTypeClass(), equalTo( BitSet.class ) );

		assertThat( attributeMapping.getValueConverter(), nullValue() );

		assertThat(
				attributeMapping.getJdbcMapping().getJdbcTypeDescriptor().getJdbcTypeCode(),
				is( Types.VARBINARY )
		);

		// it will just be serialized
		assertThat( attributeMapping.getJdbcMapping().getJavaTypeDescriptor().getJavaTypeClass(), equalTo( BitSet.class ) );
	}


	@Table(name = "products")
	//tag::basic-bitset-example-implicit[]
	@Entity(name = "Product")
	public static class Product {
		@Id
		private Integer id;

		private BitSet bitSet;

		//Getters and setters are omitted for brevity
		//end::basic-bitset-example-implicit[]

		public Integer getId() {
			return id;
		}

		public void setId(Integer id) {
			this.id = id;
		}

		public BitSet getBitSet() {
			return bitSet;
		}

		public void setBitSet(BitSet bitSet) {
			this.bitSet = bitSet;
		}
		//tag::basic-bitset-example-implicit[]
	}
	//end::basic-bitset-example-implicit[]
}
