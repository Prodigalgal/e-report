import type { AuthorizationGrantSnapshot, AuthorizationPlan } from '@/types/api';

type AuthorizationGrantSearchItem = {
  permissionName: string;
  permissionDisplayName: string;
  issuerType: string;
  status: string;
};

type PermissionOption = { name: string; displayName: string };

export function filterAuthorizationGrants<T extends AuthorizationGrantSearchItem>(
  grants: T[] | undefined,
  searchTerm = '',
): T[] {
  const keyword = searchTerm.trim().toLocaleLowerCase('zh-CN');
  return (grants || []).filter(grant => (
    !keyword || `${grant.permissionDisplayName} ${grant.permissionName} ${grant.issuerType} ${grant.status}`
      .toLocaleLowerCase('zh-CN')
      .includes(keyword)
  ));
}

export function filterPermissionOptions<T extends PermissionOption>(
  permissions: T[] | undefined,
  searchTerm = '',
  limit = 50,
): T[] {
  const keyword = searchTerm.trim().toLocaleLowerCase('zh-CN');
  return (permissions || []).filter(permission => (
    !keyword || `${permission.displayName} ${permission.name}`.toLocaleLowerCase('zh-CN').includes(keyword)
  )).slice(0, limit);
}

export type AuthorizationDiffFieldKey = 'permission' | 'parentGrantId' | 'grantOption' | 'status' | 'validFrom' | 'expiresAt';
export type AuthorizationDiffFieldValue = string | boolean | null;
export type AuthorizationDiffRow = {
  key: string;
  kind: 'ADD' | 'UPDATE' | 'REMOVE';
  permissionName: string;
  permissionDisplayName: string;
  fields: {
    field: AuthorizationDiffFieldKey;
    before: AuthorizationDiffFieldValue;
    after: AuthorizationDiffFieldValue;
  }[];
};

const DIFF_FIELD_KEYS: AuthorizationDiffFieldKey[] = [
  'permission', 'parentGrantId', 'grantOption', 'status', 'validFrom', 'expiresAt',
];

function diffFieldValue(
  snapshot: AuthorizationGrantSnapshot | null | undefined,
  field: AuthorizationDiffFieldKey,
): AuthorizationDiffFieldValue {
  if (!snapshot) return null;
  if (field === 'permission') return `${snapshot.permissionDisplayName} · ${snapshot.permissionName}`;
  return snapshot[field] ?? null;
}

export function buildAuthorizationDiffRows(plan: AuthorizationPlan | null | undefined): AuthorizationDiffRow[] {
  return (plan?.operations || []).flatMap(operation => operation.permissionChanges || []).map((change, index) => {
    const snapshot = change.after || change.before!;
    const fields = DIFF_FIELD_KEYS
      .map(field => ({
        field,
        before: diffFieldValue(change.before, field),
        after: diffFieldValue(change.after, field),
      }))
      .filter(entry => (
        change.kind === 'UPDATE'
          ? entry.before !== entry.after
          : entry.field !== 'permission'
      ));
    return {
      key: `${change.kind}-${snapshot.permissionName}-${index}`,
      kind: change.kind,
      permissionName: snapshot.permissionName,
      permissionDisplayName: snapshot.permissionDisplayName,
      fields,
    };
  });
}
