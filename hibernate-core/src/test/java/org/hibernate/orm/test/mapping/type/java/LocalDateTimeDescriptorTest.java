/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.orm.test.mapping.type.java;

import java.time.LocalDateTime;
import org.hibernate.type.descriptor.java.LocalDateTimeJavaTypeDescriptor;

/**
 * @author Jordan Gigov
 */
public class LocalDateTimeDescriptorTest extends AbstractDescriptorTest<LocalDateTime> {
	final LocalDateTime original = LocalDateTime.of( 2016, 10, 8, 10, 15, 0 );
	final LocalDateTime copy = LocalDateTime.of( 2016, 10, 8, 10, 15, 0 );
	final LocalDateTime different = LocalDateTime.of( 2013,  8, 8, 15, 12 );

	public LocalDateTimeDescriptorTest() {
		super( LocalDateTimeJavaTypeDescriptor.INSTANCE);
	}

	@Override
	protected Data<LocalDateTime> getTestData() {
		return new Data<>( original, copy, different );
	}

	@Override
	protected boolean shouldBeMutable() {
		return false;
	}
	
}
