package ru.student.hotel.gateway;

import java.util.UUID;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.*;
import reactor.core.publisher.Mono;

@Component
@Order(-200)
public class GatewayTraceFilter implements WebFilter {
  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    String incoming = exchange.getRequest().getHeaders().getFirst("X-Request-Id");
    final String trace =
        incoming != null && incoming.matches("[a-zA-Z0-9-]{1,64}")
            ? incoming
            : UUID.randomUUID().toString();
    var next = exchange.mutate().request(r -> r.headers(h -> h.set("X-Request-Id", trace))).build();
    next.getResponse().getHeaders().set("X-Request-Id", trace);
    return chain
        .filter(next)
        .doFinally(
            s ->
                LoggerFactory.getLogger(GatewayTraceFilter.class)
                    .info(
                        "event=gateway_request traceId={} method={} path={} status={}",
                        trace,
                        next.getRequest().getMethod(),
                        next.getRequest().getPath(),
                        next.getResponse().getStatusCode()));
  }
}
