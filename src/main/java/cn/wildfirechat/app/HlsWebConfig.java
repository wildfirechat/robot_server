package cn.wildfirechat.app;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves HLS segments and playlists directly from the local filesystem.
 *
 * Mapping: GET /hls/** → <hls.output.base.path>/**
 *
 * Example playlist URL: http://host:8883/hls/{callId}/stream.m3u8
 *
 * CORS is open so any browser or video player can access the stream.
 * For production, restrict allowedOrigins to your own domain(s) or CDN origin.
 */
@Configuration
public class HlsWebConfig implements WebMvcConfigurer {

    @Value("${hls.output.base.path:/tmp/hls/}")
    private String hlsBasePath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Normalise the path: ensure it ends with a slash for Spring's file: prefix
        String location = "file:" + (hlsBasePath.endsWith("/") ? hlsBasePath : hlsBasePath + "/");

        registry.addResourceHandler("/hls/**")
                .addResourceLocations(location)
                // HLS clients must never cache the playlist; segments can be cached briefly
                .setCacheControl(CacheControl.noCache().mustRevalidate());
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/hls/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "HEAD", "OPTIONS");
    }
}
