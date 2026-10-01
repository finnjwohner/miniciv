package com.miniciv;

import java.io.IOException;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the built frontend. The frontend routes in the browser (e.g. invite
 * links at {@code /rooms/{code}}), so any GET path that is not under
 * {@code /api} or {@code /ws} and does not name a file gets {@code index.html}.
 */
@Configuration
class FrontendConfig implements WebMvcConfigurer {

	private static final Resource INDEX = new ClassPathResource("static/index.html");

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler("/**")
			.addResourceLocations("classpath:/static/")
			.resourceChain(true)
			.addResolver(new PathResourceResolver() {

				@Override
				protected Resource getResource(String path, Resource location) throws IOException {
					Resource resource = super.getResource(path, location);
					if (resource != null || isServerPath(path) || namesAFile(path)) {
						return resource;
					}
					return INDEX.exists() ? INDEX : null;
				}

			});
	}

	private static boolean isServerPath(String path) {
		String first = path.split("/", 2)[0];
		return first.equals("api") || first.equals("ws");
	}

	/**
	 * Frontend routes never contain a dot, so a missing {@code /assets/x.js}
	 * stays a 404 rather than becoming the page.
	 */
	private static boolean namesAFile(String path) {
		return path.substring(path.lastIndexOf('/') + 1).contains(".");
	}

}
