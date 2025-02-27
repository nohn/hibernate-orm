/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.orm.test.annotations.id.sequences;


import org.hibernate.boot.model.relational.SqlStringGenerationContext;
import org.hibernate.dialect.H2Dialect;
import org.hibernate.id.IdentifierGenerator;
import org.hibernate.id.enhanced.SequenceStyleGenerator;
import org.hibernate.mapping.Table;
import org.hibernate.persister.entity.EntityPersister;

import org.hibernate.testing.TestForIssue;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.RequiresDialect;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.hibernate.orm.test.annotations.id.sequences.entities.HibernateSequenceEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Lukasz Antoniak (lukasz dot antoniak at gmail dot com)
 */
@TestForIssue(jiraKey = "HHH-6068")
@RequiresDialect(value = H2Dialect.class)
@DomainModel(
		annotatedClasses = HibernateSequenceEntity.class,
		xmlMappings = "org/hibernate/orm/test/annotations/id/sequences/orm.xml"
)
@SessionFactory(createSecondarySchemas = true)
public class HibernateSequenceTest {
	private static final String SCHEMA_NAME = "OTHER_SCHEMA";

	@Test
	public void testHibernateSequenceSchema(SessionFactoryScope scope) {
		EntityPersister persister = scope.getSessionFactory()
				.getEntityPersister( HibernateSequenceEntity.class.getName() );
		IdentifierGenerator generator = persister.getIdentifierGenerator();
		assertTrue( SequenceStyleGenerator.class.isInstance( generator ) );
		SequenceStyleGenerator seqGenerator = (SequenceStyleGenerator) generator;
		SqlStringGenerationContext sqlStringGenerationContext = scope.getSessionFactory().getSqlStringGenerationContext();
		assertEquals(
				Table.qualify( null, SCHEMA_NAME, "HibernateSequenceEntity_SEQ" ),
				sqlStringGenerationContext.format( seqGenerator.getDatabaseStructure().getPhysicalName() )
		);
	}

	@Test
	public void testHibernateSequenceNextVal(SessionFactoryScope scope) {
		HibernateSequenceEntity entity = new HibernateSequenceEntity();
		scope.inTransaction(
				session -> {
					entity.setText( "sample text" );
					session.save( entity );
				}
		);

		assertNotNull( entity.getId() );
	}
}
