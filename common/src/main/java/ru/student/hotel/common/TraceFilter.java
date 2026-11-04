package ru.student.hotel.common;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(-200)
public class TraceFilter extends OncePerRequestFilter {
  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    String trace = req.getHeader("X-Request-Id");
    if (trace == null || !trace.matches("[a-zA-Z0-9-]{1,64}")) trace = UUID.randomUUID().toString();
    MDC.put("traceId", trace);
    res.setHeader("X-Request-Id", trace);
    try {
      chain.doFilter(req, res);
    } finally {
      LoggerFactory.getLogger(TraceFilter.class)
          .info(
              "event=http method={} path={} status={}",
              req.getMethod(),
              req.getRequestURI(),
              res.getStatus());
      MDC.remove("traceId");
    }
  }
}
