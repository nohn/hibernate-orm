/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.userguide.locking;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PessimisticLockScope;

import org.hibernate.LockMode;
import org.hibernate.LockOptions;
import org.hibernate.Session;
import org.hibernate.dialect.Oracle8iDialect;
import org.hibernate.orm.test.jpa.BaseEntityManagerFunctionalTestCase;
import org.hibernate.query.Query;

import org.hibernate.testing.RequiresDialect;
import org.junit.Test;

import static org.hibernate.testing.transaction.TransactionUtil.doInJPA;
import static org.junit.Assert.assertEquals;

/**
 * @author Vlad Mihalcea
 */
public class ExplicitLockingTest extends BaseEntityManagerFunctionalTestCase {

	@Override
	protected Class<?>[] getAnnotatedClasses() {
		return new Class<?>[] {
				Person.class,
				Phone.class,
		};
	}

	@Test
	public void testJPALockTimeout() {
		doInJPA( this::entityManagerFactory, entityManager -> {
			Person person = new Person( "John Doe" );
			entityManager.persist( person );
		} );
		doInJPA( this::entityManagerFactory, entityManager -> {
			log.info( "testJPALockTimeout" );
			Long id = 1L;
			//tag::locking-jpa-query-hints-timeout-example[]
			entityManager.find(
				Person.class, id, LockModeType.PESSIMISTIC_WRITE,
				Collections.singletonMap( "jakarta.persistence.lock.timeout", 200 )
			);
			//end::locking-jpa-query-hints-timeout-example[]
		} );
	}

	@Test
	public void testJPALockScope() {
		doInJPA( this::entityManagerFactory, entityManager -> {
			Person person = new Person( "John Doe" );
			entityManager.persist( person );
			Phone home = new Phone( "123-456-7890" );
			Phone office = new Phone( "098-765-4321" );
			person.getPhones().add( home );
			person.getPhones().add( office );
			entityManager.persist( person );
		} );
		doInJPA( this::entityManagerFactory, entityManager -> {
			log.info( "testJPALockScope" );
			Long id = 1L;
			//tag::locking-jpa-query-hints-scope-example[]
			Person person = entityManager.find(
				Person.class, id, LockModeType.PESSIMISTIC_WRITE,
				Collections.singletonMap(
					"jakarta.persistence.lock.scope",
					PessimisticLockScope.EXTENDED )
			);
			//end::locking-jpa-query-hints-scope-example[]
			assertEquals( 2, person.getPhones().size() );
		} );
	}

	@Test
	public void testBuildLockRequest() {
		doInJPA( this::entityManagerFactory, entityManager -> {
			log.info( "testBuildLockRequest" );
			Person person = new Person( "John Doe" );
			Phone home = new Phone( "123-456-7890" );
			Phone office = new Phone( "098-765-4321" );
			person.getPhones().add( home );
			person.getPhones().add( office );
			entityManager.persist( person );
			entityManager.flush();
		} );
		doInJPA( this::entityManagerFactory, entityManager -> {
			Long id = 1L;
			//tag::locking-buildLockRequest-example[]
			Person person = entityManager.find( Person.class, id );
			Session session = entityManager.unwrap( Session.class );
			session
				.buildLockRequest( LockOptions.NONE )
				.setLockMode( LockMode.PESSIMISTIC_READ )
				.setTimeOut( LockOptions.NO_WAIT )
				.lock( person );
			//end::locking-buildLockRequest-example[]
		} );

		doInJPA( this::entityManagerFactory, entityManager -> {
			Long id = 1L;
			//tag::locking-buildLockRequest-scope-example[]
			Person person = entityManager.find( Person.class, id );
			Session session = entityManager.unwrap( Session.class );
			session
				.buildLockRequest( LockOptions.NONE )
				.setLockMode( LockMode.PESSIMISTIC_READ )
				.setTimeOut( LockOptions.NO_WAIT )
				.setScope( true )
				.lock( person );
			//end::locking-buildLockRequest-scope-example[]
		} );

	}

	@Test
	@RequiresDialect(Oracle8iDialect.class)
	public void testFollowOnLocking() {
		doInJPA( this::entityManagerFactory, entityManager -> {
			log.info( "testBuildLockRequest" );
			Person person1 = new Person( "John Doe" );
			Person person2 = new Person( "Mrs. John Doe" );

			entityManager.persist( person1 );
			entityManager.persist( person2 );
			entityManager.flush();
		} );

		doInJPA( this::entityManagerFactory, entityManager -> {
			//tag::locking-follow-on-example[]
			List<Person> persons = entityManager.createQuery(
				"select DISTINCT p from Person p", Person.class)
			.setLockMode( LockModeType.PESSIMISTIC_WRITE )
			.getResultList();
			//end::locking-follow-on-example[]
		} );

		doInJPA( this::entityManagerFactory, entityManager -> {
			//tag::locking-follow-on-secondary-query-example[]
			List<Person> persons = entityManager.createQuery(
				"select DISTINCT p from Person p", Person.class)
			.getResultList();

			entityManager.createQuery(
				"select p.id from Person p where p in :persons")
			.setLockMode( LockModeType.PESSIMISTIC_WRITE )
			.setParameter( "persons", persons )
			.getResultList();
			//end::locking-follow-on-secondary-query-example[]
		} );

		doInJPA( this::entityManagerFactory, entityManager -> {
			//tag::locking-follow-on-explicit-example[]
			List<Person> persons = entityManager.createQuery(
				"select p from Person p", Person.class)
			.setMaxResults( 10 )
			.unwrap( Query.class )
			.setLockOptions(
				new LockOptions( LockMode.PESSIMISTIC_WRITE )
					.setFollowOnLocking( false ) )
			.getResultList();
			//end::locking-follow-on-explicit-example[]
		} );
	}

	//tag::locking-jpa-query-hints-scope-entity-example[]
	@Entity(name = "Person")
	public static class Person {

		@Id
		@GeneratedValue
		private Long id;

		@Column(name = "`name`")
		private String name;

		@ElementCollection
		@JoinTable(name = "person_phone", joinColumns = @JoinColumn(name = "person_id"))
		private List<Phone> phones = new ArrayList<>();

		public Person() {
		}

		public Person(String name) {
			this.name = name;
		}

		public Long getId() {
			return id;
		}

		public String getName() {
			return name;
		}

		public List<Phone> getPhones() {
			return phones;
		}
	}

	@Embeddable
	public static class Phone {

		@Column
		private String mobile;

		public Phone() {}

		public Phone(String mobile) {
			this.mobile = mobile;
		}
	}
	//end::locking-jpa-query-hints-scope-entity-example[]
}
