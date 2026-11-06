# QA AI Work Journal

## 2026-11-06 — Claims Management: source review for a new QA cycle

- **Date:** 2026-11-06, Asia/Bangkok.
- **Task:** Read-only QA analysis of architecture, workflows, business rules, authorization, risks, existing test assets, and proposed unit, component, and integration scenarios.
- **Decision: Pending Review**
- **Review method:** Static inspection of the supplied application source, API specifications, migrations, dependency manifests, infrastructure configuration, and documentation. No application startup, builds, dependency installation, API requests, or tests were performed. Only this journal was edited.
- **Evidence boundary:** “Confirmed” means visible in this source snapshot; it does not establish deployed behavior. Potential defects and risk priorities below are review candidates, without runtime reproduction or an approved business specification. Generated Java API/client code is referenced by the application but is not present in this snapshot; it was not generated for this review.
- **Workspace limitation:** No Git repository was detected at the workspace root or in demo-app. No Git history was changed. QA_AI_WORK_JOURNAL.md was empty before this entry. The IDE-listed AI_WORKING_JOURNAL.md was absent and was not created.

### Exact prompt used

The following is the complete user request, including the IDE context. It is retained verbatim as the input prompt; its quoted decision words are not review decisions.

```text
# Context from my IDE setup:

## Active file: demo-app/QA_AI_WORK_JOURNAL.md

## Open tabs:
- QA_AI_WORK_JOURNAL.md: demo-app/QA_AI_WORK_JOURNAL.md
- AI_WORKING_JOURNAL.md: demo-app/AI_WORKING_JOURNAL.md
- architecture-diagram.excalidraw: demo-app/architecture-diagram.excalidraw
- ARCHITECTURE.md: demo-app/ARCHITECTURE.md

## My request:
Act as a Senior QA Engineer with expertise in test strategy, API testing, test automation, and risk-based testing.

We are starting a new QA testing cycle for the Claims Management application.

Please perform a read-only analysis of the application source code.

1. Identify the architecture, technology stack, and integration points.
2. Analyze the main business workflows, including claim submission and claim status management.
3. Identify important business rules, field validations, and authorization rules.
4. Identify high-risk areas based on:
- Business impact
- Data integrity
- Security
- Integration complexity
5. Review existing automated tests and testing frameworks.
6. Recommend high-priority test scenarios for component, unit, and integration testing.

For each finding, provide relevant source file references.
Clearly separate confirmed code findings from potential defects or assumptions.

AI WORKING JOURNAL:
Update QA_AI_WORK_JOURNAL.md with:
- Date and task
- Exact prompt used
- Summary of findings and recommendations
- Decision: Pending Review
- Reasoning and supporting evidence

Do not record Accepted, Challenged, or Overridden without my confirmation.

IMPORTANT:
- Do not modify production code.
- Do not implement or execute tests.
- Do not modify existing test files.
- Do not create other documents.
- Do not rewrite previous Git history.

Present the analysis for my review.
```

### Summary of findings and recommendations

The application has a Next.js UI, a Spring Boot BFF, and a Spring Boot Claims Service. PostgreSQL owns claim/user records, Keycloak owns identities, Redis caches dashboard statistics, and Kafka delivers events to the BFF for WebSocket notifications. Direct event publishing and optional Debezium CDC create distinct integration paths. See A1–A5 and W1–W5 for source evidence.

The domain explicitly validates incident details, initializes claims as SUBMITTED, and enforces a five-state lifecycle. Claim reads use JWT-subject ownership; status updates require both JWT ADMIN authority and a database ADMIN user. These are the primary business invariants to protect. See B1–B8.

Prioritize investigation of user provisioning and identity rebinding, shared mutable bearer-token clients, status concurrency, monetary precision, HTTP error contracts, and reliable events. Address logout/CSRF and configuration alignment early because they can invalidate the whole QA cycle. See R1–R13 for evidence and qualified impact.

The README advertises existing tests, but no test implementation files were found in this supplied snapshot. Dependencies and test tooling are present; passing tests, numerical coverage, and a “broken test” could not be verified. Proposed scenarios U1–U5, C1–C5, and I1–I8 are recommendations only.

### Confirmed code findings: architecture and integration points

| ID | Confirmed finding | Supporting source |
| --- | --- | --- |
| A1 | Three application tiers: Next.js 16.1.6 / React 19.2.3 UI; Java 21 / Spring Boot 4.0.2 BFF and Claims Service. UI uses TypeScript, Zustand, React Hook Form, Zod, and Tailwind. Backend packages separate domain, application use cases/ports, and adapters; some adapters also directly access repositories/construct clients. | [package.json:18](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/demo-app-ui/package.json:18); [pom.xml:7](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/pom.xml:7); [pom.xml:31](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/pom.xml:31); [pom.xml:7](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/pom.xml:7); [CreateClaimUseCase.java:19](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/application/usecases/CreateClaimUseCase.java:19); [UserController.java:32](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/adapter/in/web/UserController.java:32); [AdminController.java:32](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/adapter/in/web/AdminController.java:32). |
| A2 | API-first integration: Spring interfaces/DTOs generated from OpenAPI, BFF Java client generated with okhttp-gson from the Claims API, and UI TypeScript fetch client generated from the BFF API. Same-origin UI /api rewrites coexist with an environment-configured absolute BFF URL in the generated-client wrapper. | [pom.xml:345](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/pom.xml:345); [pom.xml:388](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/pom.xml:388); [package.json:7](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/demo-app-ui/package.json:7); [next.config.ts:4](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/demo-app-ui/next.config.ts:4); [bff-client.ts:15](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/demo-app-ui/src/lib/api/bff-client.ts:15). |
| A3 | Claims Service uses Spring Data JPA and Liquibase against PostgreSQL 15. Claims have a required user foreign key, DECIMAL(12,2) amount, status, and timestamps. Redis 7 stores dashboard statistics with a 300-second TTL and exception-to-database fallback. | [docker-compose.infra.yml:11](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/docker-compose.infra.yml:11); [docker-compose.infra.yml:76](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/docker-compose.infra.yml:76); [002-create-claims-table.yaml:6](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/resources/db/changelog/changes/002-create-claims-table.yaml:6); [DashboardController.java:24](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/adapter/in/web/DashboardController.java:24); [DashboardStatsCache.java:34](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/adapter/out/cache/DashboardStatsCache.java:34). |
| A4 | Keycloak 26.5.2 supplies JWTs. Login actually uses the password grant through the BFF; signup uses Keycloak Admin API followed by Claims user provisioning. The BFF issues HttpOnly / SameSite=Strict cookies. Resource servers map realm roles to ROLE_* authorities. Service-account token caching is in JVM memory; no BFF Redis session-cache implementation was found in the reviewed auth path. | [docker-compose.infra.yml:90](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/docker-compose.infra.yml:90); [KeycloakTokenAdapter.java:37](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/adapter/out/keycloak/KeycloakTokenAdapter.java:37); [SignupUseCaseImpl.java:19](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/application/auth/SignupUseCaseImpl.java:19); [AuthController.java:132](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/adapter/in/web/AuthController.java:132); [SecurityConfig.java:90](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/config/SecurityConfig.java:90); [ServiceAccountTokenProviderImpl.java:24](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/adapter/out/keycloak/ServiceAccountTokenProviderImpl.java:24). |
| A5 | Direct mode sends claim-events; CDC mode uses PostgreSQL WAL → Debezium 2.4 → cdc.demo-app.public.claims. BFF listens to both topics and routes WebSocket notifications. Compose selects direct mode, while the Claims application's own default enables CDC. Compose exposes UI 3001, BFF 8090, and Claims Service 8080. | [CreateClaimUseCase.java:49](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/application/usecases/CreateClaimUseCase.java:49); [KafkaDomainEventPublisher.java:21](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/adapter/out/messaging/KafkaDomainEventPublisher.java:21); [ClaimEventKafkaConsumer.java:28](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/adapter/in/messaging/ClaimEventKafkaConsumer.java:28); [claims-connector.json:4](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/docker/compose/debezium/connector-configs/claims-connector.json:4); [docker-compose.cdc.yml:16](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/docker-compose.cdc.yml:16); [docker-compose.apps.yml:14](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/docker-compose.apps.yml:14); [docker-compose.apps.yml:22](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/docker-compose.apps.yml:22); [application.yml:81](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/resources/application.yml:81). |

The architecture document's browser-redirect login and BFF Redis-session description are not supported by the reviewed implementation. Use the source flows above as the QA baseline; reconcile documentation before treating those descriptions as requirements. Evidence: [ARCHITECTURE.md](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/ARCHITECTURE.md), A3–A4.

### Confirmed code findings: business workflows

| ID | Workflow and observed behavior | Supporting source |
| --- | --- | --- |
| W1 | Signup creates a Keycloak identity with default CLAIMANT role, then provisions its UUID/email/name/role in Claims Service. Login exchanges credentials for tokens, looks up the user, and tries auto-provisioning on any IllegalStateException from lookup. UI receives user details and cookies, then routes admins to the dashboard and claimants to claims. | [SignupUseCaseImpl.java:8](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/application/auth/SignupUseCaseImpl.java:8); [LoginUseCaseImpl.java:29](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/application/auth/LoginUseCaseImpl.java:29); [LoginUseCaseImpl.java:48](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/application/auth/LoginUseCaseImpl.java:48); [AuthController.java:73](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/adapter/in/web/AuthController.java:73); [login/page.tsx:44](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/demo-app-ui/app/(auth)/login/page.tsx:44). |
| W2 | Submission has three UI steps: incident date/location/amount, description, and review/submit. Submission calls POST /api/claims; BFF forwards the user's JWT. Claims Service derives owner UUID from JWT sub, checks the local user exists, creates a random claim UUID, validates the domain object, and saves transactionally. Success returns 201 and the new claim; the wizard resets and navigates to claims. | [wizard-step-1.tsx:20](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/demo-app-ui/src/features/claims/components/wizard-step-1.tsx:20); [wizard-step-3.tsx:27](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/demo-app-ui/src/features/claims/components/wizard-step-3.tsx:27); [use-claim-wizard-store.ts:52](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/demo-app-ui/src/features/claims/stores/use-claim-wizard-store.ts:52); [ClaimsServiceAdapter.java:137](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/adapter/out/claimsservice/ClaimsServiceAdapter.java:137); [ClaimController.java:53](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/adapter/in/web/ClaimController.java:53); [CreateClaimUseCase.java:19](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/application/usecases/CreateClaimUseCase.java:19). |
| W3 | Claim list is restricted by JWT user ID and sorted newest first. Claim detail loads by UUID, then compares the owner against the requesting JWT subject. This detail path has no admin ownership bypass. Summary descriptions are truncated to 100 characters. | [ClaimController.java:91](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/adapter/in/web/ClaimController.java:91); [GetClaimUseCase.java:31](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/application/usecases/GetClaimUseCase.java:31); [ClaimJpaRepository.java:12](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/adapter/out/persistence/ClaimJpaRepository.java:12); [ClaimWebMapper.java:40](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/adapter/in/web/ClaimWebMapper.java:40). |
| W4 | Admin status management lists claims with optional status/user filters, offers valid transitions with a confirmation dialog, optimistically updates UI, and calls PATCH /api/admin/claims/{claimId}/status. The service verifies the database admin role, applies the domain transition, persists, and emits an event in direct mode. Failed UI updates restore the previous whole list. | [AdminController.java:104](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/adapter/in/web/AdminController.java:104); [AdminClaimsController.java:45](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/adapter/in/web/AdminClaimsController.java:45); [claim-status-select.tsx:31](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/demo-app-ui/src/features/admin/components/claim-status-select.tsx:31); [use-admin-claims-store.ts:77](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/demo-app-ui/src/features/admin/stores/use-admin-claims-store.ts:77); [UpdateClaimStatusUseCase.java:35](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/application/usecases/UpdateClaimStatusUseCase.java:35); [Claim.java:120](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/domain/claim/Claim.java:120). |
| W5 | Dashboard reads aggregate users/claims/status counts with Redis hit/miss/bypass/flush behavior. Submitted events notify admins; status-change events notify the owner plus admins. UI updates claimant statuses and refetches admin claims; submission also triggers an admin statistics fetch. | [DashboardController.java:35](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/adapter/in/web/DashboardController.java:35); [ClaimEventKafkaConsumer.java:75](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/adapter/in/messaging/ClaimEventKafkaConsumer.java:75); [ClaimWebSocketHandler.java:92](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/adapter/in/websocket/ClaimWebSocketHandler.java:92); [use-claim-realtime-handler.ts:37](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/demo-app-ui/src/features/claims/hooks/use-claim-realtime-handler.ts:37). |

No payment execution, policy coverage/eligibility check, document-upload workflow, or implemented claimant edit/delete flow was identified in the reviewed claim code. These are scope assumptions to resolve with the product owner, not missing-feature defects. The Claims OpenAPI does advertise a separate general PATCH; see R13.

### Confirmed code findings: rules and authorization

| ID | Rule visible in code | Supporting source |
| --- | --- | --- |
| B1 | Incident date, location, description, amount, and owner are non-null. Future dates are rejected against the backend's LocalDate.now(); there is no coded lower date bound. | [Claim.java:72](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/domain/claim/Claim.java:72); [CreateClaimCommand.java:16](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/application/usecases/CreateClaimCommand.java:16). |
| B2 | Location is trimmed before length validation and storage; valid trimmed length is 5–200. Description is trimmed, with valid trimmed length 10–1000. Frontend Zod checks raw lengths without trimming. | [Claim.java:85](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/domain/claim/Claim.java:85); [Claim.java:95](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/domain/claim/Claim.java:95); [claim-schemas.ts:3](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/demo-app-ui/src/features/claims/schemas/claim-schemas.ts:3). |
| B3 | Domain amount must be > 0 and ≤ 1,000,000. UI and both OpenAPI request contracts specify a minimum of 0.01; neither domain nor Zod imposes a two-decimal scale. Storage is DECIMAL(12,2), while API amounts are doubles. | [Claim.java:90](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/domain/claim/Claim.java:90); [claim-schemas.ts:14](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/demo-app-ui/src/features/claims/schemas/claim-schemas.ts:14); [bff-api.yml:625](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/resources/openapi/bff-api.yml:625); [claims-api.yml:665](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/resources/openapi/claims-api.yml:665); [ClaimEntity.java:47](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/adapter/out/persistence/ClaimEntity.java:47); [ClaimController.java:76](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/adapter/in/web/ClaimController.java:76). |
| B4 | New claims start SUBMITTED with createdAt = updatedAt and one ClaimSubmitted event. A valid status change updates updatedAt and adds a ClaimStatusChanged event; invalid transitions throw before mutation. | [Claim.java:100](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/domain/claim/Claim.java:100); [Claim.java:120](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/domain/claim/Claim.java:120). |
| B5 | Status transitions are exactly those listed in the table below. Same-state updates are invalid; CLOSED has no outgoing transition. UI transition choices currently match the backend. | [ClaimStatus.java:10](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/domain/claim/ClaimStatus.java:10); [claim-status-transitions.ts:3](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/demo-app-ui/src/features/admin/utils/claim-status-transitions.ts:3). |
| B6 | Signup always assigns CLAIMANT. SignupCommand requires a nonblank name/email/password, a custom email format, and a password of at least 8 characters with uppercase and digit. UI name length is 1–255; OpenAPI specifies 2–100; user domain permits nonblank names up to 255. | [SignupUseCaseImpl.java:8](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/application/auth/SignupUseCaseImpl.java:8); [SignupCommand.java:6](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/application/auth/SignupCommand.java:6); [auth-schemas.ts:3](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/demo-app-ui/src/features/auth/schemas/auth-schemas.ts:3); [bff-api.yml:542](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/resources/openapi/bff-api.yml:542); [User.java:51](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/user/domain/User.java:51). |
| B7 | BFF /api/admin/** requires JWT ADMIN; Claims admin controllers use method-level ADMIN checks. Status changes additionally require the local database user to be ADMIN. Ordinary /api/claims endpoints require authentication, without a CLAIMANT-only role check, and use subject-based ownership. | [SecurityConfig.java:67](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/config/SecurityConfig.java:67); [SecurityConfig.java:55](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/config/SecurityConfig.java:55); [AdminClaimsController.java:46](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/adapter/in/web/AdminClaimsController.java:46); [UpdateClaimStatusUseCase.java:35](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/application/usecases/UpdateClaimStatusUseCase.java:35); [ClaimController.java:58](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/adapter/in/web/ClaimController.java:58); [GetClaimUseCase.java:35](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/application/usecases/GetClaimUseCase.java:35). |
| B8 | Browser write APIs use BFF CSRF protection except signup/login/refresh and listed infrastructure paths. Claims Service disables CSRF and uses bearer JWTs. WebSocket HTTP routing is permitAll, but its handshake interceptor decodes a token and saves user/roles; configured origins are restricted. JWT role checks and ownership remain server controls; the UI middleware only checks cookie presence. | [SecurityConfig.java:52](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/config/SecurityConfig.java:52); [SecurityConfig.java:55](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/config/SecurityConfig.java:55); [WebSocketAuthInterceptor.java:30](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/adapter/in/websocket/WebSocketAuthInterceptor.java:30); [WebSocketConfig.java:31](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/config/WebSocketConfig.java:31); [middleware.ts:9](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/demo-app-ui/middleware.ts:9). |

| Current status | Allowed targets |
| --- | --- |
| SUBMITTED | UNDER_REVIEW, REJECTED |
| UNDER_REVIEW | APPROVED, REJECTED, SUBMITTED |
| APPROVED | CLOSED |
| REJECTED | CLOSED |
| CLOSED | None |

Supporting source for every transition: [ClaimStatus.java:10](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/domain/claim/ClaimStatus.java:10). Reopening UNDER_REVIEW to SUBMITTED and rejecting SUBMITTED directly are confirmed code behavior; their intended business meaning remains subject to review.

### Potential defects and risk assessment — not runtime-confirmed

Priorities are recommendations for this cycle: **P0** = first security/data-integrity investigation; **P1** = critical workflow or integration coverage; **P2** = secondary contract/operational coverage. The “evidence” column records confirmed code facts; the “potential impact” column is an inference or unresolved requirement.

| ID / priority | Risk dimensions | Confirmed evidence | Potential impact and recommendation |
| --- | --- | --- | --- |
| R1 / P0 | Security, data integrity, business | Claims createUser and getUser have no role/ownership checks. createUser trusts request UUID and role; an existing email with a different UUID triggers updateUserId. The claims FK uses ON UPDATE CASCADE. Source: [UserController.java:49](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/adapter/in/web/UserController.java:49), [UserController.java:96](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/adapter/in/web/UserController.java:96), [SecurityConfig.java:61](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/config/SecurityConfig.java:61), [002-create-claims-table.yaml:60](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/resources/db/changelog/changes/002-create-claims-table.yaml:60). | A directly authenticated caller may provision arbitrary local identities/roles, read another user's profile, or rebind an existing identity and its claims. Validate service-account-only provisioning and legitimate rebinding separately from hostile attempts. Database role creation alone does not establish a Keycloak ADMIN authority. |
| R2 / P0 | Security, integration | Singleton ClaimsServiceAdapter mutates bearer state on an injected ApiClient before user/service calls; AdminController holds another shared client and switches between service-account and user tokens. Source: [ClaimsServiceAdapter.java:32](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/adapter/out/claimsservice/ClaimsServiceAdapter.java:32), [ClaimsServiceAdapter.java:212](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/adapter/out/claimsservice/ClaimsServiceAdapter.java:212), [RestClientConfig.java:28](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/config/RestClientConfig.java:28), [AdminController.java:25](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/adapter/in/web/AdminController.java:25), [AdminController.java:148](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/adapter/in/web/AdminController.java:148). | Concurrent calls may use another request's credentials if the generated client's authentication state is shared. Generated Java client implementation is absent, so token mixing is a hypothesis. Prioritize controlled concurrent callers and inspect generated authentication behavior when available. |
| R3 / P1 | Security | Both custom JWT decoders install only JwtTimestampValidator with 30-second skew; no issuer/audience validator appears in these beans, despite issuer-uri configuration. Nimbus still performs signature verification against configured keys. Source: [SecurityConfig.java:109](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/config/SecurityConfig.java:109), [SecurityConfig.java:79](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/config/SecurityConfig.java:79), [application.yml:32](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/resources/application.yml:32). | A token signed by an accepted key but carrying an unexpected issuer/audience may be accepted. Propose negative claim-validation checks without claiming unsigned or arbitrarily forged tokens will work. |
| R4 / P1 | Business, data integrity | Claim entity has no version, repository explicitly documents last-write-wins as a training-app choice, and update use case reads/validates/saves without a lock. Source: [ClaimEntity.java:28](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/adapter/out/persistence/ClaimEntity.java:28), [ClaimRepositoryAdapter.java:21](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/adapter/out/persistence/ClaimRepositoryAdapter.java:21), [UpdateClaimStatusUseCase.java:43](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/application/usecases/UpdateClaimStatusUseCase.java:43). | Two admins can make conflicting valid-from-the-same-state decisions; one may overwrite the other. UI failure rolls back the entire list, potentially replacing newer concurrent data ([use-admin-claims-store.ts:77](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/demo-app-ui/src/features/admin/stores/use-admin-claims-store.ts:77)). Review whether concurrency control is required; propose simultaneous APPROVED/REJECTED and overlapping UI update scenarios. |
| R5 / P1 | Data integrity, business | Double ↔ BigDecimal conversions, unrestricted fractional scale, and two-decimal storage coexist. Client/server trimming and date clocks also differ. Source: B1–B3; [ClaimsServiceAdapter.java:137](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/adapter/out/claimsservice/ClaimsServiceAdapter.java:137); [wizard-step-1.tsx:37](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/demo-app-ui/src/features/claims/components/wizard-step-1.tsx:37); [SubmitClaimRequest.ts:85](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/demo-app-ui/src/lib/api/generated/models/SubmitClaimRequest.ts:85). | More than two decimals may be rounded in persistence; response and reread amounts may differ. Whitespace may pass UI then fail domain validation; UTC/client/backend midnight boundaries may disagree. Review rounding/timezone rules and test persistence round trips with boundary inputs. |
| R6 / P1 | Business, integration | InvalidStatusTransitionException and database-role UnauthorizedException extend DomainException, but Claims advice only maps InvalidClaimException, UserNotFoundException, ClaimNotFoundException, and UnauthorizedClaimAccessException. BFF claimant adapter wraps downstream errors in IllegalStateException; admin adapter returns empty 400/403/404 bodies. Source: [ClaimExceptionHandler.java:21](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/adapter/in/web/ClaimExceptionHandler.java:21), [Claim.java:120](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/domain/claim/Claim.java:120), [UpdateClaimStatusUseCase.java:35](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/application/usecases/UpdateClaimStatusUseCase.java:35), [ClaimsServiceAdapter.java:147](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/adapter/out/claimsservice/ClaimsServiceAdapter.java:147), [ClaimsServiceAdapter.java:170](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/adapter/out/claimsservice/ClaimsServiceAdapter.java:170), [AdminController.java:134](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/adapter/in/web/AdminController.java:134). | Expected 400/403/404 may become generic server errors or lose ProblemDetail information; invalid status changes may miss the documented 400 path. Validate actual responses at each service and through the BFF. BFF validation sends an errors array ([AuthExceptionHandler.java:46](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/adapter/in/web/AuthExceptionHandler.java:46)), while wizard expects fieldErrors ([use-claim-wizard-store.ts:69](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/demo-app-ui/src/features/claims/stores/use-claim-wizard-store.ts:69)). |
| R7 / P1 | Business, integration, data integrity | Transactional listener publishes after commit; Kafka send future is not observed; no application outbox/replay store was found. Both consumer topics are always registered. CDC is enabled by the application default but disabled in Compose. Source: [DomainEventListener.java:18](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/adapter/out/messaging/DomainEventListener.java:18), [KafkaDomainEventPublisher.java:28](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/adapter/out/messaging/KafkaDomainEventPublisher.java:28), [ClaimEventKafkaConsumer.java:28](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/adapter/in/messaging/ClaimEventKafkaConsumer.java:28), [application.yml:81](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/resources/application.yml:81), [docker-compose.apps.yml:22](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/docker-compose.apps.yml:22). | A committed claim may have no UI event during publisher/process failure. Misconfigured CDC can suppress direct events; enabling both paths can duplicate them. Consumers/UI do not deduplicate events or reconcile on reconnect ([use-claim-realtime-handler.ts:37](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/demo-app-ui/src/features/claims/hooks/use-claim-realtime-handler.ts:37), [use-realtime-claim-updates.ts:69](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/demo-app-ui/src/features/claims/hooks/use-realtime-claim-updates.ts:69)). Propose recovery/replay/outage tests in each mode and review delivery guarantees. |
| R8 / P1 | Integration, data integrity | Publisher validates an envelope containing eventType/eventId/payload against a schema that requires event fields at the root; several required fields are omitted from the envelope entirely. Validation is disabled by application.yml. Source: [KafkaDomainEventPublisher.java:31](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/adapter/out/messaging/KafkaDomainEventPublisher.java:31), [KafkaDomainEventPublisher.java:42](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/adapter/out/messaging/KafkaDomainEventPublisher.java:42), [ClaimSubmittedMessage.json:6](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/resources/asyncapi/schemas/ClaimSubmittedMessage.json:6), [ClaimStatusChangedMessage.json:6](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/resources/asyncapi/schemas/ClaimStatusChangedMessage.json:6), [KafkaMessageSchemaValidator.java:38](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/adapter/out/messaging/KafkaMessageSchemaValidator.java:38), [application.yml:76](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/resources/application.yml:76). | Enabling schema validation should reject current direct events. This is a confirmed shape mismatch; its runtime failure has not been reproduced. Align and verify producer/consumer/schema contracts before relying on that toggle. |
| R9 / P1 | Security, business | AppHeader sends logout with raw fetch and no CSRF header, then clears local auth regardless of response.ok. Logout is not excluded from BFF CSRF. Cookies are set Secure=false; access-cookie lifetime is 5 minutes, while realm access-token lifespan is 86400 seconds. WebSocket roles/token are validated only at handshake. Source: [app-header.tsx:16](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/demo-app-ui/src/components/layout/app-header.tsx:16), [SecurityConfig.java:55](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/config/SecurityConfig.java:55), [AuthController.java:132](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/adapter/in/web/AuthController.java:132), [demo-app-realm.json:11](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/docker/compose/keycloak/demo-app-realm.json:11), [WebSocketAuthInterceptor.java:39](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/adapter/in/websocket/WebSocketAuthInterceptor.java:39), [ClaimWebSocketHandler.java:129](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/adapter/in/websocket/ClaimWebSocketHandler.java:129). | Logout may return 403 and leave server cookies/session usable while UI appears signed out. Existing WebSocket sessions may continue after expiry/revocation. Validate actual logout, refresh, reconnect, and role-removal behavior. HTTP cookie configuration is intentional demo setup; broader deployment requirements need confirmation. |
| R10 / P1 | Business, integration | Signup performs remote Keycloak creation/role assignment before Claims provisioning without compensation. Login retries provisioning after any lookup IllegalStateException. Local user roles are returned from the database; update authorization also reads DB roles. BFF defaults the Keycloak admin password differently from Compose bootstrap; the realm has no explicit service-account-user admin-role mapping. Source: [SignupUseCaseImpl.java:19](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/application/auth/SignupUseCaseImpl.java:19), [LoginUseCaseImpl.java:48](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/application/auth/LoginUseCaseImpl.java:48), [UpdateClaimStatusUseCase.java:35](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/application/usecases/UpdateClaimStatusUseCase.java:35), [application.yml:62](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/resources/application.yml:62), [docker-compose.infra.yml:90](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/docker-compose.infra.yml:90), [demo-app-realm.json:66](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/docker/compose/keycloak/demo-app-realm.json:66). | Partial signup may leave an identity without a local user; service outages may be mistaken for absent users; role changes may diverge between Keycloak/DB/UI. Default configuration may block signup or service-account admin reads. Verify effective overrides, service-account authorities, and partial-failure recovery before the cycle. |
| R11 / P1 | Business, data integrity | Dashboard TTL is 300 seconds; claim/user writes do not evict the cache, and bypass does not refresh it. UI status-change handling does not fetch dashboard stats; submission handling does. Source: [DashboardController.java:24](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/adapter/in/web/DashboardController.java:24), [CreateClaimUseCase.java:47](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/application/usecases/CreateClaimUseCase.java:47), [UpdateClaimStatusUseCase.java:49](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/application/usecases/UpdateClaimStatusUseCase.java:49), [DashboardStatsCache.java:67](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/adapter/out/cache/DashboardStatsCache.java:67), [use-claim-realtime-handler.ts:42](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/demo-app-ui/src/features/claims/hooks/use-claim-realtime-handler.ts:42). | Counts can be stale for the remaining TTL after claim/status/user changes; bypass followed by normal read can return older totals. Confirm acceptable staleness and propose cache/DB parity checks, including Redis failure and manual flush. |
| R12 / P2 | Security, integration, test confidence | Both services permit /actuator/** and expose '*'. Contract-test profiles and BFF schemathesis profile install permissive filter chains and static admin identities. Test-only CDC helper emits after.id without after.claim_id/user_id or before.status expected by the consumer. Source: [SecurityConfig.java:71](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/config/SecurityConfig.java:71), [SecurityConfig.java:62](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/config/SecurityConfig.java:62), [application.yml:28](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/resources/application.yml:28), [application.yml:39](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/resources/application.yml:39), [ContractTestSecurityConfiguration.java:38](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/config/ContractTestSecurityConfiguration.java:38), [ContractTestSecurityConfiguration.java:35](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/config/ContractTestSecurityConfiguration.java:35), [SchemathesisSecurityConfiguration.java:38](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/config/SchemathesisSecurityConfiguration.java:38), [TestEventController.java:60](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/adapter/in/web/TestEventController.java:60), [ClaimEventKafkaConsumer.java:114](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/adapter/in/messaging/ClaimEventKafkaConsumer.java:114). | Sensitive operational endpoints may be exposed depending on endpoint availability/configuration. Bypass-profile tests cannot establish real authentication coverage, and mock CDC may produce empty IDs or omit owner delivery. Verify profiles and actual exposed endpoints; compare test-helper events with real connector output. No active test profile is established by this review. |
| R13 / P2 | Integration, business | Claims OpenAPI advertises general PATCH /api/claims/{claimId} with description up to 2000; ClaimController has no PATCH method and does not implement the generated ClaimsApi. Unfiltered admin findAll has no explicit ordering/pagination, while filtered queries sort newest first. Source: [claims-api.yml:343](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/resources/openapi/claims-api.yml:343), [claims-api.yml:698](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/resources/openapi/claims-api.yml:698), [ClaimController.java:31](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/adapter/in/web/ClaimController.java:31), [ClaimRepositoryAdapter.java:53](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/adapter/out/persistence/ClaimRepositoryAdapter.java:53), [ClaimJpaRepository.java:12](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/adapter/out/persistence/ClaimJpaRepository.java:12). | API contract may advertise an unimplemented route; exact 404/405 behavior remains unverified. Unfiltered list order can differ from the documented use-case intent, and full lists can become expensive. UI sorts results locally. Review contract scope and add route/order/volume checks. |

Additional audit concern: ClaimStatusChanged declares changedBy as the admin in its schema, but Claim.updateStatus passes the claim owner's UUID, and the publisher uses it as routing userId. No status-history/actor/reason table appears in the supplied migrations. This routing works for owner notifications but does not preserve the administrator as changedBy. Review actor/owner separation and audit requirements; no regulatory retention requirement is assumed. Evidence: [ClaimStatusChanged.java:10](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/domain/claim/events/ClaimStatusChanged.java:10), [ClaimStatusChangedMessage.json:45](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/resources/asyncapi/schemas/ClaimStatusChangedMessage.json:45), [Claim.java:130](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/domain/claim/Claim.java:130), [KafkaDomainEventPublisher.java:53](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/adapter/out/messaging/KafkaDomainEventPublisher.java:53), [002-create-claims-table.yaml:6](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/resources/db/changelog/changes/002-create-claims-table.yaml:6).

### Existing automated tests and frameworks: confirmed inventory

| Area | Static review result | Evidence and implication |
| --- | --- | --- |
| Test implementations | No backend src/test directories, Java test implementations, frontend .test/.spec implementations, Vitest config, Playwright config, Pact artifacts, CI workflow, or usable coverage reports were found in the inspected tree. UI tests contains only .DS_Store. | Filesystem inventory of [claims-service/src](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src), [bff-service/src](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src), and [UI tests](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/demo-app-ui/tests), including hidden/ignored files outside build/dependency folders. This is limited to the supplied snapshot; coverage and pass/fail state are **unknown**, not measured zero. |
| README claims | README describes JUnit 5/Mockito/AssertJ and UI unit tests as provided, ArchUnit as provided, and a broken test as a starting point. Those implementations were not located. | [README.md:129](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/README.md:129), [README.md:157](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/README.md:157). Treat this as a documentation/snapshot discrepancy; no test-quality or broken-test claim can be confirmed. |
| Backend tooling | Spring Boot test modules; Testcontainers (Claims: PostgreSQL/Kafka/Keycloak; BFF: Kafka); ArchUnit; Pact provider in Claims and consumer in BFF; OpenAPI request validation library. | [pom.xml:179](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/pom.xml:179); [pom.xml:225](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/pom.xml:225); [pom.xml:263](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/pom.xml:263); [pom.xml:235](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/pom.xml:235); [pom.xml:291](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/pom.xml:291). Dependency presence does not establish test implementation or effective transitive versions. |
| Frontend tooling | Scripts declare Vitest run/watch/coverage. Dev dependencies include Vitest 4, Testing Library, user-event, jest-dom, jsdom, Faker, and Playwright 1.58.2. | [package.json:14](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/demo-app-ui/package.json:14), [package.json:29](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/demo-app-ui/package.json:29). No explicit @vitest/coverage-v8 or @vitest/coverage-istanbul package is declared; actual coverage command availability is unverified. |
| Execution selection | Both Surefire configs exclude integration-tagged tests by default; local Maven profiles set maven.test.skip=true. Startup script also skips tests, Checkstyle, and PMD. | [pom.xml:38](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/pom.xml:38), [pom.xml:384](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/pom.xml:384), [pom.xml:374](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/pom.xml:374), [pom.xml:598](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/pom.xml:598), [pom.xml:697](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/pom.xml:697), [start-app.sh:5](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/start-app.sh:5). Starting/building the app is not evidence that integration tests ran. |
| Quality gates and limitations | JaCoCo config declares 90% line/branch gates at verify; PIT declares 80% mutation/coverage thresholds, without a lifecycle execution in its plugin block. BFF coverage explicitly excludes SecurityConfig and WebSocketConfig. AsyncAPI/Postman/WireMock generation is skipped by default. | [pom.xml:462](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/pom.xml:462), [pom.xml:522](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/pom.xml:522), [pom.xml:577](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/pom.xml:577), [pom.xml:661](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/pom.xml:661), [pom.xml:42](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/pom.xml:42). No results were inspected or produced; high configured thresholds do not prove authorization or workflow coverage. |

### Recommended high-priority scenarios — proposed only

Unit tests isolate domain/pure logic with fakes or mocks. Component tests exercise an individual React component/store or Spring HTTP adapter with dependencies stubbed; security-filter component checks must load the actual filter chain. Integration tests exercise real dependency boundaries. These labels define proposed scope, not existing coverage.

| ID / priority / level | Proposed scenario and expected evidence | Source basis |
| --- | --- | --- |
| U1 / P1 / Unit | Boundary table for domain construction: null fields; yesterday/today/tomorrow; trimmed location lengths 4/5/200/201; trimmed description 9/10/1000/1001; blank/whitespace; amount negative/0/0.01/1,000,000/above maximum. Assert valid normalization, initial status/timestamps, exactly one creation event, and no valid object/event for rejected construction. Characterize 0.001 separately because current domain accepts it while API contract disallows it. | B1–B4; [Claim.java:72](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/domain/claim/Claim.java:72). |
| U2 / P1 / Unit | All 25 current/target status pairs: permit exactly seven transitions; reject all other pairs including self-transitions and any exit from CLOSED. Assert no state/time/event mutation on failure, and correct old/new status on success. Independently check UI transition parity. | [ClaimStatus.java:10](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/domain/claim/ClaimStatus.java:10), [Claim.java:120](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/domain/claim/Claim.java:120), [claim-status-transitions.ts:3](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/demo-app-ui/src/features/admin/utils/claim-status-transitions.ts:3). |
| U3 / P1 / Unit | Create with missing user; detail as owner/non-owner/missing claim; status update as missing user/CLAIMANT/ADMIN; every optional admin-filter combination. Rejected use cases must not save or publish. Verify direct-mode events and CDC-mode suppression. | [CreateClaimUseCase.java:34](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/application/usecases/CreateClaimUseCase.java:34), [GetClaimUseCase.java:31](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/application/usecases/GetClaimUseCase.java:31), [UpdateClaimStatusUseCase.java:35](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/application/usecases/UpdateClaimStatusUseCase.java:35); [ListAllClaimsUseCase.java:29](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/application/usecases/ListAllClaimsUseCase.java:29). |
| U4 / P1 / Unit | Auth validation and orchestration: password/name/email boundaries, duplicate identity, failure before/after Keycloak creation, lookup-not-found versus infrastructure error, and one service-token retry. Assert no password/token disclosure in returned errors and preserve the original failure classification. | [SignupCommand.java:14](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/application/auth/SignupCommand.java:14), [SignupUseCaseImpl.java:19](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/application/auth/SignupUseCaseImpl.java:19), [LoginUseCaseImpl.java:48](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/application/auth/LoginUseCaseImpl.java:48), [ClaimsServiceAdapter.java:73](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/adapter/out/claimsservice/ClaimsServiceAdapter.java:73). |
| U5 / P1 / Unit | Publisher-envelope/schema agreement; domain/CDC consumer parsing for create/update, absent fields, unknown event types, malformed JSON, duplicates, and old/new status. Assert owner/admin routing and separately expose owner-versus-actor mismatch. | [KafkaDomainEventPublisher.java:42](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/adapter/out/messaging/KafkaDomainEventPublisher.java:42), [ClaimSubmittedMessage.json:6](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/resources/asyncapi/schemas/ClaimSubmittedMessage.json:6), [ClaimEventKafkaConsumer.java:33](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/adapter/in/messaging/ClaimEventKafkaConsumer.java:33), [ClaimWebSocketHandler.java:92](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/adapter/in/websocket/ClaimWebSocketHandler.java:92). |
| C1 / P1 / Component | Wizard: invalid values block Next; back preserves fields; review matches actual serialized date/amount/text; confirm cancel resets, dismiss cancel preserves; in-flight submit disables controls; success resets/navigates; 400 field errors, 401 refresh failure, 5xx, and network errors leave clear retry behavior. Include whitespace and midnight timezone cases. | [wizard-step-1.tsx:20](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/demo-app-ui/src/features/claims/components/wizard-step-1.tsx:20), [wizard-step-3.tsx:27](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/demo-app-ui/src/features/claims/components/wizard-step-3.tsx:27), [use-claim-wizard-store.ts:52](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/demo-app-ui/src/features/claims/stores/use-claim-wizard-store.ts:52); [new/page.tsx:17](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/demo-app-ui/app/(authenticated)/claims/new/page.tsx:17). |
| C2 / P1 / Component | Admin status control: options follow current state; CLOSED has no update control; cancel sends no request; confirm sends one request; success uses server result; 400/403/404/5xx restores correct state. Interleave another claim's successful update while the first request fails to check whole-list rollback data loss. | [claim-status-select.tsx:21](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/demo-app-ui/src/features/admin/components/claim-status-select.tsx:21), [use-admin-claims-store.ts:77](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/demo-app-ui/src/features/admin/stores/use-admin-claims-store.ts:77). |
| C3 / P0 / Component | HTTP security matrix under normal configuration: anonymous/claimant/admin/service-account across claims, admin routes, user creation/profile, and CSRF writes. Include own/other subject UUIDs, invalid UUIDs/enums, null/missing bodies, valid and missing CSRF headers, and direct Claims requests. Assert rejection before database mutation. | B7–B8; R1, R3; [SecurityConfig.java:48](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/config/SecurityConfig.java:48), [SecurityConfig.java:55](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/config/SecurityConfig.java:55), [UserController.java:49](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/adapter/in/web/UserController.java:49). |
| C4 / P1 / Component | Error contracts: invoke invalid transition, DB role mismatch, missing claim, foreign owner, and invalid field through real HTTP adapters with use cases/downstream calls stubbed. Assert agreed 400/401/403/404, application/problem+json, and machine-readable fields consumed by UI. | R6; [ClaimExceptionHandler.java:21](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/adapter/in/web/ClaimExceptionHandler.java:21), [AuthExceptionHandler.java:46](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/adapter/in/web/AuthExceptionHandler.java:46), [AdminController.java:134](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/adapter/in/web/AdminController.java:134). |
| C5 / P1 / Component | Logout sends CSRF and checks server response; refresh concurrent 401s uses one refresh and retries once; failed refresh signs out visibly. WebSocket cleanup/retry exhaustion, late events, unauthorized recipients, and reconnect refresh should be checked. These are intended acceptance proposals where current code differs. | R9; [app-header.tsx:16](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/demo-app-ui/src/components/layout/app-header.tsx:16), [bff-client.ts:57](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/demo-app-ui/src/lib/api/bff-client.ts:57), [use-realtime-claim-updates.ts:48](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/demo-app-ui/src/features/claims/hooks/use-realtime-claim-updates.ts:48), [ClaimWebSocketHandler.java:74](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/adapter/in/websocket/ClaimWebSocketHandler.java:74). |
| I1 / P0 / Integration | Real Keycloak + Claims + PostgreSQL: legitimate service-account provisioning succeeds; claimant attempts to create/rebind another user or read protected profiles are rejected under the reviewed authorization policy. Existing claim owner UUIDs and roles remain unchanged after rejected attempts. Verify direct-service exposure separately from BFF access. | R1; [UserController.java:49](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/adapter/in/web/UserController.java:49), [002-create-claims-table.yaml:60](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/resources/db/changelog/changes/002-create-claims-table.yaml:60), [docker-compose.apps.yml:14](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/docker-compose.apps.yml:14). |
| I2 / P0 / Integration | Concurrent BFF requests for users A/B and admin/service-account calls. Capture downstream authenticated subject per request; every request must retain its initiating identity and expected privileges. Requires generated-client inspection before judging the race. | R2; [ClaimsServiceAdapter.java:212](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/adapter/out/claimsservice/ClaimsServiceAdapter.java:212), [AdminController.java:148](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/adapter/in/web/AdminController.java:148). |
| I3 / P1 / Integration | Submission → PostgreSQL → list/detail reread: verify JWT ownership, exact persisted date/text/amount, timestamps, unique claim ID, valid FK, user-ID migration, unique email, and transaction rollback on database failure. Retry the same logical submission after a lost response; document current duplicate behavior and obtain an idempotency requirement. | [CreateClaimUseCase.java:34](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/application/usecases/CreateClaimUseCase.java:34), [ClaimEntity.java:30](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/adapter/out/persistence/ClaimEntity.java:30), [002-create-claims-table.yaml:60](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/resources/db/changelog/changes/002-create-claims-table.yaml:60), [001-create-users-table.yaml:17](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/resources/db/changelog/changes/001-create-users-table.yaml:17), [use-claim-wizard-store.ts:52](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/demo-app-ui/src/features/claims/stores/use-claim-wizard-store.ts:52). |
| I4 / P1 / Integration | Two admins concurrently approve/reject the same UNDER_REVIEW claim; compare returned responses, final DB status, published events, and audit actor. Current code documents last-write-wins; explicit conflict rejection is a proposed requirement for review, not an established contract. | R4 and audit concern; [ClaimRepositoryAdapter.java:21](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/adapter/out/persistence/ClaimRepositoryAdapter.java:21), [UpdateClaimStatusUseCase.java:43](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/application/usecases/UpdateClaimStatusUseCase.java:43). |
| I5 / P1 / Integration | Direct Kafka: no event on DB rollback; event on commit; broker outage/process restart/replay; enabled schema-validation mode; malformed/duplicate/out-of-order messages; owner/admin-only delivery. Repeat with Debezium create/update/snapshot payloads and restart, then confirm only the intended event path is active. Test BFF replication because a shared consumer group and in-memory sessions may route an event to a node without the recipient's socket. | R7–R8; [DomainEventListener.java:18](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/adapter/out/messaging/DomainEventListener.java:18), [ClaimEventKafkaConsumer.java:28](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/adapter/in/messaging/ClaimEventKafkaConsumer.java:28), [ClaimWebSocketHandler.java:29](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/adapter/in/websocket/ClaimWebSocketHandler.java:29), [claims-connector.json:4](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/docker/compose/debezium/connector-configs/claims-connector.json:4). |
| I6 / P1 / Integration | JWT signature/expiry/not-before/skew and unexpected issuer/audience; claimant/admin/DB-role divergence; service-account permissions; cookie lifetime; refresh/logout/token reuse; active socket after logout/revocation. Verify normal deployment profiles do not install contract-test/schemathesis bypass chains. | R3, R9–R10, R12; [SecurityConfig.java:109](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/config/SecurityConfig.java:109), [SecurityConfig.java:79](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/config/SecurityConfig.java:79), [ContractTestSecurityConfiguration.java:38](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/config/ContractTestSecurityConfiguration.java:38). |
| I7 / P1 / Integration | Redis empty/hit/TTL expiry/bypass/flush/outage and after claim submission/status/user changes. Compare totals and each status bucket with PostgreSQL, including transitions and zero-count states; decide whether TTL staleness is acceptable. | R11; [DashboardController.java:35](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/adapter/in/web/DashboardController.java:35), [DashboardStatsCache.java:34](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/adapter/out/cache/DashboardStatsCache.java:34). |
| I8 / P1 / Integration | BFF↔Claims consumer/provider contracts for request/response, errors, enum/date/amount, user conflict responses, and advertised routes. Exercise both local default settings and Compose configuration: UI REST must reach BFF, WS origin must be accepted, service tokens must have required roles, and selected event mode must produce notifications. | [bff-api.yml:173](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/resources/openapi/bff-api.yml:173), [claims-api.yml:186](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/resources/openapi/claims-api.yml:186), [ClaimsServiceAdapter.java:94](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/adapter/out/claimsservice/ClaimsServiceAdapter.java:94), [bff-client.ts:15](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/demo-app-ui/src/lib/api/bff-client.ts:15), [docker-compose.apps.yml:59](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/docker-compose.apps.yml:59), R10/R13. |

### Reasoning, assumptions, and pending review

The priority order follows damage potential: identity and ownership failures can expose or move claims; conflicting decisions and monetary changes corrupt business records; event/caching failures make operators and claimants act on stale state; contract and configuration mismatches can prevent valid workflows or invalidate test conclusions. These are source-based risk judgments, not observed production incidents.

Use the existing declared tooling where suitable after implementation/execution is authorized: domain/use-case tests with the backend test stack, component coverage with Vitest/Testing Library and Spring HTTP/security adapters, and dependency integration coverage with Testcontainers plus Pact/OpenAPI validation. Keep real authorization checks under normal security configuration; static-admin contract profiles serve a narrower contract-testing purpose. The immediate recommendation is to resolve the test-source discrepancy and establish these critical invariants before relying on aggregate coverage percentages.

Pending business decisions: claimant-only submission versus any authenticated user; approved state transitions; currency and decimal rounding; business timezone; trusted user-rebinding policy; duplicate-submission behavior; concurrent-decision policy; event recovery guarantees; dashboard staleness; and actor/reason audit requirements. No answer to these questions was assumed to be an approved requirement.

**Decision remains: Pending Review.** No tests were implemented, modified, or executed. No production code, infrastructure, existing test file, other document, or Git history was changed.

## 2026-11-06 — Test Specification draft

- **Date:** 2026-11-06, Asia/Bangkok.
- **Task:** Create a source-aligned Test Specification for QA review and record the activity.
- **Decision: Pending Review**
- **Artifact:** [TEST_SPECIFICATION.md](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/TEST_SPECIFICATION.md), created at the workspace project root; application sources remain under demo-app/.
- **Activity boundary:** Documentation only. No application/test code was changed, no dependency/build/generation commands were run, and no services or application tests were executed. The earlier journal entry and its decision were preserved.

### Exact prompt used

The complete user request, including IDE context, is retained verbatim below.

```text
# Context from my IDE setup:

## Active file: demo-app/QA_AI_WORK_JOURNAL.md

## Open tabs:
- QA_AI_WORK_JOURNAL.md: demo-app/QA_AI_WORK_JOURNAL.md
- AI_WORKING_JOURNAL.md: demo-app/AI_WORKING_JOURNAL.md
- architecture-diagram.excalidraw: demo-app/architecture-diagram.excalidraw
- ARCHITECTURE.md: demo-app/ARCHITECTURE.md

## My request:
Based on the source code analysis and risk assessment we have completed, please create a **Test Specification** document for the Claims Management application.

Create a file named `TEST_SPECIFICATION.md` in the project root.

The document should include:

1. **Test Objectives & Scope** – What we are going to test and why.
2. **Test Coverage** – Component, Unit, and Integration testing.
3. **Test Scenarios** – Include Test ID, description, priority, preconditions, test steps, and expected results.
4. **Risk-Based Prioritization** – Focus on critical business flows, authorization, claim lifecycle, and data validation.
5. **Test Approach** – Recommended frameworks and how each scenario should be automated.
6. **Out of Scope** – Areas not covered in this testing cycle, with reasons.
7. **Assumptions & Dependencies** – Items requiring clarification.

Please make sure the test scenarios are aligned with the actual business rules and source code.

Use clear, professional English and keep the document practical for a QA team to review and use during test implementation.

If any expected behavior is unclear, mark it as **Pending Clarification** instead of making assumptions.

Also, update `QA_AI_WORK_JOURNAL.md` with this activity and keep the decision as Pending Review.

Do not modify application code or implement tests yet. Let me review the specification before proceeding.
```

### Summary of findings and recommendations

Created a specification containing the requested objectives/scope, test coverage, practical scenarios, risk-based priorities, automation approach, exclusions, and assumptions/dependencies. It contains **41 proposed scenarios: 12 unit, 10 component, and 19 integration**, each with a stable Test ID, description, priority, preconditions, numbered test steps, expected results, automation mapping, source references, and risk traceability. All scenarios are Not Run.

The document distinguishes code-backed characterization from documented API/schema expectations and unresolved requirements. It includes the complete **7 allowed / 18 rejected** lifecycle matrix, validation boundary data, an authorization matrix, normal/CDC environment prerequisites, and 33 source-reference groups. Known gaps are identified without rewriting them into approved acceptance criteria.

Risk priorities trace to the prior R1–R13 review. P0 coverage focuses on ownership, provisioning access, and concurrent credential isolation. P1 scenarios cover claim submission/lifecycle, monetary/date integrity, HTTP error contracts, authentication lifecycle, database rollback, events/CDC/WebSockets, and cache behavior. CM-IT-019 explicitly captures duplicate-submission behavior with its acceptance outcome Pending Clarification.

A register of **13 Pending Clarification items** makes outstanding decisions explicit: provisioning/JWT policy; lifecycle/concurrency; decimal rules; timezone/incident age; validation/cancel behavior; HTTP errors; logout/refresh/revocation; provisioning recovery/role synchronization; event contracts/recovery; duplicate submissions; audit actor/history; route/list/cache scope; and environment/topology/exposure.

Recommended implementation reuses the declared Spring/JUnit test stack, Vitest/Testing Library, Testcontainers, Pact/OpenAPI validation, and actual Kafka/CDC payloads after user review. Missing test sources/dependencies, generated Java clients, effective Keycloak permissions, and optional CDC readiness remain dependencies rather than completed work.

### Reasoning and supporting evidence

- Lifecycle tests use [ClaimStatus.java:10](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/domain/claim/ClaimStatus.java:10) and the matching UI transition table; no transition was invented.
- Validation datasets follow [Claim.java:72](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/domain/claim/Claim.java:72), [claim-schemas.ts:3](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/demo-app-ui/src/features/claims/schemas/claim-schemas.ts:3), and the API contracts. Domain/API minimum, decimal scale, trimming, and date-clock differences remain visible.
- Ownership/admin assertions follow [GetClaimUseCase.java:31](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/application/usecases/GetClaimUseCase.java:31), [UpdateClaimStatusUseCase.java:35](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/application/usecases/UpdateClaimStatusUseCase.java:35), and normal security configuration. Permissive provisioning in [UserController.java:49](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/adapter/in/web/UserController.java:49) is treated as a policy/risk investigation, without assuming an approved service-account-only requirement.
- API/schema cases preserve documented expectations even where adapters/handlers currently differ. Event scenarios separately verify transaction commit, producer/schema compatibility, actual CDC payloads, and owner/admin recipients.
- Every scenario identifies its concrete source groups in the specification's Source References section. The recommendations do not claim existing test coverage, passing results, business approval, or a reproduced production defect.

**Decision remains: Pending Review.** The specification is ready for user review. Implementation and execution have not started.

## 2026-11-06 — Initial Git baseline commit and staged-content review

- **Date:** 2026-11-06, Asia/Bangkok.
- **Task:** Review and stage the verified QA baseline, exclude unapproved credential-bearing files, create the explicitly requested initial commit, and record the actual result.
- **Decision: Pending Review**
- **Authorization:** The user authorized an initial commit of safe files and supplied the author name Kook and author email in follow-up answers. Identity was applied with per-command Git options; global/repository identity configuration was not changed.
- **Repository root:** /Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa
- **Existing .gitignore:** Reused unchanged. No production/application source was edited, no existing file was deleted or moved, and no tests were implemented or executed.

### Exact prompt used

```text
# Context from my IDE setup:

## Active file: .gitignore

## Open tabs:
- .gitignore: .gitignore
- QA_AI_WORK_JOURNAL.md: demo-app/QA_AI_WORK_JOURNAL.md
- ARCHITECTURE.md: demo-app/ARCHITECTURE.md
- docker-compose.apps.yml: demo-app/docker-compose.apps.yml
- docker-compose.cdc.yml: demo-app/docker-compose.cdc.yml

## My request:
Please prepare the initial Git commit for the current QA testing project.

Use the existing `.gitignore` and keep all application source files unchanged.

Before committing:

1. Verify that no local `.env` files, private keys, access tokens, or generated build outputs are staged.
2. Review the staged file list for potentially sensitive files.
3. Flag any embedded credentials in tracked configuration files without printing their values.
4. Do not include files with unverified sensitive credentials until I approve them.

If the staged files are safe, create the initial commit with the message:

`chore: initialize QA testing project baseline`

Then report the commit hash, number of committed files, and Git status.

Do not modify production code or implement tests.

Update `QA_AI_WORK_JOURNAL.md` with the actual activity and result.
```

### Actual review activity and evidence

1. Verified the repository root, empty initial index, unborn main branch, and existing ignore configuration. Initially 285 non-ignored files were candidates for the baseline.
2. Reviewed candidate/staged filenames and credential-related contexts without printing credential values. Read the staged blobs, not just working files, and checked that staged content matched the working files.
3. Checked for local .env files, private-key/credential-store paths, token/private-key patterns, dependency directories, compiler/build output, reports, logs, and caches. The ignored local environment file remained outside the index. No prohibited paths, recognized real access-token/private-key patterns, or copies of the held credential values remained in the final staged set.
4. Cross-checked copies of credential values and API password examples. This identified additional copies in a startup script and generated authentication/request documentation. Removed those documents from the index with git rm --cached while preserving every working file.
5. Held **13 files** for credential approval and staged **272 reviewed files**, including .gitignore, TEST_SPECIFICATION.md, the existing journal, application source, and repository-owned API clients. Generated TypeScript source clients are source assets, not generated build output.
6. Reviewed the complete staged list and compared staged/working content. No application source or ignore-file changes were made. Existing whitespace warnings were left unchanged; they were not credential findings.
7. Created exactly one initial commit using the requested message. No commit amendment, history rewrite, remote push, or additional commit was performed.

The keyword review distinguished configuration values and password examples from field names, validation rules, UI labels, dynamic environment references, and explicit synthetic test markers. This was a targeted local content review; it does not claim an external secret-scanner certification.

### Files withheld pending credential approval

These files remain present and unchanged on disk, **untracked and outside the commit**. Their credential values are intentionally not reproduced.

| File | Reason for withholding |
| --- | --- |
| [demo-app/README.md](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/README.md) | Demo login/admin credentials are printed in documentation. |
| [demo-app/code/bff-service/src/main/resources/application.yml](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/resources/application.yml) | Admin-password and client-secret defaults. |
| [demo-app/code/bff-service/src/main/resources/openapi/bff-api.yml](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/resources/openapi/bff-api.yml) | Unverified password examples in API requests/schema examples. |
| [demo-app/code/claims-service/src/main/resources/application.yml](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/resources/application.yml) | Database credential configuration. |
| [demo-app/docker-compose.cdc.yml](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/docker-compose.cdc.yml) | Embedded connector credential value. |
| [demo-app/docker-compose.infra.yml](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/docker-compose.infra.yml) | Database/replication and Keycloak bootstrap credential values. |
| [demo-app/docker/compose/debezium/connector-configs/claims-connector.json](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/docker/compose/debezium/connector-configs/claims-connector.json) | Connector database password. |
| [demo-app/docker/compose/keycloak/demo-app-realm.json](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/docker/compose/keycloak/demo-app-realm.json) | Client secrets and seeded user passwords. |
| [demo-app/docker/compose/postgres/init-scripts/02-debezium-user.sh](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/docker/compose/postgres/init-scripts/02-debezium-user.sh) | Database password provisioning/default handling requires credential review. |
| [demo-app/start-infra.sh](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/start-infra.sh) | Copy of a bootstrap credential in startup handling. |
| [demo-app/code/demo-app-ui/src/lib/api/generated/docs/AuthenticationApi.md](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/demo-app-ui/src/lib/api/generated/docs/AuthenticationApi.md) | Copies unverified password examples from the OpenAPI specification. |
| [demo-app/code/demo-app-ui/src/lib/api/generated/docs/LoginRequest.md](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/demo-app-ui/src/lib/api/generated/docs/LoginRequest.md) | Copies an unverified password example from the OpenAPI specification. |
| [demo-app/code/demo-app-ui/src/lib/api/generated/docs/SignupRequest.md](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/demo-app-ui/src/lib/api/generated/docs/SignupRequest.md) | Copies an unverified password example from the OpenAPI specification. |

Because configuration/specification/startup files were withheld, this is a verified partial baseline. Reproducing the entire application build/deployment still requires those files to be reviewed and approved or separately sanitized in authorized future work. No silent configuration substitutions or production edits were made.

### Actual commit result

- **Result:** Initial commit created successfully.
- **Commit hash:** `0b7e8c1378719467650bf5a0ba47f7de7904710f`
- **Commit message:** `chore: initialize QA testing project baseline`
- **Branch:** `main`
- **Committed files:** **272**
- **History:** Exactly one commit; no pre-existing commit was replaced.
- **Index after commit:** Empty; nothing staged.
- **Working tree immediately after commit:** 13 withheld files untracked; no tracked-file changes before this result entry.
- **Journal result entry:** Appended after commit so it can record the actual hash. This entry is intentionally **uncommitted and unstaged**; the initial commit was not amended merely to add its own result.
- **Expected final status from this activity:** One modified tracked file, demo-app/QA_AI_WORK_JOURNAL.md (this entry), plus the 13 untracked withheld files; ignored environment/build/dependency files remain ignored.

**Decision remains: Pending Review.** Unverified credential-bearing files still require the user's approval before inclusion.

## 2026-11-06 — Claim Status Transition Unit Tests (JUnit 5)

- **Date:** 2026-11-06, Asia/Bangkok.
- **Task:** Implement and execute only claim status transition unit tests from the existing Test Specification.
- **Decision: Pending Review**
- **Selected scenario:** **CM-UT-005 — Every claim lifecycle transition (P1, risk R4)**, Java portion. Source: [TEST_SPECIFICATION.md:149](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/TEST_SPECIFICATION.md:149).
- **Scope:** Domain transition policy and Claim.updateStatus business outcomes. No production source, Maven POM, application configuration, existing ignore rules, or test specification was changed. No files were staged or committed.

### Exact prompt used

```text
# Context from my IDE setup:

## Active file: .gitignore

## Open tabs:
- .gitignore: .gitignore
- QA_AI_WORK_JOURNAL.md: demo-app/QA_AI_WORK_JOURNAL.md
- ARCHITECTURE.md: demo-app/ARCHITECTURE.md
- docker-compose.apps.yml: demo-app/docker-compose.apps.yml
- docker-compose.cdc.yml: demo-app/docker-compose.cdc.yml

## My request:
Act as a Senior QA Automation Engineer.

We have completed the source code analysis and prepared the test specification.

Let's start implementing automated tests, focusing on the highest-priority business rules.

Please review `TEST_SPECIFICATION.md` and select the highest-priority Unit Test scenarios related to Claim Status Transitions.

Implement the tests using JUnit 5.

Requirements:

- Use parameterized tests where appropriate.
- Cover valid and invalid status transitions.
- Verify meaningful business outcomes and error handling.
- Keep tests independent from external services.
- Do not modify production code.
- Execute the tests and report actual results.
- Update `QA_AI_WORK_JOURNAL.md` with the implementation details and results.
- Keep the decision as Pending Review.

Only implement Claim Status Transition Unit Tests in this step.

Show the test files created and the execution results.
```

### Implementation details

Created two JUnit Jupiter test classes under the Claims Service's standard src/test/java tree:

| Test file | Parameterized cases | Assertions and purpose |
| --- | --- | --- |
| [ClaimStatusTest.java](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/test/java/com/example/demo/domain/claim/ClaimStatusTest.java) | 25 | Explicit independent specification matrix for all five current states against all five targets: 7 allowed and 18 rejected. Expected outcomes are not derived from production canTransitionTo. |
| [ClaimStatusTransitionTest.java](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/test/java/com/example/demo/domain/claim/ClaimStatusTransitionTest.java) | 34 | 7 successful updates, 18 rejected updates, 7 failures after a successful event is pending, and 2 complete review/approve-or-reject/close lifecycles. |

Both classes use @Tag("unit"), parameterized CSV/enum sources, and fresh in-memory aggregates per invocation. Claim.reconstitute supplies explicit original state/timestamps and an empty event queue, avoiding date-validation setup and submission-event noise. There is no Spring context, repository, Kafka broker, database, Keycloak, network client, or external-service fixture in the tests.

Successful updates assert target status, unchanged claim/owner/incident/text/amount/createdAt fields, updatedAt within the operation's time bounds, and exactly one ClaimStatusChanged with matching claim ID and old/new states, nonblank event ID, and an occurrence time within the operation. Rejected updates assert InvalidStatusTransitionException with current/target status context and unchanged status, update timestamp, business fields, and event queue.

Additional regressions verify that rejection does not erase or append to existing pending events; sequential transitions use the latest state and retain event order; CLOSED cannot be reopened. The fixtures and assertions do not depend on test execution order or sleeps.

### Framework and execution

- **Java:** Temurin OpenJDK 21.0.13.
- **Maven:** 3.10.0.
- **Actual JUnit version:** **Jupiter 5.11.4**, with Platform **1.11.4**, verified in both Surefire XML runtime classpaths.
- **Reason for command-line override:** The existing Spring Boot 4.0.2 BOM defaults to Jupiter 6.0.2. The user explicitly requested JUnit 5, so the run overrides junit-jupiter.version without changing the production POM or default project dependency configuration.
- **Service independence:** Only the two named domain test classes were selected. No application services or Docker environment were started.

Exact successful command, executed from the workspace root:

```sh
mvn -o -f demo-app/code/claims-service/pom.xml -Djunit-jupiter.version=5.11.4 -Dtest=ClaimStatusTest,ClaimStatusTransitionTest -Dcheckstyle.skip=true test
```

The first attempt was blocked by sandbox restrictions on Maven's local .m2 cache lock before any tests ran. After the required tool permission was granted, the same command succeeded using cached dependencies in offline mode. No dependency download was needed.

The standard Maven lifecycle compiled the module and generated configured OpenAPI build artifacts under the ignored target directory. AsyncAPI/Postman generation remained skipped by existing configuration. Checkstyle was explicitly skipped for this focused test run. PMD, PIT, the full test suite, and verify-phase coverage gates were not executed; no result for those checks is claimed.

### Actual execution results

Execution completed **2026-11-06 at 14:37:37 +07:00**, exit code **0**, with Maven **BUILD SUCCESS** and total build duration **10.628 seconds**.

| Suite | Tests run | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| ClaimStatusTest | 25 | 0 | 0 | 0 |
| ClaimStatusTransitionTest | 34 | 0 | 0 | 0 |
| **Total** | **59** | **0** | **0** | **0** |

Supporting execution artifacts (generated, ignored by Git):

- [ClaimStatusTest Surefire XML](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/target/surefire-reports/TEST-com.example.demo.domain.claim.ClaimStatusTest.xml).
- [ClaimStatusTransitionTest Surefire XML](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/target/surefire-reports/TEST-com.example.demo.domain.claim.ClaimStatusTransitionTest.xml).
- [JaCoCo XML](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/target/site/jacoco/jacoco.xml).

The JaCoCo report records **100% line and branch coverage for the two targeted methods**: ClaimStatus.canTransitionTo (6 lines, 19 branches) and Claim.updateStatus (10 lines, 2 branches), with zero missed lines/branches in those methods. This is not a claim of 100% class, service, application, or requirement coverage.

### Reasoning, evidence, and limitations

- The matrix comes from specification section 2.3 and [ClaimStatus.java:10](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/domain/claim/ClaimStatus.java:10). Aggregate outcomes follow [Claim.java:120](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/domain/claim/Claim.java:120).
- Exception and event assertions validate business effects rather than only checking a boolean. Pending-event regression cases protect earlier successful decisions from being lost during a subsequent rejection.
- CL-02 remains Pending Clarification for the business meaning of reopening UNDER_REVIEW to SUBMITTED and rejecting SUBMITTED directly. These tests characterize the explicitly specified/current matrix; they do not approve that policy.
- The known changedBy/owner ambiguity (CL-11) is not converted into an asserted administrator-identity rule in this step.
- TypeScript/UI parity, controller/API error mapping, admin authorization use cases (CM-UT-009), persistence, concurrent administrators, and integration delivery were not implemented or executed. The Java portion of CM-UT-005 passed; the entire cross-language scenario is not claimed complete.
- File-hash comparison before the journal update confirmed that no pre-existing workspace source/configuration/specification/ignore file changed; only the two new test classes were added outside ignored build output. The prior journal activities and all 13 previously withheld files were retained.

**Actual result: 59 tests passed. Decision remains: Pending Review.**

## 2026-11-06 — Claim Creation and Field Validation Unit Tests

**Decision: Pending Review**

Implemented only the Java creation/field-validation group from **CM-UT-001–004**:

- [ClaimCreationTest.java](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/test/java/com/example/demo/domain/claim/ClaimCreationTest.java): valid normalized data, initial SUBMITTED status, equal creation/update timestamps, one matching ClaimSubmitted event, safe event snapshots, event clearing without business-data changes, and independent event queues.
- [ClaimFieldValidationTest.java](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/test/java/com/example/demo/domain/claim/ClaimFieldValidationTest.java): six required/null fields; controlled past/today/future dates; location lengths 4/5/200/201; description lengths 9/10/1000/1001; spaces/tabs/newlines and trim-before-length behavior; negative/zero/minimum/maximum/above-maximum amounts; and explicit API/domain minimum comparison.

Both classes are isolated domain tests. Date cases use scoped Mockito static mocking of LocalDate.now() to fix backend today without changing production code or relying on execution time. No Spring context, database, network endpoint, or external service was used.

### Actual execution results

Executed **2026-11-06 at 14:52:21 +07:00**, using Java 21, Maven 3.10.0, and **JUnit Jupiter 5.11.4** (verified in the new Surefire XML classpaths). Total build duration: **8.220 seconds**. Maven exited **1 / BUILD FAILURE** because three assertions exposed the minimum-amount discrepancy.

| New suite | Passed | Failed | Errors | Skipped | Total |
| --- | ---: | ---: | ---: | ---: | ---: |
| ClaimCreationTest | 4 | 0 | 0 | 0 | 4 |
| ClaimFieldValidationTest | 52 | 3 | 0 | 0 | 55 |
| **New group total** | **56** | **3** | **0** | **0** | **59** |

The earlier 59 status-transition tests were **not rerun or modified**; their previous results are not included in this new-group total. No tests were disabled or skipped, and no failure-ignore option was used.

Exact rerun command from the workspace root:

```sh
mvn -o -f demo-app/code/claims-service/pom.xml -Djunit-jupiter.version=5.11.4 -Dtest=ClaimCreationTest,ClaimFieldValidationTest -Dcheckstyle.skip=true test
```

The version override retains JUnit 5 without changing the existing production POM's JUnit 6 default. The run used cached dependencies offline; Checkstyle was skipped. The failing Surefire phase prevented regeneration of the JaCoCo report, so no new coverage percentage is claimed.

Evidence: [ClaimCreationTest Surefire XML](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/target/surefire-reports/TEST-com.example.demo.domain.claim.ClaimCreationTest.xml), [ClaimFieldValidationTest Surefire XML](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/target/surefire-reports/TEST-com.example.demo.domain.claim.ClaimFieldValidationTest.xml).

### Significant finding — minimum amount inconsistency (CL-03 / R5)

**Potential defect; harmonized expected policy remains Pending Clarification.** Both API request contracts specify minimum **0.01**: [claims-api.yml:693](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/resources/openapi/claims-api.yml:693) and [bff-api.yml:653](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/resources/openapi/bff-api.yml:653). UI validation also starts at 0.01: [claim-schemas.ts:14](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/demo-app-ui/src/features/claims/schemas/claim-schemas.ts:14). The domain only rejects values ≤0 or >1,000,000: [Claim.java:90](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/domain/claim/Claim.java:90).

For each of **0.001, 0.009, and 0.009999**:

- The current-domain characterization test passes: construction succeeds and preserves the amount in both claim and submission event.
- The separately tagged, API-aligned test [apiMinimumExpectationRejectsPositiveAmountsBelowOneCent](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/test/java/com/example/demo/domain/claim/ClaimFieldValidationTest.java:164) fails: it expects InvalidClaimException, but no exception is thrown.

These three failures make the cross-layer inconsistency visible. They do **not** prove the HTTP API accepts those values; HTTP validation was not executed in this unit-only step. Whether to strengthen the domain minimum or revise the contract still requires review. Expectations, production logic, and the original passing tests were not changed to hide the discrepancy.

Other tested domain behavior matched the inspected source: trimming precedes length checks, internal spaces are retained, past/today dates succeed, future dates fail, zero/negative/above-maximum amounts fail, valid claims start SUBMITTED, and amounts with more than two decimals are retained without domain rounding. Persistence rounding and the TypeScript execution portion of CM-UT-004 remain outside this step.

No production/configuration/POM/specification files or existing test files were modified. Only the two new test classes and this journal entry were added/updated; generated reports remain under ignored target/. No files were staged or committed.

**Actual result: 56 Passed, 3 Failed, 0 Errors, 0 Skipped. Decision: Pending Review.**

## 2026-11-06 — Claim Form Validation Component Tests

**Decision: Pending Review**

Implemented only **CM-CT-001 — Wizard field validation and progression** ([specification:243](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/TEST_SPECIFICATION.md:243)), using the real claim wizard page, form components, React Hook Form/Zod validators, progress indicator, and Zustand store. Routing and the outbound claim API are mocked; each test verifies that no network request or claim submission occurs.

Created test-only files:

- [claim-form-validation.test.tsx](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/demo-app-ui/tests/components/claim-form-validation.test.tsx): reusable render/input/error/navigation helpers and parameterized user-visible cases.
- [vitest.config.mts](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/demo-app-ui/vitest.config.mts): jsdom environment, JSX transform, existing @ path alias, and test setup.
- [tests/setup.ts](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/demo-app-ui/tests/setup.ts): jest-dom matchers, DOM cleanup, and timer/global restoration.

### Actual execution results

Executed **2026-11-06 at 15:02:03 +07:00** with Vitest **4.0.18**, React Testing Library **16.3.2**, user-event **14.6.1**, React **19.2.3**, and jsdom **28.1.0**. The selected file completed in **6.32 seconds**, exit code **0**.

| Coverage area | Passed | Failed | Skipped |
| --- | ---: | ---: | ---: |
| Required fields and visible feedback | 5 | 0 | 0 |
| Past/today/future and malformed incident dates | 6 | 0 | 0 |
| Amount minimum, sub-cent, zero/negative, maximum and over-maximum | 11 | 0 | 0 |
| Location minimum feedback, correction, and input length cap | 3 | 0 | 0 |
| Description boundaries, feedback, and input length cap | 5 | 0 | 0 |
| **Total** | **30** | **0** | **0** |

Tests assert visible error text, aria-invalid/error associations, Next enabled/disabled state, and the displayed heading/progress step. Invalid input retains the current step after a Next attempt; corrected and valid boundary values reach Supporting Details or Review. Required-field feedback is checked after clearing a previously valid value, consistent with the forms' onChange validation mode.

Exact rerun command from the workspace root:

```sh
cd demo-app/code/demo-app-ui
npm test -- tests/components/claim-form-validation.test.tsx --reporter=default --reporter=json --outputFile=test-results/claim-form-validation.json
```

Evidence: [Vitest JSON report](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/demo-app-ui/test-results/claim-form-validation.json). The report is generated under the existing ignored test-results directory.

### Significant findings and blockers

- **No confirmed component failures in the selected 30 cases.** The UI rejects **0.001, 0.009, and 0.009999** with the minimum-amount message and blocked progression; **0.01** and **1,000,000** can progress. This confirms the UI side of the existing API/domain minimum inconsistency. The domain's earlier three contract-gap failures remain documented and unchanged; no backend tests were rerun or production validation changed.
- Text entry is capped at 200 location characters and 1000 description characters through the actual controls. Tests assert these user-visible caps instead of fabricating an overlong value that normal user typing/pasting cannot enter.
- Date is fixed for deterministic tests, without replacing asynchronous timers. jsdom has no native calendar picker, so date selection uses the input change event; malformed/calendar-invalid strings are sanitized to empty by the native date input and show the required-date message. Browser calendar interactions and cross-timezone policy CL-04 are not claimed covered.
- **Initial execution blocker resolved:** frontend dependencies and Vitest configuration were absent. The first npm ci attempt failed on sandbox registry/cache access. After tool permission, `npm ci --no-audit --no-fund` installed the existing locked dependencies successfully; no package.json/package-lock.json changes or dependency-version updates were made. No execution blocker remains for this group.
- Production files, existing Java tests, specification, and .gitignore were unchanged. Only the three test-infrastructure/test files, the ignored JSON report, and this journal entry were added/updated. No files were staged or committed.

**Actual result: 30 Passed, 0 Failed, 0 Skipped. Decision: Pending Review.**

## 2026-11-06 — Claim Submission and Status Management API Integration Test

**Decision: Pending Review**

### Actual prompt

```text
Act as a Senior QA Automation Engineer.

Unit and Component Tests are completed.

Next, implement one focused Integration Test for the Claim Submission and Status Management workflow, based on TEST_SPECIFICATION.md.

**Scope:**

1. Authenticate with valid test users.
2. Submit a new claim through the BFF API.
3. Verify claim ownership, submitted data, and initial status.
4. Retrieve the claim and verify persisted data.
5. Update the claim status using an Admin account.
6. Verify valid status transitions and persisted results.

**Requirements:**

- Use the existing Docker Compose environment.
- Use Playwright API testing or another suitable existing framework.
- Verify expected results against actual API contracts.
- Use isolated test data without modifying existing claims.
- Do not modify production code.
- Keep implementation small and maintainable.
- Run the Integration Tests and report Passed, Failed, and Skipped.
- If blocked, document the issue and available evidence without expanding scope.

**AI Working Journal:**\
Update QA_AI_WORK_JOURNAL.md with the actual prompt, implementation summary, execution results, significant findings, and supporting evidence.

Keep decisions as Pending Review.

Do not include routine Git operations.

Provide the test files created, execution results, exact rerun command, and any remaining blockers.
```

### Implementation and expected-result evidence

Created only these test files, using existing **Playwright 1.58.2** dependencies:

- [playwright.api.config.ts](code/demo-app-ui/playwright.api.config.ts): one API-only test, one worker, no retries, 60-second test timeout, default BFF URL http://localhost:8090 (QA_BFF_URL override), JSON reporting under ignored test-results/, and tracing disabled. No browser, web server, service startup, or dependency changes are required.
- [claim-submission-status.api.ts](code/demo-app-ui/tests/integration/claim-submission-status.api.ts): separate claimant/admin cookie contexts; login and role/identity checks; CSRF retrieval; unique claim creation; owner/data/status assertions; independent detail GETs; admin transitions **SUBMITTED → UNDER_REVIEW → APPROVED**; and claimant readback after each update. Creation time and business fields must stay unchanged, and update timestamps must not go backwards. Only this run's POST-returned, validated UUID can be updated.

This implements the requested API workflow portions of [CM-IT-001](../TEST_SPECIFICATION.md:365) and [CM-IT-003](../TEST_SPECIFICATION.md:389), rather than claiming every check in those broader scenarios. It verifies persistence through fresh API reads; no independent SQL query, event-delivery assertion, listing, invalid transition, or negative authorization test was added.

Expected HTTP results come from [bff-api.yml](code/bff-service/src/main/resources/openapi/bff-api.yml:173) (submission 201), its [detail route](code/bff-service/src/main/resources/openapi/bff-api.yml:252) (200), and [admin status route](code/bff-service/src/main/resources/openapi/bff-api.yml:472) (200). The selected transitions are allowed by [ClaimStatus.java](code/claims-service/src/main/java/com/example/demo/domain/claim/ClaimStatus.java:10). Cookie login is implemented in [AuthController.java](code/bff-service/src/main/java/com/example/demo/adapter/in/web/AuthController.java:73); CSRF token/header retrieval in [CsrfController.java](code/bff-service/src/main/java/com/example/demo/adapter/in/web/CsrfController.java:14). Admin access is enforced by BFF [SecurityConfig.java](code/bff-service/src/main/java/com/example/demo/config/SecurityConfig.java) and the Claims [UpdateClaimStatusUseCase.java](code/claims-service/src/main/java/com/example/demo/application/usecases/UpdateClaimStatusUseCase.java:35).

Credentials are read in memory from the existing local Compose Keycloak realm, or supplied using **QA_CLAIMANT_EMAIL / QA_CLAIMANT_PASSWORD** and **QA_ADMIN_EMAIL / QA_ADMIN_PASSWORD**. The local realm configuration remains an external prerequisite where it is excluded from the baseline. Values are never embedded in the new files or journal. Evidence records only action names, routes, HTTP statuses, unique test identifiers, verified statuses, and a CSRF-cookie-cleared boolean; no credentials, tokens, cookie values, storage-state files, or raw response bodies are saved.

### Actual execution results

Used the already-running Docker Compose BFF, Claims, Keycloak, PostgreSQL and supporting services. No containers were started, stopped, rebuilt, or reconfigured. The final selected run started **2026-11-06 at 15:18:38.857 +07:00**, completed in **2.079 seconds**, and passed. Playwright report counts are **1 Passed, 0 Failed, 0 Skipped**, with no retries.

| Final workflow checkpoint | Actual result |
| --- | --- |
| BFF readiness | 200 / UP |
| Claimant and admin login, separate identities and expected roles | 200 / verified for both |
| CSRF acquisition before writes | 200 |
| New claim submission with unique text and amount 1234.56 | 201 / correct owner, fields, SUBMITTED |
| Claimant detail read | 200 / submitted data and status matched |
| Admin update to UNDER_REVIEW, then claimant detail read | 200 / 200; fields and timestamps verified |
| Admin update to APPROVED, then claimant detail read | 200 / 200; fields and timestamps verified |

Evidence: [Playwright JSON report](code/demo-app-ui/test-results/claim-submission-status.report.json), including the sanitized claim-workflow-evidence attachment. Final run ID: **3130695b-744b-4d17-9ebb-979bdf43adef**. Created claim: **c907657b-9727-4da6-9e1c-b61b81e30aef**, last verified status **APPROVED**.

Exact rerun command from the workspace root:

```sh
cd demo-app/code/demo-app-ui
npx --no-install playwright test --config=playwright.api.config.ts
```

Prerequisites: existing locked UI dependencies installed, the current Docker environment reachable, and valid claimant/admin accounts with matching application and Keycloak roles. Each run creates a fresh claim and retains it for review; no cleanup deletion or modification of pre-existing claims is performed.

### Significant findings and execution limitations

- **Initial execution restrictions resolved:** the sandbox blocked the localhost request; execution was repeated with tool permission. A subsequent test-harness assertion rejected the valid Actuator application/vnd.spring-boot.actuator.v3+json media type before any claim submission. The helper was corrected to accept JSON and structured +json media types; API expectations were not relaxed.
- **Earlier workflow failure retained as evidence:** the run at **15:14:40.117 +07:00** authenticated both users, submitted a new claim (201), retrieved SUBMITTED (200), and persisted/read UNDER_REVIEW (200). Reusing an admin CSRF token for the next valid APPROVED update returned **403**, expected **200**: that run had **0 Passed, 1 Failed, 0 Skipped**. Its run ID was **54d99857-31c4-4743-b2ac-9744470d55ad** and claim ID **a8dbc276-1e4c-447d-9498-3d8cebe28183**, last verified status **UNDER_REVIEW**. This record preserves the significant earlier result because the latest report replaces the previous report.
- The final client fetches the current CSRF token before each admin mutation. The same unchanged 200/persistence assertions then passed for a new claim. Existing service logs did not establish the earlier 403's cause, and the final responses did not report CSRF-cookie deletion. **A production defect or precise CSRF invalidation mechanism is not confirmed.** Repeated token reuse and broader security diagnostics remain outside this single-workflow scope.
- **No remaining execution blocker for this workflow.** These passing results do not resolve the previously documented sub-cent amount inconsistency; that potential defect remains unchanged. This test uses a valid two-decimal amount and does not retest unit/component groups.
- Production files, existing tests, dependency manifests, Docker configuration and the specification were unchanged. Only the two new test files, generated ignored execution artifacts, and this journal entry were added/updated.

**Final selected-run result: 1 Passed, 0 Failed, 0 Skipped. Decision: Pending Review.**

## 2026-11-06 — Main Docker Configuration Reproducibility

**Decision: Pending Review**

### Significant changes and evidence

- Added the previously excluded [BFF settings](code/bff-service/src/main/resources/application.yml), [Claims settings](code/claims-service/src/main/resources/application.yml), [BFF OpenAPI contract](code/bff-service/src/main/resources/openapi/bff-api.yml), and [main infrastructure Compose file](docker-compose.infra.yml). Credential values in runtime settings now come from environment variables. The contract changes only four demonstration password examples; API schemas, validation constraints, routes and expected responses are preserved. Both services regenerate their API code and compile successfully from these versioned contracts.
- Added [.env.example](.env.example), a sanitized [realm template](config/demo-app-realm.example.json), and [configure-local.py](configure-local.py). Fresh setup generates random local passwords and a matching BFF client secret, with synthetic claimant/admin identities and stable fixture UUIDs. Realized credentials live only in ignored .env and docker/compose/keycloak/demo-app-realm.json. The helper uses exclusive file creation, preserves existing files, and refuses to generate incompatible new credentials when only an existing realm is present.
- The generated .env has mode 0600. The generated realm directory is 0700 and its JSON file is 0644, allowing the read-only bind mount to be read by Keycloak UID 1000 across host UIDs while preventing other host users from traversing the private directory. An isolated, network-disabled container using the existing Keycloak image confirmed UID 1000 can read the generated file, without printing its contents.
- [Application Compose](docker-compose.apps.yml), [start-app.sh](start-app.sh) and [start-infra.sh](start-infra.sh) pass the required local settings explicitly. The infrastructure helper no longer prints a password. Existing local credential values were preserved in a new ignored .env; the pre-existing realm's bytes are unchanged. Legacy BFF and bootstrap-admin password defaults differ: the local-only KEYCLOAK_BOOTSTRAP_ADMIN_PASSWORD override preserves that existing difference. Fresh setups share the generated KEYCLOAK_ADMIN_PASSWORD at both boundaries; no local account was rotated or reconfigured.
- Added a precise local-realm exclusion to [.gitignore](../.gitignore), and a UI [.dockerignore](code/demo-app-ui/.dockerignore) to keep local credentials, dependencies and reports out of the build context. No usable secret is included in the new examples or template. Review of staged files found no private-key/token material or embedded local credential assignments; the deliberately nonfunctional OpenAPI examples are marked example-only.
- Deferred CDC: main PostgreSQL startup no longer mounts CDC init scripts or requires a Debezium password, and application Compose retains APP_EVENTS_CDC_ENABLED=false. Liquibase supplies the application schema; BFF login provisions the two application users using the service account. Optional CDC Compose, connector and replication-user script remain excluded; they are not prerequisites for the implemented workflow test.
- Added [QA_SETUP.md](QA_SETUP.md) with first setup, startup, readiness checks and exact commands for the existing tests. Application packaging explicitly skips tests/static analysis, as before; this is not a claim that the known failures or those checks pass. No business logic, test file, test expectation, dependency manifest or lockfile changed.

### Verification results

Verification used a clean export of the staged repository, with no pre-existing .env, realm, node_modules, target output or excluded local configuration. Clean builds and tests completed at **15:38–15:39 +07:00 on 2026-11-06**, followed by final configuration checks. Source build/test outputs were confined to temporary checkout directories, not the running application's build context.

| Check | Actual result |
| --- | --- |
| Fresh helper generation and second invocation | Passed; coherent credentials/realm generated, second invocation preserved bytes |
| Generated file/directory permissions and non-root Keycloak readability | Passed; .env 0600, realm directory 0700, realm file 0644; isolated UID 1000 read check exit 0 |
| Combined main Compose resolution | Passed; all bind sources/build contexts present, database/BFF/Keycloak credential wiring consistent, CDC disabled |
| Startup shell syntax | Passed: bash -n start-app.sh start-infra.sh |
| Claims clean-source packaging | BUILD SUCCESS, exit 0, 11.844 seconds |
| BFF clean-source packaging and API generation | BUILD SUCCESS, exit 0, 12.094 seconds |
| Frontend locked dependency installation | npm ci exit 0; 471 packages installed in 15 seconds; no lockfile change |
| Existing four unit classes, JUnit 5.11.4 | **115 Passed, 3 Failed, 0 Errors, 0 Skipped**; exit 1, 9.327 seconds |
| Existing component file, Vitest 4.0.18 | **30 Passed, 0 Failed, 0 Skipped**; exit 0, 7.13 seconds |
| Playwright API discovery | --list exit 0, exactly one workflow test discovered; no API test execution in this verification |
| Existing environment preservation | Same nine container IDs; Claims and BFF health endpoints both 200 / UP; no restart, rebuild or main-container configuration change |

The three unit failures are exactly apiMinimumExpectationRejectsPositiveAmountsBelowOneCent for **0.001, 0.009 and 0.009999**. They remain active evidence of the API minimum 0.01 versus domain >0 discrepancy. All other 115 unit cases passed. No failure-ignore option, exclusion or expectation change was used.

Commands used in the clean export:

```sh
# From its demo-app directory:
python3 configure-local.py
python3 configure-local.py
docker compose --env-file .env -f docker-compose.infra.yml -f docker-compose.apps.yml config --quiet
bash -n start-app.sh start-infra.sh

# Run in each service directory (claims-service and bff-service):
mvn -o package -Dmaven.test.skip=true -Dcheckstyle.skip -Dpmd.skip

# Run in code/claims-service:
mvn -o test -Djunit-jupiter.version=5.11.4 -Dtest=ClaimStatusTest,ClaimStatusTransitionTest,ClaimCreationTest,ClaimFieldValidationTest -Dcheckstyle.skip=true

# Run in code/demo-app-ui:
npm ci --no-audit --no-fund
npm test -- tests/components/claim-form-validation.test.tsx
npx --no-install playwright test --config=playwright.api.config.ts --list
```

The clean Java builds used the existing Maven cache offline. The delivery guide omits -o for a first setup so dependencies can be downloaded. Surefire XML in the temporary clean export confirmed the four suite totals: ClaimCreationTest 4/0, ClaimFieldValidationTest 52/3, ClaimStatusTest 25/0, ClaimStatusTransitionTest 34/0 (passed/failed). Generated reports and temporary credentials are not delivery source files.

### Remaining limitations and review decisions

- **Full fresh Docker startup and fresh-realm authentication/integration are not verified.** The main Compose files use fixed container names, network name and host ports already occupied by the preserved local stack. No full-stack up/down, image rebuild or new API claim was attempted during this configuration task. A separate Docker host/context, or a later explicitly agreed maintenance window, is needed to execute the startup and workflow commands in QA_SETUP.md without disturbing the existing environment. Static resolution, clean JAR builds and a realm-file readability check do not prove successful Keycloak realm import, complete container readiness or API workflow behavior with generated accounts.
- Docker image construction/UI startup and uncached first-time image/dependency downloads were not verified. Existing-environment health confirms preservation, not fresh-stack startup. The earlier passing integration run remains historical evidence for the existing environment only.
- The three amount-validation failures remain unresolved by design. Optional CDC setup remains deferred. Legacy README and generated authentication documentation containing credential examples are not required by the new setup guide and remain excluded.

**Decision: Pending Review.** Configuration/build checks and the recorded test reproduction are confirmed; complete fresh-stack runtime verification remains outstanding.
