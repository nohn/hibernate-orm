/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.test.loadplans.process;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.engine.spi.QueryParameters;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.jdbc.Work;
import org.hibernate.persister.entity.EntityPersister;

import org.junit.Test;

import org.hibernate.testing.junit4.BaseCoreFunctionalTestCase;
import org.hibernate.testing.junit4.ExtraAssertions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * @author Gail Badner
 */
public class EntityWithNonLazyOneToManyListResultSetProcessorTest extends BaseCoreFunctionalTestCase {

	@Override
	protected Class<?>[] getAnnotatedClasses() {
		return new Class[] { Poster.class, Message.class };
	}

	@Test
	public void testEntityWithList() throws Exception {
		final EntityPersister entityPersister = sessionFactory().getEntityPersister( Poster.class.getName() );

		// create some test data
		Session session = openSession();
		session.beginTransaction();
		Poster poster = new Poster();
		poster.pid = 0;
		poster.name = "John Doe";
		Message message1 = new Message();
		message1.mid = 1;
		message1.msgTxt = "Howdy!";
		message1.poster = poster;
		poster.messages.add( message1 );
		Message message2 = new Message();
		message2.mid = 2;
		message2.msgTxt = "Bye!";
		message2.poster = poster;
		poster.messages.add( message2 );
		session.save( poster );
		session.getTransaction().commit();
		session.close();

//		session = openSession();
//		session.beginTransaction();
//		Poster posterGotten = (Poster) session.get( Poster.class, poster.pid );
//		assertEquals( 0, posterGotten.pid.intValue() );
//		assertEquals( poster.name, posterGotten.name );
//		assertTrue( Hibernate.isInitialized( posterGotten.messages ) );
//		assertEquals( 2, posterGotten.messages.size() );
//		assertEquals( message1.msgTxt, posterGotten.messages.get( 0 ).msgTxt );
//		assertEquals( message2.msgTxt, posterGotten.messages.get( 1 ).msgTxt );
//		assertSame( posterGotten, posterGotten.messages.get( 0 ).poster );
//		assertSame( posterGotten, posterGotten.messages.get( 1 ).poster );
//		session.getTransaction().commit();
//		session.close();

		{

			final LoadPlan plan = Helper.INSTANCE.buildLoadPlan( sessionFactory(), entityPersister );

			final LoadQueryDetails queryDetails = Helper.INSTANCE.buildLoadQueryDetails( plan, sessionFactory() );
			final String sql = queryDetails.getSqlStatement();
			final ResultSetProcessor resultSetProcessor = queryDetails.getResultSetProcessor();

			final List results = new ArrayList();

			final Session workSession = openSession();
			workSession.beginTransaction();
			workSession.doWork(
					new Work() {
						@Override
						public void execute(Connection connection) throws SQLException {
							PreparedStatement ps = connection.prepareStatement( sql );
							ps.setInt( 1, 0 );
							ResultSet resultSet = ps.executeQuery();
							results.addAll(
									resultSetProcessor.extractResults(
											resultSet,
											(SessionImplementor) workSession,
											new QueryParameters(),
											Helper.parameterContext(),
											true,
											false,
											null,
											null
									)
							);
							resultSet.close();
							ps.close();
						}
					}
			);
			assertEquals( 2, results.size() );
			Object result1 = results.get( 0 );
			assertNotNull( result1 );
			assertSame( result1, results.get( 1 ) );

			Poster workPoster = ExtraAssertions.assertTyping( Poster.class, result1 );
			assertEquals( 0, workPoster.pid.intValue() );
			assertEquals( poster.name, workPoster.name );
			assertTrue( Hibernate.isInitialized( workPoster.messages ) );
			assertEquals( 2, workPoster.messages.size() );
			assertTrue( Hibernate.isInitialized( workPoster.messages ) );
			assertEquals( 2, workPoster.messages.size() );
			assertEquals( message1.msgTxt, workPoster.messages.get( 0 ).msgTxt );
			assertEquals( message2.msgTxt, workPoster.messages.get( 1 ).msgTxt );
			assertSame( workPoster, workPoster.messages.get( 0 ).poster );
			assertSame( workPoster, workPoster.messages.get( 1 ).poster );
			workSession.getTransaction().commit();
			workSession.close();
		}

		// clean up test data
		session = openSession();
		session.beginTransaction();
		session.delete( poster );
		session.getTransaction().commit();
		session.close();
	}

	@Entity( name = "Message" )
	public static class Message {
		@Id
		private Integer mid;
		private String msgTxt;
		@ManyToOne
		@JoinColumn
		private Poster poster;
	}

	@Entity( name = "Poster" )
	public static class Poster {
		@Id
		private Integer pid;
		private String name;
		@OneToMany(mappedBy = "poster", fetch = FetchType.EAGER, cascade = CascadeType.ALL )
		private List<Message> messages = new ArrayList<Message>();
	}
}
