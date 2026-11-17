import { UNIT_ROLE_LABEL } from '@/composables/useAuth'

/** Audit log entry as the backend sends it (ids, not names; the admin page maps them back). */
export interface AuditEntry {
  id: number
  at: string
  actorType: string
  actorId: number | null
  actorLabel: string | null
  action: string
  targetType: string | null
  targetId: string | null
  unitId: number | null
  reason: string | null
  detail: string | null
  ip: string | null
}

/** Actions by topic, for the filter; every action the backend records needs a label here. */
export const ACTION_GROUPS: { label: string; actions: Record<string, string> }[] = [
  {
    label: '登入與帳號',
    actions: {
      LOGIN: '登入',
      LOGIN_FAILED: '登入失敗',
      LOGOUT: '登出',
      PASSWORD_CHANGED: '變更密碼',
      PASSWORD_RESET: '重設密碼',
      STAFF_CREATED: '建立帳號',
      STAFF_ENABLED: '啟用帳號',
      STAFF_DISABLED: '停用帳號',
      STAFF_DELETED: '永久刪除帳號',
      SYSTEM_ADMIN_GRANTED: '設為系統管理員',
      SYSTEM_ADMIN_REVOKED: '取消系統管理員',
    },
  },
  {
    label: '單位與角色',
    actions: {
      UNIT_CREATED: '建立單位',
      UNIT_RENAMED: '單位改名',
      UNIT_ENABLED: '啟用單位',
      UNIT_DISABLED: '停用單位',
      UNIT_ROLE_GRANTED: '指派角色',
      UNIT_ROLE_REVOKED: '移除角色',
    },
  },
  {
    label: '題庫與考試',
    actions: {
      BANK_CREATED: '建立題庫',
      BANK_DELETED: '刪除題庫',
      EXAM_CREATED: '建立考試',
      EXAM_PUBLISHED: '發布考試',
      EXAM_WITHDRAWN: '取消發布',
      EXAM_ENDED: '結束考試',
      EXAM_ARCHIVED: '封存考試',
      EXAM_UNARCHIVED: '取消封存',
      EXAM_DELETED: '刪除考試',
      EXAM_PURGED: '刪除考試資料',
    },
  },
  {
    label: '考生與作答',
    actions: {
      CANDIDATES_IMPORTED: '匯入考生名單',
      CANDIDATE_REMOVED: '移除考生',
      CANDIDATE_CODE_RESET: '重設登入碼',
      CANDIDATE_CODES_ISSUED: '產生登入碼',
      CANDIDATE_LOGIN: '考生登入',
      CANDIDATE_LOGIN_FAILED: '考生登入失敗',
      CANDIDATE_LOGOUT: '考生登出',
      ATTEMPT_STARTED: '開始作答',
      ATTEMPT_SUBMITTED: '交卷',
      ATTEMPT_EXTENDED: '補時',
    },
  },
  {
    label: '評分與成績',
    actions: {
      GRADER_ASSIGNED: '指派評分者',
      GRADER_UNASSIGNED: '取消指派評分者',
      GRADING_REOPENED: '重開評分',
      RESULTS_PUBLISHED: '公布成績',
      RESULTS_EXPORTED: '匯出成績',
      CANDIDATE_RESULTS_CHANGED: '考生查詢成績設定',
    },
  },
  {
    label: '系統',
    actions: {
      SYSTEM_SETUP: '初始設定',
      SETTINGS_CHANGED: '變更系統設定',
      BACKUP_REQUESTED: '建立備份',
      BACKUP_DOWNLOADED: '下載備份',
      BACKUP_DELETE_REQUESTED: '刪除備份',
      BACKUP_RESTORE_REQUESTED: '要求還原備份',
      BACKUP_RESTORED: '完成還原備份',
      EMERGENCY_ACCESS_GRANTED: '授權緊急檢視',
      EMERGENCY_ACCESS_REVOKED: '撤銷緊急檢視',
      EMERGENCY_ACCESS_VIEWED: '緊急檢視考試',
    },
  },
]

export const ACTION_LABEL: Record<string, string> = Object.assign({}, ...ACTION_GROUPS.map((g) => g.actions))

/** Failures and destructive actions stand out in the list */
export const isAlarming = (action: string) =>
  action.endsWith('_FAILED') || ['STAFF_DELETED', 'EXAM_PURGED', 'EXAM_DELETED', 'BACKUP_RESTORE_REQUESTED', 'BACKUP_RESTORED', 'EMERGENCY_ACCESS_VIEWED'].includes(action)

export const DETAIL_LABEL: Record<string, string> = {
  bad_password: '密碼錯誤',
  unknown_account: '帳號不存在',
  locked: '帳號鎖定中',
  inactive: '帳號已停用',
  system_admin: '系統管理員',
  unknown_ticket: '准考證號不在名單上',
  bad_code: '登入碼錯誤',
  replaced_session: '在別處重新登入，舊連線已登出',
  auto: '系統自動收卷',
  ...UNIT_ROLE_LABEL,
}

export const ACTOR_TYPE_LABEL: Record<string, string> = {
  STAFF: '教職員',
  CANDIDATE: '考生',
  ANONYMOUS: '未登入',
  SYSTEM: '系統',
}
