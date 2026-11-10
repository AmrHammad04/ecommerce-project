package com.perfumestore.service;

import java.util.*;
import com.perfumestore.dao.CartRepository;
import com.perfumestore.dao.UserRepository;
import com.perfumestore.dto.request.RegisterRequest;
import com.perfumestore.dto.response.UserResponse;
import com.perfumestore.entity.Cart;
import com.perfumestore.entity.Role;
import com.perfumestore.entity.User;
import com.perfumestore.exception.ApiException;
import com.perfumestore.security.PasswordPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AccountServiceImpl implements AccountService {

    private final UserRepository userRepository;
    private final CartRepository cartRepository;
    private final PasswordEncoder passwordEncoder;
    private final DtoMapper dtoMapper;

    public AccountServiceImpl(
            UserRepository userRepository,
            CartRepository cartRepository,
            PasswordEncoder passwordEncoder,
            DtoMapper dtoMapper) {

        this.userRepository = userRepository;
        this.cartRepository = cartRepository;
        this.passwordEncoder = passwordEncoder;
        this.dtoMapper = dtoMapper;
    }

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {

        String email = request.getEmail()
                .trim()
                .toLowerCase(Locale.ROOT);

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("Email is already registered");
        }

        PasswordPolicy.validate(request.getPassword());

        User user = new User();

        user.setName(request.getName().trim());
        user.setEmail(email);
        user.setPasswordHash(
                passwordEncoder.encode(request.getPassword())
        );
        user.setRole(Role.CUSTOMER);

        User savedUser = userRepository.saveAndFlush(user);

        Cart cart = new Cart();
        cart.setUser(savedUser);

        cartRepository.save(cart);

        return dtoMapper.user(savedUser);
    }

    @Override
    public UserResponse getByEmail(String email) {

        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() ->
                        ApiException.notFound("User not found")
                );

        return dtoMapper.user(user);
    }

    @Override
    public UserResponse me(Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        ApiException.notFound("User not found")
                );

        return dtoMapper.user(user);
    }
}