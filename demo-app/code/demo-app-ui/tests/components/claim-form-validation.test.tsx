import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import NewClaimPage from '@/app/(authenticated)/claims/new/page';
import { useClaimWizardStore } from '@/src/features/claims/stores/use-claim-wizard-store';

const boundaries = vi.hoisted(() => ({
  push: vi.fn(),
  submitClaim: vi.fn(),
  fetch: vi.fn(),
}));

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: boundaries.push }),
}));

vi.mock('@/src/lib/api/bff-client', () => ({
  claimsApi: { submitClaim: boundaries.submitClaim },
}));

const validLocation = '12 Example Road';
const validDate = '2026-11-05';
const minimumAmountMessage = 'Claim amount must be at least $0.01';
const maximumAmountMessage = 'Claim amount cannot exceed $1,000,000';

beforeEach(() => {
  // Mock Date only: user-event and Testing Library retain their real asynchronous timers.
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date('2026-11-06T12:00:00Z'));
  useClaimWizardStore.getState().resetWizard();
  boundaries.fetch.mockRejectedValue(new Error('Network calls are outside this component-test group'));
  vi.stubGlobal('fetch', boundaries.fetch);
});

afterEach(() => {
  expect(boundaries.submitClaim).not.toHaveBeenCalled();
  expect(boundaries.fetch).not.toHaveBeenCalled();
  expect(boundaries.push).not.toHaveBeenCalled();
});

function setupForm() {
  const user = userEvent.setup();
  render(<NewClaimPage />);
  return {
    user,
    date: screen.getByLabelText(/when did the incident occur/i) as HTMLInputElement,
    location: screen.getByRole('textbox', { name: /where did the incident occur/i }),
    amount: screen.getByRole('spinbutton', { name: /claim amount/i }),
  };
}

type Form = ReturnType<typeof setupForm>;

function selectDate(form: Form, value: string) {
  // jsdom has no native calendar picker; dispatch its change event through the real input.
  fireEvent.change(form.date, { target: { value } });
}

async function fillValidIncident(form: Form, date = validDate, amount = '1234.56') {
  selectDate(form, date);
  await form.user.type(form.location, validLocation);
  await form.user.type(form.amount, amount);
  await waitFor(() => expect(screen.getByRole('button', { name: 'Next' })).toBeEnabled());
}

async function expectFieldError(field: HTMLElement, message: string) {
  const alert = await screen.findByRole('alert');
  expect(alert).toHaveTextContent(message);
  expect(field).toHaveAttribute('aria-invalid', 'true');
  expect(field.getAttribute('aria-describedby')?.split(/\s+/)).toContain(alert.id);
}

async function expectNavigationBlocked(form: Form, step: 1 | 2 = 1) {
  const next = screen.getByRole('button', { name: 'Next' });
  await waitFor(() => expect(next).toBeDisabled());
  await form.user.click(next);
  expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', String(step));
  expect(screen.getByRole('heading', {
    name: step === 1 ? 'Incident Details' : 'Supporting Details',
  })).toBeVisible();
  expect(screen.queryByRole('heading', {
    name: step === 1 ? 'Supporting Details' : 'Review Your Claim',
  })).not.toBeInTheDocument();
}

async function enterDescriptionStep(form: Form) {
  await fillValidIncident(form);
  await form.user.click(screen.getByRole('button', { name: 'Next' }));
  await screen.findByRole('heading', { name: 'Supporting Details' });
  return screen.getByRole('textbox', { name: /describe what happened/i });
}

describe('CM-CT-001: required fields and visible validation errors', () => {
  it('keeps an initially empty form on Incident Details', async () => {
    const form = setupForm();
    await expectNavigationBlocked(form);
  });

  it.each([
    ['date', 'Incident date is required'],
    ['location', 'Location must be at least 5 characters'],
    ['amount', 'Claim amount is required'],
  ] as const)('shows the error and blocks Next when required %s is cleared', async (field, message) => {
    const form = setupForm();
    await fillValidIncident(form);

    if (field === 'date') {
      selectDate(form, '');
    } else {
      await form.user.clear(form[field]);
    }

    await expectFieldError(form[field], message);
    await expectNavigationBlocked(form);
  });

  it('requires a description before leaving Supporting Details', async () => {
    const form = setupForm();
    const description = await enterDescriptionStep(form);
    await expectNavigationBlocked(form, 2);
    await form.user.type(description, 'A valid description');
    await waitFor(() => expect(screen.getByRole('button', { name: 'Next' })).toBeEnabled());

    await form.user.clear(description);

    await expectFieldError(description, 'Description must be at least 10 characters');
    await expectNavigationBlocked(form, 2);
  });
});

describe('CM-CT-001: incident dates', () => {
  it.each(['2026-11-07', '2027-01-01'])('rejects future date %s with a visible error', async (date) => {
    const form = setupForm();
    await fillValidIncident(form);

    selectDate(form, date);

    expect(form.date).toHaveAttribute('max', '2026-11-06');
    await expectFieldError(form.date, 'Incident date cannot be in the future');
    await expectNavigationBlocked(form);
  });

  it.each(['not-a-date', '2026-02-30'])('blocks an invalid native date value: %s', async (date) => {
    const form = setupForm();
    await fillValidIncident(form);

    selectDate(form, date);

    // Native date inputs sanitize malformed/calendar-invalid strings to an empty value.
    expect(form.date).toHaveValue('');
    await expectFieldError(form.date, 'Incident date is required');
    await expectNavigationBlocked(form);
  });

  it.each(['2026-11-05', '2026-11-06'])('allows past/today date %s to reach the next step', async (date) => {
    const form = setupForm();
    await fillValidIncident(form, date);

    await form.user.click(screen.getByRole('button', { name: 'Next' }));

    expect(await screen.findByRole('heading', { name: 'Supporting Details' })).toBeVisible();
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '2');
  });
});

describe('CM-CT-001: claim amount boundaries', () => {
  it.each([
    ['-0.01', minimumAmountMessage],
    ['0', minimumAmountMessage],
    ['0.001', minimumAmountMessage],
    ['0.009', minimumAmountMessage],
    ['0.009999', minimumAmountMessage],
    ['1000000.01', maximumAmountMessage],
    ['1000001', maximumAmountMessage],
  ])('rejects amount %s and prevents progression', async (amount, message) => {
    const form = setupForm();
    await fillValidIncident(form);

    await form.user.clear(form.amount);
    await form.user.type(form.amount, amount);

    await expectFieldError(form.amount, message);
    await expectNavigationBlocked(form);
  });

  it.each(['0.01', '1', '999999.99', '1000000'])('allows amount %s to progress', async (amount) => {
    const form = setupForm();
    await fillValidIncident(form, validDate, amount);
    expect(form.amount).toHaveValue(Number(amount));
    expect(form.amount).toHaveAttribute('aria-invalid', 'false');

    await form.user.click(screen.getByRole('button', { name: 'Next' }));

    expect(await screen.findByRole('heading', { name: 'Supporting Details' })).toBeVisible();
  });
});

describe('CM-CT-001: location feedback', () => {
  it('rejects a four-character location', async () => {
    const form = setupForm();
    await fillValidIncident(form);
    await form.user.clear(form.location);
    await form.user.type(form.location, 'Road');

    await expectFieldError(form.location, 'Location must be at least 5 characters');
    await expectNavigationBlocked(form);
  });

  it('clears location feedback and enables progression when the user corrects the value', async () => {
    const form = setupForm();
    await fillValidIncident(form);
    await form.user.clear(form.location);
    await form.user.type(form.location, 'Road');
    await expectFieldError(form.location, 'Location must be at least 5 characters');

    await form.user.type(form.location, 's');

    await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument());
    expect(form.location).toHaveAttribute('aria-invalid', 'false');
    expect(screen.getByText('5/200')).toBeVisible();
    await waitFor(() => expect(screen.getByRole('button', { name: 'Next' })).toBeEnabled());
    await form.user.click(screen.getByRole('button', { name: 'Next' }));
    expect(await screen.findByRole('heading', { name: 'Supporting Details' })).toBeVisible();
  });

  it('caps user-entered location text at 200 characters', async () => {
    const form = setupForm();
    await fillValidIncident(form);
    await form.user.clear(form.location);
    await form.user.click(form.location);

    await form.user.paste('L'.repeat(201));

    expect(form.location).toHaveValue('L'.repeat(200));
    expect(screen.getByText('200/200')).toBeVisible();
    await waitFor(() => expect(screen.getByRole('button', { name: 'Next' })).toBeEnabled());
  });
});

describe('CM-CT-001: description validation', () => {
  it.each([1, 9])('rejects a %i-character description', async (length) => {
    const form = setupForm();
    const description = await enterDescriptionStep(form);
    await form.user.click(description);

    await form.user.paste('D'.repeat(length));

    await expectFieldError(description, 'Description must be at least 10 characters');
    expect(screen.getByText(`${length}/1000`)).toBeVisible();
    await expectNavigationBlocked(form, 2);
  });

  it.each([10, 1000])('allows a %i-character description to reach Review', async (length) => {
    const form = setupForm();
    const description = await enterDescriptionStep(form);
    await form.user.click(description);
    await form.user.paste('D'.repeat(length));
    await waitFor(() => expect(screen.getByRole('button', { name: 'Next' })).toBeEnabled());

    await form.user.click(screen.getByRole('button', { name: 'Next' }));

    expect(await screen.findByRole('heading', { name: 'Review Your Claim' })).toBeVisible();
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '3');
  });

  it('caps user-entered description text at 1000 characters', async () => {
    const form = setupForm();
    const description = await enterDescriptionStep(form);
    await form.user.click(description);

    await form.user.paste('D'.repeat(1001));

    expect(description).toHaveValue('D'.repeat(1000));
    expect(screen.getByText('1000/1000')).toBeVisible();
    await waitFor(() => expect(screen.getByRole('button', { name: 'Next' })).toBeEnabled());
  });
});
