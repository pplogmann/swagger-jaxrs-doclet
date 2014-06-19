package com.hypnoticocelot.jaxrs.doclet.model;

import com.google.common.base.Objects;

import java.util.List;

public class ResourceListing {

	private String apiVersion;
	private String basePath;
	private List<ResourceListingAPI> apis;
	private String swaggerVersion = "1.1";

	@SuppressWarnings("unused")
	private ResourceListing() {
	}

	public ResourceListing(String apiVersion, String basePath, List<ResourceListingAPI> apis) {
		this.apiVersion = apiVersion;
		this.basePath = basePath;
		this.apis = apis;
	}

	public ResourceListing(String apiVersion, String basePath, List<ResourceListingAPI> apis, String swaggerVersion) {
		this.apiVersion = apiVersion;
		this.basePath = basePath;
		this.apis = apis;
		this.swaggerVersion = swaggerVersion;
	}

	public String getApiVersion() {
		return apiVersion;
	}

	public String getSwaggerVersion() {
		return swaggerVersion;
	}

	public String getBasePath() {
		return basePath;
	}

	public List<ResourceListingAPI> getApis() {
		return apis;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		ResourceListing that = (ResourceListing) o;
		return Objects.equal(apiVersion, that.apiVersion)
				&& Objects.equal(basePath, that.basePath)
				&& Objects.equal(apis, that.apis);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(apiVersion, basePath, apis);
	}

	@Override
	public String toString() {
		return Objects.toStringHelper(this)
				.add("apiVersion", apiVersion)
				.add("basePath", basePath)
				.add("apis", apis)
				.toString();
	}
}
