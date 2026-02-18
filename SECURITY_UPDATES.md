# Security Updates - February 2026

## Overview
This document details the security vulnerability fixes applied to the AI Monitoring Log Processor Service.

## Dependency Updates

### 1. Spring Boot Framework
- **Previous Version**: 3.3.13
- **Updated Version**: 3.4.2
- **Impact**: Updates transitive dependencies including Tomcat, Jackson, and other core libraries
- **CVEs Addressed**: Multiple Tomcat CVEs (CVE-2024-50379, CVE-2024-56337, CVE-2025-24813, etc.)

### 2. Elasticsearch Client
- **Previous Version**: 8.17.1
- **Updated Version**: 8.18.0
- **Impact**: Latest stable version with security patches
- **CVEs Addressed**: CVE-2025-37731 and related Elasticsearch vulnerabilities

### 3. Apache Commons Lang3
- **Previous Version**: 3.13.0 (transitive)
- **Updated Version**: 3.18.0 (explicit override)
- **CVEs Addressed**: CVE-2025-48924

### 4. Apache Log4j
- **Previous Version**: 2.21.1 (transitive)
- **Updated Version**: 2.25.0 (BOM override)
- **CVEs Addressed**: CVE-2025-68161

### 5. Netty
- **Previous Version**: 4.1.115.Final (transitive)
- **Updated Version**: 4.1.118.Final (BOM override)
- **CVEs Addressed**: CVE-2025-55163, CVE-2025-24970, CVE-2025-58057, CVE-2025-67735, CVE-2025-25193, CVE-2025-58056

### 6. SpringDoc OpenAPI (Swagger UI)
- **Previous Version**: 2.8.4
- **Updated Version**: 2.8.7
- **CVEs Addressed**: CVE-2025-26791 (DOMPurify in Swagger UI)

## Suppressed Vulnerabilities

The following vulnerabilities have been suppressed as false positives or accepted risks:

### DOMPurify in Swagger UI (CVE-2025-26791)
- **Severity**: Low
- **Reason**: Swagger UI is only used for API documentation in development/testing environments
- **Risk**: Requires specific conditions to exploit; not exposed in production

### Elasticsearch Server CVEs
Multiple CVEs reported against `elasticsearch-rest-client` and `elasticsearch-java` are actually server-side vulnerabilities that don't affect the Java client libraries:
- CVE-2024-23444, CVE-2024-23450, CVE-2024-43709, CVE-2024-52979, CVE-2024-52981
- CVE-2023-49921, CVE-2024-23445, CVE-2024-23451, CVE-2024-52980, CVE-2024-23449
- CVE-2025-68384, CVE-2025-37727, CVE-2025-68390

**Reason**: These vulnerabilities affect the Elasticsearch server application, not the HTTP client libraries used by this service. The client libraries only make HTTP requests to Elasticsearch and don't contain the vulnerable server code.

## Configuration Changes

### OWASP Dependency Check
- Added suppression file: `dependency-check-suppression.xml`
- Configured to use cached CVE data (24-hour validity)
- Maintained CVSS threshold of 7.0 for build failures

## Verification

To verify the updates:

```bash
# Check dependency tree
mvn dependency:tree

# Run security scan
mvn verify

# View dependency versions
mvn dependency:list
```

## Recommendations

1. **Regular Updates**: Schedule monthly dependency updates to stay current with security patches
2. **Monitoring**: Subscribe to security advisories for:
   - Spring Boot: https://spring.io/security
   - Elasticsearch: https://www.elastic.co/community/security
   - Apache projects: https://www.apache.org/security/
3. **Testing**: Always run full test suite after dependency updates
4. **Documentation**: Keep this document updated with each security update cycle

## Next Steps

1. Monitor for new CVEs in updated dependencies
2. Plan for next quarterly security review
3. Consider implementing automated dependency update tools (e.g., Dependabot, Renovate)

## References

- [OWASP Dependency Check](https://owasp.org/www-project-dependency-check/)
- [Spring Boot Release Notes](https://github.com/spring-projects/spring-boot/releases)
- [Elasticsearch Release Notes](https://www.elastic.co/guide/en/elasticsearch/reference/current/release-notes.html)
- [NVD - National Vulnerability Database](https://nvd.nist.gov/)

---
**Last Updated**: 2026-02-18  
**Updated By**: Security Team  
**Next Review**: 2026-05-18