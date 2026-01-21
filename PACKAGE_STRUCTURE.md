# Package Structure

```
com.university.grade/
├── service/
│   └── GradeInquiryService.java
├── repository/
│   ├── command/
│   │   └── (Master DB write repositories - not implemented in this scope)
│   └── query/
│       ├── GradeSummaryQueryRepository.java
│       ├── GradeListQueryRepository.java
│       └── GradeReleasePolicyQueryRepository.java
├── repository/
│   └── projection/
│       ├── GradeSummaryProjection.java
│       ├── GradeDetailProjection.java
│       └── GradeReleasePolicyProjection.java
├── cache/
│   ├── GradeSummaryCache.java
│   ├── GradeListCache.java
│   └── GradeReleasePolicyCache.java
├── event/
│   └── CacheInvalidationEvent.java
└── dto/
    ├── GradeSummaryResponse.java
    └── GradeDetailResponse.java
```
