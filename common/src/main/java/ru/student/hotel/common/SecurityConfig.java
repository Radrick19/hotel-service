package ru.student.hotel.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
  @Bean
  SecretKeySpec jwtKey(@Value("${security.jwt.secret}") String secret) {
    return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
  }

  @Bean
  JwtDecoder jwtDecoder(SecretKeySpec key) {
    var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("hotel-booking"));
    return decoder;
  }

  @Bean
  JwtEncoder jwtEncoder(SecretKeySpec key) {
    return new NimbusJwtEncoder(new com.nimbusds.jose.jwk.source.ImmutableSecret<>(key));
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  SecurityFilterChain security(HttpSecurity http, ObjectMapper mapper) throws Exception {
    var roles = new JwtGrantedAuthoritiesConverter();
    roles.setAuthoritiesClaimName("roles");
    roles.setAuthorityPrefix("ROLE_");
    var auth = new JwtAuthenticationConverter();
    auth.setJwtGrantedAuthoritiesConverter(roles);
    http.csrf(c -> c.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/user/register",
                        "/user/auth",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/v3/api-docs/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            o ->
                o.jwt(j -> j.jwtAuthenticationConverter(auth))
                    .authenticationEntryPoint(
                        (r, s, e) -> {
                          s.setStatus(401);
                          s.setContentType("application/json");
                          mapper.writeValue(
                              s.getOutputStream(), Errors.body(401, "Authentication required"));
                        }))
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                        (r, s, x) -> {
                          s.setStatus(401);
                          s.setContentType("application/json");
                          mapper.writeValue(
                              s.getOutputStream(), Errors.body(401, "Authentication required"));
                        })
                    .accessDeniedHandler(
                        (r, s, x) -> {
                          s.setStatus(403);
                          s.setContentType("application/json");
                          mapper.writeValue(s.getOutputStream(), Errors.body(403, "Access denied"));
                        }));
    return http.build();
  }
}
