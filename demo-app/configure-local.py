#!/usr/bin/env python3
"""Create local-only credentials and a Keycloak import without overwriting local files."""

import json
import os
from pathlib import Path
import re
import secrets
import shlex


ROOT = Path(__file__).resolve().parent
ENV_FILE = ROOT / '.env'
REALM_FILE = ROOT / 'docker/compose/keycloak/demo-app-realm.json'
SECRET_KEYS = (
    'POSTGRES_PASSWORD', 'KEYCLOAK_ADMIN_PASSWORD', 'BFF_SERVICE_CLIENT_SECRET',
    'QA_ADMIN_PASSWORD', 'QA_CLAIMANT_PASSWORD',
)


def read_env(path):
    values = {}
    for line in path.read_text().splitlines():
        if not line.strip() or line.lstrip().startswith('#'):
            continue
        key, value = line.split('=', 1)
        parts = shlex.split(value, comments=True)
        if len(parts) > 1:
            raise SystemExit('Invalid local environment format; values omitted')
        values[key.strip()] = parts[0] if parts else ''
    return values


def write_private(path, text, mode=0o600):
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, mode)
    with os.fdopen(descriptor, 'w') as output:
        os.fchmod(output.fileno(), mode)
        output.write(text)


def configure():
    if not ENV_FILE.exists():
        if REALM_FILE.exists():
            raise SystemExit('Existing realm found: provide a matching .env; no local file was overwritten')
        values = read_env(ROOT / '.env.example')
        for key in SECRET_KEYS:
            values[key] = values[key] or secrets.token_urlsafe(32)
        write_private(ENV_FILE, '# Generated local credentials. Never commit.\n'
                      + ''.join(f'{key}={shlex.quote(value)}\n' for key, value in values.items()))

    values = read_env(ENV_FILE)
    required = (*SECRET_KEYS, 'POSTGRES_USER', 'KEYCLOAK_ADMIN_USERNAME',
                'QA_ADMIN_EMAIL', 'QA_CLAIMANT_EMAIL')
    if any(not values.get(key) for key in required):
        raise SystemExit('Required local environment settings are missing; values omitted')

    if REALM_FILE.exists():
        print('Existing .env and realm preserved without changes.')
        return

    template = json.loads((ROOT / 'config/demo-app-realm.example.json').read_text())

    def render(value):
        if isinstance(value, dict):
            return {key: render(item) for key, item in value.items()}
        if isinstance(value, list):
            return [render(item) for item in value]
        if isinstance(value, str) and re.fullmatch(r'\$\{[A-Z_]+\}', value):
            return values[value[2:-1]]
        return value

    # The host directory is private; the read-only bind-mounted file must be
    # readable by Keycloak's UID 1000 even when the host UID differs (e.g. macOS).
    write_private(REALM_FILE, json.dumps(render(template), indent=2) + '\n', mode=0o644)
    print('Local .env and realm ready; credentials are not printed. See QA_SETUP.md.')


if __name__ == '__main__':
    configure()
