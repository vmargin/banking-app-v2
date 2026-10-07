package com.vmargin.banking.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfiguration {
    @Bean
    public HttpSessionSecurityContextRepository securityContexts() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SecurityFilterChain bankingSecurity(HttpSecurity http,
                                               HttpSessionSecurityContextRepository contexts) throws Exception {
        http.authorizeHttpRequests(auth -> auth
            .requestMatchers("/", "/login", "/register", "/css/**", "/js/**", "/error", "/favicon.svg")
                .permitAll()
            .anyRequest().authenticated())
            .securityContext(context -> context.securityContextRepository(contexts))
            .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(
                "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; "
                    + "object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'")))
            .exceptionHandling(errors -> errors.authenticationEntryPoint(
                new LoginUrlAuthenticationEntryPoint("/login")))
            .logout(logout -> logout.logoutUrl("/logout").logoutSuccessUrl("/login")
                .invalidateHttpSession(true).clearAuthentication(true));
        return http.build();
    }
}
