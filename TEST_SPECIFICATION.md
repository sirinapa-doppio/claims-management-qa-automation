# Claims Management — Test Specification

**Status:** Pending Review  
**Prepared:** 2026-11-06 (Asia/Bangkok)  
**Baseline:** Supplied source snapshot and the [QA source-review journal](demo-app/QA_AI_WORK_JOURNAL.md).  
**Document location:** Workspace project root; application source is under `demo-app/`.  
**Execution state:** Specification only. No application code/test implementation, build, service startup, or test execution was performed.

## 1. Test Objectives & Scope

Protect claim ownership, valid claim submission, permitted status decisions, and persisted business data. Verify that API contracts, identity provisioning, database transactions, events, WebSockets, and dashboard caching agree at their boundaries. Investigate the source-review risks without presenting an unverified hypothesis as a reproduced defect.

This cycle specifies unit, component, and integration coverage for:

- Claim construction, field validation, normalization, persistence, reads, filters, and lifecycle transitions.
- JWT subject ownership, ADMIN controls, database role checks, cookie authentication, CSRF, signup/login/refresh/logout, and service-account forwarding.
- UI wizard behavior, admin confirmation/optimistic updates, error presentation, and real-time state handling.
- PostgreSQL constraints/transactions, BFF↔Claims contracts, Keycloak integration, direct Kafka, optional Debezium CDC, WebSocket recipient isolation, and Redis statistics.
- Focused concurrency/failure checks where they affect business correctness. Load benchmarking is outside this cycle.

### How to interpret expected results

| Label | Meaning for implementation and review |
| --- | --- |
| **Code-backed** | Explicit behavior in the inspected implementation. It is a characterization baseline, not a statement that a known weakness has business approval. |
| **Contract-backed** | Behavior declared by the operation's OpenAPI/schema. A conflicting implementation is a defect candidate; do not relax assertions merely to make the current code pass. |
| **Pending Clarification** | An authoritative expected outcome is missing or conflicting. Record observations and the CL item; do not mark the unresolved assertion passed or invent a status code, retry limit, SLA, policy, or rounding rule. |

A scenario may contain settled assertions and a separate Pending Clarification outcome. Implement settled assertions independently after review; defer the disputed assertion until its CL item is resolved. Existing code findings and risks remain **Pending Review**. All scenario execution results are currently **Not Run**.

## 2. Test Coverage

### 2.1 Layers and boundaries

| Level | Scenario IDs | Boundary and purpose |
| --- | --- | --- |
| Unit | CM-UT-001–012 (12 scenarios) | Pure Java domain/use cases/consumer parsing and TypeScript schemas/utilities. Mock repositories, ports, metrics, and publication; no external services. |
| Component | CM-CT-001–010 (10 scenarios) | React component/store interactions or one Spring HTTP/security boundary with dependencies stubbed. Exercise the actual filter chain for security cases. |
| Integration | CM-IT-001–019 (19 scenarios) | Real PostgreSQL, Keycloak, Kafka/WebSocket, Redis, and optional Debezium boundaries, plus BFF↔Claims contract verification. |

There are **41 proposed scenarios**. Parameterized data rows, identities, transition pairs, and event modes produce multiple executable cases under each stable ID. The source snapshot did not contain test implementation files despite README claims; framework declarations are not proof of existing coverage. See S29 and the journal's test inventory.

### 2.2 Confirmed rule baseline

| Rule | Current source behavior | Source |
| --- | --- | --- |
| Required data | Claim/user UUID, incident date, location, description, and amount are non-null in domain construction. HTTP creation takes owner from JWT `sub`, not from form data. | S01, S03 |
| Incident date | Backend rejects dates after its own `LocalDate.now()`; no lower date bound is coded. UI compares a Date to its current time. Timezone/age policy: **Pending Clarification — CL-04**. | S01, S14 |
| Location | Backend trims and accepts 5–200 characters. UI validates raw length 5–200. | S01, S14 |
| Description | Backend trims and accepts 10–1000 characters. UI validates raw length 10–1000. Summaries truncate to 100 characters. | S01, S14, S31 |
| Amount | Domain: >0 and ≤1,000,000. UI/OpenAPI: 0.01–1,000,000 USD. API uses double; DB uses DECIMAL(12,2); no explicit domain/UI decimal-scale rule. Precision policy: **Pending Clarification — CL-03**. | S01, S14, S22, S23 |
| Initialization | SUBMITTED; createdAt = updatedAt; one ClaimSubmitted event. | S01 |
| Reads | Ordinary list/detail use JWT owner; detail has no administrator ownership bypass. | S04 |
| Admin decisions | JWT ADMIN at the HTTP layer; local database ADMIN is additionally required to update status. | S05, S07 |
| Submission role | Ordinary claim API requires authentication but no CLAIMANT-only annotation. Intended role policy: **Pending Clarification — CL-01**. | S03, S07 |
| Signup | Default role CLAIMANT; password at least 8 characters with uppercase and digit. UI name limit 1–255, API 2–100, user domain nonblank up to 255. Unified name rule: **Pending Clarification — CL-05**. | S10, S11, S32 |
| Events/cache | Direct publishing and optional CDC are separate paths; submitted events target admins, status changes target owner plus admins. Dashboard TTL is 300 seconds; bypass does not refresh cache. | S17–S21, S25 |

### 2.3 Claim lifecycle matrix

Every cell is covered by CM-UT-005. A fresh starting aggregate is required per pair.

| From / To | SUBMITTED | UNDER_REVIEW | APPROVED | REJECTED | CLOSED |
| --- | --- | --- | --- | --- | --- |
| SUBMITTED | Reject | Allow | Reject | Allow | Reject |
| UNDER_REVIEW | Allow | Reject | Allow | Allow | Reject |
| APPROVED | Reject | Reject | Reject | Reject | Allow |
| REJECTED | Reject | Reject | Reject | Reject | Allow |
| CLOSED | Reject | Reject | Reject | Reject | Reject |

Source: S02. Exactly **7 allowed / 18 rejected** pairs. Whether the business wants UNDER_REVIEW→SUBMITTED or direct SUBMITTED→REJECTED remains **Pending Clarification — CL-02**; until revised, characterize the explicit current matrix.

### 2.4 Authorization coverage matrix

| Operation | Anonymous | Authenticated claimant | JWT ADMIN | Additional constraint / open policy |
| --- | --- | --- | --- | --- |
| Ordinary claim list/detail | Denied | Own data | Own data on ordinary detail path | Owner from JWT subject; missing claim handled separately. |
| Submit claim | Denied | Reaches business validation | No role restriction currently | Local user must exist; intended claimant-only policy CL-01. |
| Admin claim list/dashboard/user list/cache flush | Denied | Denied | Reaches handler | Some BFF reads use a service-account token downstream; verify its ADMIN authority. |
| Admin status update | Denied | Denied | Reaches business validation | Database user must also have ADMIN role. |
| Claims user creation/profile read | Denied | No explicit role/owner guard currently | No additional method restriction currently | Authorized callers, profile visibility, and identity rebinding CL-01. |
| WebSocket claim channel | Missing token rejected at handshake | Own status notifications | Admin notifications | Valid JWT and allowed Origin; established-socket revocation CL-07. |

Source: S04, S05, S07–S09, S19. Test unauthenticated API access with GET to isolate authentication; an unauthenticated BFF write may fail CSRF before authentication. Do not assert 401 for every missing-token write regardless of the filter order.

## 3. Test Scenarios

### 3.1 Shared data and preconditions

| Fixture | Definition |
| --- | --- |
| D1 — valid claim | Incident date = backend date T−1; location = `12 Example Road`; description = `Water damage occurred near the kitchen sink.`; amount = 1234.56. Owner A is supplied through JWT, not the request body. |
| D2 — text/required boundaries | ASCII location lengths 0/4/5/200/201; description 0/9/10/1000/1001; null, omitted HTTP field, spaces only, padded valid text, and padded text whose trimmed length falls below minimum. Each mutation uses otherwise valid data. |
| D3 — amounts | −0.01, 0, 0.001, 0.01, 1.00, 1.005, 999999.99, 999999.999, 1000000, 1000000.01. HTTP null/omitted/string/invalid numeric forms are separate validation cases. Extra decimal places are not automatically assumed invalid. |
| D4 — identities/signup | A/B are distinct claimants; M/M1/M2 have both JWT ADMIN and local ADMIN; mismatch fixtures have JWT ADMIN with local CLAIMANT/missing user. Use unique `qa-cycle+<run>@example.test`, a valid name, and disposable password `QaCycle1!`. Never embed live credentials. |
| D5 — events | Use exact producer envelopes and actual Debezium payloads. Domain topic `claim-events`; CDC topic `cdc.demo-app.public.claims`. Seed explicit claim/owner UUIDs, statuses, timestamps, event ID where present, and known consumer offsets. |
| E1 — normal integration environment | UI/BFF/Claims plus isolated PostgreSQL, Keycloak, Kafka, Redis; application migrations applied. Normal security is active; neither `contract-test` nor `schemathesis` supplies evidence of real authorization. |
| E2 — CDC extension | E1 plus real Debezium connector/publication/slot; CDC enabled in Claims; direct publishing disabled. Connector readiness precedes observing database changes. |

Each parameterized row starts from a fresh aggregate/transaction or uniquely named fixture. Acquire valid BFF CSRF cookie/header for authenticated writes unless CSRF rejection is the purpose. Subscribe/readiness-check event observers before triggering a write; filter observations by test claim ID. Use SQL/Kafka evidence alongside HTTP to verify absence of side effects. Reset component stores, timers, sockets, cookies, and mocked module state between cases.

### 3.2 Unit scenarios

### CM-UT-001 — Construct a valid claim and initial event

- **Priority:** P1
- **Expected-result basis:** Code-backed.
- **Preconditions:** Valid owner and claim UUIDs; backend date controlled away from midnight; baseline data D1.
- **Test steps:**
  1. Construct a claim using D1 and padded valid location/description.
  2. Read all fields, timestamps, and domain events; then clear events.
- **Expected results:** Owner/claim IDs and incident date/amount are retained; text is trimmed; status is SUBMITTED; createdAt equals updatedAt and falls within the call interval; one ClaimSubmitted has matching claim/owner/date/amount. clearEvents removes events without changing claim data.
- **Automation:** JAVA-UNIT. **Sources:** S01. **Risk trace:** R5.

### CM-UT-002 — Required values and trimmed text boundaries

- **Priority:** P1
- **Expected-result basis:** Code-backed.
- **Preconditions:** A fresh valid constructor input for each D2 row.
- **Test steps:**
  1. Set each required constructor argument to null independently.
  2. For location and description, apply every D2 boundary and whitespace value while other fields stay valid.
- **Expected results:** Null claimId/userId/date/location/description/amount raises NullPointerException. Trimmed location lengths 5 and 200 and description lengths 10 and 1000 succeed; adjacent invalid lengths and whitespace-only strings raise InvalidClaimException. No invalid aggregate is returned.
- **Automation:** JAVA-UNIT. **Sources:** S01. **Risk trace:** R5.

### CM-UT-003 — Incident-date rules

- **Priority:** P1
- **Expected-result basis:** Code-backed; timezone policy Pending Clarification.
- **Preconditions:** Controlled backend local date T; valid other inputs.
- **Test steps:**
  1. Construct separate claims for T−1 day, T, and T+1 day.
  2. Repeat with a much older valid LocalDate.
  3. Reserve cross-timezone midnight cases for CM-IT-006.
- **Expected results:** T−1 and T succeed; T+1 raises InvalidClaimException. There is no coded lower date limit, so the older valid date succeeds. The business timezone and any maximum incident age are Pending Clarification (CL-04).
- **Automation:** JAVA-UNIT. **Sources:** S01, S14. **Risk trace:** R5. **Open items:** CL-04.

### CM-UT-004 — Amount boundaries at domain and UI schema layers

- **Priority:** P1
- **Expected-result basis:** Code-backed; precision policy Pending Clarification.
- **Preconditions:** Fresh valid domain inputs and the pure Zod step1 schema; amount dataset D3.
- **Test steps:**
  1. Construct domain claims for every D3 amount.
  2. Evaluate the same finite numeric amounts against step1Schema.
- **Expected results:** Domain accepts positive values through 1,000,000, including 0.001; it rejects zero, negatives, and values above the maximum. UI schema rejects amounts below 0.01 and above 1,000,000. Neither layer rejects 1.005 solely for having three decimal places. Harmonized minimum/rounding policy is Pending Clarification (CL-03).
- **Automation:** JAVA-UNIT + TS-UNIT. **Sources:** S01, S14, S23. **Risk trace:** R5. **Open items:** CL-03.

### CM-UT-005 — Every claim lifecycle transition

- **Priority:** P1
- **Expected-result basis:** Code-backed.
- **Preconditions:** An independently reconstituted claim for each current state; no pre-existing domain events.
- **Test steps:**
  1. Parameterize all 25 current/target pairs using the transition matrix in section 2.
  2. Invoke updateStatus once for each pair; compare with the UI transition utility.
- **Expected results:** Exactly seven pairs succeed. Success changes status, retains createdAt, sets updatedAt within the operation interval, and appends one event with matching old/new states. The other 18 pairs, including self-transitions and all transitions from CLOSED, raise InvalidStatusTransitionException and leave state/timestamps/events unchanged. UI choices match the matrix.
- **Automation:** JAVA-UNIT + TS-UNIT. **Sources:** S01, S02. **Risk trace:** R4.

### CM-UT-006 — Persistence reconstruction does not create new business events

- **Priority:** P1
- **Expected-result basis:** Code-backed.
- **Preconditions:** A complete persisted record with known IDs, status, decimal amount, and timestamps.
- **Test steps:**
  1. Reconstitute the aggregate and map an equivalent persistence entity to domain.
  2. Inspect all fields and event count without performing an update.
- **Expected results:** Persisted values and timestamps are preserved; no creation event is generated. Reconstitution bypasses construction validation by design; it is a persistence boundary, not a user-input validation path.
- **Automation:** JAVA-UNIT. **Sources:** S01, S23. **Risk trace:** R4, R7.

### CM-UT-007 — Create-claim user check and event-mode selection

- **Priority:** P1
- **Expected-result basis:** Code-backed.
- **Preconditions:** Mock user/claim repositories, event publisher, and metrics; valid command.
- **Test steps:**
  1. Execute with an existing user in direct mode, then repeat in CDC mode.
  2. Execute with the user missing.
  3. Inspect saves and application-event publications.
- **Expected results:** Existing user: claim is saved and returned; direct mode publishes its creation event, CDC mode publishes no application event. Missing user: UserNotFoundException occurs before claim save or event publication. These unit checks do not prove transaction commit or Kafka delivery.
- **Automation:** JAVA-UNIT. **Sources:** S03, S17. **Risk trace:** R7, R10.

### CM-UT-008 — Claim detail ownership and missing records

- **Priority:** P0
- **Expected-result basis:** Code-backed.
- **Preconditions:** Claim A owned by claimant A; claimant B; a nonexistent claim UUID.
- **Test steps:**
  1. Get claim A as A, then as B.
  2. Get the nonexistent UUID as A.
- **Expected results:** A receives claim A. B receives UnauthorizedClaimAccessException. Missing UUID raises ClaimNotFoundException. Reads cause no save or event publication. An ADMIN UUID that does not own the claim also has no bypass in this use case.
- **Automation:** JAVA-UNIT. **Sources:** S04. **Risk trace:** R1, R6.

### CM-UT-009 — Status-change business authorization and rejection side effects

- **Priority:** P1
- **Expected-result basis:** Code-backed.
- **Preconditions:** Mock user/claim repositories; valid claim state and update command; direct and CDC modes.
- **Test steps:**
  1. Execute with local ADMIN, local CLAIMANT, and missing admin user.
  2. For ADMIN, repeat with missing claim and an invalid target transition.
  3. Execute one valid transition in each event mode.
- **Expected results:** Only local ADMIN reaches a successful update. Missing/non-admin user raises UnauthorizedException; missing claim raises ClaimNotFoundException; invalid transition raises InvalidStatusTransitionException. Each failure produces no save/event publication. Valid update saves once; only direct mode publishes its domain event.
- **Automation:** JAVA-UNIT. **Sources:** S05. **Risk trace:** R4, R6.

### CM-UT-010 — Claim filters and summary mapping

- **Priority:** P1
- **Expected-result basis:** Code-backed.
- **Preconditions:** Mock repository results for all four combinations of optional status/user filters; descriptions of 99, 100, and 101 characters.
- **Test steps:**
  1. Execute ListAllClaimsUseCase with no filters, status only, user only, and both.
  2. Map each description to a claim summary.
- **Expected results:** Each query delegates to its matching repository method and returns those results. Summary descriptions are unchanged up to 100 characters and truncated to 100 above that. Do not assert sorted unfiltered findAll results at unit level; that repository path has no explicit ordering.
- **Automation:** JAVA-UNIT. **Sources:** S06, S31. **Risk trace:** R13.

### CM-UT-011 — Domain and CDC consumer parsing/routing

- **Priority:** P1
- **Expected-result basis:** Code-backed; malformed-message policy Pending Clarification.
- **Preconditions:** Real JSON parser; mocked WebSocket handler; valid domain envelopes and CDC c/u payloads from D5.
- **Test steps:**
  1. Feed submitted and status-changed domain events, including status change with/without owner userId.
  2. Feed CDC create/update payloads with valid field names and ts_ms.
  3. Feed malformed JSON, an unknown event type, and CDC snapshot op=r.
- **Expected results:** Submission routes to admins; status change with owner routes to owner plus admins, without owner to admins. Valid CDC c/u follows equivalent routing. Malformed JSON is caught/logged; unknown types and op=r are ignored. Handling of structurally incomplete but valid JSON, dead-letter behavior, and snapshot replay expectations are Pending Clarification (CL-09).
- **Automation:** JAVA-UNIT. **Sources:** S18, S19. **Risk trace:** R7, R12. **Open items:** CL-09.

### CM-UT-012 — Signup rules and orchestration

- **Priority:** P1
- **Expected-result basis:** Code-backed; conflicting name limits Pending Clarification.
- **Preconditions:** Mock Keycloak/Claims ports; pure auth schema available; D4 data.
- **Test steps:**
  1. Validate passwords of 7/8 characters, with/without uppercase and digit; blank names and invalid emails.
  2. Run successful signup; then fail Keycloak creation and Claims provisioning separately.
  3. Evaluate name lengths 1, 2, 100, 101, 255, and 256 at UI, command, and user-domain layers.
- **Expected results:** Password rule requires at least 8 characters, uppercase, and digit. Valid signup calls Keycloak first, then Claims, always with CLAIMANT. Keycloak failure prevents Claims creation; a later Claims failure propagates and has no coded compensation. Name limits differ by layer; unified signup acceptance and partial-failure recovery are Pending Clarification (CL-05, CL-08).
- **Automation:** JAVA-UNIT + TS-UNIT. **Sources:** S10, S11, S32. **Risk trace:** R10. **Open items:** CL-05, CL-08.

### 3.3 Component scenarios

### CM-CT-001 — Wizard field validation and progression

- **Priority:** P1
- **Expected-result basis:** Code-backed.
- **Preconditions:** Wizard/store reset; UI clock controlled; API mocked; D1–D3.
- **Test steps:**
  1. Attempt Next with empty and invalid step-1 fields; correct each independently.
  2. Enter description boundary values on step 2.
  3. Inspect validation messages, aria-invalid, counters, and the active step.
- **Expected results:** Invalid UI schema values keep the relevant Next button disabled. Valid step 1 moves to step 2; a valid 10–1000 character description moves to review. Length messages/counters reflect raw UI input. Whitespace can pass raw-length checks before server trimming; harmonized behavior is Pending Clarification (CL-05).
- **Automation:** UI-COMP. **Sources:** S13, S14. **Risk trace:** R5. **Open items:** CL-05.

### CM-CT-002 — Wizard back-navigation and cancel semantics

- **Priority:** P1
- **Expected-result basis:** Code-backed.
- **Preconditions:** New claim page mounted; store reset; navigation mocked.
- **Test steps:**
  1. Complete step 1, advance to review, and return to earlier steps.
  2. Cancel after saved form data exists; dismiss the confirmation, then confirm it.
  3. Repeat Cancel with an empty store; separately enter unsaved values without advancing.
- **Expected results:** Saved step data repopulates when returning. Dismiss keeps saved data; confirmed cancel resets the wizard and routes to /claims; empty-store cancel exits directly. Cancel checks saved store values, so unsaved current-step edits may not trigger confirmation; whether they must is Pending Clarification (CL-05).
- **Automation:** UI-COMP. **Sources:** S13, S14. **Risk trace:** R5. **Open items:** CL-05.

### CM-CT-003 — Review display and serialized submission

- **Priority:** P1
- **Expected-result basis:** Code-backed; date policy Pending Clarification.
- **Preconditions:** Wizard store contains D1; API mock captures its request; navigation/toast spies.
- **Test steps:**
  1. Open review and compare date, location, description, and USD display against form data.
  2. Submit and inspect the API request object and generated serializer.
  3. Return a successful claim response.
- **Expected results:** Review shows the entered data with USD formatting. Request carries the four claim fields; generated date serialization uses the ISO date portion. Success shows the returned claim ID, resets the wizard, and navigates to /claims. Cross-timezone date equivalence is Pending Clarification (CL-04), covered in CM-IT-006.
- **Automation:** UI-COMP. **Sources:** S13, S14. **Risk trace:** R5. **Open items:** CL-04.

### CM-CT-004 — Submission in progress and retry feedback

- **Priority:** P1
- **Expected-result basis:** Code-backed; duplicate policy Pending Clarification.
- **Preconditions:** Valid review state; API promises controlled independently.
- **Test steps:**
  1. Keep a submission unresolved and inspect Submit/Back controls.
  2. Resolve it with failure, then retry with success.
  3. Return 400 with detail/fieldErrors, then 500 and network failure in separate runs.
- **Expected results:** Submit and Back disable while isSubmitting is true. Failure ends submitting state and retains form data for retry; 400 detail and fieldErrors populate the store, while generic failures show retry feedback. Success follows CM-CT-003. This does not establish server-side duplicate prevention or guarantee the API actually supplies fieldErrors; those are Pending Clarification (CL-05, CL-10).
- **Automation:** UI-COMP. **Sources:** S13, S22. **Risk trace:** R5, R6. **Open items:** CL-05, CL-10.

### CM-CT-005 — HTTP request validation and error contract

- **Priority:** P1
- **Expected-result basis:** Contract-backed; known implementation gaps.
- **Preconditions:** Each service's HTTP adapters and normal validation loaded; downstream ports/use cases stubbed; valid authentication/CSRF so business errors are reached.
- **Test steps:**
  1. POST each required field omitted/null; send malformed date, invalid UUID, invalid amount, and invalid enum.
  2. Stub domain validation, missing claim, foreign-owner, and invalid-transition failures.
  3. Compare status and error JSON at Claims and BFF boundaries with each OpenAPI response.
- **Expected results:** Documented validation/invalid-transition responses are 400; unauthenticated read is 401; foreign-owner read is 403; missing claim is 404. Error bodies follow the operation's ProblemDetail schema. Missing exception mappings and BFF wrapping/empty responses are anticipated defect candidates, not acceptable replacements for the contract. OpenAPI specifies application/json while Spring ProblemDetail may use application/problem+json; canonical media type and field-error representation are Pending Clarification (CL-06).
- **Automation:** HTTP-COMP. **Sources:** S04, S05, S22. **Risk trace:** R6. **Open items:** CL-06.

### CM-CT-006 — Admin transition confirmation and normal update

- **Priority:** P1
- **Expected-result basis:** Code-backed.
- **Preconditions:** Admin UI mounted with each lifecycle status; admin API mocked.
- **Test steps:**
  1. Inspect transition choices for all five states.
  2. Choose an allowed transition; cancel once and confirm on the next attempt.
  3. Resolve the confirmed update with a server response.
- **Expected results:** Options match the seven allowed transitions; CLOSED exposes no update control. Cancel sends no request. Confirm sends one PATCH with claimId/newStatus, shows pending state, and replaces the optimistic value with the response. A single failed request restores the pre-request list and shows an error.
- **Automation:** UI-COMP. **Sources:** S02, S15. **Risk trace:** R4.

### CM-CT-007 — Overlapping admin updates and rollback

- **Priority:** P1
- **Expected-result basis:** Pending Clarification.
- **Preconditions:** Two claims in the admin store; separately controlled update promises; desired concurrency policy CL-02 unresolved.
- **Test steps:**
  1. Start update of claim A.
  2. Complete a concurrent update/refetch affecting claim B.
  3. Fail A's request and inspect both records.
- **Expected results:** Current implementation restores A's entire pre-request list and may discard B's newer data. Capture this evidence. Whether rollback must preserve independently completed changes, and how to reconcile refetches, is Pending Clarification (CL-02); do not turn current whole-list rollback into an approved acceptance rule.
- **Automation:** UI-COMP. **Sources:** S15. **Risk trace:** R4. **Open items:** CL-02.

### CM-CT-008 — HTTP authorization, cookie precedence, and CSRF

- **Priority:** P0
- **Expected-result basis:** Code-backed; endpoint policy gaps Pending Clarification.
- **Preconditions:** Normal Spring Security chain loaded; fixed test signing key/decoder isolated from business stubs; real cookie authentication filter; valid CSRF obtained from /api/auth/csrf.
- **Test steps:**
  1. Call protected GET APIs anonymously, as claimant, and as ADMIN; call admin APIs with each role.
  2. POST a valid claim with valid authentication and valid/missing/mismatched CSRF cookie/header.
  3. Send a valid bearer header with a different access-token cookie and inspect the effective principal.
- **Expected results:** Anonymous protected GET is 401; claimant admin access is 403; ADMIN reaches admin handlers. BFF protected writes lacking matching CSRF are 403 before mutation; valid CSRF reaches the handler. Explicit Authorization takes precedence over the cookie. Direct Claims uses bearer auth and no CSRF. Role restrictions for provisioning and claimant-only submission remain Pending Clarification (CL-01).
- **Automation:** HTTP-COMP. **Sources:** S07, S08, S16. **Risk trace:** R1, R3, R9. **Open items:** CL-01.

### CM-CT-009 — Real-time client updates, connection state, and filtering

- **Priority:** P1
- **Expected-result basis:** Code-backed; recovery guarantee Pending Clarification.
- **Preconditions:** Mock WebSocket transport, timers, claims/admin/dashboard stores; authenticated claimant/admin fixtures.
- **Test steps:**
  1. Deliver valid status-changed and submitted messages; deliver an invalid status.
  2. Simulate connection close, retry attempts, and unmount.
  3. Apply an admin status filter and user-ID search, then return results.
- **Expected results:** Valid status messages update the matching claimant claim; admins refetch claims; admin submission handling also fetches stats. Invalid status is ignored. Connection state and warning reflect retries; configured retry limit is five and delays back off. Admin store fetch uses selected status, results are newest-first, and page search filters user IDs locally. Recovery of missed/stale events is Pending Clarification (CL-09).
- **Automation:** UI-COMP. **Sources:** S15, S20. **Risk trace:** R7, R9, R13. **Open items:** CL-09.

### CM-CT-010 — Refresh coordination and logout UI behavior

- **Priority:** P1
- **Expected-result basis:** Code-backed; failed-logout behavior Pending Clarification.
- **Preconditions:** Browser environment with generated client wrapper; isolated module refresh state; auth store; controlled fetch.
- **Test steps:**
  1. Return concurrent 401s followed by successful refresh and successful retries.
  2. Return unsuccessful refresh and observe sign-out/redirect.
  3. Trigger AppHeader logout with 204, then separately with 403.
- **Expected results:** Concurrent 401s share one refresh operation; successful refresh retries the original requests once. Failed refresh clears local auth and redirects to login. Logout with 204 clears local auth and navigates to login. Current AppHeader also clears local auth on 403 and omits CSRF; record that defect candidate. Required UI recovery after server logout failure and refresh succeeded/retry still 401 behavior are Pending Clarification (CL-07).
- **Automation:** UI-COMP. **Sources:** S12, S16. **Risk trace:** R9. **Open items:** CL-07.

### 3.4 Integration scenarios

### CM-IT-001 — Submit a claim through BFF and verify persistence

- **Priority:** P1
- **Expected-result basis:** Code-backed and contract-backed.
- **Preconditions:** Integration environment E1; claimant A provisioned in Keycloak and Claims; D1; valid cookies and CSRF; direct event mode.
- **Test steps:**
  1. POST D1 to BFF /api/claims.
  2. Read the returned claim through GET /api/claims/{claimId} and /api/claims.
  3. Query the corresponding PostgreSQL row independently.
- **Expected results:** POST is 201 with a generated claim UUID, owner A's JWT subject, SUBMITTED, and creation/update timestamps. Reread/DB values match normalized text, date, and two-decimal D1 amount. Only A owns the row. Claims Service directly returns Location; the current BFF controller does not add Location, so do not require that header at the BFF.
- **Automation:** API-INT + DB-INT. **Sources:** S01, S03, S04, S09, S22, S23. **Risk trace:** R5.

### CM-IT-002 — Read isolation and admin filtered listing

- **Priority:** P0
- **Expected-result basis:** Code-backed and contract-backed.
- **Preconditions:** Claimants A/B, provisioned ADMIN M, multiple claims per user at different timestamps and states; valid tokens/CSRF as needed.
- **Test steps:**
  1. List as A/B and get own, foreign-owned, and nonexistent claims through BFF and directly.
  2. Call admin listing with no filter, status, userId, and both filters; repeat as claimant.
  3. Compare filtered results with SQL.
- **Expected results:** Each claimant list contains only its owner's claims newest-first; own detail is 200, foreign detail 403, missing detail 404 per contract. ADMIN listing returns the matching records; claimant admin access is 403. Normal claim detail still enforces ownership for ADMIN. Record BFF error-mapping defects. Do not assume unfiltered direct admin list ordering; policy is Pending Clarification (CL-12).
- **Automation:** API-INT + DB-INT. **Sources:** S04, S05, S06, S09, S22. **Risk trace:** R1, R6, R13. **Open items:** CL-12.

### CM-IT-003 — Lifecycle and dual-role authorization through API

- **Priority:** P1
- **Expected-result basis:** Code-backed and contract-backed.
- **Preconditions:** Independent claims in every state; M has JWT ADMIN and local ADMIN; fixtures with JWT ADMIN/local CLAIMANT or missing local user; valid BFF CSRF.
- **Test steps:**
  1. Apply all seven allowed transitions through BFF; confirm PostgreSQL after each.
  2. Attempt representative invalid/self/CLOSED transitions and a nonexistent claim.
  3. Repeat update as claimant and with each JWT/local-role mismatch.
- **Expected results:** Allowed transitions return 200 and persist the target; createdAt and non-status business fields stay unchanged. Invalid transitions are documented as 400, missing claim 404, and claimant access 403. JWT ADMIN without local ADMIN cannot save; exact HTTP mapping for that domain denial is Pending Clarification (CL-06) because no explicit handler exists. Rejections leave data and events unchanged.
- **Automation:** API-INT + DB-INT. **Sources:** S02, S05, S07, S22. **Risk trace:** R4, R6. **Open items:** CL-06.

### CM-IT-004 — Provisioning permissions and identity rebinding

- **Priority:** P0
- **Expected-result basis:** Pending Clarification.
- **Preconditions:** Disposable E1 dataset with A/B and linked claims; service-account token; SQL snapshots; no real user data; provisioning policy CL-01 unresolved.
- **Test steps:**
  1. Call POST /api/users and GET /api/users/{userId} directly as claimant, ADMIN, and service account.
  2. Attempt a new local ADMIN-role user and a different UUID for an existing email.
  3. Observe returned data, roles, affected user/claim UUIDs, and any transaction failure; restore disposable fixtures.
- **Expected results:** Current controller authenticates callers but has no role/ownership guard on these methods and accepts request UUID/role; FK migration permits owner-ID cascade. Record actual effects. Allowed provisioning callers, profile visibility, email proof, and trusted rebinding behavior are Pending Clarification (CL-01). A service-account-only rule or required rejection code must not be assumed before review.
- **Automation:** API-INT + DB-INT. **Sources:** S07, S08, S23. **Risk trace:** R1. **Open items:** CL-01.

### CM-IT-005 — Concurrent downstream credential isolation

- **Priority:** P0
- **Expected-result basis:** Code-backed forwarding invariant; race remains unverified.
- **Preconditions:** Generated Java clients inspected; controllable downstream request capture; independent valid A/B/M/service tokens; barriers to force overlap.
- **Test steps:**
  1. Overlap A and B submission/list/detail requests across ClaimsServiceAdapter.
  2. Overlap provisioning/service-token calls with user-token calls.
  3. Overlap AdminController service-account reads with M's user-token status update; capture effective downstream subject per request.
- **Expected results:** Every user operation retains its initiating JWT subject; service-account operations use the service token; no response/data is attributed to another identity. A shared-client race is a hypothesis until this evidence is collected. Record safe subject/request identifiers, not raw tokens.
- **Automation:** API-INT. **Sources:** S09. **Risk trace:** R2.

### CM-IT-006 — Amount precision, normalization, and business date

- **Priority:** P1
- **Expected-result basis:** Mixed code/contract; disputed outcomes Pending Clarification.
- **Preconditions:** E1; valid A; SQL access; host/browser timezone controllable; separate fixtures for each input.
- **Test steps:**
  1. Submit 0.01, 1.00, and 1,000,000 and compare POST response, GET, DB.
  2. Submit 0.001, 1.005, 999999.999, padded text, and whitespace-only text.
  3. Repeat today/tomorrow at browser/backend midnight boundaries and record selected date versus serialized/stored date.
- **Expected results:** Valid two-decimal amounts/date/text round-trip; 0.001 is below the API minimum, and whitespace-only text fails domain validation without persistence. Future dates according to backend local date are rejected. Exact handling of extra decimal places, rounded maximum-edge amounts, timezone disagreements, and client/server normalization differences is Pending Clarification (CL-03, CL-04, CL-05); record all layer values.
- **Automation:** API-INT + DB-INT. **Sources:** S01, S14, S22, S23. **Risk trace:** R5. **Open items:** CL-03, CL-04, CL-05.

### CM-IT-007 — Database rollback and commit-dependent event publication

- **Priority:** P1
- **Expected-result basis:** Code-backed transaction invariant.
- **Preconditions:** E1 with real PostgreSQL/Kafka; direct mode; Kafka observer ready at known offsets; controlled persistence failure before transaction commit.
- **Test steps:**
  1. Submit a valid claim with the write forced to fail before commit.
  2. Read DB and topic after failure.
  3. Remove the fault, repeat once successfully, and observe commit then publication.
- **Expected results:** Failed write leaves no new claim and publishes no corresponding domain event. Healthy commit stores the claim and produces its event. The test must separate pre-commit database failure from post-commit Kafka failure; the latter is covered by CM-IT-015 and cannot be treated as a database rollback.
- **Automation:** DB-INT + EVENT-INT. **Sources:** S03, S17. **Risk trace:** R7.

### CM-IT-008 — Conflicting admin decisions and audit actor

- **Priority:** P1
- **Expected-result basis:** Pending Clarification.
- **Preconditions:** Two distinct local/JWT ADMIN users; same UNDER_REVIEW claim; barriers ensuring both read the original state; DB/Kafka capture.
- **Test steps:**
  1. Concurrently approve as M1 and reject as M2.
  2. Capture both HTTP responses, final row, event sequence, and timestamps.
  3. Compare any changedBy value with owner A and initiating administrator.
- **Expected results:** Current repository explicitly permits last-write-wins; current event construction places owner A in changedBy. Record these observations without requiring 409 or inventing an audit history. Conflict policy, actor/owner separation, and required audit evidence are Pending Clarification (CL-02, CL-11).
- **Automation:** API-INT + DB-INT + EVENT-INT. **Sources:** S05, S23, S30. **Risk trace:** R4. **Open items:** CL-02, CL-11.

### CM-IT-009 — Signup, login, refresh, and logout protocol

- **Priority:** P1
- **Expected-result basis:** Code-backed and contract-backed; revocation policy Pending Clarification.
- **Preconditions:** E1 with effective Keycloak admin credentials and service-account access; unique D4 email; cookie jar/CSRF capture.
- **Test steps:**
  1. Signup, confirm local identity/role, log in with valid then invalid credentials.
  2. Refresh with valid, absent, and invalid refresh cookie; inspect cookie flags/lifetimes.
  3. Log out with valid auth/CSRF, then try cookie-based APIs; separately attempt logout without CSRF and token reuse.
- **Expected results:** Signup 201 creates a CLAIMANT; login 200 sets HttpOnly/SameSite=Strict cookies (current demo Secure=false; access 5 minutes, refresh 30). Invalid credentials return 401. Valid refresh returns 204 and sets cookies; absent/invalid refresh returns 401. Valid logout returns 204 and expires both cookies; missing CSRF returns 403. Global token/session/WebSocket revocation and hardened cookie settings are Pending Clarification (CL-07).
- **Automation:** API-INT + KEYCLOAK. **Sources:** S10, S11, S12, S16, S33. **Risk trace:** R9, R10. **Open items:** CL-07.

### CM-IT-010 — Partial signup, lookup outage, duplicate user, and role drift

- **Priority:** P1
- **Expected-result basis:** Mixed contract and Pending Clarification.
- **Preconditions:** Disposable E1 identities; fault control between Keycloak and Claims; fixtures with existing UUID/email and differing JWT/DB roles.
- **Test steps:**
  1. Create an identity, fail local provisioning, restore Claims, and retry signup/login.
  2. Cause lookup-not-found separately from timeout/5xx; capture any provisioning attempt.
  3. Repeat provisioning with the same email/UUID, then change the Keycloak role while local role remains unchanged.
- **Expected results:** Duplicate signup is documented as 409. Existing-email/same-UUID provisioning currently returns 409 with UserResponse, which the BFF parses. Current login provisions after any lookup IllegalStateException; document this separately from a genuine missing user. Compensation, retry/idempotency, lookup error classification, and role synchronization are Pending Clarification (CL-08); never require automatic deletion or privilege upgrade without a decision.
- **Automation:** API-INT + KEYCLOAK + DB-INT. **Sources:** S08, S09, S10, S22. **Risk trace:** R10. **Open items:** CL-08.

### CM-IT-011 — JWT trust and temporal validation

- **Priority:** P1
- **Expected-result basis:** Code-backed signature/time checks; issuer/audience policy Pending Clarification.
- **Preconditions:** Normal security on both services; isolated trusted test signing key/JWKS endpoint; controlled time; valid UUID subject; valid CSRF on writes.
- **Test steps:**
  1. Call protected GET with valid token, bad signature, malformed token, expired token, and not-before token outside 30-second skew.
  2. Repeat at documented skew edges using controlled time.
  3. Use a trusted-key-signed token with unexpected issuer/audience, missing realm roles, and malformed subject; record results.
- **Expected results:** Valid token authenticates; invalid signatures/malformed tokens and time claims outside accepted skew are rejected. Claimants cannot access ADMIN endpoints. Exact issuer/audience allowlists and treatment of malformed/missing subject/role claims are Pending Clarification (CL-01, CL-06); do not assume configured issuer-uri adds validation to the custom decoder.
- **Automation:** API-INT + TEST-JWKS. **Sources:** S07, S25. **Risk trace:** R3. **Open items:** CL-01, CL-06.

### CM-IT-012 — Direct Kafka to WebSocket recipient isolation

- **Priority:** P1
- **Expected-result basis:** Code-backed.
- **Preconditions:** E1 direct mode, CDC connector stopped; authenticated sockets for A, B, and M; known topic offsets; observer readiness.
- **Test steps:**
  1. Create A's claim and collect domain event plus socket messages.
  2. Change its status as M; compare event claimId/old/new status and recipients.
  3. Repeat with multiple A sockets, invalid/missing handshake token, and disallowed Origin.
- **Expected results:** Submission event reaches admins; status change reaches A and admins, never B; multiple A sessions each receive their applicable message. API and DB state agree with the payload. Invalid/missing-token and disallowed-origin handshakes are rejected. Healthy single-operation delivery is assessed without claiming an exactly-once guarantee under replay.
- **Automation:** EVENT-INT + API-INT + WS. **Sources:** S17, S18, S19. **Risk trace:** R7.

### CM-IT-013 — Producer/consumer/schema contract compatibility

- **Priority:** P1
- **Expected-result basis:** Contract-backed; known source mismatch.
- **Preconditions:** Real producer serializer/validator and published JSON schemas; isolated Kafka; normal database transaction; schema validation toggled explicitly.
- **Test steps:**
  1. Capture creation and status-change messages emitted with validation disabled.
  2. Validate exact emitted envelopes against their declared schemas.
  3. Enable validation and repeat; inspect committed DB record, send outcome, and consumer compatibility.
- **Expected results:** Messages should satisfy the declared contract and preserve required event fields. Current envelope/root-field mismatch is expected to expose a contract failure; record it instead of modifying the schema or omitting required assertions. A post-commit validation failure must not be reported as rolled-back persistence. Canonical schema/envelope ownership is Pending Clarification (CL-09); neither contract nor producer was changed for this specification.
- **Automation:** CONTRACT + EVENT-INT. **Sources:** S17, S18, S24. **Risk trace:** R8. **Open items:** CL-09.

### CM-IT-014 — Real Debezium claim create/update path

- **Priority:** P1
- **Expected-result basis:** Code-backed c/u routing; CDC policy Pending Clarification.
- **Preconditions:** E1 plus E2 connector; app.events.cdc-enabled=true; no direct publishing; publication/slot verified; real connector output captured.
- **Test steps:**
  1. Create a claim and perform a valid status change through the API.
  2. Capture actual c/u event structure, claim_id, user_id, before/after status, and ts_ms.
  3. Observe A/B/M sockets; compare helper-injected payloads separately; exercise a connector snapshot if supported.
- **Expected results:** Healthy c/u changes are consumed and routed as in CM-IT-012 using actual IDs/owners. No direct-domain event is expected from the use case in CDC mode. Missing before.status, snapshot op=r (currently ignored), and the helper's after.id mismatch are recorded explicitly. Snapshot/recovery and required old-status semantics are Pending Clarification (CL-09); a mock helper alone cannot establish CDC coverage.
- **Automation:** EVENT-INT + DB-INT + DEBEZIUM. **Sources:** S18, S19, S25, S26, S27. **Risk trace:** R7, R12. **Open items:** CL-09.

### CM-IT-015 — Broker/socket failure, duplicate events, and recovery

- **Priority:** P1
- **Expected-result basis:** Pending Clarification.
- **Preconditions:** Disposable integration stack; persisted operation/event IDs recorded; fault control; delivery/recovery window CL-09 unresolved.
- **Test steps:**
  1. Disconnect Kafka during/after a committed write, restore it, and restart producer/consumer where relevant.
  2. Disconnect A's socket while changing status; reconnect and compare UI/API state.
  3. Replay duplicate events and an older status after a newer one; repeat with two BFF nodes if that topology is intended.
- **Expected results:** Record DB commit, emitted/replayed events, recipients, final UI versus API status, and any duplicate notifications. Current code has no application outbox/deduplication or reconnect reconciliation; shared consumer group plus node-local sockets may miss recipients in a replicated topology. Delivery guarantees, ordering/stale-message handling, recovery bounds, replica support, and post-logout socket behavior are Pending Clarification (CL-07, CL-09, CL-13).
- **Automation:** EVENT-INT + WS. **Sources:** S17, S18, S19, S20. **Risk trace:** R7, R9. **Open items:** CL-07, CL-09, CL-13.

### CM-IT-016 — Dashboard cache correctness and degradation

- **Priority:** P1
- **Expected-result basis:** Code-backed cache operations; freshness policy Pending Clarification.
- **Preconditions:** Known user/claim/status counts in PostgreSQL; Redis controllable and isolated; ADMIN has downstream permissions; empty cache.
- **Test steps:**
  1. Read stats twice; change a claim/status/user and compare cached versus bypass=true reads.
  2. Flush cache and read; verify expiry at the configured 300-second TTL in an isolated controlled setup.
  3. Repeat get/put/flush with Redis unavailable.
- **Expected results:** Initial miss reads DB and caches; next hit reports cacheHit=true. Bypass reads DB without updating the cache; flush returns 204; expiry causes a fresh DB read. Redis get/put failures fall back without failing the DB-backed stats response; failed eviction is swallowed by current adapter. Immediate invalidation, allowed staleness, and empty status-bucket representation are Pending Clarification (CL-12).
- **Automation:** API-INT + DB-INT + REDIS. **Sources:** S21. **Risk trace:** R11. **Open items:** CL-12.

### CM-IT-017 — API contract compatibility and advertised route gap

- **Priority:** P1
- **Expected-result basis:** Contract-backed; route scope Pending Clarification.
- **Preconditions:** Both OpenAPI specs and generated-client version captured; running normal BFF/Claims; prepared valid/error fixtures; no bypass-auth profile.
- **Test steps:**
  1. Verify claim/status/user request/response enums, dates, required fields, error statuses/bodies, and user-conflict response at both boundaries.
  2. Call the general PATCH /api/claims/{claimId} advertised only by Claims OpenAPI.
  3. Compare status/description limits with the implemented admin-status route and creation domain.
- **Expected results:** Implemented routes must match their operation contracts; wrappers, empty errors, and 409 UserResponse versus documented ProblemDetail are reported as contract mismatches. General PATCH has no matching implementation in ClaimController; record actual response. Whether to implement/remove that route and permit description editing is Pending Clarification (CL-12); do not infer 200 or 2000-character description support.
- **Automation:** CONTRACT + API-INT. **Sources:** S09, S22, S28. **Risk trace:** R6, R13. **Open items:** CL-06, CL-12.

### CM-IT-018 — Environment wiring and security-profile isolation

- **Priority:** P1
- **Expected-result basis:** Code-backed configuration; exposure policy Pending Clarification.
- **Preconditions:** Approved local/Compose test environment only; effective variables, profiles, URLs, and image versions recorded; no production endpoints.
- **Test steps:**
  1. Verify UI REST resolves to BFF, WebSocket URL/origin matches it, and BFF reaches Claims/Keycloak.
  2. Confirm service-account authorities and configured identity/bootstrap credentials support required operations; record selected direct/CDC mode.
  3. Check normal profiles do not expose /api/test/trigger-event or inject static admin auth; inventory accessible actuator endpoints without destructive calls.
- **Expected results:** Normal environment uses real authentication; missing normal API credentials do not receive a static admin identity; test-only helper is absent. Intended REST/WS/service dependencies resolve and admin reads have the needed effective permissions. Configuration mismatches are environment blockers. Allowed actuator exposure, replica topology, and deployment hardening expectations are Pending Clarification (CL-13), not inferred production requirements.
- **Automation:** API-INT + CONFIGURATION. **Sources:** S07, S25, S27, S29, S33. **Risk trace:** R10, R12, R13. **Open items:** CL-13.


### CM-IT-019 — Duplicate submissions after a lost response or concurrent requests

- **Priority:** P1
- **Expected-result basis:** Pending Clarification.
- **Preconditions:** Disposable E1 claims for A; valid auth/CSRF; capture HTTP, SQL, and event IDs; no agreed idempotency key or duplicate window.
- **Test steps:**
  1. Submit D1 and discard the successful response after the database commits; retry the same logical request.
  2. Separately send two concurrent identical valid submissions directly to BFF, bypassing UI button disabling.
  3. Count persisted claims and related events; compare generated IDs and responses.
- **Expected results:** The initial request returns 201 when observed normally and creates a valid owned claim. Current creation generates a fresh UUID on every call and has no coded duplicate-submission check. Record retry/concurrency outcomes. Whether to create multiple claims, return a prior result, or reject a duplicate, and any key/window/status code, is Pending Clarification (CL-10); duplicate handling cannot receive an acceptance pass until resolved.
- **Automation:** API-INT + DB-INT + EVENT-INT. **Sources:** S03, S09, S13, S17. **Risk trace:** R5. **Open items:** CL-10.

## 4. Risk-Based Prioritization

Priorities express business damage and investigation urgency, not a confirmed defect severity or an execution result.

- **P0:** First implementation/investigation batch: identity isolation, provisioning access, and credential cross-talk. An unclarified P0 policy remains unresolved, not passed.
- **P1:** Critical submission/lifecycle/data correctness, auth lifecycle, error contracts, and dependency consistency.
- **P2:** Secondary operational exposure and feature-scope inventory after core correctness is understood. P1 scenarios may gather P2 observations without making production hardening part of this cycle.

Risk IDs below preserve traceability to R1–R13 in the QA journal. Several P1 scenarios also provide the supporting evidence for the journal's P2 route/operational risks.

| Risk | Priority | Impact / why prioritized | Main scenarios | Source evidence |
| --- | --- | --- | --- | --- |
| R1 | P0 | Identity/profile access and claim ownership corruption | CM-UT-008; CM-CT-008; CM-IT-002, 004 | S04, S07, S08 |
| R2 | P0 | Concurrent requests may use another caller's bearer identity | CM-IT-005 | S09 |
| R3 | P1 | Token trust, signature/time checks and role boundaries | CM-CT-008; CM-IT-011 | S07 |
| R4 | P1 | Invalid lifecycle changes, conflicting decisions, rollback/audit errors | CM-UT-005, 006, 009; CM-CT-006, 007; CM-IT-003, 008 | S02, S05, S06, S15, S30 |
| R5 | P1 | Invalid input, monetary precision, inconsistent dates/text | CM-UT-001–004; CM-CT-001–004; CM-IT-001, 006, 019 | S01, S13, S14, S23 |
| R6 | P1 | Business errors become misleading responses or lose useful details | CM-UT-008, 009; CM-CT-005; CM-IT-002, 003, 017 | S04, S05, S09, S22 |
| R7 | P1 | Committed changes and real-time state diverge | CM-UT-006, 007, 011; CM-CT-009; CM-IT-007, 012, 014, 015 | S17–S20, S25–S27 |
| R8 | P1 | Producer envelopes fail their event schemas | CM-IT-013 | S17, S24 |
| R9 | P1 | Failed logout/refresh or stale WebSocket authorization | CM-CT-008–010; CM-IT-009, 015 | S07, S12, S16, S19, S20 |
| R10 | P1 | Partial provisioning, role drift, invalid integration configuration | CM-UT-007, 012; CM-IT-009, 010, 018 | S08–S12, S25, S33 |
| R11 | P1 | Dashboard decisions based on stale counts or cache failure | CM-IT-016 | S21 |
| R12 | P1 profile isolation / P2 operational inventory | Static-admin test profiles or invalid CDC helpers give false confidence | CM-UT-011; CM-IT-014, 018 | S07, S18, S27 |
| R13 | P1 contract/filter coverage / P2 route-scope review | Unimplemented advertised operations, inconsistent ordering/wiring | CM-UT-010; CM-CT-009; CM-IT-002, 017, 018 | S06, S15, S25, S28 |

Suggested implementation sequence after review: normal-security/environment readiness → P0 ownership/credential/provisioning checks → unit lifecycle/data boundaries and core submission/status integration → UI/error/identity lifecycle coverage → event/CDC/cache/failure cases. A failing readiness check is an environment blocker; it is not an application pass or a reason to bypass authentication.

## 5. Test Approach

### 5.1 Automation mapping

Each scenario names the relevant automation codes below. These are implementation recommendations only.

| Code | Recommended tools and automation method |
| --- | --- |
| JAVA-UNIT | JUnit Jupiter parameterized tests, Mockito/fakes, and AssertJ using the declared Spring test stack. Exercise domain/use cases directly; verify mutation/event absence on rejected operations. Confirm resolved dependency versions before implementation rather than inferring them from README labels. |
| TS-UNIT | Vitest for Zod schemas and pure transition utilities. Parameterize D2/D3 and lifecycle parity; use controlled dates where necessary. |
| UI-COMP | Vitest + Testing Library + user-event + jsdom. Render one component/store boundary; stub generated API calls/fetch; control promises/timers; query by accessible label/role. Reset Zustand persistence/state per test. |
| HTTP-COMP | Spring Boot MVC/security test facilities and MockMvc with the real controller, validation, exception advice, and normal SecurityFilterChain. Stub use cases/outbound dependencies; use a trusted test decoder for filter wiring and reserve real signature verification for CM-IT-011. |
| API-INT | Spring integration test harness or HTTP client against isolated BFF/Claims with real configured dependencies. Obtain real role-specific tokens/cookies from Keycloak; verify outcomes independently in DB and event observers. |
| DB-INT | Testcontainers PostgreSQL using the supplied Liquibase migrations, not H2 for decimal/FK/transaction/concurrency claims. Use barriers and controlled fault injection for conflicting writes and pre-commit failure. |
| KEYCLOAK / TEST-JWKS | Real Keycloak container for user/service-account/grant behavior; isolated signed tokens/JWKS and controlled time for negative decoder tests. Verify imported realm and container/test-library compatibility first. |
| EVENT-INT / WS | Testcontainers Kafka plus real BFF consumer and Java WebSocket clients; unique fixtures/offsets and bounded polling. Use actual serialized messages. Mocks do not establish broker/recipient delivery. Fault injection is limited to the disposable stack. |
| DEBEZIUM | Optional Compose/Testcontainers extension with PostgreSQL logical replication and real connector. Keep direct and CDC suites separate; record mode in test output. Verify real connector envelopes before using synthetic helpers. |
| REDIS | Isolated Redis container (a generic Testcontainers container is suitable). Verify cacheHit/SQL counts and TTL/bypass/flush; inject connection failure without affecting shared resources. |
| CONTRACT | Pact BFF consumer/Claims provider plus the declared OpenAPI request validator and actual JSON-schema validator. Cover success/error/conflict bodies and message envelopes, not only generated-client compilation. Verify contract state setup and real-auth coverage separately. |
| CONFIGURATION | Read effective configuration/profiles plus minimal non-destructive dependency/auth readiness checks. Use CM-IT-018 before deeper integration suites. |

Framework evidence: S29. PostgreSQL/Kafka/Keycloak Testcontainers, Pact/ArchUnit and coverage plugins are declared; UI dependencies include Vitest/Testing Library/Playwright. No runnable test suite was established by this activity. A controllable HTTP stub server may be useful for outbound faults; its dependency/configuration must be confirmed or added later within authorized test work. Redis/CDC container setup, timeout/polling support, and any missing coverage adapter are implementation dependencies, not completed work.

### 5.2 Implementation conventions

- Use stable test IDs in test names/tags and report parameterized row, actor, event mode, and source/contract version.
- Separate code characterization from contract acceptance. Known bad behavior must not silently become a passing business requirement.
- Isolate all users/claims by run ID; clean up only that run's disposable resources. Do not alter seeded/shared identities to test rebinding.
- Assert both allowed actions and denied-action side effects: DB rows/owner/status/amount and emitted events.
- Avoid clock sleeps in unit/component tests. Bound asynchronous integration waits with an agreed harness timeout; that timeout does not establish a product SLA. Capture before/after timestamps rather than assuming strict millisecond increase.
- Use barriers for token/state races; capture downstream identity and final persistence, not only the number of requests.
- Keep secrets/raw JWTs out of reports. Capture endpoint, actor alias/subject, claim ID, status, relevant sanitized payload, and database/event evidence.
- Verify normal security independently from any contract profile that injects a static admin. A contract-test result cannot demonstrate JWT, CSRF, or ownership controls.

### 5.3 Future execution and review gates

Implementation and execution require the user's review before proceeding. This document does not authorize code changes or test runs.

Once implementation is authorized, record each scenario as Not Run, Pass, Fail, Blocked, or Pending Clarification, with actual results and evidence. A known contract mismatch may fail; a disputed policy remains Pending Clarification. Do not count an unexecuted or unresolved assertion as covered/passing.

Surefire excludes integration-tagged tests by default; local Maven profiles and startup builds skip tests. The eventual test workflow must explicitly include the intended suites. JaCoCo 90% line/branch thresholds and PIT 80% thresholds are configuration, not measured results. Do not use aggregate percentages alone to accept critical authorization/lifecycle coverage (S29).

Proposed completion evidence for review: each P0/P1 scenario has a traceable result or an explicitly reviewed blocker/open CL item; the seven allowed and eighteen rejected domain transitions are covered; ownership/role and rejected-write integrity are demonstrated; each selected event mode has real dependency evidence; unresolved policies and defects remain visible. Release acceptance criteria require a separate user decision.

## 6. Out of Scope

| Area | Reason / boundary |
| --- | --- |
| Test implementation/execution and application fixes in this activity | Current authorization is documentation for review only. |
| Full browser end-to-end suite and broad browser/device matrix | This cycle targets unit/component/integration boundaries. Playwright is declared but browser journey automation is a later cycle; UI component tests remain included. |
| Load, capacity, soak, performance SLAs, and benchmarking | No targets/workload have been agreed. Focused concurrent decisions/token races and bounded dependency failures remain in scope for correctness. |
| Full penetration test, DAST/SAST rollout, platform hardening, and secrets rotation | Current security scope is application authorization/JWT/CSRF/identity isolation and profile checks. Production policies/infrastructure boundaries require clarification. |
| Payment processing, policy eligibility, attachments, fraud checks, external insurance systems | No implemented workflow/integration was identified in the reviewed claim paths; no behavior will be invented. |
| General claimant edit/delete implementation | The general PATCH is only a contract-scope check in CM-IT-017; implemented behavior and description-edit rules are Pending Clarification (CL-12). |
| Disaster recovery, backup/restore certification, and broad infrastructure chaos | Requires separate objectives and environment. Targeted DB rollback, Kafka/socket interruption, and Redis fallback are included. |
| Accessibility certification, visual regression, localization, and mobile-app testing | No dedicated acceptance baseline is provided. Accessible control/error behavior used by the component scenarios remains included. |
| New architecture/mutation-test campaign | ArchUnit/PIT are declared but not expanded in this cycle; business/security assertions take precedence over an additional quality-tool rollout. |
| Regulatory retention/compliance certification and historical migration/backfill | No approved requirements are supplied. Actor/owner audit discrepancy is investigated in CM-IT-008 without assuming retention rules. |

## 7. Assumptions & Dependencies

### 7.1 Clarification register

Every item is **Pending Clarification**; no policy decision is implied by inclusion.

| ID | Decision needed and source conflict | Affected scenarios | Suggested reviewer |
| --- | --- | --- | --- |
| CL-01 | Which principals may submit, provision users, view profiles, select local roles, or rebind an existing email to a new UUID? What identity proof/service-account permissions apply? Which issuer/audience/subject/role claims are required? Current code leaves several boundaries open. | CM-CT-008; CM-IT-004, 011; authorization matrix | Product owner + security + backend |
| CL-02 | Confirm the lifecycle business meaning, concurrent approve/reject policy, retry/conflict response, and preservation of independently updated UI records. Current code explicitly uses last-write-wins. | CM-UT-005; CM-CT-007; CM-IT-008 | Claims business owner + backend/frontend |
| CL-03 | Confirm USD/currency scope, minimum, allowed scale, rounding/rejection rules, and canonical response/persistence amount. Domain accepts >0; API starts at 0.01; DB has scale 2. | CM-UT-004; CM-IT-006 | Claims business owner + backend |
| CL-04 | Define business timezone, date-only serialization/display, midnight behavior, and any maximum incident age. Client/backend clocks differ; no lower bound is coded. | CM-UT-003; CM-CT-003; CM-IT-006 | Product owner + frontend/backend |
| CL-05 | Resolve name limits, whitespace normalization, validation messages/field-error shape, and cancel behavior for unsaved step edits. UI/API/domain rules differ. | CM-UT-012; CM-CT-001–004; CM-IT-006 | Product owner + frontend/backend |
| CL-06 | Resolve HTTP status for database-role denial/malformed subjects, ProblemDetail media type, and error schema at each boundary. Missing mappings and BFF wrapping conflict with documented errors; documented 400/403/404 requirements remain visible as contract assertions. | CM-CT-005; CM-IT-003, 011, 017 | API owner + backend |
| CL-07 | Define failed-logout UI recovery, refresh-success/retry-still-401 handling, global token revocation, open-socket expiry/role revocation, and deployment cookie attributes. | CM-CT-010; CM-IT-009, 015 | Security + auth/frontend owners |
| CL-08 | Define partial signup compensation, retry/idempotency, lookup-not-found versus service failure, duplicate provisioning response, and Keycloak↔DB↔UI role synchronization. | CM-UT-012; CM-IT-010 | Identity/backend owner |
| CL-09 | Choose canonical event schema/envelope and owner, direct/CDC modes, event delivery/order/deduplication guarantees, malformed-message policy, CDC snapshots/oldStatus, replay/reconnect reconciliation, and recovery window. | CM-UT-011; CM-CT-009; CM-IT-013–015 | Backend/event platform + product owner |
| CL-10 | Define duplicate-submission detection/idempotency after double actions, timeout, lost response, or retry; identify key and retention scope if required. UI disabling alone is not a server policy. | CM-CT-004; CM-IT-019 | Product owner + backend |
| CL-11 | Define changedBy versus claim owner, decision actor/reason/history requirements, and any audit-retention requirement. Current event uses owner in changedBy. | CM-IT-008 | Claims business owner + backend |
| CL-12 | Decide general PATCH support/description editing, unfiltered list ordering/pagination scope, dashboard freshness/zero buckets, and whether bypass should refresh cache. | CM-IT-002, 016, 017 | API/product + frontend/backend |
| CL-13 | Confirm supported topology/replicas, effective URLs/ports/credentials/roles, optional CDC availability, normal/test profile boundaries, and permitted operational endpoint exposure. | CM-IT-015, 018 | Environment/platform + security |

CM-IT-019 records duplicate-submission evidence while CL-10 is open. Its acceptance outcome remains Pending Clarification; UI disabling or characterization results alone cannot establish an approved server idempotency policy.

### 7.2 Implementation dependencies

- Review this specification before implementing or executing tests. Existing code-review decisions remain Pending Review.
- Locate/restore the test sources described in README or confirm this is an intentionally source-only snapshot. Reuse real existing test assets only after inspecting them.
- Confirm Java 21/Node/Maven/container availability and the resolved Spring/JUnit/Pact/ArchUnit/Testcontainers versions. Generated Java interfaces/clients must be produced by the normal approved build when implementation begins; generation was not performed here.
- Establish unique test identities including claimant pairs, local/JWT ADMIN matches and mismatches, service-account grants, and controlled test signing keys.
- Verify effective Keycloak admin credentials and service-account authorities rather than relying on conflicting defaults. No live secrets belong in this document.
- Apply Liquibase to PostgreSQL, prepare isolated Redis/topics/consumer groups, and ensure event observers are ready before each operation.
- For CDC, prepare logical replication, publication, slot, connector, and real payload capture. Mark CDC scenarios Blocked if that optional dependency is unavailable; do not substitute the mock helper and report real CDC coverage.
- Provide controlled dependency-failure and concurrency barriers confined to the test environment; verify framework/dependency support before implementing them.
- Resolve CL items needed for disputed assertions; other code/contract-backed assertions can proceed independently after review.

## 8. Source References

Source IDs used in scenarios identify the exact reviewed files/lines. Paths are local clickable references; recheck line locations if the implementation changes.

| ID | Evidence | Source files |
| --- | --- | --- |
| S01 | Claim construction, normalization, events, reconstruction | [Claim.java:55](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/domain/claim/Claim.java:55); [Claim.java:72](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/domain/claim/Claim.java:72); [Claim.java:120](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/domain/claim/Claim.java:120) |
| S02 | Backend/UI transition matrix | [ClaimStatus.java:10](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/domain/claim/ClaimStatus.java:10); [claim-status-transitions.ts:3](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/demo-app-ui/src/features/admin/utils/claim-status-transitions.ts:3) |
| S03 | Transactional creation and direct/CDC mode | [CreateClaimUseCase.java:19](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/application/usecases/CreateClaimUseCase.java:19); [ClaimController.java:53](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/adapter/in/web/ClaimController.java:53); [ClaimsController.java:32](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/adapter/in/web/ClaimsController.java:32) |
| S04 | Ownership, claim read/list HTTP adapters | [GetClaimUseCase.java:31](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/application/usecases/GetClaimUseCase.java:31); [ClaimController.java:91](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/adapter/in/web/ClaimController.java:91); [ClaimController.java:115](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/adapter/in/web/ClaimController.java:115) |
| S05 | JWT and database admin authorization; status command | [UpdateClaimStatusUseCase.java:35](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/application/usecases/UpdateClaimStatusUseCase.java:35); [AdminClaimsController.java:45](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/adapter/in/web/AdminClaimsController.java:45) |
| S06 | Admin filters, query ordering, last-write-wins note | [ListAllClaimsUseCase.java:29](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/application/usecases/ListAllClaimsUseCase.java:29); [ClaimJpaRepository.java:12](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/adapter/out/persistence/ClaimJpaRepository.java:12); [ClaimRepositoryAdapter.java:21](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/adapter/out/persistence/ClaimRepositoryAdapter.java:21); [ClaimRepositoryAdapter.java:53](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/adapter/out/persistence/ClaimRepositoryAdapter.java:53) |
| S07 | Normal BFF/Claims security and custom JWT validators | [SecurityConfig.java:48](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/config/SecurityConfig.java:48); [SecurityConfig.java:109](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/config/SecurityConfig.java:109); [SecurityConfig.java:55](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/config/SecurityConfig.java:55); [SecurityConfig.java:79](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/config/SecurityConfig.java:79) |
| S08 | Provisioning/profile access and UUID cascade | [UserController.java:49](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/adapter/in/web/UserController.java:49); [UserController.java:96](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/adapter/in/web/UserController.java:96); [002-create-claims-table.yaml:60](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/resources/db/changelog/changes/002-create-claims-table.yaml:60) |
| S09 | BFF forwarding, mutable bearer clients, conflict parsing | [ClaimsServiceAdapter.java:73](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/adapter/out/claimsservice/ClaimsServiceAdapter.java:73); [ClaimsServiceAdapter.java:137](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/adapter/out/claimsservice/ClaimsServiceAdapter.java:137); [ClaimsServiceAdapter.java:212](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/adapter/out/claimsservice/ClaimsServiceAdapter.java:212); [AdminController.java:148](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/adapter/in/web/AdminController.java:148) |
| S10 | Signup and login orchestration | [SignupUseCaseImpl.java:19](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/application/auth/SignupUseCaseImpl.java:19); [LoginUseCaseImpl.java:29](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/application/auth/LoginUseCaseImpl.java:29); [LoginUseCaseImpl.java:48](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/application/auth/LoginUseCaseImpl.java:48) |
| S11 | Signup/password/name validation at request/command/UI | [SignupCommand.java:14](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/application/auth/SignupCommand.java:14); [auth-schemas.ts:3](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/demo-app-ui/src/features/auth/schemas/auth-schemas.ts:3); [bff-api.yml:542](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/resources/openapi/bff-api.yml:542) |
| S12 | Cookie issuance, refresh/logout, Keycloak grants | [AuthController.java:73](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/adapter/in/web/AuthController.java:73); [AuthController.java:103](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/adapter/in/web/AuthController.java:103); [AuthController.java:132](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/adapter/in/web/AuthController.java:132); [KeycloakTokenAdapter.java:37](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/adapter/out/keycloak/KeycloakTokenAdapter.java:37) |
| S13 | Wizard state, review, cancel, serialization | [use-claim-wizard-store.ts:52](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/demo-app-ui/src/features/claims/stores/use-claim-wizard-store.ts:52); [wizard-step-3.tsx:27](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/demo-app-ui/src/features/claims/components/wizard-step-3.tsx:27); [page.tsx:17](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/demo-app-ui/app/(authenticated)/claims/new/page.tsx:17); [SubmitClaimRequest.ts:85](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/demo-app-ui/src/lib/api/generated/models/SubmitClaimRequest.ts:85) |
| S14 | Wizard controls and raw UI schema constraints | [wizard-step-1.tsx:20](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/demo-app-ui/src/features/claims/components/wizard-step-1.tsx:20); [wizard-step-2.tsx:8](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/demo-app-ui/src/features/claims/components/wizard-step-2.tsx:8); [claim-schemas.ts:3](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/demo-app-ui/src/features/claims/schemas/claim-schemas.ts:3) |
| S15 | Admin confirmation, optimistic rollback, filtering | [claim-status-select.tsx:21](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/demo-app-ui/src/features/admin/components/claim-status-select.tsx:21); [use-admin-claims-store.ts:31](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/demo-app-ui/src/features/admin/stores/use-admin-claims-store.ts:31); [use-admin-claims-store.ts:77](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/demo-app-ui/src/features/admin/stores/use-admin-claims-store.ts:77); [page.tsx:34](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/demo-app-ui/app/(authenticated)/admin/claims/page.tsx:34) |
| S16 | Refresh wrapper, logout caller, cookie precedence/UX guard | [bff-client.ts:57](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/demo-app-ui/src/lib/api/bff-client.ts:57); [app-header.tsx:16](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/demo-app-ui/src/components/layout/app-header.tsx:16); [CookieAuthenticationFilter.java:26](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/config/CookieAuthenticationFilter.java:26); [middleware.ts:13](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/demo-app-ui/middleware.ts:13) |
| S17 | After-commit listener and direct Kafka serializer | [DomainEventListener.java:18](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/adapter/out/messaging/DomainEventListener.java:18); [KafkaDomainEventPublisher.java:28](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/adapter/out/messaging/KafkaDomainEventPublisher.java:28); [KafkaDomainEventPublisher.java:42](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/adapter/out/messaging/KafkaDomainEventPublisher.java:42) |
| S18 | Domain/CDC event consumer paths | [ClaimEventKafkaConsumer.java:28](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/adapter/in/messaging/ClaimEventKafkaConsumer.java:28); [ClaimEventKafkaConsumer.java:75](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/adapter/in/messaging/ClaimEventKafkaConsumer.java:75); [ClaimEventKafkaConsumer.java:114](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/adapter/in/messaging/ClaimEventKafkaConsumer.java:114) |
| S19 | Handshake, allowed origins, recipient/session routing | [WebSocketAuthInterceptor.java:30](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/adapter/in/websocket/WebSocketAuthInterceptor.java:30); [WebSocketConfig.java:31](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/config/WebSocketConfig.java:31); [ClaimWebSocketHandler.java:29](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/adapter/in/websocket/ClaimWebSocketHandler.java:29); [ClaimWebSocketHandler.java:74](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/adapter/in/websocket/ClaimWebSocketHandler.java:74) |
| S20 | UI socket retries and event-driven store refresh | [use-realtime-claim-updates.ts:59](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/demo-app-ui/src/features/claims/hooks/use-realtime-claim-updates.ts:59); [use-claim-realtime-handler.ts:37](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/demo-app-ui/src/features/claims/hooks/use-claim-realtime-handler.ts:37) |
| S21 | Dashboard query, cache TTL/bypass/fallback/flush | [DashboardController.java:24](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/adapter/in/web/DashboardController.java:24); [DashboardStatsCache.java:34](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/adapter/out/cache/DashboardStatsCache.java:34); [GetDashboardStatsUseCase.java:31](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/application/usecases/GetDashboardStatsUseCase.java:31) |
| S22 | API contracts and HTTP error handling | [bff-api.yml:173](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/resources/openapi/bff-api.yml:173); [bff-api.yml:472](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/resources/openapi/bff-api.yml:472); [claims-api.yml:186](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/resources/openapi/claims-api.yml:186); [claims-api.yml:527](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/resources/openapi/claims-api.yml:527); [ClaimExceptionHandler.java:21](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/adapter/in/web/ClaimExceptionHandler.java:21); [AuthExceptionHandler.java:46](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/adapter/in/web/AuthExceptionHandler.java:46); [AdminController.java:134](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/adapter/in/web/AdminController.java:134) |
| S23 | Persistence fields, constraints, mapping | [ClaimEntity.java:28](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/adapter/out/persistence/ClaimEntity.java:28); [002-create-claims-table.yaml:6](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/resources/db/changelog/changes/002-create-claims-table.yaml:6); [001-create-users-table.yaml:17](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/resources/db/changelog/changes/001-create-users-table.yaml:17); [ClaimPersistenceMapper.java:11](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/adapter/out/persistence/ClaimPersistenceMapper.java:11) |
| S24 | JSON-schema validator and required event fields | [KafkaMessageSchemaValidator.java:38](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/adapter/out/messaging/KafkaMessageSchemaValidator.java:38); [ClaimSubmittedMessage.json:6](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/resources/asyncapi/schemas/ClaimSubmittedMessage.json:6); [ClaimStatusChangedMessage.json:6](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/resources/asyncapi/schemas/ClaimStatusChangedMessage.json:6) |
| S25 | App wiring, event defaults, Keycloak connection defaults | [docker-compose.apps.yml:10](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/docker-compose.apps.yml:10); [application.yml:76](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/resources/application.yml:76); [application.yml:62](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/resources/application.yml:62); [next.config.ts:4](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/demo-app-ui/next.config.ts:4) |
| S26 | CDC connector and logical replication | [claims-connector.json:4](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/docker/compose/debezium/connector-configs/claims-connector.json:4); [docker-compose.cdc.yml:16](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/docker-compose.cdc.yml:16); [postgresql.conf:4](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/docker/compose/postgres/postgresql.conf:4) |
| S27 | Test/static-admin profiles and mock CDC shape | [ContractTestSecurityConfiguration.java:38](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/config/ContractTestSecurityConfiguration.java:38); [ContractTestSecurityConfiguration.java:35](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/config/ContractTestSecurityConfiguration.java:35); [SchemathesisSecurityConfiguration.java:38](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/src/main/java/com/example/demo/config/SchemathesisSecurityConfiguration.java:38); [TestEventController.java:60](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/adapter/in/web/TestEventController.java:60) |
| S28 | Advertised general PATCH versus controller implementation | [claims-api.yml:343](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/resources/openapi/claims-api.yml:343); [claims-api.yml:698](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/resources/openapi/claims-api.yml:698); [ClaimController.java:31](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/adapter/in/web/ClaimController.java:31) |
| S29 | Declared test tooling and test selection | [package.json:14](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/demo-app-ui/package.json:14); [pom.xml:179](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/pom.xml:179); [pom.xml:384](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/pom.xml:384); [pom.xml:235](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/pom.xml:235); [pom.xml:374](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/pom.xml:374); [pom.xml:462](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/pom.xml:462); [pom.xml:522](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/pom.xml:522); [pom.xml:577](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/bff-service/pom.xml:577); [start-app.sh:5](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/start-app.sh:5) |
| S30 | changedBy owner/actor mismatch | [ClaimStatusChanged.java:10](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/domain/claim/events/ClaimStatusChanged.java:10); [ClaimStatusChangedMessage.json:45](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/resources/asyncapi/schemas/ClaimStatusChangedMessage.json:45); [Claim.java:130](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/domain/claim/Claim.java:130) |
| S31 | Summary truncation and UI list ordering | [ClaimWebMapper.java:40](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/adapter/in/web/ClaimWebMapper.java:40); [ClaimJpaRepository.java:12](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/adapter/out/persistence/ClaimJpaRepository.java:12) |
| S32 | User-domain name constraints | [User.java:51](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/code/claims-service/src/main/java/com/example/demo/user/domain/User.java:51) |
| S33 | Keycloak token lifespan, roles, bootstrap configuration | [demo-app-realm.json:11](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/docker/compose/keycloak/demo-app-realm.json:11); [demo-app-realm.json:66](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/docker/compose/keycloak/demo-app-realm.json:66); [docker-compose.infra.yml:90](/Users/sirinapa.s/Documents/kook/Personal/Assignment/THA-QE/tech-assess-qa/demo-app/docker-compose.infra.yml:90) |

All scenarios are proposed and **Not Run**. **Document decision: Pending Review.**

