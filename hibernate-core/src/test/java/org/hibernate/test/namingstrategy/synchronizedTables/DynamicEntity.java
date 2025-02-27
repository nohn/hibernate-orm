/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.test.namingstrategy.synchronizedTables;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import org.hibernate.annotations.Synchronize;

/**
 * @author Steve Ebersole
 */
@Entity
@Synchronize( value = "table_a" )
public class DynamicEntity {
	@Id
	public Integer id;
}
