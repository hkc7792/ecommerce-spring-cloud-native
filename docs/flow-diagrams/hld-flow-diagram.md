# High-Level Architecture Flow Diagrams

Below are the flow diagrams and data models rendered using Mermaid.js.

``mermaid
graph TB
    subgraph Client Layer
        CLIENT["🌐 Client / Browser / Mobile App"]
    end

    subgraph Service Layer
        US["👤 User Service\n:8084"]
        OS["📦 Order Service\n:8082"]
        IS["📋 Inventory Service\n:8081"]
        PS["💳 Payment Service\n:8083"]
    end

    subgraph Messaging Layer
        ZK["🐘 Zookeeper\n:2181"]
        KAFKA["📡 Apache Kafka\n:9092"]
        KD["🔍 Kafdrop UI\n:9000"]
    end

    subgraph Data Layer
        UDB[("🗄️ user_db\nMySQL :3310")]
        ODB[("🗄️ order_db\nMySQL :3308")]
        IDB[("🗄️ inventory_db\nMySQL :3307")]
        PDB[("🗄️ payment_db\nMySQL :3309")]
    end

    subgraph Shared Library
        COM["📚 commons\nShared DTOs, Events, Clients"]
    end

    CLIENT --> US
    CLIENT --> OS
    CLIENT --> IS
    CLIENT --> PS

    US --> UDB
    OS --> ODB
    IS --> IDB
    PS --> PDB

    OS -- "HTTP: Check/Reduce Stock" --> IS
    OS -- "Kafka: OrderPlaceEvent" --> KAFKA
    KAFKA -- "Kafka: OrderPlaceEvent" --> PS

    ZK --> KAFKA
    KD --> KAFKA

    COM -.-> US
    COM -.-> OS
    COM -.-> IS
    COM -.-> PS
``

``mermaid
sequenceDiagram
    autonumber
    participant C as 🌐 Client
    participant OS as 📦 Order Service
    participant ODB as 🗄️ order_db
    participant IS as 📋 Inventory Service
    participant IDB as 🗄️ inventory_db
    participant K as 📡 Kafka
    participant PS as 💳 Payment Service
    participant PDB as 🗄️ payment_db

    Note over C,PDB: Phase 1 — Order Placement (Synchronous)
    C->>+OS: POST /api/order (OrderRequest)
    OS->>OS: Generate UUID orderNumber
    OS->>OS: Map OrderRequest → Order + OrderLineItems
    OS->>OS: Calculate totalAmount from line items
    OS->>ODB: Save Order + OrderLineItems (JPA @Transactional)
    ODB-->>OS: ✅ Persisted

    Note over C,PDB: Phase 2 — Event Publishing (Async + Retry)
    OS->>OS: Build OrderPlaceEvent from saved Order
    OS->>+K: kafkaTemplate.send("notificationTopic", event)
    K-->>-OS: ✅ Acknowledged

    alt Kafka publish fails (after 3 retries)
        OS->>ODB: Save to OutBox table (PENDING)
        Note over OS,ODB: Outbox Relay Worker picks up later
    end

    OS-->>-C: 201 "Order Placed Successfully"

    Note over C,PDB: Phase 3 — Payment Processing (Async / Event-Driven)
    K->>+PS: OrderEventConsumer receives OrderPlaceEvent
    PS->>PS: processPayment(event)
    PS->>PDB: Save Payment (PENDING → COMPLETED/FAILED)
    PDB-->>PS: ✅ Persisted
    PS-->>-K: Acknowledge message
``

``mermaid
flowchart TD
    A["Order Service: placeOrder()"] --> B{"Kafka.send() succeeded?"}
    B -- "✅ Yes" --> C["Event delivered to notificationTopic"]
    B -- "❌ No after 3 retries" --> D["@Recover: Save to OutBox table PENDING"]
    D --> E["OutboxRelayWorker runs every 30s"]
    E --> F["Fetch PENDING events retryCount less than 5"]
    F --> G["Set status → PROCESSING"]
    G --> H{"Kafka.send() succeeded?"}
    H -- "✅ Yes" --> I["Set status → PUBLISH"]
    H -- "❌ No" --> J["Increment retryCount"]
    J --> K{"retryCount >= 5?"}
    K -- "Yes" --> L["Set status → FAILED Dead letter"]
    K -- "No" --> M["Set status → PENDING retry next cycle"]

    style A fill:#4a90d9,color:#fff
    style C fill:#27ae60,color:#fff
    style I fill:#27ae60,color:#fff
    style L fill:#e74c3c,color:#fff
``

``mermaid
sequenceDiagram
    autonumber
    participant C as 🌐 Client
    participant AC as 🔐 AuthController
    participant AS as AuthService
    participant DB as 🗄️ user_db
    participant JWT as JwtTokenProvider

    Note over C,JWT: Registration Flow
    C->>+AC: POST /api/auth/register (RegisterRequest)
    AC->>+AS: register(request)
    AS->>AS: Encode password (BCrypt)
    AS->>DB: Save User (CUSTOMER role, ACTIVE status)
    DB-->>AS: ✅ Persisted
    AS-->>-AC: Success
    AC-->>-C: 201 "User registered successfully"

    Note over C,JWT: Login Flow
    C->>+AC: POST /api/auth/login (LoginRequest)
    AC->>+AS: login(request)
    AS->>DB: Find user by email
    DB-->>AS: User entity
    AS->>AS: Verify password (BCrypt)
    AS->>+JWT: generateToken(email)
    JWT-->>-AS: JWT token string
    AS-->>-AC: LoginResponse token
    AC-->>-C: 200 LoginResponse token

    Note over C,JWT: Authenticated Request Flow
    C->>+AC: GET /api/user/profile (Authorization: Bearer JWT)
    AC->>AC: JwtAuthenticationFilter intercepts
    AC->>+JWT: extractEmail, validateToken
    JWT-->>-AC: email authenticated principal
    AC->>+AS: getProfile(email)
    AS->>DB: Find user + addresses
    DB-->>AS: User + Address entities
    AS-->>-AC: UserProfileResponse
    AC-->>-C: 200 UserProfileResponse
``

``mermaid
flowchart LR
    subgraph Order Service
        OS_SVC["OrderServiceImpl"]
    end
    subgraph Commons Library
        IC["InventoryClient\n(HTTP Interface)"]
    end
    subgraph Inventory Service
        IC_CTRL["InventoryController"]
        IC_SVC["InventoryService"]
        IC_DB[("inventory_db")]
    end

    OS_SVC -- "isInStock(skuCodes)" --> IC
    IC -- "GET /api/inventory/items?skuCode=..." --> IC_CTRL
    IC_CTRL --> IC_SVC
    IC_SVC --> IC_DB
    IC_DB --> IC_SVC
    IC_SVC --> IC_CTRL
    IC_CTRL -- "List of InventoryResponse" --> IC

    OS_SVC -- "reduceStock(requests)" --> IC
    IC -- "POST /api/inventory/reduce" --> IC_CTRL
``

``mermaid
flowchart LR
    subgraph Producers
        OS["📦 Order Service"]
        PS_PUB["💳 Payment Service"]
    end

    subgraph Kafka Topics
        NT["📫 notificationTopic"]
        PT["📫 paymentTopic"]
    end

    subgraph Consumers
        PS_CON["💳 Payment Service\n(payment-service-group)"]
        OS_CON["📦 Order Service\n(future consumer)"]
    end

    OS -- "OrderPlaceEvent" --> NT
    NT -- "OrderPlaceEvent" --> PS_CON
    PS_PUB -- "PaymentCompletedEvent" --> PT
    PS_PUB -- "PaymentFailedEvent" --> PT
    PT -- "PaymentCompletedEvent /\nPaymentFailedEvent" --> OS_CON
``

``mermaid
erDiagram
    USERS {
        Long id PK
        String full_name
        String email UK
        String phone
        String password_hash
        Enum role
        Enum status
        DateTime created_at
        DateTime updated_at
    }
    ADDRESSES {
        Long id PK
        Long user_id FK
        String label
        String street
        String city
        String state
        String pincode
        String phone
        Boolean is_default
    }
    USERS ||--o{ ADDRESSES : "has many"
``

``mermaid
erDiagram
    ORDERS {
        Long id PK
        String order_number UK
        Long customer_id
        BigDecimal total_amount
    }
    ORDER_LINE_ITEMS {
        Long id PK
        Long order_id FK
        String sku_code
        BigDecimal price
        Integer quantity
    }
    OUT_BOX {
        UUID id PK
        String aggregate_type
        String aggregate_id
        String event_type
        JSON payload
        Enum status
        Int retry_count
        DateTime created_at
        DateTime last_modified_at
    }
    ORDERS ||--o{ ORDER_LINE_ITEMS : "contains"
``

``mermaid
erDiagram
    PAYMENTS {
        Long id PK
        String payment_id UK
        String order_number
        Long customer_id
        BigDecimal amount
        String currency
        Enum payment_method
        String gateway_transaction_id UK
        Enum status
        String failure_reason
        DateTime paid_at
        DateTime created_at
        DateTime updated_at
    }
``

``mermaid
erDiagram
    INVENTORY {
        Long id PK
        String sku_code UK
        Integer quantity
        String status
    }
``

``mermaid
graph TB
    subgraph ecommerceNetwork ["🌐 Docker Bridge Network: ecommerce-network"]
        subgraph Messaging
            ZK["Zookeeper :2181"]
            KAFKA["Kafka :29092 internal\n:9092 host"]
            KD["Kafdrop :9000"]
        end

        subgraph UserDomain ["User Domain"]
            USER_SVC["user-service :8080\nhost :8084"]
            USER_DB["user_db MySQL :3306\nhost :3310"]
        end

        subgraph OrderDomain ["Order Domain"]
            ORDER_SVC["order-service :8080\nhost :8082"]
            ORDER_DB["order_db MySQL :3306\nhost :3308"]
        end

        subgraph InventoryDomain ["Inventory Domain"]
            INV_SVC["inventory-service :8080\nhost :8081"]
            INV_DB["inventory_db MySQL :3306\nhost :3307"]
        end

        subgraph PaymentDomain ["Payment Domain"]
            PAY_SVC["payment-service :8080\nhost :8083"]
            PAY_DB["payment_db MySQL :3306\nhost :3309"]
        end
    end

    ORDER_SVC -- "HTTP\nhttp://inventory-service:8080" --> INV_SVC
    ORDER_SVC -- "Kafka\nkafka:29092" --> KAFKA
    PAY_SVC -- "Kafka\nkafka:29092" --> KAFKA
``


