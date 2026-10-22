export type WorkOrderType = 'NETWORK_FAULT' | 'BOX_DOOR_OPEN' | 'DAMAGED_BOX_REPLACEMENT';
export type MaterialCategory = 'OPTICAL_CABLE' | 'JOINT_BOX' | 'SPLITTER';
export type WorkOrderStatusCode = 'SUBMITTED' | 'IN_PROGRESS' | 'PENDING_VERIFICATION' | 'ON_HOLD' | 'CLOSED' | 'CANCELLED';
export type WorkOrderPriority = 'URGENT' | 'HIGH' | 'ROUTINE' | 'LOW';
export type WorkOrderSlaStage = 'CLAIM' | 'ASSIGNMENT_ACCEPTANCE' | 'RESPONSE' | 'VERIFICATION' | 'REWORK' | 'COMPLETED';
export type WorkOrderSlaRiskLevel = 'NORMAL' | 'DUE_SOON' | 'BREACHED' | 'PAUSED' | 'COMPLETED';
export type WorkOrderHandlerState = 'CURRENT' | 'PENDING_ACCEPTANCE' | 'RESPONDED';
export type WorkOrderAllowedAction = 'CLAIM' | 'ASSIGN' | 'REASSIGN' | 'ACCEPT_ASSIGNMENT' | 'REJECT_ASSIGNMENT' | 'RESPOND' | 'VERIFY_APPROVE' | 'VERIFY_REJECT' | 'HOLD' | 'RESUME' | 'CANCEL' | 'WITHDRAW' | 'TRANSFER_CREATE';
export type ObservationPlatform = 'WEB' | 'ANDROID' | 'SERVER';
export type ObservationEventType = 'ERROR' | 'WARNING' | 'OPERATION' | 'DIAGNOSTIC';
export type ObservationSeverity = 'FATAL' | 'ERROR' | 'WARN' | 'INFO';
export type ObservationOutcome = 'SUCCESS' | 'FAILURE' | 'CANCELLED';
export type ObservationViewMode = 'DIAGNOSTIC' | 'BEHAVIOR';

export type AuthChallenge = {
  challengeId?: string | null;
  captchaQuestion?: string | null;
  powAlgorithm: string;
  powPrefix?: string | null;
  powDifficulty: number;
  expiresInSeconds: number;
  mode: 'SILENT' | 'INTERACTIVE' | 'COOLDOWN';
  captchaRequired: boolean;
  serverNonce?: string | null;
  retryAfterSeconds: number;
};

export type LoginResponse = {
  token?: string;
  refreshToken?: string;
  accessTokenExpiresInSeconds?: number;
  sessionId?: string;
  clientType?: 'WEB' | 'ANDROID';
  passwordChangeRequired?: boolean;
  username: string;
  name: string;
  phone?: string;
  email?: string;
  permissions: string[];
  membershipId?: string;
  tenantId?: string;
  tenantCode?: string;
  tenantName?: string;
  orgUnitId?: string;
  authorizationVersion?: number;
};

export type PasswordPolicy = {
  minLength: number;
  maxLength: number;
  requireUppercase: boolean;
  requireLowercase: boolean;
  requireDigit: boolean;
  requireSpecial: boolean;
  sourceScope?: 'PLATFORM_DEFAULT' | 'TENANT_OVERRIDE' | 'ORGANIZATION_OVERRIDE';
  sourceOrgUnitName?: string | null;
  inherited?: boolean;
  localOverride?: boolean;
};

export type AuthSessionView = {
  id: string;
  clientType: 'WEB' | 'ANDROID';
  status: 'ACTIVE' | 'REVOKED' | 'EXPIRED' | 'COMPROMISED';
  current: boolean;
  deviceName?: string;
  deviceModel?: string;
  osVersion?: string;
  appVersion?: string;
  hardwareBacked: boolean;
  securitySignals?: string;
  createdAt: string;
  lastSeenAt: string;
  idleExpiresAt: string;
  absoluteExpiresAt: string;
  revokedAt?: string;
  revokeReason?: string;
};

export type AuthSessionPage = {
  page: PageResponse<AuthSessionView>;
  activeOtherSessionCount: number;
};

export type UserStatus = 'ACTIVE' | 'INACTIVE' | 'DISABLED' | 'LOCKED' | 'DELETED';

export type AdminUser = {
  id: string;
  username: string;
  name: string;
  phone?: string;
  email?: string;
  status: UserStatus;
  createdAt: string;
  updatedAt?: string;
  tenantId: string;
  orgUnitId: string;
  orgUnitName?: string;
  departmentId?: string;
  departmentName?: string;
  authorizationVersion: number;
  systemProtected: boolean;
};

export type AccessMode = 'NONE' | 'R' | 'W' | 'RW';
export type PermissionRiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export type Permission = {
  id: string;
  name: string;
  displayName: string;
  description?: string;
  accessMode: AccessMode;
  riskLevel: PermissionRiskLevel;
  apiTokenAssignable: boolean;
};

export type PermissionGrantStatus = 'ACTIVE' | 'REVOKED' | 'SUSPENDED_UPSTREAM';
export type GrantIssuerType = 'USER' | 'IMPORT' | 'MIGRATION' | 'SYSTEM_ROOT' | 'SYSTEM_BASELINE' | 'SYSTEM_SELF_BASELINE';
export type AuthorizationOperationType = 'APPLY_TEMPLATE' | 'GRANT_PERMISSION' | 'ADJUST_PERMISSION' | 'REVOKE_PERMISSION' | 'REATTACH_PERMISSION';

export type AuthorizationGrantView = {
  id: string;
  parentGrantId?: string | null;
  permissionName: string;
  permissionDisplayName: string;
  description?: string | null;
  operationRiskLevel: PermissionRiskLevel;
  status: PermissionGrantStatus;
  effective: boolean;
  grantOption: boolean;
  validFrom?: string | null;
  expiresAt?: string | null;
  issuerType: GrantIssuerType;
  grantedByMembershipId?: string | null;
  grantedAt: string;
  reason?: string | null;
  manageable: boolean;
  manageabilityReason?: string | null;
};

export type UserAuthorizationView = {
  membershipId: string;
  authorizationVersion: number;
  grants: AuthorizationGrantView[];
  effectivePermissionCount: number;
};

export type AuthorizationTemplateOption = {
  id: string;
  code: string;
  displayName: string;
  description?: string;
  builtIn: boolean;
  enabled: boolean;
  permissionNames: string[];
};

export type AuthorizationPermissionOption = {
  name: string;
  displayName: string;
  description?: string;
  operationRiskLevel: PermissionRiskLevel;
  systemScoped: boolean;
  apiTokenAssignable: boolean;
};

export type AuthorizationOptions = {
  templates: AuthorizationTemplateOption[];
  permissions: AuthorizationPermissionOption[];
  parentGrants: {
    grantId: string;
    permissionName: string;
    permissionDisplayName: string;
    expiresAt?: string | null;
  }[];
};

export type AuthorizationProfile = {
  id: string;
  code: string;
  displayName: string;
  description?: string | null;
  builtIn: boolean;
  active: boolean;
  currentVersion?: AuthorizationProfileVersion | null;
  draftVersion?: AuthorizationProfileVersion | null;
};

export type AuthorizationProfileSummary = {
  id: string;
  code: string;
  displayName: string;
  description?: string | null;
  builtIn: boolean;
  active: boolean;
  currentVersionId?: string | null;
  currentVersionNo?: number | null;
};

export type AuthorizationProfileVersion = {
  id: string;
  versionNo: number;
  status: 'DRAFT' | 'PUBLISHED' | 'RETIRED';
  changeSummary?: string | null;
  createdAt: string;
  publishedAt?: string | null;
  rules: {
    id: string;
    permissionName: string;
    permissionDisplayName: string;
    sortOrder: number;
  }[];
};

export type AuthorizationOperationPayload = {
  type: AuthorizationOperationType;
  grantId?: string | null;
  parentGrantId?: string | null;
  templateId?: string | null;
  permissionName?: string | null;
  validFrom?: string | null;
  expiresAt?: string | null;
  grantOption?: boolean | null;
  reason?: string | null;
};

export type AuthorizationGrantSnapshot = {
  grantId?: string | null;
  parentGrantId?: string | null;
  permissionName: string;
  permissionDisplayName: string;
  status: PermissionGrantStatus;
  grantOption: boolean;
  validFrom?: string | null;
  expiresAt?: string | null;
};

export type AuthorizationPermissionChange = {
  kind: 'ADD' | 'UPDATE' | 'REMOVE';
  before?: AuthorizationGrantSnapshot | null;
  after?: AuthorizationGrantSnapshot | null;
};

export type AuthorizationPlan = {
  planToken: string;
  expiresAt: string;
  expectedAuthorizationVersion: number;
  operations: {
    type: AuthorizationOperationType;
    title: string;
    detail: string;
    increasesAccess: boolean;
    reasonRequired: boolean;
    permissionChanges: AuthorizationPermissionChange[];
  }[];
  resultingActiveGrantCount: number;
};

export type UserOrganizationMoveStrategy = 'KEEP' | 'REVOKE_ALL';

export type UserOrganizationMoveRequest = {
  targetOrgUnitId: string;
  strategy: UserOrganizationMoveStrategy;
  expectedAuthorizationVersion: number;
};

export type Department = {
  id: string;
  tenantId: string;
  orgUnitId: string;
  orgUnitName?: string;
  parentId?: string;
  code: string;
  name: string;
  path: string;
  status: 'ACTIVE' | 'SUSPENDED' | 'MERGED';
};

export type UserDepartmentMoveRequest = {
  targetDepartmentId?: string | null;
  expectedAuthorizationVersion: number;
};

export type UserOrganizationMovePreview = {
  membershipId: string;
  expectedAuthorizationVersion: number;
  selectedStrategy: UserOrganizationMoveStrategy;
  recommendedStrategy: UserOrganizationMoveStrategy;
  organization: {
    beforeOrgUnitId: string;
    beforeOrgUnitName: string;
    afterOrgUnitId: string;
    afterOrgUnitName: string;
  };
  authorization: {
    beforeActiveGrantCount: number;
    afterActiveGrantCount: number;
    changes: AuthorizationPermissionChange[];
  };
  management: {
    systemIdentity: boolean;
    activeGrantCount: number;
    reauthorizationRecommended: boolean;
  };
  workOrders: {
    openReportedWorkOrders: number;
    pendingAcceptanceAssignments: number;
    acceptedAssignments: number;
    activeResponsibilities: number;
    automaticallyReassigned: boolean;
  };
  notices: {
    severity: 'INFO' | 'WARNING';
    code: string;
    title: string;
    detail: string;
  }[];
};

export type UserWorkOrderPerformance = {
  reportedTotal: number;
  reportedLast30Days: number;
  reportedOpen: number;
  reportedClosed: number;
  assignedTotal: number;
  activeAssignments: number;
  responsesSubmitted: number;
  responsesLast30Days: number;
  responsesWithin24Hours: number;
  verificationsCompleted: number;
  verificationsApproved: number;
  averageResponseMinutes: number;
  averageVerificationMinutes: number;
  averageReportedClosureHours: number;
  responseWithin24HourRate: number;
  verificationApprovalRate: number;
};

export type AdminUserDetail = {
  user: AdminUser;
  performance: UserWorkOrderPerformance;
  capabilities: {
    organizationName: string;
    actions: Record<string, {
      allowed: boolean;
      reasonCode: string;
      message: string;
    }>;
  };
};

export type AppConfig = {
  configKey: string;
  configValue: string | null;
  description?: string;
  lastUpdatedTime?: string;
  sensitive: boolean;
  configured: boolean;
  displayValue?: string;
  requestedOrgUnitId?: string;
  requestedOrgUnitName?: string;
  sourceScope: 'PLATFORM_DEFAULT' | 'TENANT_OVERRIDE' | 'ORGANIZATION_OVERRIDE';
  sourceOrgUnitId?: string;
  sourceOrgUnitName?: string;
  inherited: boolean;
};

export type OrgUnit = {
  id: string;
  tenantId: string;
  parentId?: string;
  code: string;
  name: string;
  unitType: string;
  path: string;
  status: string;
  tenantRoot: boolean;
};

export type ConfigScopeResponse = {
  tenantWide: boolean;
  organizations: OrgUnit[];
};

export type AppRelease = {
  edition: 'NATIVE' | 'LITE';
  id: string;
  platform: 'ANDROID';
  channel: string;
  versionCode: number;
  versionName: string;
  runtimeVersion: string;
  minSupportedVersionCode: number;
  forceUpdate: boolean;
  releaseNotes?: string;
  originalFileName: string;
  storedFileName: string;
  fileSize: number;
  sha256: string;
  contentType: string;
  published: boolean;
  publishedAt?: string;
  createdAt?: string;
  updatedAt?: string;
  apkUrl: string;
};

export type ReverseGeocodeResponse = {
  gpsCoordinates: string;
  township: string;
  villageOrStreet: string;
  formattedAddress: string;
  resolutionStatus: 'CONFIRMED' | 'NEEDS_CONFIRMATION' | 'UNRESOLVED';
  candidates: ReverseGeocodeCandidate[];
};

export type ReverseGeocodeCandidate = {
  name: string;
  category: string;
  distanceMeters: number;
};

export type UserBasicInfo = {
  id: string;
  username: string;
  name: string;
};

export type Attachment = {
  id: string;
  fileName: string;
  storedFileName: string;
  fileType: string;
  fileSize: number;
  attachmentStage?: 'REPORT' | 'RESPONSE';
  imageRedactionStatus?: 'QUEUED' | 'DEFERRED' | 'PROCESSING' | 'RETRY_PENDING' | 'READY' | 'FAILED' | 'CANCELLED';
  imageRedactionMessage?: string;
};

export type MaterialUsage = {
  category: MaterialCategory;
  categoryName?: string;
  specification: string;
  quantity: number;
};

export type WorkOrderListItem = {
  id: string;
  tenantId?: string;
  workOrderNo?: string;
  location: string;
  gpsCoordinates?: string;
  reason: string;
  phone: string;
  contactPerson: string;
  status: string;
  statusCode: WorkOrderStatusCode;
  statusLabel: string;
  phaseCode?: string;
  version: number;
  responsibleOrgId?: string;
  ownerOrgId?: string;
  reportingDepartmentId?: string;
  responsibleDepartmentId?: string;
  allowedActions: WorkOrderAllowedAction[];
  currentAssignment?: WorkOrderAssignment;
  pendingAssignment?: WorkOrderAssignment;
  handler?: UserBasicInfo;
  handlerState?: WorkOrderHandlerState;
  handlerStateName?: string;
  reportType: WorkOrderType;
  reportTypeName: string;
  priority: WorkOrderPriority;
  priorityName: string;
  slaStage: WorkOrderSlaStage;
  slaStageName: string;
  slaRiskLevel: WorkOrderSlaRiskLevel;
  slaRiskName: string;
  slaDueAt?: string;
  slaPaused: boolean;
  slaBreached: boolean;
  slaEverBreached: boolean;
  slaRemainingSeconds: number;
  slaBreachSeconds: number;
  elapsedSeconds: number;
  totalHoldSeconds: number;
  boxQuantity?: number;
  replacementRequired?: boolean;
  replacementCompleted?: boolean;
  reportTime: string;
  lastUpdatedTime?: string;
  resolutionTime?: string;
  reportedBy?: UserBasicInfo;
  resolvedBy?: UserBasicInfo;
  attachmentCount: number;
  attachments?: Attachment[];
};

export type WorkOrder = WorkOrderListItem & {
  replacementResult?: string;
  resolutionDetails?: string;
  resolutionMethod?: string;
  closureReason?: string;
  closureTime?: string;
  closedBy?: UserBasicInfo;
  attachments: Attachment[];
  materialUsages?: MaterialUsage[];
  reportingOrgId?: string;
  organizationParticipations?: WorkOrderOrganizationParticipation[];
  departmentParticipations?: WorkOrderDepartmentParticipation[];
  reworkCount?: number;
  currentResponseId?: string;
  allowedViews: WorkOrderView[];
};

export type WorkOrderView = 'DETAIL' | 'HISTORY' | 'ATTACHMENTS' | 'AUDIT';

export type WorkOrderOrganizationParticipation = {
  id: string;
  orgUnitId: string;
  organizationName: string;
  relationType: 'REPORTING' | 'OWNER' | 'PREVIOUS_OWNER' | 'PENDING_TRANSFER_TARGET';
  active: boolean;
  startedAt: string;
  endedAt?: string;
  reason?: string;
};

export type WorkOrderDepartmentParticipation = {
  id: string;
  departmentId?: string;
  departmentName: string;
  relationType: 'REPORTING' | 'RESPONSIBLE' | 'PROCESSING' | 'VERIFICATION' | 'COPIED';
  active: boolean;
  startedAt: string;
  endedAt?: string;
  reason?: string;
};

export type WorkOrderTransfer = {
  id: string;
  workOrderId: string;
  sourceOrgUnitId: string;
  sourceOrgUnitName: string;
  targetOrgUnitId: string;
  targetOrgUnitName: string;
  status: 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'CANCELLED';
  requestedAt: string;
  requestReason: string;
  decidedAt?: string;
  decisionComment?: string;
  allowedActions: ('ACCEPT' | 'REJECT' | 'CANCEL')[];
};

export type WorkOrderTransferTarget = {
  id: string;
  parentId?: string;
  code: string;
  name: string;
  path: string;
};

export type WorkOrderAssignment = {
  id: string;
  state: string;
  assignmentType: string;
  membershipId: string;
  username: string;
  name: string;
  responsibleOrgId: string;
  reason?: string;
  assignedAt: string;
  acceptedAt?: string;
};

export type AssignmentCandidate = {
  membershipId: string;
  username: string;
  name: string;
  orgUnitId: string;
  orgName: string;
  activeWorkOrderCount: number;
};

export type WorkOrderEvent = {
  id: string;
  eventType: string;
  eventTypeLabel: string;
  previousStatusCode?: string;
  nextStatusCode?: string;
  operatorMembershipId?: string;
  operatorName?: string;
  reason?: string;
  occurredAt: string;
};

export type WorkOrderResponseHistory = {
  id: string;
  attemptNo: number;
  resolutionDetails: string;
  resolutionMethod: string;
  submittedBy?: UserBasicInfo;
  submittedAt: string;
  verificationDecision?: string;
  verificationDecisionLabel?: string;
  verificationComment?: string;
  verifiedBy?: UserBasicInfo;
  verifiedAt?: string;
};

export type WorkOrderTypeStatistics = {
  reportType: WorkOrderType;
  reportTypeName: string;
  total: number;
  pending: number;
  resolved: number;
  closed: number;
  boxQuantityTotal: number;
  replacementRequired: number;
  replacementCompleted: number;
};

export type WorkOrderStatusStatistics = {
  status: string;
  total: number;
};

export type WorkOrderDailyStatistics = {
  date: string;
  total: number;
};

export type WorkOrderStatistics = {
  total: number;
  pending: number;
  resolved: number;
  closed: number;
  otherStatusTotal: number;
  resolutionRate: number;
  boxQuantityTotal: number;
  replacementRequired: number;
  replacementCompleted: number;
  active: number;
  within24Hours: number;
  from24To48Hours: number;
  from48To72Hours: number;
  over72Hours: number;
  currentSlaBreached: number;
  everSlaBreached: number;
  longestActiveElapsedSeconds: number;
  byType: WorkOrderTypeStatistics[];
  byStatus: WorkOrderStatusStatistics[];
  dailyNewReports: WorkOrderDailyStatistics[];
  countyComparison: WorkOrderCountyComparison[];
};

export type WorkOrderCountyComparison = {
  countyOrgUnitId: string;
  countyName: string;
  total: number;
  active: number;
  closed: number;
  resolutionRate: number;
  currentSlaBreached: number;
  over72Hours: number;
};

export type WorkOrderStatisticsScope = {
  countyOrgUnitId: string;
  countyName: string;
};

export type NotificationSwitch = {
  name: string;
  displayName: string;
  description: string;
  enabled: boolean;
  sourceScope: 'PLATFORM_DEFAULT' | 'TENANT_OVERRIDE' | 'ORGANIZATION_OVERRIDE';
  sourceOrgUnitId?: string;
  sourceOrgUnitName?: string;
  inherited: boolean;
};

export type NotificationCenterSettings = {
  center: NotificationSwitch;
  channels: NotificationSwitch[];
  dailyOutstandingReport: DailyOutstandingReportSettings;
};

export type DailyOutstandingReportSettings = {
  switchSetting: NotificationSwitch;
  sendTime: string;
  sourceScope: NotificationSwitch['sourceScope'];
  sourceOrgUnitId?: string;
  sourceOrgUnitName?: string;
  inherited: boolean;
};

export type PageResponse<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
};

export type ObservationEventSummary = {
  id: string;
  platform: ObservationPlatform;
  eventType: ObservationEventType;
  severity: ObservationSeverity;
  category: string;
  eventName: string;
  message?: string;
  route?: string;
  operationName?: string;
  outcome?: ObservationOutcome;
  traceId?: string;
  orgUnitId?: string;
  orgUnitName?: string;
  userId?: string;
  userName?: string;
  appVersion?: string;
  osName?: string;
  osVersion?: string;
  manufacturer?: string;
  deviceModel?: string;
  networkType?: string;
  clientOccurredAt?: string;
  receivedAt: string;
};

export type ObservationEventDetail = ObservationEventSummary & {
  clientEventId: string;
  stackTrace?: string;
  installationId?: string;
  sessionId?: string;
  appBuild?: string;
  osName?: string;
  userAgent?: string;
  context?: unknown;
  breadcrumbs?: unknown;
};

export type ObservationUserOption = {
  id: string;
  name?: string;
  username: string;
  orgUnitId?: string;
  orgUnitName?: string;
};

export type RedisRuntimeStatus = {
  available: boolean;
  collectedAt: string;
  version?: string;
  uptimeSeconds?: number;
  connectedClients?: number;
  usedMemoryBytes?: number;
  maxMemoryBytes?: number;
  maxMemoryPolicy?: string;
  totalKeys?: number;
  keyspaceHits?: number;
  keyspaceMisses?: number;
  hitRate?: number;
  aofEnabled: boolean;
  aofLastWriteStatus?: string;
  lastSuccessfulSaveEpochSeconds?: number;
  observationReadyBatches?: number;
  observationProcessingBatches?: number;
  observationDeadLetterBatches?: number;
  error?: string;
};

export type SubmitWorkOrderData = {
  reportType: WorkOrderType;
  priority: WorkOrderPriority;
  location: string;
  gpsCoordinates?: string;
  reason: string;
  phone: string;
  contactPerson: string;
  boxQuantity?: number;
  uploadedAttachmentIds?: string[];
};

export type WorkOrderSlaPolicy = {
  id: string;
  reportType: WorkOrderType;
  reportTypeName: string;
  priority: WorkOrderPriority;
  priorityName: string;
  claimMinutes: number;
  assignmentAcceptanceMinutes: number;
  responseMinutes: number;
  verificationMinutes: number;
  reworkMinutes: number;
  pauseOnHold: boolean;
  maxSingleHoldMinutes: number;
  maxTotalHoldMinutes: number;
  updatedAt?: string;
};

export type WorkOrderSlaPolicyUpdate = Pick<WorkOrderSlaPolicy,
  'claimMinutes' | 'assignmentAcceptanceMinutes' | 'responseMinutes' | 'verificationMinutes' | 'reworkMinutes' | 'pauseOnHold' | 'maxSingleHoldMinutes' | 'maxTotalHoldMinutes'>;

export type LocalAttachment = {
  uri: string;
  name: string;
  type: string;
  mediaType: 'image' | 'video';
  size?: number;
};

export type SubmitWorkOrderRequest = {
  data: SubmitWorkOrderData;
  attachments: LocalAttachment[];
};

export type RespondWorkOrderRequest = {
  expectedVersion: number;
  resolutionDetails: string;
  resolutionMethod: string;
  replacementCompleted?: boolean;
  replacementResult?: string;
  materialUsages?: MaterialUsage[];
  attachments?: LocalAttachment[];
  uploadedAttachmentIds?: string[];
};

export type AppUpdateCheckResponse = {
  edition?: 'NATIVE' | 'LITE';
  updateAvailable: boolean;
  forceUpdate: boolean;
  nativeUpgradeRequired: boolean;
  platform: 'ANDROID';
  channel: string;
  versionCode?: number;
  versionName?: string;
  runtimeVersion?: string;
  minSupportedVersionCode?: number;
  releaseNotes?: string;
  apkUrl?: string;
  apkSize?: number;
  sha256?: string;
  rangeDownloadSupported?: boolean;
  downloadChunkSize?: number;
  publishedAt?: string;
  message?: string;
};

export type FileTransferPurpose = 'WORK_ORDER_ATTACHMENT' | 'APP_RELEASE';

export type FileTransferSession = {
  uploadId: string;
  purpose: FileTransferPurpose;
  status: 'INITIATED' | 'COMPLETED' | 'CLAIMED' | 'ABORTED';
  fileName: string;
  contentType: string;
  totalSize: number;
  chunkSize: number;
  receivedBytes: number;
  sha256?: string;
  storedFileName?: string;
  expiresAt: string;
  imageRedactionStatus?: 'QUEUED' | 'DEFERRED' | 'PROCESSING' | 'RETRY_PENDING' | 'READY' | 'FAILED' | 'CANCELLED';
  imageRedactionMessage?: string;
};

export type FileTransferChunkResponse = {
  uploadId: string;
  chunkIndex: number;
  receivedBytes: number;
  totalSize: number;
  readyToComplete: boolean;
};

export type ApiTokenStatus = 'ACTIVE' | 'EXPIRED' | 'REVOKED';

export type ApiTokenView = {
  id: string;
  name: string;
  tokenPrefix: string;
  createdAt: string;
  expiresAt?: string;
  lastUsedAt?: string;
  revokedAt?: string;
  status: ApiTokenStatus;
  permissionNames: string[];
};

export type ApiTokenPermissionOption = {
  name: string;
  displayName: string;
  description: string;
  accessMode: 'NONE' | 'R' | 'W' | 'RW';
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
};

export type ApiTokenCreated = {
  tokenInfo: ApiTokenView;
  token: string;
};
