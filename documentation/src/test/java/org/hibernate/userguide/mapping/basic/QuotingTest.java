/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.userguide.mapping.basic;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import org.hibernate.orm.test.jpa.BaseEntityManagerFunctionalTestCase;

import org.junit.Test;

import static org.hibernate.testing.transaction.TransactionUtil.doInJPA;

/**
 * @author Vlad Mihalcea
 */
public class QuotingTest extends BaseEntityManagerFunctionalTestCase {

	@Override
	protected Class<?>[] getAnnotatedClasses() {
		return new Class<?>[] {
			Product.class
		};
	}

	@Test
	public void test() {
		doInJPA( this::entityManagerFactory, entityManager -> {
			//tag::basic-quoting-persistence-example[]
			Product product = new Product();
			product.setId( 1L );
			product.setName( "Mobile phone" );
			product.setNumber( "123-456-7890" );
			entityManager.persist( product );
			//end::basic-quoting-persistence-example[]
		} );
	}

	//tag::basic-quoting-example[]
	@Entity(name = "Product")
	public static class Product {

		@Id
		private Long id;

		@Column(name = "`name`")
		private String name;

		@Column(name = "`number`")
		private String number;

		//Getters and setters are omitted for brevity

	//end::basic-quoting-example[]

		public Long getId() {
			return id;
		}

		public void setId(Long id) {
			this.id = id;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getNumber() {
			return number;
		}

		public void setNumber(String number) {
			this.number = number;
		}


		//tag::basic-quoting-example[]
	}
	//end::basic-quoting-example[]
}
