# MySQL Schema Design Document
## University Academic Portal - Grade Inquiry System

### System Architecture Context
- **Database**: Cloud DB for MySQL (Primary + Read Replica)
- **Cache Layer**: Redis (Session & Grade Summary)
- **Read/Write Separation**: Master DB for writes, Read Replica for reads
- **High Availability**: Master-Standby with automatic failover

---

## Table: USERS

### Purpose / Role
- Core user authentication table storing login credentials and user roles
- Primary source for password verification during login
- Must be accessed from Master DB to prevent replication lag security issues

### Access Pattern
- **Write-heavy**: New user registration, password updates
- **Read-heavy**: Login authentication (critical read from Master only)
- **Master DB only**: Security-critical table, replication lag must be avoided
- **Not cached**: Password hashes should never be cached

### Columns
- `user_id` (BIGINT, PK, AUTO_INCREMENT): Unique user identifier
- `login_id` (VARCHAR(50), UNIQUE, NOT NULL): User's login ID (학번/사번)
- `password_hash` (VARCHAR(255), NOT NULL): Bcrypt/Argon2 encrypted password
- `role` (VARCHAR(20), NOT NULL, DEFAULT 'ROLE_STUDENT'): User role (ROLE_STUDENT, ROLE_PROFESSOR, ROLE_ADMIN)
- `created_at` (DATETIME, NOT NULL, DEFAULT CURRENT_TIMESTAMP): Account creation timestamp

### Indexes
- **PRIMARY KEY** (`user_id`): Auto-increment primary key
- **UNIQUE INDEX** (`login_id`): Fast login lookup, prevents duplicate accounts
- **INDEX** (`role`): Role-based access control queries

### Caching Relation
- **Not cached**: Password hashes are security-sensitive and should never be cached
- Session tokens stored in Redis separately (key pattern: `session:{token}`)

### DDL
```sql
CREATE TABLE USERS (
    user_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    login_id VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'ROLE_STUDENT',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

---

## Table: AUTH_LOGS

### Purpose / Role
- Audit trail for all authentication attempts (successful and failed)
- Security monitoring and fraud detection
- Compliance logging for access attempts

### Access Pattern
- **Write-heavy**: Every login attempt creates a log entry
- **Read-light**: Primarily for audit/reporting (infrequent queries)
- **Master DB only**: Write-only table, audit integrity requires immediate persistence
- **Not cached**: Audit logs should not be cached

### Columns
- `log_id` (BIGINT, PK, AUTO_INCREMENT): Unique log entry identifier
- `user_id` (BIGINT, FK, NULLABLE): Reference to USERS table (NULL for failed attempts with invalid user_id)
- `action` (VARCHAR(20), NOT NULL): Action type ('LOGIN', 'FAIL', 'LOGOUT')
- `ip_address` (VARCHAR(45), NOT NULL): Client IP address (supports IPv6)
- `created_at` (DATETIME, NOT NULL, DEFAULT CURRENT_TIMESTAMP): Log entry timestamp

### Indexes
- **PRIMARY KEY** (`log_id`): Auto-increment primary key
- **INDEX** (`user_id`): Filter logs by user
- **INDEX** (`created_at`): Time-based queries for audit reports
- **INDEX** (`action`, `created_at`): Filter by action type and time range

### Caching Relation
- **Not cached**: Audit logs are write-only and should not be cached

### DDL
```sql
CREATE TABLE AUTH_LOGS (
    log_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NULL,
    action VARCHAR(20) NOT NULL,
    ip_address VARCHAR(45) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_created_at (created_at),
    INDEX idx_action_created (action, created_at),
    FOREIGN KEY (user_id) REFERENCES USERS(user_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

---

## Table: STUDENTS

### Purpose / Role
- Student profile information linked to user accounts
- 1:1 relationship with USERS table
- Used for student-specific queries and grade lookups

### Access Pattern
- **Read-heavy**: Frequently accessed during grade inquiries
- **Write-light**: Profile updates are infrequent
- **Read Replica target**: Non-critical reads can use replica
- **Master DB for writes**: Profile updates must go to Master

### Columns
- `student_id` (BIGINT, PK, AUTO_INCREMENT): Unique student identifier
- `user_id` (BIGINT, FK, UNIQUE, NOT NULL): 1:1 relationship with USERS
- `student_number` (VARCHAR(20), UNIQUE, NOT NULL): Official student number (학번)
- `name` (VARCHAR(100), NOT NULL): Student's full name
- `department` (VARCHAR(100), NULLABLE): Department/major name

### Indexes
- **PRIMARY KEY** (`student_id`): Auto-increment primary key
- **UNIQUE INDEX** (`user_id`): Enforces 1:1 relationship with USERS
- **UNIQUE INDEX** (`student_number`): Fast lookup by student number
- **INDEX** (`department`): Department-based filtering

### Caching Relation
- **Partially cached**: Student profile can be cached in Redis (key pattern: `student:{user_id}`)
- Cache TTL: 1 hour (profile changes are infrequent)

### DDL
```sql
CREATE TABLE STUDENTS (
    student_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    student_number VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    department VARCHAR(100) NULL,
    INDEX idx_department (department),
    FOREIGN KEY (user_id) REFERENCES USERS(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

---

## Table: COURSES

### Purpose / Role
- Course catalog storing all available courses
- Reference data for enrollments and grades
- Relatively static data (course information changes infrequently)

### Access Pattern
- **Read-heavy**: Frequently queried during grade lookups
- **Write-light**: Course creation/updates are administrative operations
- **Read Replica target**: All read queries use replica
- **Master DB for writes**: Course management operations

### Columns
- `course_id` (BIGINT, PK, AUTO_INCREMENT): Unique course identifier
- `course_code` (VARCHAR(20), UNIQUE, NOT NULL): Course code (e.g., "COME2201")
- `course_name` (VARCHAR(200), NOT NULL): Full course name
- `credit` (INT, NOT NULL): Credit hours (학점)
- `semester` (VARCHAR(20), NOT NULL): Semester identifier (e.g., "2025-1")

### Indexes
- **PRIMARY KEY** (`course_id`): Auto-increment primary key
- **UNIQUE INDEX** (`course_code`): Fast lookup by course code
- **INDEX** (`semester`): Filter courses by semester

### Caching Relation
- **Cached**: Course catalog can be cached in Redis (key pattern: `course:{course_code}` or `courses:semester:{semester}`)
- Cache TTL: 24 hours (course data is relatively static)

### DDL
```sql
CREATE TABLE COURSES (
    course_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    course_code VARCHAR(20) NOT NULL UNIQUE,
    course_name VARCHAR(200) NOT NULL,
    credit INT NOT NULL,
    semester VARCHAR(20) NOT NULL,
    INDEX idx_semester (semester)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

---

## Table: ENROLLMENTS

### Purpose / Role
- Junction table linking students to courses they are enrolled in
- Tracks which courses a student took in which semester
- Foundation for grade records

### Access Pattern
- **Read-heavy**: Frequently queried during grade lookups (joins with GRADES)
- **Write-light**: Enrollment happens during registration periods
- **Read Replica target**: All read queries use replica
- **Master DB for writes**: Enrollment changes must go to Master

### Columns
- `enrollment_id` (BIGINT, PK, AUTO_INCREMENT): Unique enrollment identifier
- `student_id` (BIGINT, FK, NOT NULL): Reference to STUDENTS table
- `course_id` (BIGINT, FK, NOT NULL): Reference to COURSES table
- `semester` (VARCHAR(20), NOT NULL): Semester when enrolled

### Indexes
- **PRIMARY KEY** (`enrollment_id`): Auto-increment primary key
- **INDEX** (`student_id`, `semester`): Fast lookup of student's enrollments by semester
- **INDEX** (`course_id`): Join optimization with COURSES
- **UNIQUE INDEX** (`student_id`, `course_id`, `semester`): Prevents duplicate enrollments

### Caching Relation
- **Not directly cached**: Enrollments are queried via joins, cached as part of grade summary

### DDL
```sql
CREATE TABLE ENROLLMENTS (
    enrollment_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    semester VARCHAR(20) NOT NULL,
    INDEX idx_student_semester (student_id, semester),
    INDEX idx_course_id (course_id),
    UNIQUE KEY uk_student_course_semester (student_id, course_id, semester),
    FOREIGN KEY (student_id) REFERENCES STUDENTS(student_id) ON DELETE CASCADE,
    FOREIGN KEY (course_id) REFERENCES COURSES(course_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

---

## Table: GRADES

### Purpose / Role
- Stores final grade information for each enrollment
- 1:1 relationship with ENROLLMENTS
- Core data for grade inquiry system

### Access Pattern
- **Read-heavy**: Primary data for grade lookups
- **Write-light**: Grades are finalized by professors/admin
- **Read Replica target**: All read queries use replica
- **Master DB for writes**: Grade updates must go to Master

### Columns
- `grade_id` (BIGINT, PK, AUTO_INCREMENT): Unique grade identifier
- `enrollment_id` (BIGINT, FK, UNIQUE, NOT NULL): 1:1 relationship with ENROLLMENTS
- `score` (DECIMAL(5,2), NULLABLE): Numeric score (0.00-100.00)
- `grade_letter` (VARCHAR(5), NULLABLE): Letter grade (A+, A0, B+, B0, C+, C0, D+, D0, F, P)
- `is_finalized` (BOOLEAN, NOT NULL, DEFAULT FALSE): Whether grade is finalized and visible to student

### Indexes
- **PRIMARY KEY** (`grade_id`): Auto-increment primary key
- **UNIQUE INDEX** (`enrollment_id`): Enforces 1:1 relationship with ENROLLMENTS
- **INDEX** (`is_finalized`): Filter finalized grades for display

### Caching Relation
- **Not directly cached**: Grades are cached as part of GRADE_SUMMARY in Redis
- Individual grade details retrieved from Read Replica on cache miss

### DDL
```sql
CREATE TABLE GRADES (
    grade_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    enrollment_id BIGINT NOT NULL UNIQUE,
    score DECIMAL(5,2) NULL,
    grade_letter VARCHAR(5) NULL,
    is_finalized BOOLEAN NOT NULL DEFAULT FALSE,
    INDEX idx_is_finalized (is_finalized),
    FOREIGN KEY (enrollment_id) REFERENCES ENROLLMENTS(enrollment_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

---

## Table: GRADE_OBJECTIONS

### Purpose / Role
- Stores student objections/appeals for grades
- Write-only from student perspective (inserts only)
- Triggers async serverless events for professor notifications
- Critical write table requiring immediate persistence

### Access Pattern
- **Write-heavy**: Students submit objections frequently during grade release periods
- **Read-light**: Professors/admin review objections (infrequent)
- **Master DB only**: Write-critical table, must ensure data integrity
- **Not cached**: Objections are write-only and should not be cached

### Columns
- `objection_id` (BIGINT, PK, AUTO_INCREMENT): Unique objection identifier
- `enrollment_id` (BIGINT, FK, NOT NULL): Reference to ENROLLMENTS (which grade is being objected)
- `title` (VARCHAR(200), NOT NULL): Objection title/subject
- `reason` (TEXT, NOT NULL): Detailed objection reason
- `status` (ENUM('PENDING', 'APPROVED', 'REJECTED'), NOT NULL, DEFAULT 'PENDING'): Objection status
- `professor_reply` (TEXT, NULLABLE): Professor's response to objection
- `created_at` (DATETIME, NOT NULL, DEFAULT CURRENT_TIMESTAMP): Submission timestamp

### Indexes
- **PRIMARY KEY** (`objection_id`): Auto-increment primary key
- **INDEX** (`enrollment_id`): Fast lookup of objections for a specific enrollment
- **INDEX** (`status`, `created_at`): Filter pending objections for professor review

### Caching Relation
- **Not cached**: Objections are write-only and should not be cached
- **Async trigger**: After INSERT, triggers Cloud Functions event via SYSTEM_EVENTS table

### DDL
```sql
CREATE TABLE GRADE_OBJECTIONS (
    objection_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    enrollment_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    reason TEXT NOT NULL,
    status ENUM('PENDING', 'APPROVED', 'REJECTED') NOT NULL DEFAULT 'PENDING',
    professor_reply TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_enrollment_id (enrollment_id),
    INDEX idx_status_created (status, created_at),
    FOREIGN KEY (enrollment_id) REFERENCES ENROLLMENTS(enrollment_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

---

## Table: GRADE_RELEASE_POLICY

### Purpose / Role
- Gatekeeper table controlling when grades are visible to students
- Global configuration per semester
- Frequently checked before displaying grades
- Must be cached to reduce database load

### Access Pattern
- **Read-heavy**: Checked on every grade inquiry request
- **Write-light**: Updated only when grade release periods change
- **Read Replica target**: Can use replica for reads
- **Master DB for writes**: Policy updates must go to Master

### Columns
- `policy_id` (BIGINT, PK, AUTO_INCREMENT): Unique policy identifier
- `semester` (VARCHAR(20), UNIQUE, NOT NULL): Semester identifier (e.g., "2025-1")
- `is_released` (BOOLEAN, NOT NULL, DEFAULT FALSE): Whether grades are currently visible
- `release_at` (DATETIME, NULLABLE): Scheduled release timestamp

### Indexes
- **PRIMARY KEY** (`policy_id`): Auto-increment primary key
- **UNIQUE INDEX** (`semester`): One policy per semester, fast lookup

### Caching Relation
- **Highly cached**: Must be cached in Redis (key pattern: `grade_policy:{semester}`)
- Cache TTL: 1 hour (policy changes are infrequent but critical)
- Cache invalidation: When policy is updated, invalidate Redis cache immediately

### DDL
```sql
CREATE TABLE GRADE_RELEASE_POLICY (
    policy_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    semester VARCHAR(20) NOT NULL UNIQUE,
    is_released BOOLEAN NOT NULL DEFAULT FALSE,
    release_at DATETIME NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

---

## Table: GRADE_SUMMARY

### Purpose / Role
- **Denormalized table** designed for cache-first grade summary lookups
- Pre-calculated GPA and credit totals per student per semester
- Reduces complex JOIN queries during high-traffic periods
- Primary target for Redis caching

### Access Pattern
- **Cache-first**: Redis is primary source, DB is fallback
- **Read-heavy**: Most frequently accessed data in the system
- **Write-light**: Updated by batch jobs when grades are finalized
- **Read Replica target**: Read queries use replica (cache miss scenario)

### Columns
- `summary_id` (BIGINT, PK, AUTO_INCREMENT): Unique summary identifier
- `student_id` (BIGINT, FK, NOT NULL): Reference to STUDENTS table
- `semester` (VARCHAR(20), NOT NULL): Semester identifier
- `gpa` (DECIMAL(4,2), NOT NULL): Calculated GPA (0.00-4.50)
- `total_credits` (INT, NOT NULL): Total credit hours for semester
- `updated_at` (DATETIME, NOT NULL, DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP): Last update timestamp

### Indexes
- **PRIMARY KEY** (`summary_id`): Auto-increment primary key
- **UNIQUE INDEX** (`student_id`, `semester`): Fast lookup by student and semester (primary access pattern)
- **INDEX** (`updated_at`): Track when summaries were last updated

### Caching Relation
- **Primary cache target**: Stored in Redis (key pattern: `grade_summary:{student_id}:{semester}`)
- Cache TTL: 1 hour (summary updates are infrequent)
- Cache invalidation: When grades are finalized, invalidate related summaries
- **Cache-first strategy**: Application checks Redis first, falls back to Read Replica on cache miss

### DDL
```sql
CREATE TABLE GRADE_SUMMARY (
    summary_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id BIGINT NOT NULL,
    semester VARCHAR(20) NOT NULL,
    gpa DECIMAL(4,2) NOT NULL,
    total_credits INT NOT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_student_semester (student_id, semester),
    INDEX idx_updated_at (updated_at),
    FOREIGN KEY (student_id) REFERENCES STUDENTS(student_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

---

## Table: SYSTEM_EVENTS

### Purpose / Role
- Decouples database commits from Cloud Functions triggers
- Stores events that need to be processed asynchronously
- Supports retry logic and auditing for serverless function invocations
- Used for grade objection notifications, cache invalidation triggers, etc.

### Access Pattern
- **Write-heavy**: Events inserted frequently (after grade objections, policy changes)
- **Read-light**: Cloud Functions poll this table periodically
- **Master DB only**: Event integrity requires immediate persistence
- **Not cached**: Events are consumed and should not be cached

### Columns
- `event_id` (BIGINT, PK, AUTO_INCREMENT): Unique event identifier
- `instance_id` (VARCHAR(100), NULLABLE): Pod name or IP address that created the event
- `event_type` (VARCHAR(50), NOT NULL): Event type ('GRADE_OBJECTION', 'CACHE_INVALIDATE', 'FAILOVER', 'RESTART')
- `description` (TEXT, NULLABLE): Event details or payload
- `created_at` (DATETIME, NOT NULL, DEFAULT CURRENT_TIMESTAMP): Event creation timestamp

### Indexes
- **PRIMARY KEY** (`event_id`): Auto-increment primary key
- **INDEX** (`event_type`, `created_at`): Filter events by type and process in order
- **INDEX** (`created_at`): Process events chronologically

### Caching Relation
- **Not cached**: Events are consumed by Cloud Functions and should not be cached
- **Retry mechanism**: Cloud Functions mark processed events (via application logic, not DB column)

### DDL
```sql
CREATE TABLE SYSTEM_EVENTS (
    event_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    instance_id VARCHAR(100) NULL,
    event_type VARCHAR(50) NOT NULL,
    description TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_event_type_created (event_type, created_at),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

---

## Summary: Access Pattern Matrix

| Table | Write Pattern | Read Pattern | Primary DB | Cache Strategy |
|-------|--------------|-------------|------------|----------------|
| USERS | Moderate | Heavy (Master only) | Master | Not cached |
| AUTH_LOGS | Heavy | Light | Master | Not cached |
| STUDENTS | Light | Heavy | Replica (read), Master (write) | Partially cached |
| COURSES | Light | Heavy | Replica (read), Master (write) | Cached |
| ENROLLMENTS | Light | Heavy | Replica (read), Master (write) | Not directly cached |
| GRADES | Light | Heavy | Replica (read), Master (write) | Cached via SUMMARY |
| GRADE_OBJECTIONS | Heavy | Light | Master | Not cached |
| GRADE_RELEASE_POLICY | Light | Very Heavy | Replica (read), Master (write) | Highly cached |
| GRADE_SUMMARY | Light | Very Heavy | Replica (read), Master (write) | Cache-first |
| SYSTEM_EVENTS | Heavy | Light | Master | Not cached |

---

## Redis Cache Key Patterns

- `session:{token}` - User session data
- `student:{user_id}` - Student profile (TTL: 1 hour)
- `course:{course_code}` - Course information (TTL: 24 hours)
- `courses:semester:{semester}` - Course list by semester (TTL: 24 hours)
- `grade_policy:{semester}` - Grade release policy (TTL: 1 hour)
- `grade_summary:{student_id}:{semester}` - Grade summary (TTL: 1 hour)
