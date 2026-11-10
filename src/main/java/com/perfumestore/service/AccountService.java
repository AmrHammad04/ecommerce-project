package com.perfumestore.service;

import com.perfumestore.dto.request.RegisterRequest;
import com.perfumestore.dto.response.UserResponse;

public interface AccountService {

    UserResponse register(RegisterRequest request);
    UserResponse getByEmail(String email);
    UserResponse me(Long userId);
}