package com.flashsale.gateway;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.removeRequestHeader;
import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.web.servlet.function.RequestPredicates.path;

import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

@Configuration(proxyBeanMethods = false)
public class GatewayRoutes {

    static final String USER_ID_HEADER = "X-User-Id";

    @Bean
    RouterFunction<ServerResponse> userRoute(GatewayProperties props) {
        return route("user-service")
            .route(path("/api/users/**"), http())
            .before(removeRequestHeader(USER_ID_HEADER))
            .before(uri(props.userUri()))
            .build();
    }

    @Bean
    RouterFunction<ServerResponse> productRoute(GatewayProperties props) {
        return route("product-service")
            .route(path("/api/products/**"), http())
            .before(removeRequestHeader(USER_ID_HEADER))
            .before(uri(props.productUri()))
            .build();
    }

    @Bean
    RouterFunction<ServerResponse> orderRoute(GatewayProperties props, JwtVerifier verifier) {
        return route("order-service")
            .route(path("/api/orders/**"), http())
            .filter(authenticate(verifier))
            .before(uri(props.orderUri()))
            .build();
    }

    /** Verifies the bearer JWT and replaces any client-supplied X-User-Id with the token subject. */
    static HandlerFilterFunction<ServerResponse, ServerResponse> authenticate(JwtVerifier verifier) {
        return (request, next) -> {
            String header = request.headers().firstHeader(HttpHeaders.AUTHORIZATION);
            if (header == null || !header.startsWith("Bearer ")) {
                return unauthorized("Missing bearer token");
            }
            Optional<String> userId = verifier.verify(header.substring("Bearer ".length()).trim());
            if (userId.isEmpty()) {
                return unauthorized("Invalid or expired token");
            }
            return next.handle(ServerRequest.from(request)
                .headers(headers -> headers.set(USER_ID_HEADER, userId.get()))
                .build());
        };
    }

    private static ServerResponse unauthorized(String message) {
        return ServerResponse.status(HttpStatus.UNAUTHORIZED)
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("code", "UNAUTHORIZED", "message", message));
    }
}
