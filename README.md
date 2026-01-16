# Log Processor Service

The Log Processor Service consumes log entries from RabbitMQ, processes them, and indexes them to Elasticsearch for searching and analysis.

## Features

- ✅ RabbitMQ consumer with manual acknowledgment
- ✅ Elasticsearch integration with automatic index creation
- ✅ Log normalization and enrichment
- ✅ Error detection and metadata extraction
- ✅ Concurrent processing (3-10 consumers)
- ✅ Dead letter queue for failed messages
- ✅ Health check endpoint

## Architecture

```
┌─────────────┐
│  RabbitMQ   │
│ logs.raw    │
└──────┬──────┘
       │ consume
       ▼
┌─────────────────┐
│  LogConsumer    │
│  - Manual ACK   │
│  - Error Handle │
└──────┬──────────┘
       │
       ▼
┌─────────────────┐
│LogProcessorSvc  │
│  - Normalize    │
│  - Enrich       │
└──────┬──────────┘
       │
       ▼
┌─────────────────┐
│Elasticsearch    │
│  logs index     │
└─────────────────┘
```

## Running the Service

### Prerequisites
- Java 17+
- Maven 3.9+
- Docker (for RabbitMQ and Elasticsearch)
- Log Ingestion Service running (to produce messages)

### Start Dependencies
```bash
# From project root
docker-compose up -d
```

### Run the Service
```bash
cd backend/log-processor
./mvnw spring-boot:run
```

The service will start on port **8082**.

### Verify Service is Running
```bash
# Health check
curl http://localhost:8082/api/v1/processor/health

# Expected response:
{
  "status": "UP",
  "service": "log-processor",
  "elasticsearch": "UP"
}
```

## How It Works

### 1. Message Consumption
- Listens to `logs.raw` queue in RabbitMQ
- Uses manual acknowledgment for reliability
- Concurrent consumers (3-10) for parallel processing
- Prefetch count of 10 messages per consumer

### 2. Log Processing Pipeline

**Normalization:**
- Sets timestamp if missing
- Normalizes log level to uppercase
- Truncates messages > 10,000 characters
- Sets default environment to "unknown"

**Enrichment:**
- Adds `processedAt` timestamp
- Adds `processor` identifier
- Calculates `messageLength`
- Detects error indicators:
  - `hasException` - contains "exception" or "error"
  - `hasTimeout` - contains "timeout"
  - `hasConnection` - contains "connection" or "connect"

### 3. Elasticsearch Indexing
- Automatically creates `logs` index on startup
- Indexes processed logs with proper mappings
- Keyword fields: level, service, host, environment, traceId, spanId
- Text field: message (with standard analyzer)
- Date field: timestamp
- Object field: metadata

## Configuration

Configuration is in `src/main/resources/application.yml`:

```yaml
spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: admin
    password: admin123
    listener:
      simple:
        acknowledge-mode: manual
        prefetch: 10
        retry:
          enabled: true
          initial-interval: 5000
          max-attempts: 3

elasticsearch:
  host: localhost
  port: 9200
  index:
    name: logs
    shards: 1
    replicas: 0

rabbitmq:
  queue:
    name: logs.raw

server:
  port: 8082
```

## Testing the End-to-End Flow

### 1. Start All Services
```bash
# Terminal 1: Start dependencies
docker-compose up -d

# Terminal 2: Start Log Ingestion Service
cd backend/log-ingestion
./mvnw spring-boot:run

# Terminal 3: Start Log Processor Service
cd backend/log-processor
./mvnw spring-boot:run
```

### 2. Send a Log Entry
```bash
curl -X POST http://localhost:8081/api/v1/logs \
  -H "Content-Type: application/json" \
  -d '{
    "level": "ERROR",
    "message": "Database connection timeout",
    "service": "user-service",
    "host": "prod-server-01",
    "environment": "production"
  }'
```

### 3. Verify Processing

**Check RabbitMQ:**
```bash
# Open RabbitMQ Management UI
open http://localhost:15672
# Login: admin/admin123
# Check logs.raw queue - should show messages being consumed
```

**Check Elasticsearch:**
```bash
# Search for the log
curl "http://localhost:9200/logs/_search?pretty" \
  -H "Content-Type: application/json" \
  -d '{
    "query": {
      "match": {
        "message": "timeout"
      }
    }
  }'
```

**Check Service Logs:**
```bash
# Look for these log messages:
# - "Received log from queue: service=user-service, level=ERROR"
# - "Processing log: service=user-service, level=ERROR"
# - "Log processed successfully: documentId=..."
# - "Log processed and acknowledged: ..."
```

## Elasticsearch Index Structure

The `logs` index is created with the following mapping:

```json
{
  "mappings": {
    "properties": {
      "timestamp": { "type": "date", "format": "strict_date_optional_time" },
      "level": { "type": "keyword" },
      "message": { "type": "text", "analyzer": "standard" },
      "service": { "type": "keyword" },
      "host": { "type": "keyword" },
      "environment": { "type": "keyword" },
      "traceId": { "type": "keyword" },
      "spanId": { "type": "keyword" },
      "metadata": { "type": "object", "enabled": true }
    }
  }
}
```

## Monitoring

### Actuator Endpoints
Available at `/actuator`:
- `/actuator/health` - Health status
- `/actuator/metrics` - Metrics
- `/actuator/prometheus` - Prometheus metrics

### Key Metrics to Monitor
- RabbitMQ queue depth (logs.raw)
- Message processing rate
- Elasticsearch indexing rate
- Error rate (messages sent to DLQ)
- Consumer lag

## Error Handling

### Message Processing Failures
1. Exception caught in consumer
2. Message is NACK'd (not requeued)
3. Message goes to Dead Letter Queue (logs.dlq)
4. Manual intervention required for DLQ messages

### Elasticsearch Failures
- Logged as errors
- Message is NACK'd and sent to DLQ
- Service continues processing other messages

### RabbitMQ Connection Failures
- Spring AMQP automatically reconnects
- Messages are redelivered after reconnection
- Check logs for connection errors

## Troubleshooting

### No Messages Being Consumed
```bash
# Check if RabbitMQ is running
docker ps | grep rabbitmq

# Check if queue exists and has messages
curl -u admin:admin123 http://localhost:15672/api/queues/%2F/logs.raw

# Check service logs for connection errors
```

### Elasticsearch Connection Failed
```bash
# Check if Elasticsearch is running
curl http://localhost:9200

# Check service logs
# Look for: "Elasticsearch ping failed"

# Restart Elasticsearch
docker-compose restart elasticsearch
```

### Messages Going to DLQ
```bash
# Check DLQ in RabbitMQ Management UI
# Inspect message content
# Fix the issue
# Manually requeue or delete messages
```

## Development

### Project Structure
```
src/
├── main/
│   ├── java/com/ibm/aimonitoring/processor/
│   │   ├── config/          # Configuration classes
│   │   ├── consumer/        # RabbitMQ consumers
│   │   ├── controller/      # REST controllers
│   │   ├── dto/             # Data Transfer Objects
│   │   └── service/         # Business logic
│   └── resources/
│       └── application.yml  # Configuration
└── test/
    └── java/com/ibm/aimonitoring/processor/
        └── ...              # Tests
```

### Running Tests
```bash
./mvnw test
```

## Performance Tuning

### Increase Concurrent Consumers
Edit `RabbitMQConfig.java`:
```java
factory.setConcurrentConsumers(5);  // Default: 3
factory.setMaxConcurrentConsumers(20);  // Default: 10
```

### Increase Prefetch Count
Edit `application.yml`:
```yaml
spring:
  rabbitmq:
    listener:
      simple:
        prefetch: 20  # Default: 10
```

### Elasticsearch Bulk Indexing
For higher throughput, implement bulk indexing in `ElasticsearchService`.

## Next Steps

After the Log Processor Service is running:
1. ✅ Build API Gateway for unified access
2. ✅ Add log search API
3. ✅ Implement ML Service for anomaly detection
4. ✅ Build Alert Service
5. ✅ Create Angular frontend

## License

MIT License - See LICENSE file for details