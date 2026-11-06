import { randomUUID } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { expect, test, type APIRequestContext, type APIResponse } from '@playwright/test';

type Role = 'CLAIMANT' | 'ADMIN';
type Account = { email: string; password: string };
type ClaimResponse = {
  claimId: string;
  userId: string;
  incidentDate: string;
  incidentLocation: string;
  description: string;
  claimAmount: number;
  status: string;
  createdAt: string;
  updatedAt: string;
};
type Evidence = {
  runId: string;
  claimId?: string;
  verifiedStatuses: string[];
  requests: { action: string; route: string; status: number | 'transport-error'; csrfCookieCleared?: boolean }[];
};

function accountFor(role: Role): Account {
  const email = process.env[`QA_${role}_EMAIL`];
  const password = process.env[`QA_${role}_PASSWORD`];
  if (email || password) {
    if (!email || !password) {
      throw new Error(`Set both QA_${role}_EMAIL and QA_${role}_PASSWORD; values are not logged`);
    }
    return { email, password };
  }

  // Read the existing local Compose realm at runtime; never embed its credentials in test source or reports.
  let realm: {
    users?: {
      email?: string;
      enabled?: boolean;
      realmRoles?: string[];
      credentials?: { type?: string; value?: string }[];
    }[];
  };
  try {
    const path = resolve(__dirname, '../../../../docker/compose/keycloak/demo-app-realm.json');
    realm = JSON.parse(readFileSync(path, 'utf8'));
  } catch {
    throw new Error(`Local realm unavailable: provide QA_${role}_EMAIL and QA_${role}_PASSWORD`);
  }
  const user = realm.users?.find((candidate) => candidate.enabled !== false
    && candidate.realmRoles?.some((value) => value.toUpperCase() === role));
  const credential = user?.credentials?.find((value) => value.type === 'password' && value.value);
  if (!user?.email || !credential?.value) {
    throw new Error(`No usable ${role} account in the local realm; provide the QA_${role}_* variables`);
  }
  return { email: user.email, password: credential.value };
}

async function responseJson<T>(
  action: string,
  route: string,
  send: () => Promise<APIResponse>,
  expectedStatus: number,
  evidence: Evidence,
): Promise<T> {
  let response: APIResponse;
  try {
    response = await send();
  } catch {
    evidence.requests.push({ action, route, status: 'transport-error' });
    // Do not forward Playwright transport call logs, which may contain authentication headers/body.
    throw new Error(`${action}: BFF transport unavailable; check the existing Compose environment`);
  }
  const csrfCookieCleared = response.headersArray().some(({ name, value }) => name.toLowerCase() === 'set-cookie'
    && /^XSRF-TOKEN=/i.test(value) && /max-age=0(?:;|$)/i.test(value));
  evidence.requests.push({ action, route, status: response.status(), csrfCookieCleared });
  expect(response.status(), `${action}: expected HTTP ${expectedStatus} from ${route}`).toBe(expectedStatus);
  expect(response.headers()['content-type'], `${action}: expected a JSON response`)
    .toMatch(/^application\/(?:[a-z0-9.+-]+\+)?json\b/i);
  try {
    return await response.json() as T;
  } catch {
    throw new Error(`${action}: response is not valid JSON; body omitted to protect sensitive data`);
  }
}

async function authenticate(client: APIRequestContext, role: Role, evidence: Evidence) {
  const route = '/api/auth/login';
  const user = await responseJson<{ userId: string; role: Role }>(
    `Authenticate ${role}`, route,
    () => client.post(route, { data: accountFor(role) }), 200, evidence,
  );
  expect(user.role, `${role} must have the expected local application role`).toBe(role);

  const state = await client.storageState();
  const token = state.cookies.find((cookie) => cookie.name === 'access_token')?.value;
  if (!token) {
    throw new Error(`${role} login did not set the access_token cookie`);
  }
  let claims: { sub?: string; realm_access?: { roles?: string[] } };
  try {
    claims = JSON.parse(Buffer.from(token.split('.')[1], 'base64url').toString('utf8'));
  } catch {
    throw new Error(`${role} access cookie is not a readable JWT; its value is not logged`);
  }
  expect(typeof claims.sub, 'JWT subject must be present').toBe('string');
  expect(claims.sub, 'Login identity and token subject must agree').toBe(user.userId);
  expect(claims.realm_access?.roles?.some((value) => value.toUpperCase() === role),
    `${role} must also have the expected JWT realm role`).toBe(true);
  return user.userId;
}

async function csrfHeaders(client: APIRequestContext, role: Role, evidence: Evidence) {
  const route = '/api/auth/csrf';
  const csrf = await responseJson<{ headerName: string; token: string }>(
    `Obtain ${role} CSRF`, route, () => client.get(route), 200, evidence,
  );
  expect(csrf.headerName).toBe('X-XSRF-TOKEN');
  expect(typeof csrf.token).toBe('string');
  expect(csrf.token.length > 0, 'CSRF token must not be empty').toBe(true);
  return { [csrf.headerName]: csrf.token };
}

test('CM-IT-001/003: claimant submission persists and admin advances its status', async ({ playwright, baseURL }, testInfo) => {
  const claimant = await playwright.request.newContext({ baseURL, timeout: 10_000 });
  const admin = await playwright.request.newContext({ baseURL, timeout: 10_000 });
  const runId = randomUUID();
  const evidence: Evidence = { runId, verifiedStatuses: [], requests: [] };
  const submitted = {
    incidentDate: new Date(Date.now() - 86_400_000).toISOString().slice(0, 10),
    incidentLocation: `QA API ${runId}`,
    description: `Isolated claim submission/status workflow ${runId}.`,
    claimAmount: 1234.56,
  };

  try {
    await test.step('Verify existing BFF readiness', async () => {
      const health = await responseJson<{ status: string }>('BFF readiness', '/actuator/health',
        () => claimant.get('/actuator/health'), 200, evidence);
      expect(health.status).toBe('UP');
    });

    const ownerId = await test.step('Authenticate claimant', () => authenticate(claimant, 'CLAIMANT', evidence));
    const adminId = await test.step('Authenticate admin', () => authenticate(admin, 'ADMIN', evidence));
    expect(adminId, 'Claimant and admin must be separate test identities').not.toBe(ownerId);
    const claimantCsrf = await csrfHeaders(claimant, 'CLAIMANT', evidence);

    const created = await test.step('Submit a new isolated claim through BFF', async () => {
      const claim = await responseJson<ClaimResponse>('Submit claim', '/api/claims',
        () => claimant.post('/api/claims', { data: submitted, headers: claimantCsrf }), 201, evidence);
      expect(claim.claimId).toMatch(/^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/i);
      expect(claim).toMatchObject({ ...submitted, userId: ownerId, status: 'SUBMITTED' });
      expect(Number.isFinite(Date.parse(claim.createdAt))).toBe(true);
      expect(claim.updatedAt).toBe(claim.createdAt);
      evidence.claimId = claim.claimId;
      return claim;
    });

    const route = `/api/claims/${created.claimId}`;
    async function readBack(status: string) {
      const claim = await responseJson<ClaimResponse>(`Read persisted ${status}`, route,
        () => claimant.get(route, { headers: { 'Cache-Control': 'no-cache' } }), 200, evidence);
      expect(claim).toMatchObject({ ...submitted, claimId: created.claimId, userId: ownerId, status });
      expect(Number.isFinite(Date.parse(claim.createdAt))).toBe(true);
      expect(Date.parse(claim.updatedAt)).toBeGreaterThanOrEqual(Date.parse(claim.createdAt));
      evidence.verifiedStatuses.push(status);
      return claim;
    }

    const persisted = await test.step('Retrieve submitted claim and verify persisted data', () => readBack('SUBMITTED'));
    let previousUpdate = Date.parse(persisted.updatedAt);
    for (const status of ['UNDER_REVIEW', 'APPROVED']) {
      await test.step(`Admin changes this new claim to ${status} and claimant reads it back`, async () => {
        // Authentication responses can invalidate CSRF cookies; obtain the current token for every write.
        const adminCsrf = await csrfHeaders(admin, 'ADMIN', evidence);
        // Only the validated ID returned by this run's successful POST is ever patched.
        const updateRoute = `/api/admin/claims/${created.claimId}/status`;
        const changed = await responseJson<ClaimResponse>(`Admin update ${status}`, updateRoute,
          () => admin.patch(updateRoute, { data: { newStatus: status }, headers: adminCsrf }), 200, evidence);
        expect(changed).toMatchObject({ ...submitted, claimId: created.claimId, userId: ownerId, status });
        const actual = await readBack(status);
        expect(Date.parse(actual.createdAt), 'Creation time must remain unchanged').toBe(Date.parse(persisted.createdAt));
        expect(Date.parse(actual.updatedAt)).toBeGreaterThanOrEqual(previousUpdate);
        previousUpdate = Date.parse(actual.updatedAt);
      });
    }
  } finally {
    // No credentials, cookies, tokens, or raw response bodies are attached or persisted.
    await testInfo.attach('claim-workflow-evidence', {
      body: Buffer.from(JSON.stringify(evidence, null, 2)),
      contentType: 'application/json',
    });
    await Promise.all([claimant.dispose(), admin.dispose()]);
    // Keep the new, uniquely marked claim for review; no existing claim or database row is deleted.
  }
});
