# ML Service Integration - Log Processor

## Overview

The log-processor service now includes real-time anomaly detection powered by the ML Service using Isolation Forest algorithm.

## Architecture

```
Log Entry → Normalize → Enrich → Elasticsearch
                                      ↓
                                [Async Thread]
                                      ↓
                              Feature Extraction
                                      ↓
                              ML Service (HTTP)
                                      ↓
                            Anomaly Prediction
                                      ↓
                          PostgreSQL (anomaly_detections)
```

## Components

### 1. MLServiceClient
**Location:** `service/MLServiceClient.java`

**Features:**
- WebClient-based HTTP client
- Automatic feature extraction from logs
- Retry logic with exponential backoff (3 attempts)
- Circuit breaker pattern
- 5-second timeout
- Health check capability

**Extracted Features:**
- `messageLength`: Length of log message
- `level`: Log level (DEBUG, INFO, WARN, ERROR, FATAL)
- `service`: Service name (hashed)
- `hasException`: Contains exception keywords
- `hasTimeout`: Contains timeout keywords
- `hasConnectionError`: Contains connection error keywords

### 2. AnomalyDetection Entity
**Location:** `model/AnomalyDetection.java`

**Schema:** `ml_service.anomaly_detections`

**Fields:**
- `id`: Primary key
- `logId`: Reference to log entry
- `anomalyScore`: Score from ML model (0-1)
- `isAnomaly`: Boolean flag
- `confidence`: Prediction confidence (0-1)
- `modelVersion`: ML model version used
- `features`: JSON of extracted features
- `detectedAt`: Timestamp

### 3. AnomalyDetectionRepository
**Location:** `repository/AnomalyDetectionRepository.java`

**Queries:**
- `findByLogId()`: Get anomaly detection for specific log
- `findAnomaliesBetween()`: Get anomalies in time range
- `findRecentAnomalies()`: Get recent anomalies
- `countAnomaliesBetween()`: Count anomalies in time range
- `findHighConfidenceAnomalies()`: Get high-confidence anomalies

### 4. Async Processing
**Location:** `config/AsyncConfig.java`

**Configuration:**
- Core pool size: 5 threads
- Max pool size: 10 threads
- Queue capacity: 100 requests

## Configuration

### application.yml

```yaml
# ML Service Configuration
ml:
  service:
    url: ${ML_SERVICE_URL:http://localhost:8000}
    timeout: 5000
    retry:
      max-attempts: 3

# Async Processing
spring:
  task:
    execution:
      pool:
        core-size: 5
        max-size: 10
        queue-capacity: 100
```

### Environment Variables

- `ML_SERVICE_URL`: ML service endpoint (default: http://localhost:8000)

## Workflow

### 1. Log Processing
```java
processLog(logEntry)
  → normalizeLog()
  → enrichLog()
  → elasticsearchService.indexLog()
  → detectAnomaliesAsync() // Non-blocking
```

### 2. Anomaly Detection (Async)
```java
detectAnomaliesAsync(logId, logEntry)
  → mlServiceClient.predictAnomaly()
  → saveAnomalyDetection()
  → [If anomaly] Log warning
  → [If high confidence] Trigger alert (TODO)
```

### 3. Feature Extraction
```java
extractFeatures(logEntry)
  → messageLength
  → level (normalized)
  → service name
  → hasException (keyword detection)
  → hasTimeout (keyword detection)
  → hasConnectionError (keyword detection)
```

## Error Handling

### Retry Logic
- 3 retry attempts with exponential backoff
- Initial delay: 100ms
- Multiplier: 2.0
- Max delay: ~400ms

### Graceful Degradation
- If ML service is unavailable, log processing continues
- Anomaly detection is skipped
- Warning logged but no exception thrown

### Timeout Handling
- 5-second timeout per request
- Prevents blocking on slow ML service
- Async execution prevents impact on main flow

## Monitoring

### Logs

**Normal Log:**
```
DEBUG - Starting anomaly detection for log: abc-123
DEBUG - ML prediction for log abc-123: isAnomaly=false, score=0.60
DEBUG - Anomaly detection result saved to database for log: abc-123
```

**Anomalous Log:**
```
DEBUG - Starting anomaly detection for log: xyz-789
WARN  - Anomaly detected in log xyz-789: score=0.85, confidence=0.75
INFO  - High-confidence anomaly detected (confidence=0.75), alert should be triggered
DEBUG - Anomaly detection result saved to database for log: xyz-789
```

**ML Service Unavailable:**
```
WARN  - ML service unavailable, skipping anomaly detection for log: def-456
```

### Metrics

Monitor these metrics:
- Anomaly detection rate
- ML service response time
- ML service availability
- High-confidence anomaly count
- Async thread pool utilization

## Testing

### Unit Tests
```bash
cd backend/log-processor
./mvnw test
```

### Integration Test
```bash
# 1. Start ML service
cd backend/ml-service
uvicorn main:app --reload

# 2. Train model
curl -X POST http://localhost:8000/api/v1/train \
  -H "Content-Type: application/json" \
  -d @sample_training_data.json

# 3. Start log-processor
cd backend/log-processor
./mvnw spring-boot:run

# 4. Send test log
curl -X POST http://localhost:8081/api/v1/logs \
  -H "Content-Type: application/json" \
  -d '{
    "level": "ERROR",
    "message": "Connection timeout exception occurred in database",
    "service": "api-gateway"
  }'

# 5. Check database
psql -U admin -d ai_monitoring -c "SELECT * FROM ml_service.anomaly_detections ORDER BY detected_at DESC LIMIT 5;"
```

## Performance

### Throughput
- Async processing: No impact on main log flow
- ML prediction: ~50ms per log
- Database save: ~10ms per log
- Total overhead: ~60ms (async, non-blocking)

### Scalability
- Thread pool handles burst traffic
- Queue capacity: 100 concurrent requests
- Graceful degradation under load

## Future Enhancements

1. **Alert Integration**
   - Trigger alerts for high-confidence anomalies
   - Configurable confidence threshold
   - Multiple notification channels

2. **Batch Processing**
   - Batch ML predictions for efficiency
   - Reduce HTTP overhead

3. **Caching**
   - Cache ML predictions for similar logs
   - Reduce ML service load

4. **Model Updates**
   - Automatic model retraining
   - A/B testing of models
   - Model performance tracking

## Troubleshooting

### ML Service Connection Issues
```
Error: ML service unavailable
Solution: Check ML_SERVICE_URL configuration and ML service health
```

### Database Persistence Errors
```
Error: Error saving anomaly detection to database
Solution: Check PostgreSQL connection and ml_service schema exists
```

### Async Thread Pool Exhaustion
```
Error: Task rejected from thread pool
Solution: Increase pool size or queue capacity in application.yml
```

---

**Made with Bob**