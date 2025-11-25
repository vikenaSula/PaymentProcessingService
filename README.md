# Payment Processing Service

A Spring Boot-based payment processing microservice that integrates with Stripe for handling various payment methods including credit cards and bank transfers.

## Features

- **Multiple Payment Methods**: Support for credit cards, bank transfers
- **JWT Authentication**: Secure user authentication and authorization
- **Transaction Management**: Complete transaction lifecycle management with status tracking
- **Webhook Integration**: Stripe webhook support for real-time payment updates
- **Audit Logging**: Basic audit logging for transaction creation and modifications
- **API Documentation**: Interactive Swagger UI for API exploration
- **Comprehensive Testing**: Unit and integration tests included

## Technology Stack

- **Java 21**
- **Spring Boot 3.5.7**
- **Spring Data JPA**
- **Spring Security**
- **PostgreSQL**
- **Stripe API**
- **JWT (JSON Web Tokens)**
- **Lombok**
- **Springdoc OpenAPI (Swagger)**
- **Maven**

## Prerequisites

Before you begin, ensure you have the following installed:

- **Java 21** or higher ([Download](https://www.oracle.com/java/technologies/downloads/))
- **Maven 3.6+** or use the included Maven wrapper (`./mvnw`)
- **PostgreSQL 12+** ([Download](https://www.postgresql.org/download/))
- **Stripe Account** (for payment processing - [Sign up](https://dashboard.stripe.com/register))

## Getting Started

### 1. Clone the Repository

```bash
git clone https://github.com/vikenaSula/PaymentProcessingService
cd PaymentProcessingService

```

### 2. Database Setup

Create a PostgreSQL database:

```sql
CREATE DATABASE payment_service;
```

The application will automatically create the required tables on startup using Hibernate DDL auto-update.

### 3. Configuration

Update the `src/main/resources/application.properties` file with your environment-specific values:

#### Database Configuration

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/payment_service
spring.datasource.username=postgres
spring.datasource.password=your_password
# Replace with your DB password
```

#### Stripe Configuration

Replace with your Stripe test/live keys from [Stripe Dashboard](https://dashboard.stripe.com/apikeys):

```properties
stripe.secret-key=sk_test_YOUR_STRIPE_SECRET_KEY_HERE
stripe.publishable-key=pk_test_YOUR_STRIPE_PUBLISHABLE_KEY_HERE
```

#### JWT Configuration (Optional)

```properties
jwt.secret=YOUR_SECRET_HERE
jwt.expiration=86400000   # 24 hours
```
> ⚠️ **Security Warning**: Change the JWT secret to a strong, unique value in production!

### 4. Build the Application

```bash
# Windows
mvnw.cmd clean install

# Linux/Mac
./mvnw clean install
```

### 5. Run the Application

```bash
# Windows
mvnw.cmd spring-boot:run

# Linux/Mac
./mvnw spring-boot:run
```
Or run the JAR file:

```bash
java -jar target/payment-service-0.0.1-SNAPSHOT.jar
```

App runs at **http://localhost:8080**

## API Documentation

Swagger UI:
🔗 **http://localhost:8080/swagger-ui.html**

OpenAPI spec (JSON):
🔗 **http://localhost:8080/v3/api-docs**

## API Endpoints

### Authentication

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/register` | Register a new user |
| POST | `/api/auth/login` |Authenticate user & return JWT |

### Payment Processing

| Method | Endpoint | Description | Auth  |
|--------|----------|-------------|---------------|
| POST | `/api/payments/initiate` | Initiate a new payment | ✅ |
| GET | `/api/payments/transactions` | Get all transactions | ✅ |
| GET | `/api/payments/transactions/{id}` | Get transaction by ID | ✅ |

### Webhooks

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/webhook/stripe` | Stripe webhook endpoint |

## Testing the API

### 1. Register a User

```bash
POST http://localhost:8080/api/auth/register
Content-Type: application/json

{
  "username": "testuser",
  "password": "password123",
  "email": "testuser@example.com"
}
```

### 2. Login

```bash
POST http://localhost:8080/api/auth/login
Content-Type: application/json

{
  "username": "testuser",
  "password": "password123"
}
```

Save the `JWT token` from the response.

### 3. Initiate a Payment

```bash
POST http://localhost:8080/api/payments/initiate
Content-Type: application/json
Authorization: Bearer <your_jwt_token>

{
  "amount": 100.00,
  "currency": "USD",
  "paymentMethod": "CREDIT_CARD",
  "description": "Test payment",
  "paymentDetails": {
    "type": "CREDIT_CARD",
    "cardNumber": "4242424242424242",
    "expiryDate": "12/25",
    "cvv": "123",
    "cardHolderName": "Test User"
  }
}
```

### Use Stripe Test Cards:

- **Success**: 4242 4242 4242 4242
- **Requires Authentication**: 4000 0027 6000 3184
- **Declined**: 4000 0000 0000 0002


## Running Tests

Run all tests:

```bash
# Windows
mvnw.cmd test

# Linux/Mac
./mvnw test
```

Run specific test class:

```bash
mvnw test -Dtest=PaymentServiceTest
```

## Webhook Configuration

To test Stripe webhooks locally:

1. Install Stripe CLI: [https://stripe.com/docs/stripe-cli](https://www.stripe.com/docs/stripe-cli)

2. Login to Stripe:
```bash
stripe login
```

3. Forward webhooks to your local server:
```bash
stripe listen --forward-to localhost:8080/webhook/stripe
```

4. Copy the webhook signing secret and add to `application.properties`:
```properties
stripe.webhook.secret=whsec_your_webhook_secret
```

## Project Structure

```
payment-service/
├── src/
│   ├── main/
│   │   ├── java/com/dev/payment_service/
│   │   │   ├── config/           # Configuration classes
│   │   │   ├── controller/       # REST controllers
│   │   │   ├── dto/              # Data Transfer Objects
│   │   │   ├── enums/            # Enumerations
│   │   │   ├── exception/        # Exception handlers
│   │   │   ├── model/            # JPA entities
│   │   │   ├── repository/       # Data repositories
│   │   │   ├── security/         # Security & JWT
│   │   │   └── service/          # Business logic
│   │   └── resources/
│   │       └── application.properties
│   └── test/                     # Test classes
├── pom.xml
└── README.md
```

## Security Considerations

- BCrypt password hashing
- JWT authentication for all protected endpoints
- Token expiration configurable



## Troubleshooting

### Database Connection Issues

```
Error: Connection refused. Check that the hostname and port are correct...
```

**Solution**: Ensure PostgreSQL is running and credentials are correct.

### Stripe API Errors

```
Error: Invalid API Key provided
```

**Solution**: Verify your Stripe keys in `application.properties`.

### JWT Token Expired

```
Error: JWT expired
```

**Solution**: Login again to get a new token.
