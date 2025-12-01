package com.backend.tpi.api_gateway.filters;

import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.server.reactive.ServerHttpRequest;

import java.util.List;

/**
 * Global filter that ensures the Authorization header is forwarded to downstream
 * services. When the gateway validates the JWT, some configurations remove the
 * original header; this filter reinjects it from the reactive SecurityContext
 * (if present) before proxying the request.
 */
@Component
public class AuthorizationForwardFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        List<String> authHeaders = exchange.getRequest().getHeaders().getOrEmpty(HttpHeaders.AUTHORIZATION);
        // If Authorization header already present, forward as-is
        if (authHeaders != null && !authHeaders.isEmpty()) {
            return chain.filter(exchange);
        }

        // Otherwise try to extract token from reactive SecurityContext and re-add header
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .filter(a -> a instanceof JwtAuthenticationToken)
                .flatMap(a -> {
                    String token = ((JwtAuthenticationToken) a).getToken().getTokenValue();
                    ServerHttpRequest mutated = exchange.getRequest().mutate()
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .build();
                    return chain.filter(exchange.mutate().request(mutated).build());
                })
                .switchIfEmpty(chain.filter(exchange));
    }

    @Override
    public int getOrder() {
        // High precedence so header is present before route filters execute
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
