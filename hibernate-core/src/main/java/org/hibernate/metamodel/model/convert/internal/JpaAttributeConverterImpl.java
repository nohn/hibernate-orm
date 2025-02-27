/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.metamodel.model.convert.internal;

import jakarta.persistence.AttributeConverter;

import org.hibernate.boot.model.convert.spi.JpaAttributeConverterCreationContext;
import org.hibernate.metamodel.model.convert.spi.JpaAttributeConverter;
import org.hibernate.resource.beans.spi.ManagedBean;
import org.hibernate.type.descriptor.converter.AttributeConverterMutabilityPlanImpl;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.java.MutabilityPlan;
import org.hibernate.type.descriptor.java.spi.JavaTypeRegistry;
import org.hibernate.type.descriptor.java.spi.RegistryHelper;

/**
 * Standard implementation of JpaAttributeConverter
 *
 * @author Steve Ebersole
 */
public class JpaAttributeConverterImpl<O,R> implements JpaAttributeConverter<O,R> {
	private final ManagedBean<? extends AttributeConverter<O,R>> attributeConverterBean;
	private final JavaType<? extends AttributeConverter<O, R>> converterJtd;
	private final JavaType<O> domainJtd;
	private final JavaType<R> jdbcJtd;

	public JpaAttributeConverterImpl(
			ManagedBean<? extends AttributeConverter<O, R>> attributeConverterBean,
			JavaType<? extends AttributeConverter<O,R>> converterJtd,
			JavaType<O> domainJtd,
			JavaType<R> jdbcJtd) {
		this.attributeConverterBean = attributeConverterBean;
		this.converterJtd = converterJtd;
		this.domainJtd = domainJtd;
		this.jdbcJtd = jdbcJtd;
	}

	public JpaAttributeConverterImpl(
			ManagedBean<? extends AttributeConverter<O,R>> attributeConverterBean,
			JavaType<? extends AttributeConverter<O,R>> converterJtd,
			Class<O> domainJavaType,
			Class<R> jdbcJavaType,
			JpaAttributeConverterCreationContext context) {
		this.attributeConverterBean = attributeConverterBean;
		this.converterJtd = converterJtd;

		final JavaTypeRegistry jtdRegistry = context.getJavaTypeDescriptorRegistry();

		jdbcJtd = jtdRegistry.getDescriptor( jdbcJavaType );
		//noinspection unchecked
		domainJtd = (JavaType<O>) jtdRegistry.resolveDescriptor(
				domainJavaType,
				() -> RegistryHelper.INSTANCE.createTypeDescriptor(
						domainJavaType,
						() -> {
							final Class<? extends AttributeConverter<O, R>> converterClass = attributeConverterBean.getBeanClass();
							final MutabilityPlan<Object> mutabilityPlan = RegistryHelper.INSTANCE.determineMutabilityPlan(
									converterClass,
									context.getTypeConfiguration()
							);

							if ( mutabilityPlan != null ) {
								return mutabilityPlan;
							}

							return new AttributeConverterMutabilityPlanImpl<>( this, true );
						},
						context.getTypeConfiguration()
				)
		);
	}

	@Override
	public ManagedBean<? extends AttributeConverter<O, R>> getConverterBean() {
		return attributeConverterBean;
	}

	@Override
	public O toDomainValue(R relationalForm) {
		return attributeConverterBean.getBeanInstance().convertToEntityAttribute( relationalForm );
	}

	@Override
	public R toRelationalValue(O domainForm) {
		return attributeConverterBean.getBeanInstance().convertToDatabaseColumn( domainForm );
	}

	@Override
	public JavaType<? extends AttributeConverter<O, R>> getConverterJavaTypeDescriptor() {
		return converterJtd;
	}

	@Override
	public JavaType<O> getDomainJavaDescriptor() {
		return getDomainJavaTypeDescriptor();
	}

	@Override
	public JavaType<R> getRelationalJavaDescriptor() {
		return getRelationalJavaTypeDescriptor();
	}

	@Override
	public JavaType<O> getDomainJavaTypeDescriptor() {
		return domainJtd;
	}

	@Override
	public JavaType<R> getRelationalJavaTypeDescriptor() {
		return jdbcJtd;
	}
}
