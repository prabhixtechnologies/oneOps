let accessTokenGetter: () => string | null = () => null;
let orgIdGetter: () => string | null = () => null;

export function setAuthTokenBridge(
  getToken: () => string | null,
  getOrg: () => string | null,
) {
  accessTokenGetter = getToken;
  orgIdGetter = getOrg;
}

export function getAccessToken(): string | null {
  return accessTokenGetter();
}

export function getOrgId(): string | null {
  return orgIdGetter();
}
