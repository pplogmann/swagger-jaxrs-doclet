package com.hypnoticocelot.jaxrs.doclet.parser;

import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.hypnoticocelot.jaxrs.doclet.DocletOptions;
import com.hypnoticocelot.jaxrs.doclet.Recorder;
import com.hypnoticocelot.jaxrs.doclet.model.*;
import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.RootDoc;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static com.google.common.collect.Maps.uniqueIndex;
import com.hypnoticocelot.jaxrs.doclet.ObjectMapperReader;
import com.hypnoticocelot.jaxrs.doclet.ServiceDoclet;
import com.sun.javadoc.Tag;
import java.io.FileInputStream;

public class JaxRsAnnotationParser {

	private final DocletOptions options;
	private final RootDoc rootDoc;

	public JaxRsAnnotationParser(DocletOptions options, RootDoc rootDoc) {
		this.options = options;
		this.rootDoc = rootDoc;
	}

	public boolean run() {
		try {
			Collection<ApiDeclaration> declarations = new ArrayList<ApiDeclaration>();
			for (ClassDoc classDoc : rootDoc.classes()) {
				ApiClassParser classParser = new ApiClassParser(options, classDoc, Arrays.asList(rootDoc.classes()));
				Collection<Api> apis = classParser.parse();
				if (apis.isEmpty()) {
					continue;
				}

				Map<String, Model> models = uniqueIndex(classParser.models(), new Function<Model, String>() {
					@Override
					public String apply(Model model) {
						return model.getId();
					}
				});
				// The idea (and need) for the declaration is that "/foo" and "/foo/annotated" are stored in separate
				// Api classes but are part of the same resource.
				declarations.add(new ApiDeclaration(options.getApiVersion(), options.getApiBasePath(), classParser.getRootPath(), apis, models, getApiDescription(classDoc)));
			}
			writeApis(declarations);
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	private String getApiDescription(ClassDoc classDoc) {
		String description;
		Tag[] firstSentenceTag = classDoc.firstSentenceTags();
		if (firstSentenceTag.length > 0) {
			description = firstSentenceTag[0].text();
		} else {
			description = classDoc.commentText();
		}
		return description;
	}

	private void writeApis(Collection<ApiDeclaration> apis) throws IOException {
		List<ResourceListingAPI> resources = new LinkedList<ResourceListingAPI>();
		File outputDirectory = options.getOutputDirectory();
		String swaggerUiZipPath = options.getSwaggerUiZipPath();
		Recorder recorder = options.getRecorder();
		for (ApiDeclaration api : apis) {
			String resourcePath = api.getResourcePath();
			if (!Strings.isNullOrEmpty(resourcePath)) {
				String resourceName = resourcePath.replaceFirst("/", "").replaceAll("/", "_").replaceAll("[\\{\\}]", "");
				resources.add(new ResourceListingAPI("/" + resourceName + ".{format}", api.getDescription()));
				File apiFile = new File(outputDirectory, resourceName + ".json");
				recorder.record(apiFile, api);
			}
		}

		//If multiple maven modules write their API doc to the same directory we test if there service.json already exists
		//and then read the existing resources to join them with the current resources
		File docFile = new File(outputDirectory, "service.json");
		if (docFile.exists()) {
			ObjectMapperReader reader = new ObjectMapperReader();
			ResourceListing resourceListing = reader.read(docFile);
			for (ResourceListingAPI resourceListingApi : resourceListing.getApis()) {
				// Make sure we dont add an API twice (in case of common shared libs across modules)
				if (!resources.contains(resourceListingApi)) {
					resources.add(resourceListingApi);
				}
			}
		}
		//write out json for api
		ResourceListing listing = new ResourceListing(options.getApiVersion(), options.getDocBasePath(), resources);
		recorder.record(docFile, listing);

		// Copy swagger-ui into the output directory.
		ZipInputStream swaggerZip;
		if (DocletOptions.DEFAULT_SWAGGER_UI_ZIP_PATH.equals(swaggerUiZipPath)) {
			swaggerZip = new ZipInputStream(ServiceDoclet.class.getResourceAsStream("/swagger-ui.zip"));
			System.out.println("Using default swagger-ui.zip file from SwaggerDoclet jar file");
		} else {
			if (new File(swaggerUiZipPath).exists()) {
				swaggerZip = new ZipInputStream(new FileInputStream(swaggerUiZipPath));
				System.out.println("Using swagger-ui.zip file from: " + swaggerUiZipPath);
			} else {
				File f = new File(".");
				System.out.println("SwaggerDoclet working directory: " + f.getAbsolutePath());
				System.out.println("-swaggerUiZipPath not set correct: " + swaggerUiZipPath);

				throw new RuntimeException("-swaggerUiZipPath not set correct, file not found: " + swaggerUiZipPath);
			}
		}

		ZipEntry entry = swaggerZip.getNextEntry();
		while (entry != null) {
			final File swaggerFile = new File(outputDirectory, entry.getName());
			if (entry.isDirectory()) {
				if (!swaggerFile.isDirectory() && !swaggerFile.mkdirs()) {
					throw new RuntimeException("Unable to create directory: " + swaggerFile);
				}
			} else {
				recorder.record(swaggerFile, swaggerZip);
			}

			entry = swaggerZip.getNextEntry();
		}
		swaggerZip.close();
	}

}
