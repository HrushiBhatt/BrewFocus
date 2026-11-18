package io.github.hrushibhatt.brewfocus.config;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import io.github.hrushibhatt.brewfocus.web.RequestLogFilter;
import io.github.hrushibhatt.brewfocus.web.RequestSizeLimitFilter;

/**
 * Serves the built React app from {@code STATIC_DIR}, and registers the filters every request passes
 * through before Spring Security.
 *
 * <p>One process serves the API and the app, so the browser sees one origin: no CORS to configure, and
 * the session cookie is first-party. In development the Vite dev server serves the app instead and
 * proxies {@code /api} here.
 */
@Configuration(proxyBeanMethods = false)
public class WebConfig implements WebMvcConfigurer {

    private final BrewFocusProperties props;

    public WebConfig(BrewFocusProperties props) {
        this.props = props;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        if (!props.servesFrontend()) {
            return;
        }
        String root = Path.of(props.staticDir()).toAbsolutePath().normalize().toUri().toString();
        // Built files have a hash of their contents in the name, so they can be cached for good.
        registry.addResourceHandler("/assets/**")
                .addResourceLocations(root + "assets/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
        // Everything else: a file of the app, or index.html so React Router can handle the path.
        registry.addResourceHandler("/**")
                .addResourceLocations(root)
                .setCacheControl(CacheControl.noCache())
                .resourceChain(false)
                .addResolver(new SpaFallback());
    }

    /** The site's root is the app itself. */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        if (props.servesFrontend()) {
            registry.addViewController("/").setViewName("forward:/index.html");
        }
    }

    /**
     * Resolves a real file when there is one, and index.html for anything else, except under
     * {@code /api}, where an unknown path is a JSON 404. PathResourceResolver refuses paths that climb
     * out of the folder ({@code ../}), so only the app's own files can be served.
     */
    static final class SpaFallback extends PathResourceResolver {
        @Override
        protected Resource getResource(String path, Resource location) throws IOException {
            if (path.startsWith("api/") || path.equals("api")) {
                return null;
            }
            Resource file = super.getResource(path, location);
            if (file != null) {
                return file;
            }
            Resource index = location.createRelative("index.html");
            return index.exists() && index.isReadable() ? index : null;
        }
    }

    /** Logs every request (path only, never the query string), outermost so it sees the final status. */
    @Bean
    FilterRegistrationBean<RequestLogFilter> requestLogFilter() {
        var registration = new FilterRegistrationBean<>(new RequestLogFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    /** Bounds request bodies before anything reads them. */
    @Bean
    FilterRegistrationBean<RequestSizeLimitFilter> requestSizeLimitFilter() {
        var registration = new FilterRegistrationBean<>(new RequestSizeLimitFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }
}
