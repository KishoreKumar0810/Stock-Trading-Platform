package com.trading.userservice.service;

import com.trading.userservice.dto.AuthResponseDto;
import com.trading.userservice.dto.LoginRequestDto;
import com.trading.userservice.dto.RegisterRequestDto;
import com.trading.userservice.dto.UserResponseDto;
import com.trading.userservice.entity.User;
import com.trading.userservice.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.Key;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final String USER_REGISTERED_TOPIC = "user.registered";

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    public AuthResponseDto registerUser(RegisterRequestDto request) {

        log.info("registering user with email: {}", request.getEmail());

        if(userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("User with email " + request.getEmail() + " already exists");
        }

        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .walletBalance(request.getInitialDeposit() != null
                ? request.getInitialDeposit()
                : BigDecimal.valueOf(10000))
                .build();

        User savedUser = userRepository.save(user);
        log.info("User registered successfully with id: {}", savedUser.getId());

        // Publish user registration event to Kafka
        log.info("Publishing Kafka event");
        Map<String, Object> userRegisteredEvent = new HashMap<>();
        userRegisteredEvent.put("userId", savedUser.getId());
        userRegisteredEvent.put("email", savedUser.getEmail());
        userRegisteredEvent.put("firstName", savedUser.getFirstName());
        userRegisteredEvent.put("lastName", savedUser.getLastName());
        userRegisteredEvent.put("walletBalance", savedUser.getWalletBalance());

        kafkaTemplate.send(USER_REGISTERED_TOPIC, savedUser.getId(), userRegisteredEvent);

        log.info("Kafka event published successfully");
        log.info("Generating access token");

        String accessToken = generateAccessToken(savedUser.getId(), savedUser.getEmail());
        String refreshToken = generateRefreshToken(savedUser.getId());

        return buildAuthResponse(savedUser, accessToken, refreshToken);
    }

    public AuthResponseDto loginUser(LoginRequestDto request) {
        log.info("login attempt: {}", request.getEmail());

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User with email " + request.getEmail() + " not found"));

        if(!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid Credentials");
        }

        log.info("login successfully with email: {}", user.getEmail());

        String accessToken = generateAccessToken(user.getId(), user.getEmail());
        String refreshToken = generateRefreshToken(user.getId());

        return buildAuthResponse(user, accessToken, refreshToken);
    }

    // adding funds to our wallet
    @Transactional
    public UserResponseDto addFunds(String userId, BigDecimal amount) {
        User user = findById(userId);
        user.setWalletBalance(user.getWalletBalance().add(amount));
        log.info("amount of {} successfully added to the user: {}",amount, userId);
        User savedUser = userRepository.save(user);
        return convertToDto(savedUser);
    }

    // deducting funds from our wallet
    @Transactional
    public UserResponseDto deductFunds(String userId, BigDecimal amount) {
        User user = findById(userId);
        if(user.getWalletBalance().compareTo(amount) < 0)
            throw new RuntimeException("Insufficient Funds");

        user.setWalletBalance(user.getWalletBalance().subtract(amount));
        log.info("amount of {} successfully deducted from the user: {}",amount, userId);
        User savedUser = userRepository.save(user);
        return convertToDto(savedUser);
    }

    // crediting funds to our wallet by sell stocks
    @Transactional
    public UserResponseDto creditFunds(String userId, BigDecimal amount) {
        User user = findById(userId);
        user.setWalletBalance(user.getWalletBalance().add(amount));
        log.info("fund credited to the user: {}", userId);
        User savedUser = userRepository.save(user);
        return convertToDto(savedUser);
    }

    private String generateAccessToken(String userId, String email) {
        return Jwts.builder()
                .claim("userId", userId)
                .setSubject(email)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(getSignKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    private String generateRefreshToken(String userId) {
        return Jwts.builder()
                .claim("userId", userId)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + refreshExpiration))
                .signWith(getSignKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    private AuthResponseDto buildAuthResponse(User user, String accessToken, String refreshToken) {
        AuthResponseDto authResponseDto = new AuthResponseDto();
        authResponseDto.setUserId(user.getId());
        authResponseDto.setAccessToken(accessToken);
        authResponseDto.setRefreshToken(refreshToken);
        authResponseDto.setEmail(user.getEmail());
        authResponseDto.setFirstName(user.getFirstName());
        authResponseDto.setLastName(user.getLastName());
        authResponseDto.setWalletBalance(user.getWalletBalance());
        return authResponseDto;
    }

    private Key getSignKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);

    }

    public UserResponseDto getUserById(String id) {
        return convertToDto(findById(id));
    }

    public User findById(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User with id " + userId + " not found"));
    }

    private UserResponseDto convertToDto(User user) {
        return UserResponseDto.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .walletBalance(user.getWalletBalance())
                .createdAt(user.getCreatedAt())
                .status(user.getStatus())
                .build();
    }
}
