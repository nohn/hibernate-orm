/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */

//$Id$
package org.hibernate.test.annotations;
import jakarta.persistence.Entity;


/**
 * @author Emmanuel Bernard
 */
@Entity()
public class Ferry extends Boat {
	private String sea;

	public String getSea() {
		return sea;
	}

	public void setSea(String string) {
		sea = string;
	}

}
