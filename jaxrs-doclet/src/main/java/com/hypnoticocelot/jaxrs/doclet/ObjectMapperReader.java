package com.hypnoticocelot.jaxrs.doclet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypnoticocelot.jaxrs.doclet.model.ResourceListing;

import java.io.File;
import java.io.IOException;

public class ObjectMapperReader {

	private final ObjectMapper mapper = new ObjectMapper();

	public ResourceListing read(File file) throws IOException {
		return mapper.readValue(file, ResourceListing.class);
	}
}
