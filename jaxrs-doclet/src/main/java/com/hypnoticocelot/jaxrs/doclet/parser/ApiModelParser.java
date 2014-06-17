package com.hypnoticocelot.jaxrs.doclet.parser;

import com.google.common.base.Predicate;
import static com.google.common.collect.Collections2.filter;
import com.hypnoticocelot.jaxrs.doclet.DocletOptions;
import com.hypnoticocelot.jaxrs.doclet.model.Model;
import com.hypnoticocelot.jaxrs.doclet.model.Property;
import com.hypnoticocelot.jaxrs.doclet.translator.Translator;
import com.sun.javadoc.*;

import java.util.*;

public class ApiModelParser {

	private final DocletOptions options;
	private final Translator translator;
	private final Type rootType;
	private final Set<Model> models;

	public ApiModelParser(DocletOptions options, Translator translator, Type rootType) {
		this.options = options;
		this.translator = translator;
		this.rootType = rootType;
		this.models = new LinkedHashSet<Model>();
	}

	public Set<Model> parse() {
		parseModel(rootType);
		return models;
	}

	private void parseModel(Type type) {
		boolean isPrimitive = AnnotationHelper.isPrimitive(type);
		boolean isJavaxType = type.qualifiedTypeName().startsWith("javax.");
		boolean isBaseObject = type.qualifiedTypeName().equals("java.lang.Object");
		boolean isTypeToTreatAsOpaque = options.getTypesToTreatAsOpaque().contains(type.qualifiedTypeName());
		ClassDoc classDoc = type.asClassDoc();
		if (isPrimitive || isJavaxType || isBaseObject || isTypeToTreatAsOpaque || classDoc == null || alreadyStoredType(type)) {
			return;
		}

		parseProperties(classDoc);
	}

	private void parseProperties(ClassDoc classDoc) {
		Map<String, Type> types = new HashMap<String, Type>();
		Map<String, Property> elements = new HashMap<String, Property>();
		MethodDoc[] methodDocs = classDoc.methods();
		if (methodDocs != null) {
			for (MethodDoc method : methodDocs) {
				String name = translator.methodName(method).value();
				if (name != null && !elements.containsKey(name)) {
					elements.put(name, getProperty(method.returnType(), getDescription(method)));
					types.put(name, method.returnType());
				}
			}
		}

		FieldDoc[] fieldDocs = classDoc.fields();
		if (fieldDocs != null) {
			for (FieldDoc field : fieldDocs) {
				String name = translator.fieldName(field).value();
				if (name != null && !elements.containsKey(name)) {
					elements.put(name, getProperty(field.type(), field.commentText()));
					types.put(name, field.type());
				}
			}
		}

		if (!elements.isEmpty()) {
			models.add(new Model(translator.typeName(classDoc).value(), elements));
			parseNestedModels(types.values());
		}
	}

	private String getDescription(MethodDoc method) {
		String comment;
		Tag[] returnTag = method.tags("@return");
		if (returnTag.length > 0) {
			comment = returnTag[0].text();
		} else {
			comment = method.commentText();
		}
		return comment;
	}

	private Property getProperty(Type type, String description) {
		ClassDoc typeClassDoc = type.asClassDoc();

		Type containerOf = parseParameterisedTypeOf(type);
		String containerTypeOf = containerOf == null ? null : translator.typeName(containerOf).value();

		String propertyName = translator.typeName(type).value();
		Property property;
		if (typeClassDoc != null && typeClassDoc.isEnum()) {
			property = new Property(typeClassDoc.enumConstants(), description);
		} else {
			property = new Property(propertyName, description, containerTypeOf);
		}
		return property;
	}

	private void parseNestedModels(Collection<Type> types) {
		for (Type type : types) {
			parseModel(type);
			Type pt = parseParameterisedTypeOf(type);
			if (pt != null) {
				parseModel(pt);
			}
		}
	}

	private Type parseParameterisedTypeOf(Type type) {
		Type result = null;
		ParameterizedType pt = type.asParameterizedType();
		if (pt != null) {
			Type[] typeArgs = pt.typeArguments();
			if (typeArgs != null && typeArgs.length > 0) {
				result = typeArgs[0];
			}
		}
		return result;
	}

	private boolean alreadyStoredType(final Type type) {
		return filter(models, new Predicate<Model>() {
			@Override
			public boolean apply(Model model) {
				return model.getId().equals(translator.typeName(type).value());
			}
		}).size() > 0;
	}
}
