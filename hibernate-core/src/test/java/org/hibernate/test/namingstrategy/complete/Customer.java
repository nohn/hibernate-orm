/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.test.namingstrategy.complete;

import java.util.List;
import java.util.Set;
import jakarta.persistence.Basic;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Version;

/**
 * @author Steve Ebersole
 */
@Entity( name="CuStOmEr" )
public class Customer {
	private Integer id;
	private Integer version;
	private String name;
	private Set<String> registeredTrademarks;

	private Address hqAddress;
	private Set<Address> addresses;

	private List<Order> orders;

	private Set<Industry> industries;

	@Id
	@GeneratedValue
	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	@Version
	public Integer getVersion() {
		return version;
	}

	public void setVersion(Integer version) {
		this.version = version;
	}

	@Basic
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	@ElementCollection
	public Set<String> getRegisteredTrademarks() {
		return registeredTrademarks;
	}

	public void setRegisteredTrademarks(Set<String> registeredTrademarks) {
		this.registeredTrademarks = registeredTrademarks;
	}

	@Embedded
	public Address getHqAddress() {
		return hqAddress;
	}

	public void setHqAddress(Address hqAddress) {
		this.hqAddress = hqAddress;
	}

	@ElementCollection
	@Embedded
	public Set<Address> getAddresses() {
		return addresses;
	}

	public void setAddresses(Set<Address> addresses) {
		this.addresses = addresses;
	}

	@OneToMany( mappedBy = "customer" )
	@OrderColumn
	public List<Order> getOrders() {
		return orders;
	}

	public void setOrders(List<Order> orders) {
		this.orders = orders;
	}

	@ManyToMany
	public Set<Industry> getIndustries() {
		return industries;
	}

	public void setIndustries(Set<Industry> industries) {
		this.industries = industries;
	}
}
