graph TD
    %% 사용자 및 진입점
    User["사용자 (Student)"] -->|HTTPS| ALB["Load Balancer / Ingress"]
    
    subgraph "Naver Cloud Platform (VPC)"
        ALB -->|Traffic Distribute| Service["K8s Service (ClusterIP)"]
        
        subgraph "NKS Cluster (Application Layer)"
            direction TB
            
            subgraph "Pod 1"
                App1["Spring Boot App"]
                Cache1[("Local Cache\n(Caffeine/Ehcache)\n[TTL: 5min]")]
                App1 <--> Cache1
            end
            
            subgraph "Pod 2 (Replica)"
                App2["Spring Boot App"]
                Cache2[("Local Cache\n(Caffeine/Ehcache)\n[TTL: 5min]")]
                App2 <--> Cache2
            end
            
            Service --> App1
            Service --> App2
        end
        
        subgraph "Data Layer (Cloud DB for MySQL)"
            direction TB
            
            MasterDB[("MySQL Master\n(Write Only)")]
            SlaveDB[("MySQL Slave\n(Read Only)")]
            
            MasterDB -.->|Async Replication| SlaveDB
        end
    end
    
    %% 데이터 흐름 (Read/Write Split)
    App1 -->|"INSERT/UPDATE\n(Auth_Logs, Objections, Sessions)"| MasterDB
    App2 -->|"INSERT/UPDATE"| MasterDB
    
    App1 -->|"SELECT\n(Grades, Summary, Policy)"| SlaveDB
    App2 -->|"SELECT"| SlaveDB
    
    %% 스타일 정의
    classDef app fill:#e1f5fe,stroke:#01579b,stroke-width:2px;
    classDef db fill:#fff3e0,stroke:#e65100,stroke-width:2px,shape:cylinder;
    classDef cache fill:#f3e5f5,stroke:#4a148c,stroke-width:2px,shape:cylinder;
    classDef ext fill:#f5f5f5,stroke:#666,stroke-width:1px;
    
    class App1,App2 app;
    class MasterDB,SlaveDB db;
    class Cache1,Cache2 cache;
    class User,ALB,Service ext;