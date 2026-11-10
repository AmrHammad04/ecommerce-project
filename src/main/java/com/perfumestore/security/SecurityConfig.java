package com.perfumestore.security;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.security.provisioning.UserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public UserDetailsManager userDetailsManager(DataSource dataSource) {

        JdbcUserDetailsManager userDetailsManager = new JdbcUserDetailsManager(dataSource);

        userDetailsManager.setUsersByUsernameQuery(
                "select email, password_hash, true from users where email = ?"
        );

        userDetailsManager.setAuthoritiesByUsernameQuery(
                "select email, concat('ROLE_', role) from users where email = ?"
        );

        return userDetailsManager;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http.authorizeHttpRequests(configurer -> configurer

                .requestMatchers(HttpMethod.POST,"/api/auth/register").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/products","/api/products/**","/api/categories","/api/categories/**").permitAll()
                .requestMatchers("/api/admin","/api/admin/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST,"/api/products","/api/categories").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT,"/api/products/{productId}","/api/categories/{categoryId}").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH,"/api/products/{productId}","/api/categories/{categoryId}").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE,"/api/products/{productId}","/api/categories/{categoryId}").hasRole("ADMIN")
                .requestMatchers("/api/cart","/api/cart/**","/api/orders","/api/orders/**").hasRole("CUSTOMER")
                .requestMatchers(HttpMethod.POST,"/api/products/{productId}/reviews").hasRole("CUSTOMER")

                .anyRequest().authenticated()
        );

        http.httpBasic(Customizer.withDefaults());

        http.csrf(csrf -> csrf.disable());

        return http.build();
    }
}