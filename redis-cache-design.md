# Redis Cache Implementation Design
## University Academic Portal - Grade Inquiry System

## 1. Cache Design Goals

### Purpose
Redis is deployed as a cache layer to protect the MySQL Read Replica from excessive load during grade release periods. The system experiences extreme read traffic spikes when grades are released to students, with thousands of concurrent requests querying the same grade data.

### Problems Solved

**Traffic Pattern Protection**
- During grade release periods, read traffic increases by 50-100x normal levels
- Without caching, the Read Replica would be overwhelmed by repeated queries for the same grade summaries
- Redis serves as a high-throughput buffer, reducing Read Replica load by 80-95%

**Database Load Reduction**
- Grade summary queries involve denormalized data from GRADE_SUMMARY table
- Grade list queries require JOIN operations across ENROLLMENTS, GRADES, and COURSES tables
- Caching eliminates redundant database round-trips for frequently accessed data

**Response Time Improvement**
- Redis read latency: < 1ms
- Read Replica query latency: 10-50ms (depending on query complexity)
- Cache hits provide 10-50x faster response times

**Cost Optimization**
- Reduced Read Replica query volume allows for smaller instance sizing
- Lower database connection pool requirements
- Reduced network bandwidth between application and database

---

## 2. Cache Target Definition

### 2.1 Grade Summary

**Domain Name**: Grade Summary

**Source of Truth**: `GRADE_SUMMARY` table (denormalized read-only snapshot)

**Read Pattern**:
- Frequency: Very high during grade release periods (thousands of requests per minute per student)
- Traffic Characteristics: Read-heavy, cache-first access pattern
- Access Pattern: Single student, single semester lookup per request
- Query Pattern: `SELECT * FROM GRADE_SUMMARY WHERE student_id = ? AND semester = ?`

**Cache Strategy**: Cache-First (Cache-Aside)
- Application checks Redis before any database access
- Cache HIT: Return immediately, no database query
- Cache MISS: Query Read Replica, populate cache, return result
- Cache population occurs only after successful database read

### 2.2 Grade List

**Domain Name**: Grade List

**Source of Truth**: JOIN result from `ENROLLMENTS`, `GRADES`, and `COURSES` tables

**Read Pattern**:
- Frequency: High during grade release periods (hundreds of requests per minute per student)
- Traffic Characteristics: Read-heavy, cache-first access pattern
- Access Pattern: Single student, single semester lookup per request
- Query Pattern: Complex JOIN query across three tables

**Cache Strategy**: Cache-First (Cache-Aside)
- Application checks Redis before any database access
- Cache HIT: Return immediately, no database query
- Cache MISS: Query Read Replica with JOIN, populate cache, return result
- Cache population occurs only after successful database read

### 2.3 Grade Release Policy

**Domain Name**: Grade Release Policy

**Source of Truth**: `GRADE_RELEASE_POLICY` table

**Read Pattern**:
- Frequency: Very high (checked on every grade inquiry request before data retrieval)
- Traffic Characteristics: Gatekeeper pattern - every request must check policy before proceeding
- Access Pattern: Single semester lookup per request
- Query Pattern: `SELECT is_released FROM GRADE_RELEASE_POLICY WHERE semester = ?`

**Cache Strategy**: Cache-First (Cache-Aside)
- Application checks Redis before any database access
- Cache HIT: Return immediately, no database query
- Cache MISS: Query Read Replica, populate cache, return result
- Critical for reducing database load since policy check happens on every request

### 2.4 User Session

**Domain Name**: User Session

**Source of Truth**: Application-managed session state (not database-backed)

**Read Pattern**:
- Frequency: High (checked on every authenticated request)
- Traffic Characteristics: Session validation on every API call
- Access Pattern: Token-based lookup

**Cache Strategy**: Cache-First
- Session data stored in Redis with token as key
- Session validation requires Redis lookup
- Not covered in detail as it is application-level session management

---

## 3. Redis Key Specification

### 3.1 Grade Summary Key

**Naming Convention**: `grade:summary:{studentId}:{semester}`

**Key Format**: 
- Prefix: `grade:summary:`
- Variable 1: `{studentId}` - BIGINT student identifier
- Separator: `:`
- Variable 2: `{semester}` - VARCHAR(20) semester identifier (e.g., "2025-1")

**Example Keys**:
- `grade:summary:12345:2025-1`
- `grade:summary:67890:2024-2`
- `grade:summary:11111:2025-S`

**Key Characteristics**:
- Unique per student-semester combination
- No wildcard patterns required for invalidation
- Direct key lookup for cache operations

### 3.2 Grade List Key

**Naming Convention**: `grade:list:{studentId}:{semester}`

**Key Format**:
- Prefix: `grade:list:`
- Variable 1: `{studentId}` - BIGINT student identifier
- Separator: `:`
- Variable 2: `{semester}` - VARCHAR(20) semester identifier

**Example Keys**:
- `grade:list:12345:2025-1`
- `grade:list:67890:2024-2`
- `grade:list:11111:2025-S`

**Key Characteristics**:
- Unique per student-semester combination
- No wildcard patterns required for invalidation
- Direct key lookup for cache operations

### 3.3 Grade Release Policy Key

**Naming Convention**: `grade:release:{semester}`

**Key Format**:
- Prefix: `grade:release:`
- Variable: `{semester}` - VARCHAR(20) semester identifier

**Example Keys**:
- `grade:release:2025-1`
- `grade:release:2024-2`
- `grade:release:2025-S`

**Key Characteristics**:
- Unique per semester
- Single key per semester (not per student)
- Direct key lookup for cache operations

### 3.4 User Session Key

**Naming Convention**: `session:{token}`

**Key Format**:
- Prefix: `session:`
- Variable: `{token}` - Session token string

**Example Keys**:
- `session:abc123def456`
- `session:xyz789uvw012`

**Key Characteristics**:
- Unique per session token
- Token-based authentication lookup

---

## 4. Redis Value Schema

### 4.1 Grade Summary Value

**Storage Format**: JSON string

**Schema**:
```json
{
  "studentId": 12345,
  "semester": "2025-1",
  "gpa": 3.82,
  "totalCredits": 18,
  "updatedAt": "2025-01-15T10:30:00"
}
```

**Field-to-DB Column Mapping**:
- `studentId` → `GRADE_SUMMARY.student_id` (BIGINT)
- `semester` → `GRADE_SUMMARY.semester` (VARCHAR(20))
- `gpa` → `GRADE_SUMMARY.gpa` (DECIMAL(4,2))
- `totalCredits` → `GRADE_SUMMARY.total_credits` (INT)
- `updatedAt` → `GRADE_SUMMARY.updated_at` (DATETIME, ISO 8601 format)

**Denormalization Notes**:
- This is a direct mapping from the denormalized GRADE_SUMMARY table
- No additional denormalization required
- All fields are stored as-is from the source table

### 4.2 Grade List Value

**Storage Format**: JSON array of objects

**Schema**:
```json
[
  {
    "enrollmentId": 1001,
    "courseCode": "COME2201",
    "courseName": "정보보호개론",
    "credit": 3,
    "score": 96.0,
    "gradeLetter": "A+",
    "isFinalized": true,
    "finalizedAt": "2025-01-10T14:20:00"
  },
  {
    "enrollmentId": 1002,
    "courseCode": "COME2302",
    "courseName": "운영체제",
    "credit": 3,
    "score": 90.0,
    "gradeLetter": "A0",
    "isFinalized": true,
    "finalizedAt": "2025-01-10T14:25:00"
  }
]
```

**Field-to-DB Column Mapping**:
- `enrollmentId` → `ENROLLMENTS.enrollment_id` (BIGINT)
- `courseCode` → `COURSES.course_code` (VARCHAR(20))
- `courseName` → `COURSES.course_name` (VARCHAR(200))
- `credit` → `COURSES.credit` (INT)
- `score` → `GRADES.score` (DECIMAL(5,2), nullable)
- `gradeLetter` → `GRADES.grade_letter` (VARCHAR(5), nullable)
- `isFinalized` → `GRADES.is_finalized` (BOOLEAN)
- `finalizedAt` → `GRADES.finalized_at` (DATETIME, nullable, ISO 8601 format)

**Denormalization Notes**:
- This value denormalizes data from three tables (ENROLLMENTS, GRADES, COURSES)
- JOIN result is pre-computed and stored as a single cache entry
- Eliminates need for repeated JOIN queries during cache hit scenarios
- Array order matches query ORDER BY clause (course_code ascending)

### 4.3 Grade Release Policy Value

**Storage Format**: String (boolean value as string)

**Schema**: `"true"` or `"false"`

**Field-to-DB Column Mapping**:
- String value → `GRADE_RELEASE_POLICY.is_released` (BOOLEAN)
  - `"true"` → `is_released = TRUE`
  - `"false"` → `is_released = FALSE`

**Denormalization Notes**:
- Only the `is_released` flag is cached (gatekeeper check)
- Other policy fields (policy_id, release_at, updated_at) are not cached
- Minimal storage footprint for high-frequency access

---

## 5. TTL Policy

### 5.1 Grade Summary TTL

**Default TTL**: 3600 seconds (1 hour)
- TTL 1 hour aligns with typical student access behavior, where grade inquiries are concentrated within short time windows after release.

**Rationale**:
- Grade summary data is relatively stable once finalized
- Updates to GRADE_SUMMARY table occur infrequently (batch jobs after grade finalization)
- 1-hour TTL balances cache freshness with database load reduction
- Long enough to provide significant load reduction during peak traffic
- Short enough to reflect updates within reasonable time window

**TTL Type**: Fixed TTL
- TTL is set at cache write time
- No event-driven TTL extension
- Automatic expiration after 1 hour

### 5.2 Grade List TTL

**Default TTL**: 3600 seconds (1 hour)

**Rationale**:
- Grade list data changes only when grades are finalized or updated
- During grade release periods, data is stable (read-only from student perspective)
- 1-hour TTL provides significant cache hit rate during peak traffic
- Balances freshness requirements with performance gains

**TTL Type**: Fixed TTL
- TTL is set at cache write time
- No event-driven TTL extension
- Automatic expiration after 1 hour

### 5.3 Grade Release Policy TTL

**Default TTL**: 3600 seconds (1 hour)

**Rationale**:
- Policy changes are infrequent (administrative operations)
- Policy is checked on every request, making cache critical for performance
- 1-hour TTL ensures policy changes are reflected within reasonable time
- Short enough to capture policy updates during grade release periods

**TTL Type**: Fixed TTL with Event-Driven Invalidation
- Default TTL: 1 hour
- Policy updates trigger immediate cache invalidation (see Invalidation Policy)
- TTL serves as safety net for missed invalidation events

---

## 6. Cache Invalidation Policy

| Cache Target | Triggering Event | Invalidation Scope | Timing | Notes |
|--------------|------------------|-------------------|--------|-------|
| Grade Summary | `GRADE_SUMMARY` table UPDATE | Single key: `grade:summary:{studentId}:{semester}` | Async (via SYSTEM_EVENTS) | Triggered when batch job updates summary after grade finalization |
| Grade Summary | `GRADES.is_finalized` changed to TRUE | Single key: `grade:summary:{studentId}:{semester}` | Async (via SYSTEM_EVENTS) | Grade finalization may trigger summary recalculation |
| Grade List | `GRADES` table INSERT/UPDATE | Single key: `grade:list:{studentId}:{semester}` | Async (via SYSTEM_EVENTS) | New grade entry or grade update affects list |
| Grade List | `GRADES.is_finalized` changed to TRUE | Single key: `grade:list:{studentId}:{semester}` | Async (via SYSTEM_EVENTS) | Grade finalization makes grade visible in list |
| Grade Release Policy | `GRADE_RELEASE_POLICY` table UPDATE | Single key: `grade:release:{semester}` | Sync (immediate) | Policy changes must be reflected immediately for gatekeeper logic |
| Grade Release Policy | `GRADE_RELEASE_POLICY.is_released` changed | Single key: `grade:release:{semester}` | Sync (immediate) | Critical for access control - must invalidate synchronously |

### Invalidation Implementation Notes

**Synchronous Invalidation**:
- Grade Release Policy invalidation occurs synchronously during policy update transaction
- Ensures immediate consistency for gatekeeper checks
- Failure to invalidate does not block database transaction

**Asynchronous Invalidation**:
- Grade Summary and Grade List invalidation occurs via SYSTEM_EVENTS table
- Application writes invalidation event to SYSTEM_EVENTS after database commit
- Cloud Functions consume events and perform Redis invalidation
- Decouples database transaction from cache invalidation
- Provides retry mechanism for failed invalidations

**Invalidation Scope**:
- All invalidations target specific keys (no wildcard patterns)
- Student ID and semester are extracted from database change context
- No bulk invalidation required (per-student, per-semester granularity)

**Failure Handling**:
- Invalidation failures are logged but do not block application flow
- TTL serves as safety net for missed invalidations
- Manual cache clearing available for administrative operations

---

## 7. Failure & Fallback Strategy

### 7.1 Redis Read Failure

**Behavior**: Fallback to Read Replica

**Implementation**:
- Application catches Redis read exceptions
- Logs warning message with error details
- Proceeds to query Read Replica directly
- Returns database result without cache population attempt
- Service remains fully functional

**Impact**:
- Response time increases (10-50ms instead of <1ms)
- Read Replica load increases temporarily
- No data loss or service degradation beyond performance impact

### 7.2 Redis Write Failure

**Behavior**: Non-blocking, log and continue

**Implementation**:
- Application catches Redis write exceptions during cache population
- Logs warning message with error details
- Continues with normal response flow
- Does not retry cache write (avoids blocking response)
- TTL expiration will naturally refresh cache on next request

**Impact**:
- Cache miss rate increases temporarily
- No impact on response correctness
- Subsequent requests will attempt cache population again
- Self-healing behavior when Redis recovers

### 7.3 Redis Unavailable

**Behavior**: Complete fallback to Read Replica

**Implementation**:
- Application detects Redis connection failure
- All cache operations (read and write) are bypassed
- All requests query Read Replica directly
- Service operates in degraded mode (no caching)
- Health checks may report degraded status

**Impact**:
- Read Replica experiences full traffic load
- Response times increase to database query latency
- System remains functional but at reduced capacity
- May require Read Replica scaling if prolonged

**Recovery**:
- When Redis becomes available, cache naturally repopulates via cache miss path
- No manual intervention required
- Gradual cache warm-up as requests come in

### 7.4 Cache Miss Storm (Stampede Risk)

**Problem**: Simultaneous cache expiration for many students during peak traffic

**Mitigation Strategy**: Staggered TTL

**Implementation**:
- Base TTL: 3600 seconds (1 hour)
- Jitter: Add random 0-300 seconds to TTL per key
- Effective TTL range: 3600-3900 seconds
- Prevents synchronized expiration across all keys

**Additional Protection**:
- Application-level request deduplication (not implemented in cache layer)
- Database connection pooling limits concurrent queries
- Read Replica can handle burst traffic within capacity limits

**Monitoring**:
- Track cache miss rate spikes
- Alert on Read Replica connection pool exhaustion
- Monitor database query latency during stampede events

---

## 8. Consistency Model

### 8.1 Expected Consistency Level

**Consistency Model**: Eventual Consistency

**Definition**:
- Cache may temporarily serve stale data (up to TTL duration or until invalidation)
- Database is always the source of truth
- Cache updates occur asynchronously after database writes
- No strong consistency guarantees between cache and database

### 8.2 Why Eventual Consistency is Acceptable

**Grade Data Characteristics**:
- Grade data is relatively stable once finalized
- Updates are infrequent (administrative operations, grade corrections)
- Students do not require real-time consistency (sub-second updates)
- Acceptable delay window: minutes to hours for non-critical updates

**Business Requirements**:
- Grade release policy changes require faster consistency (synchronous invalidation)
- Grade summary/list updates can tolerate eventual consistency (asynchronous invalidation)
- System prioritizes availability and performance over strict consistency

**User Experience**:
- Students typically view grades once or twice per day
- Cache staleness of minutes to hours is imperceptible to users
- Performance benefits (fast response times) outweigh minor consistency delays

### 8.3 Guarantees NOT Provided

**Strong Consistency**:
- Cache does not guarantee immediate reflection of database changes
- Cache may serve data that is up to TTL duration old
- No distributed locking or transaction coordination between cache and database

**Read-Your-Writes Consistency**:
- User's own write may not be immediately visible in cache
- Requires cache invalidation or TTL expiration to see updates
- Not applicable for grade inquiry (read-only from student perspective)

**Monotonic Reads**:
- Subsequent reads may see older data if cache is repopulated with stale data
- Rare edge case during cache invalidation race conditions

**Causal Consistency**:
- No ordering guarantees for cache updates
- Cache updates may arrive out of order relative to database writes

### 8.4 Consistency Mechanisms

**TTL-Based Expiration**:
- Ensures cache does not serve data older than TTL duration
- Provides upper bound on staleness

**Event-Driven Invalidation**:
- Reduces staleness window for critical updates
- Grade release policy: immediate invalidation
- Grade data: asynchronous invalidation within seconds to minutes

**Cache-Aside Pattern**:
- Database is always queried on cache miss
- Ensures cache population uses latest database state
- No risk of serving permanently stale data

---

## Appendix: Cache Operation Patterns

### Read Pattern (Cache-First)

1. Check Redis cache
2. If HIT: Return cached value immediately
3. If MISS: Query Read Replica
4. If query successful: Populate cache, return result
5. If query fails: Return error (no cache fallback for failed queries)

### Write Pattern (Cache Population)

1. Execute database query (Read Replica)
2. If query successful: Serialize result to JSON
3. Write to Redis with TTL
4. If write fails: Log warning, continue (non-blocking)
5. Return result to caller

### Invalidation Pattern

1. Database write completes (Master DB)
2. Extract invalidation context (studentId, semester)
3. Construct Redis key(s)
4. Delete key(s) from Redis
5. If deletion fails: Log error, rely on TTL expiration
