# User Service — API Documentation (Planned)

## Service Overview

| Property         | Value                                                |
|------------------|------------------------------------------------------|
| Service Name     | `user-service`                                        |
| Base URL         | `http://localhost:8084/api/user`                      |
| Database         | `user_db` (MySQL 8.x, port 3310)                     |
| Package          | `com.ecommerce.user`                                  |
| Spring Boot      | 3.4.1                                                 |
| Java             | 21                                                    |
| Status           | 🔲 **Planned** — Not yet implemented                 |

---

## Architecture Context

The User Service is the identity and access management backbone of the platform. It issues JWT tokens consumed by all other services for authentication and authorization. It also manages user profiles, shipping addresses, and preferences.

```
┌──────────┐     ┌──────────────┐     ┌──────────────┐
│  Client  │────▶│ User Service │────▶│   user_db    │
│ (App/Web)│     │   (8084)     │     │   (MySQL)    │
└──────────┘     └──────┬───────┘     └──────────────┘
                        │
                  JWT Token issued
                        │
        ┌───────────────┼───────────────┐
        ▼               ▼               ▼
┌──────────────┐ ┌──────────────┐ ┌──────────────┐
│Order Service │ │Payment Service│ │Inventory Svc │
│ (validates   │ │ (validates   │ │ (validates   │
│  JWT token)  │ │  JWT token)  │ │  JWT token)  │
└──────────────┘ └──────────────┘ └──────────────┘
```

---

## Endpoints

### 1. Register New User

Creates a new customer account with email verification.

| Property    | Value                                             |
|-------------|---------------------------------------------------|
| Method      | `POST`                                             |
| Path        | `/api/user/register`                               |
| Auth        | None (public)                                      |
| Status      | `201 Created`                                      |

#### Request Body
```json
{
  "fullName": "Rahul Sharma",
  "email": "rahul.sharma@gmail.com",
  "phone": "9876543210",
  "password": "SecurePass@123"
}
```

#### Request Schema

| Field      | Type     | Required | Validation                                      |
|------------|----------|----------|-------------------------------------------------|
| `fullName` | `String` | Yes      | 2–100 characters                                |
| `email`    | `String` | Yes      | Valid email format, unique in system             |
| `phone`    | `String` | Yes      | 10-digit Indian mobile number                   |
| `password` | `String` | Yes      | Min 8 chars, 1 uppercase, 1 lowercase, 1 digit  |

#### Response — 201 Created
```json
{
  "userId": 1001,
  "fullName": "Rahul Sharma",
  "email": "rahul.sharma@gmail.com",
  "phone": "9876543210",
  "status": "PENDING_VERIFICATION",
  "createdAt": "2026-08-04T12:30:00Z"
}
```

#### Response — 409 Conflict
```json
{
  "status": 409,
  "message": "An account with email rahul.sharma@gmail.com already exists",
  "timestamp": "2026-08-04T12:30:00Z"
}
```

---

### 2. User Login

Authenticates a user and returns JWT tokens.

| Property    | Value                                             |
|-------------|---------------------------------------------------|
| Method      | `POST`                                             |
| Path        | `/api/user/login`                                  |
| Auth        | None (public)                                      |
| Status      | `200 OK`                                           |

#### Request Body
```json
{
  "email": "rahul.sharma@gmail.com",
  "password": "SecurePass@123"
}
```

#### Response — 200 OK
```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiIs...",
  "refreshToken": "dGhpcyBpcyBhIHJlZnJlc2g...",
  "tokenType": "Bearer",
  "expiresIn": 86400,
  "user": {
    "userId": 1001,
    "fullName": "Rahul Sharma",
    "email": "rahul.sharma@gmail.com",
    "role": "CUSTOMER"
  }
}
```

#### Response — 401 Unauthorized
```json
{
  "status": 401,
  "message": "Invalid email or password",
  "timestamp": "2026-08-04T12:30:00Z"
}
```

#### Response — 423 Locked
```json
{
  "status": 423,
  "message": "Account locked due to too many failed attempts. Try again after 30 minutes.",
  "timestamp": "2026-08-04T12:30:00Z"
}
```

---

### 3. Get User Profile

Returns the authenticated user's profile information.

| Property    | Value                                             |
|-------------|---------------------------------------------------|
| Method      | `GET`                                              |
| Path        | `/api/user/profile`                                |
| Auth        | JWT Bearer token                                   |
| Status      | `200 OK`                                           |

#### Response — 200 OK
```json
{
  "userId": 1001,
  "fullName": "Rahul Sharma",
  "email": "rahul.sharma@gmail.com",
  "phone": "9876543210",
  "role": "CUSTOMER",
  "status": "ACTIVE",
  "addresses": [
    {
      "addressId": 1,
      "label": "HOME",
      "street": "42, MG Road, Indiranagar",
      "city": "Bengaluru",
      "state": "Karnataka",
      "pincode": "560038",
      "phone": "9876543210",
      "isDefault": true
    },
    {
      "addressId": 2,
      "label": "OFFICE",
      "street": "WeWork Galaxy, Residency Road",
      "city": "Bengaluru",
      "state": "Karnataka",
      "pincode": "560025",
      "phone": "9876543211",
      "isDefault": false
    }
  ],
  "createdAt": "2026-08-04T12:30:00Z",
  "lastLoginAt": "2026-08-04T18:00:00Z"
}
```

---

### 4. Update User Profile

Updates the authenticated user's profile fields.

| Property    | Value                                             |
|-------------|---------------------------------------------------|
| Method      | `PUT`                                              |
| Path        | `/api/user/profile`                                |
| Auth        | JWT Bearer token                                   |
| Status      | `200 OK`                                           |

#### Request Body (partial update)
```json
{
  "fullName": "Rahul K. Sharma",
  "phone": "9876543299"
}
```

#### Response — 200 OK
```json
{
  "userId": 1001,
  "fullName": "Rahul K. Sharma",
  "email": "rahul.sharma@gmail.com",
  "phone": "9876543299",
  "message": "Profile updated successfully"
}
```

---

### 5. Manage Shipping Addresses

#### 5a. Add Address

| Property    | Value                                             |
|-------------|---------------------------------------------------|
| Method      | `POST`                                             |
| Path        | `/api/user/addresses`                              |
| Auth        | JWT Bearer token                                   |
| Status      | `201 Created`                                      |

#### Request Body
```json
{
  "label": "HOME",
  "street": "42, MG Road, Indiranagar",
  "city": "Bengaluru",
  "state": "Karnataka",
  "pincode": "560038",
  "phone": "9876543210",
  "isDefault": true
}
```

#### Request Schema

| Field       | Type      | Required | Validation                          |
|-------------|-----------|----------|-------------------------------------|
| `label`     | `String`  | Yes      | Enum: `HOME`, `OFFICE`, `OTHER`    |
| `street`    | `String`  | Yes      | Max 200 characters                  |
| `city`      | `String`  | Yes      | Max 100 characters                  |
| `state`     | `String`  | Yes      | Valid Indian state                  |
| `pincode`   | `String`  | Yes      | Exactly 6 digits                    |
| `phone`     | `String`  | Yes      | 10-digit mobile number              |
| `isDefault` | `boolean` | No       | Default: `false`                    |

#### Response — 400 Bad Request (max addresses reached)
```json
{
  "status": 400,
  "message": "Maximum 5 addresses allowed. Please remove an existing address first.",
  "timestamp": "2026-08-04T12:30:00Z"
}
```

#### 5b. Update Address

| Property    | Value                                             |
|-------------|---------------------------------------------------|
| Method      | `PUT`                                              |
| Path        | `/api/user/addresses/{addressId}`                  |
| Auth        | JWT Bearer token                                   |
| Status      | `200 OK`                                           |

#### 5c. Delete Address

| Property    | Value                                             |
|-------------|---------------------------------------------------|
| Method      | `DELETE`                                           |
| Path        | `/api/user/addresses/{addressId}`                  |
| Auth        | JWT Bearer token                                   |
| Status      | `204 No Content`                                   |

---

### 6. Password Reset

#### 6a. Request OTP

| Property    | Value                                             |
|-------------|---------------------------------------------------|
| Method      | `POST`                                             |
| Path        | `/api/user/password/reset-request`                 |
| Auth        | None (public)                                      |
| Status      | `200 OK`                                           |

#### Request Body
```json
{
  "email": "rahul.sharma@gmail.com"
}
```

#### Response — 200 OK
```json
{
  "message": "OTP sent to rahul.s*****@gmail.com. Valid for 10 minutes.",
  "expiresAt": "2026-08-04T12:40:00Z"
}
```

> **Security Note**: Always returns 200 even if the email doesn't exist (prevents email enumeration).

#### 6b. Verify OTP & Reset Password

| Property    | Value                                             |
|-------------|---------------------------------------------------|
| Method      | `POST`                                             |
| Path        | `/api/user/password/reset`                         |
| Auth        | None (public)                                      |
| Status      | `200 OK`                                           |

#### Request Body
```json
{
  "email": "rahul.sharma@gmail.com",
  "otp": "483927",
  "newPassword": "NewSecurePass@456"
}
```

#### Response — 200 OK
```json
{
  "message": "Password reset successfully. Please log in with your new password."
}
```

#### Response — 400 Bad Request
```json
{
  "status": 400,
  "message": "Invalid or expired OTP",
  "timestamp": "2026-08-04T12:45:00Z"
}
```

---

### 7. Refresh Token

| Property    | Value                                             |
|-------------|---------------------------------------------------|
| Method      | `POST`                                             |
| Path        | `/api/user/token/refresh`                          |
| Auth        | None (refresh token in body)                       |
| Status      | `200 OK`                                           |

#### Request Body
```json
{
  "refreshToken": "dGhpcyBpcyBhIHJlZnJlc2g..."
}
```

#### Response — 200 OK
```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiIs...(new token)...",
  "refreshToken": "bmV3IHJlZnJlc2ggdG9rZW4...(rotated)...",
  "tokenType": "Bearer",
  "expiresIn": 86400
}
```

---

## Domain Model

### User Entity

| Column               | Type          | Constraints              | Description                           |
|----------------------|---------------|--------------------------|---------------------------------------|
| `id`                 | `BIGINT`      | PK, AUTO_INCREMENT       | Internal surrogate key                |
| `full_name`          | `VARCHAR(100)`| NOT NULL                 | User's display name                   |
| `email`              | `VARCHAR(150)`| UNIQUE, NOT NULL         | Login identifier + communication      |
| `phone`              | `VARCHAR(10)` | NOT NULL                 | Mobile number for OTP                 |
| `password_hash`      | `VARCHAR(60)` | NOT NULL                 | BCrypt-hashed password (strength 12)  |
| `role`               | `ENUM`        | NOT NULL, Default: CUSTOMER | `CUSTOMER`, `SELLER`, `ADMIN`      |
| `status`             | `ENUM`        | NOT NULL                 | `PENDING_VERIFICATION`, `ACTIVE`, `SUSPENDED`, `DELETED` |
| `failed_login_count` | `INTEGER`     | Default: 0               | Consecutive failed login attempts     |
| `locked_until`       | `TIMESTAMP`   | Nullable                 | Account lock expiry time              |
| `last_login_at`      | `TIMESTAMP`   | Nullable                 | Last successful login                 |
| `created_at`         | `TIMESTAMP`   | Auto-generated           | Registration time                     |
| `updated_at`         | `TIMESTAMP`   | Auto-updated             | Last profile update                   |

### Address Entity

| Column       | Type          | Constraints              | Description                           |
|--------------|---------------|--------------------------|---------------------------------------|
| `id`         | `BIGINT`      | PK, AUTO_INCREMENT       | Internal surrogate key                |
| `user_id`    | `BIGINT`      | FK → `users.id`, NOT NULL| Parent user reference                 |
| `label`      | `ENUM`        | NOT NULL                 | `HOME`, `OFFICE`, `OTHER`            |
| `street`     | `VARCHAR(200)`| NOT NULL                 | Street address                        |
| `city`       | `VARCHAR(100)`| NOT NULL                 | City name                             |
| `state`      | `VARCHAR(50)` | NOT NULL                 | State name                            |
| `pincode`    | `CHAR(6)`     | NOT NULL                 | Indian postal code                    |
| `phone`      | `VARCHAR(10)` | NOT NULL                 | Contact number for this address       |
| `is_default` | `BOOLEAN`     | Default: `false`         | Whether this is the default address   |
| `created_at` | `TIMESTAMP`   | Auto-generated           | Address creation time                 |

---

## JWT Token Specification

### Access Token (JWT)
| Claim       | Value                                  | Description                    |
|-------------|----------------------------------------|--------------------------------|
| `sub`       | `1001`                                 | User ID                       |
| `email`     | `rahul.sharma@gmail.com`               | User email                    |
| `role`      | `CUSTOMER`                             | User role for authorization   |
| `iat`       | `1722772200`                           | Issued at (epoch seconds)     |
| `exp`       | `1722858600`                           | Expires at (24h after iat)    |
| `iss`       | `shopease-user-service`                | Issuer                        |

### Token Configuration
| Property            | Value              |
|---------------------|--------------------|
| Algorithm           | RS256 (RSA)        |
| Access token TTL    | 24 hours           |
| Refresh token TTL   | 7 days             |
| Key rotation        | Every 90 days      |
| Token storage       | HttpOnly cookie (web) / Secure storage (mobile) |

---

## Security Measures

| Threat                  | Mitigation                                              |
|-------------------------|---------------------------------------------------------|
| Brute-force login       | Account locks after 5 failed attempts (30-min cooldown) |
| Password storage        | BCrypt hash with strength 12                            |
| Email enumeration       | Consistent 200 response on password reset requests      |
| Token theft             | Short-lived access tokens + refresh token rotation      |
| SQL injection           | Parameterized queries via Spring Data JPA               |
| CSRF                    | JWT in Authorization header (not cookies for API)       |
| XSS                     | HttpOnly cookies for web, input sanitization            |

---

## Planned Package Structure

```
com.ecommerce.user/
├── UserServiceApplication.java
├── config/
│   ├── SecurityConfig.java
│   ├── JwtConfig.java
│   └── WebMvcConfig.java
├── controller/
│   ├── AuthController.java
│   ├── UserController.java
│   └── AddressController.java
├── service/
│   ├── UserService.java
│   ├── AuthService.java
│   └── impl/
│       ├── UserServiceImpl.java
│       ├── AuthServiceImpl.java
│       └── OtpServiceImpl.java
├── repository/
│   ├── UserRepository.java
│   └── AddressRepository.java
├── entities/
│   ├── User.java
│   └── Address.java
├── dto/
│   ├── RegisterRequest.java
│   ├── LoginRequest.java
│   ├── LoginResponse.java
│   ├── UserProfileResponse.java
│   ├── AddressRequest.java
│   ├── PasswordResetRequest.java
│   └── TokenRefreshRequest.java
├── security/
│   ├── JwtTokenProvider.java
│   ├── JwtAuthenticationFilter.java
│   └── UserDetailsServiceImpl.java
└── exceptions/
    ├── UserNotFoundException.java
    ├── AccountLockedException.java
    ├── DuplicateEmailException.java
    └── GlobalExceptionHandler.java
```

---

## Configuration

### Docker Ports (Planned)
| Type             | Host     | Container |
|------------------|----------|-----------|
| Application      | `8084`   | `8080`    |
| Remote Debug     | `5004`   | `5000`    |

### Database (Planned)
| Property                 | Value                                          |
|--------------------------|------------------------------------------------|
| URL                      | `jdbc:mysql://user_db_container:3306/user_db`   |
| Host Port                | `3310`                                          |
| Container Port           | `3306`                                          |
| Credentials              | `scott` / `tiger` (dev only)                   |

### Dependencies
| Dependency                       | Purpose                                |
|----------------------------------|----------------------------------------|
| `spring-boot-starter-security`   | Spring Security framework              |
| `jjwt` (io.jsonwebtoken)         | JWT token creation and validation      |
| `spring-boot-starter-mail`       | OTP email delivery                     |
| `spring-boot-starter-validation` | Bean validation for DTOs               |
