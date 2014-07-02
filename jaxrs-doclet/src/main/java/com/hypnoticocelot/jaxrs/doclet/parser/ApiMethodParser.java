package com.hypnoticocelot.jaxrs.doclet.parser;

import com.hypnoticocelot.jaxrs.doclet.DocletOptions;
import com.hypnoticocelot.jaxrs.doclet.model.*;
import com.hypnoticocelot.jaxrs.doclet.translator.Translator;
import com.sun.javadoc.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.Objects.firstNonNull;
import static com.google.common.collect.Collections2.filter;
import static com.hypnoticocelot.jaxrs.doclet.parser.AnnotationHelper.parsePath;

public class ApiMethodParser {

	private static final String TAG_INHERIT_DOC = "@inheritDoc";
	private static final String TAG_RETURN = "return";
	private final DocletOptions options;
	private final Translator translator;
	private final String parentPath;
	private final MethodDoc methodDoc;
	private final Set<Model> models;
	private final HttpMethod httpMethod;
	private final Method parentMethod;

	public ApiMethodParser(DocletOptions options, String parentPath, MethodDoc methodDoc) {
		this.options = options;
		this.translator = options.getTranslator();
		this.parentPath = parentPath;
		this.methodDoc = methodDoc;
		this.models = new LinkedHashSet<Model>();
		this.httpMethod = HttpMethod.fromMethod(methodDoc);
		this.parentMethod = null;
	}

	public ApiMethodParser(DocletOptions options, Method parentMethod, MethodDoc methodDoc) {
		this.options = options;
		this.translator = options.getTranslator();
		this.methodDoc = methodDoc;
		this.models = new LinkedHashSet<Model>();
		this.httpMethod = HttpMethod.fromMethod(methodDoc);
		this.parentPath = parentMethod.getPath();
		this.parentMethod = parentMethod;
	}

	public Method parse() {
		String methodPath = firstNonNull(parsePath(methodDoc.annotations()), "");
		if (httpMethod == null && methodPath.isEmpty()) {
			return null;
		}
		String path = parentPath + methodPath;

		MethodDoc realMethodDoc = findMethodDocumentation(methodDoc);

		// parameters
		List<ApiParameter> parameters = new LinkedList<ApiParameter>();

		for (int i = 0; i < methodDoc.parameters().length; i++) {
			Parameter parameter = methodDoc.parameters()[i];
			if (!shouldIncludeParameter(httpMethod, parameter)) {
				continue;
			}
			if (options.isParseModels()) {
				models.addAll(new ApiModelParser(options, translator, parameter.type()).parse());
			}
			parameters.add(new ApiParameter(
					AnnotationHelper.paramTypeOf(parameter),
					AnnotationHelper.paramNameOf(parameter),
					commentForParameter(realMethodDoc, realMethodDoc.parameters()[i]),
					translator.typeName(parameter.type()).value()
			));
		}

		// parent method parameters are inherited
		if (parentMethod != null) {
			parameters.addAll(parentMethod.getParameters());
		}

		// response messages
		Pattern pattern = Pattern.compile("(\\d+) (.+)"); // matches "<code><space><text>"
		List<ApiResponseMessage> responseMessages = new LinkedList<ApiResponseMessage>();
		for (String tagName : options.getErrorTags()) {
			for (Tag tagValue : realMethodDoc.tags(tagName)) {
				Matcher matcher = pattern.matcher(tagValue.text());
				if (matcher.find()) {
					responseMessages.add(new ApiResponseMessage(Integer.valueOf(matcher.group(1)),
							matcher.group(2)));
				}
			}
		}

		// return type
		Type type = methodDoc.returnType();
		String returnType = translator.typeName(type).value();
		if (options.isParseModels()) {
			models.addAll(new ApiModelParser(options, translator, type).parse());
		}

		String firstSentencesComment = parseDocs(realMethodDoc);
		if (firstSentencesComment.isEmpty()) {
			firstSentencesComment = getTextForTag(realMethodDoc, TAG_RETURN);
		}
		String implementationNote = methodDoc.commentText().
				replace(firstSentencesComment, "").replace("{" + TAG_INHERIT_DOC + "}", "");

		return new Method(
				httpMethod,
				methodDoc.name(),
				path,
				parameters,
				responseMessages,
				firstSentencesComment,
				implementationNote,
				returnType
		);
	}

	public Set<Model> models() {
		return models;
	}

	/**
	 * Looks for the method documentation in parent classes and interfaces if there is no documentation at the method
	 * level.
	 *
	 * @param methodDoc
	 * @return
	 */
	private MethodDoc findMethodDocumentation(final MethodDoc methodDoc) {
		// Look in parent class for javadoc
		if ((hasOverrideAnnotation(methodDoc) && methodDoc.commentText().isEmpty())
				|| methodDoc.commentText().contains(TAG_INHERIT_DOC)) {

			ClassDoc containingClass = methodDoc.containingClass();
			ClassDoc superClass = containingClass.superclass().asClassDoc();
			for (MethodDoc md : superClass.methods()) {
				if (md.name().equalsIgnoreCase(methodDoc.name())
						&& md.signature().equalsIgnoreCase(methodDoc.signature())) {
					return md;
				}
			}
			// Look in interfaces for javadoc
			for (Type interfaceType : containingClass.interfaceTypes()) {
				ClassDoc cd = interfaceType.asClassDoc();

				List<TypeReplacement> typeReplacements = getTypeVariableMapping(interfaceType.asParameterizedType(), cd);

				for (MethodDoc md : cd.methods()) {
					String typerizedSignature = md.signature();

					for (TypeReplacement tr : typeReplacements) {
						typerizedSignature = typerizedSignature.replaceAll(tr.getTypeVariable(), tr.getTyperizedType());
					}
					if (md.name().equalsIgnoreCase(methodDoc.name())
							&& typerizedSignature.equalsIgnoreCase(methodDoc.signature())) {

						return md;
					}
				}
			}
		}
		return methodDoc;
	}

	private boolean hasOverrideAnnotation(MethodDoc methodDoc) {
		for (AnnotationDesc annotation : methodDoc.annotations()) {
			if (annotation.annotationType().qualifiedTypeName().equals(Override.class.getName())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Maps the Type Variables of an interface (e.g. T and K) to the concrete types in the implementation (e.g.
	 * java.lang.Long and java.lang.Object).
	 *
	 * @param type The typerized interface type. This argument may be null in which case an empty list is returned.
	 * @param cd The ClassDoc of the implementing class.
	 * @return A list with all Type mappings for the given interface and implementation classes. An empty list if the
	 * ParameterizedType is null.
	 */
	private List<TypeReplacement> getTypeVariableMapping(ParameterizedType type, ClassDoc cd) {
		List<TypeReplacement> replacements = new ArrayList<>();
		if (type != null) {
			int typeArgumentsCount = type.typeArguments().length;
			for (int i = 0; i < typeArgumentsCount; i++) {
				Type typeArgument = type.typeArguments()[i];
				TypeVariable tv = cd.typeParameters()[i];

				replacements.add(new TypeReplacement(tv.qualifiedTypeName(), typeArgument.qualifiedTypeName()));
			}
		}
		return replacements;
	}

	/**
	 * First Sentence of Javadoc method description
	 */
	private String parseDocs(Doc doc) {
		StringBuilder sentences = new StringBuilder();
		Tag[] fst = doc.firstSentenceTags();

		for (Tag tag : fst) {
			sentences.append(tag.text());
		}

		return sentences.toString();
	}

	private String getTextForTag(Doc doc, String tagname) {
		StringBuilder sentences = new StringBuilder();
		for (Tag tag : doc.tags(tagname)) {
			sentences.append(tag.text());
		}
		return sentences.toString();
	}

	private boolean shouldIncludeParameter(HttpMethod httpMethod, Parameter parameter) {
		List<AnnotationDesc> allAnnotations = Arrays.asList(parameter.annotations());
		Collection<AnnotationDesc> excluded = filter(allAnnotations, new AnnotationHelper.ExcludedAnnotations(options));
		if (!excluded.isEmpty()) {
			return false;
		}

		Collection<AnnotationDesc> jaxRsAnnotations = filter(allAnnotations, new AnnotationHelper.JaxRsAnnotations());
		if (!jaxRsAnnotations.isEmpty()) {
			return true;
		}

		return (allAnnotations.isEmpty() || httpMethod == HttpMethod.POST);
	}

	private String commentForParameter(MethodDoc method, Parameter parameter) {
		for (ParamTag tag : method.paramTags()) {
			if (tag.parameterName().equals(parameter.name())) {
				return tag.parameterComment();
			}
		}
		return "";

	}

	/**
	 * Defines a Mapping of a type Variable (e.g. T) to a concrete type (e.g. java.lang.Long).
	 */
	private static class TypeReplacement {

		private final String typeVariable;
		private final String typerizedType;

		public TypeReplacement(String typeVariable, String typerizedType) {
			this.typeVariable = typeVariable;
			this.typerizedType = typerizedType;
		}

		public String getTypeVariable() {
			return typeVariable;
		}

		public String getTyperizedType() {
			return typerizedType;
		}
	}
}
