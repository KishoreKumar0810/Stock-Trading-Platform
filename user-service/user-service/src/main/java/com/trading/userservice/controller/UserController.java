package com.trading.userservice.controller;

import com.trading.userservice.dto.AuthResponseDto;
import com.trading.userservice.dto.LoginRequestDto;
import com.trading.userservice.dto.RegisterRequestDto;
import com.trading.userservice.dto.UserResponseDto;
import com.trading.userservice.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/users")
@Slf4j
@RequiredArgsConstructor
public class UserController {

   private final UserService userService;

   @PostMapping("/register")
   public ResponseEntity<AuthResponseDto> registerUser(@Valid @RequestBody RegisterRequestDto request) {
       return ResponseEntity.status(HttpStatus.CREATED)
                            .body(userService.registerUser(request));
   }

   @PostMapping("/login")
   public ResponseEntity<AuthResponseDto> loginUser(@Valid @RequestBody LoginRequestDto request) {
         return ResponseEntity.ok(userService.loginUser(request));
   }

   @GetMapping("/me")
   public ResponseEntity<UserResponseDto> getMyProfile(@RequestHeader ("X-User-Id") String userId) {
       return ResponseEntity.ok(userService.getUserById(userId));
   }

   @GetMapping("/{userId}")
    public ResponseEntity<UserResponseDto> getUserById(@PathVariable String userId) {
       return ResponseEntity.ok(userService.getUserById(userId));
   }

    @PostMapping("{userId}/funds/add")
    public ResponseEntity<UserResponseDto> addFunds(@PathVariable String userId, @RequestParam BigDecimal amount) {
        return ResponseEntity.ok(userService.addFunds(userId, amount));
    }

    @PostMapping("{userId}/funds/deduct")
    public ResponseEntity<UserResponseDto> deductFunds(@PathVariable String userId, @RequestParam BigDecimal amount) {
        return ResponseEntity.ok(userService.deductFunds(userId, amount));
    }

    @PostMapping("{userId}/funds/credit")
    public ResponseEntity<UserResponseDto> creditFunds(@PathVariable String userId, @RequestParam BigDecimal amount) {
        return ResponseEntity.ok(userService.creditFunds(userId, amount));
    }

}
