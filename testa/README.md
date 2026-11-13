# Testa 題庫編輯器（原型）

開源、自行部署的線上考試平台，第一階段：教師題庫編輯器。
純前端原型，使用 Mock Data，資料存在瀏覽器的 localStorage。

## 開發

```bash
npm install
npm run dev        # 開發伺服器
npm run build      # 型別檢查 + 建置
```

## 技術

Vue 3（Composition API、`<script setup>`）、TypeScript、Vite、Tailwind CSS v4、
reka-ui（shadcn-vue 的底層元件）、Lucide、KaTeX、vue-draggable-plus、vue-sonner。

`src/components/ui` 依 shadcn-vue 的寫法（reka-ui + cva + `cn()`）手寫並套用本專案的 design tokens，
沒有透過 shadcn-vue CLI 產生。之後要加元件可以直接 `npx shadcn-vue add`，再把顏色類別換成本專案的 token。

## 頁面

| 路徑 | 內容 |
| --- | --- |
| `/login` | 登入。考生：准考證號＋6 碼登入碼（示範 `A115-0001`／`482915`）；教職員：`teacher@testa.tw`、`admin@testa.tw`，密碼任意 4 碼以上 |
| `/` | 首頁：三個主要動作、進行中的考試、我的題庫 |
| `/banks` | 題庫列表：新增、刪除、進入編輯 |
| `/banks/:id` | 題庫編輯器（專注模式）：編輯／模擬作答 |
| `/exams` | 考試：建立草稿、發布、取消發布、結束考試 |
| `/exams/:id` | 單場考試三步驟：考生名單（白名單匯入）→ 登入單 → 監考 |
| `/grading` | 閱卷：一次一份、匿名、AI 建議分數 |
| `/admin` | 後台管理（僅管理員）：使用者、單位、系統設定 |
| `/student` | 考生端：我的考試（可以作答、即將開始、已交卷與成績） |
| `/student/exam/:id` | 考生作答（專注模式）：核對身分與須知 → 計時作答 → 交卷、成績與檢討 |

導覽列在桌面是頂部分頁，手機是底部分頁。未登入會被導向登入頁；非管理員看不到也進不去 `/admin`；考生只能進考生端，教師與管理員不進考生端。

**白名單**：考生沒有帳號，只能用名單上的准考證號與登入碼登入，而且只看得到自己被指派的考試。考生開始作答與交卷後，教師端「監考」的狀態會同步更新。

考生作答與教師的「模擬作答」共用 `ExamPreview` 元件（`real` 屬性切換）。考生交卷後，教師端該場考試的交卷人數會加一。

**回饋慣例**：所有無法輕易回復的動作（刪除、重設、交卷、結束考試、停用帳號、登出等）都透過 `useConfirm()` 跳出確認視窗；動作完成後用 toast 回報。

## 版面（題庫編輯器）

編輯與作答共用同一種題卡：編輯時看到的題目就是考生作答時的樣子，只是文字與選項可以直接點進去改。

- **編輯**：試卷資訊卡 + 題卡。選取的題卡多出題型、配分與操作列；含公式的文字平常顯示排版結果，點進去才顯示 LaTeX 原始碼（`InlineMathField`）。
- **模擬作答**：倒數計時、一次一題、標記、答案卡、交卷、自動批改與逐題檢討。

## 結構

```
src/
  router.ts               路由與登入、權限守衛
  App.vue                 全域：RouterView、確認視窗、Toast
  layouts/AppLayout.vue   導覽列 + 內容區 + 手機底部分頁
  pages/                  Login、Home、Banks、Editor、Exams、Grading、Admin
  composables/
    useAuth.ts            登入狀態（模擬）
    useBanks.ts           題庫狀態、自動儲存、增刪改排序
    useWorkspace.ts       考試、閱卷、使用者、單位、設定的示範資料
    useConfirm.ts         確認視窗（Promise 介面）
    useQuestionActions.ts 帶確認與 toast 的題目動作
    useTheme.ts           深淺色
  components/
    ui/                   Button、Switch、Tip、Menu、SidePanel、ConfirmDialog…
    app/                  Logo、UserMenu
    editor/               題庫編輯器的各個區塊
  lib/                    math（KaTeX）、question、grading（自動批改）、utils
  data/                   示範題庫、模擬的 AI 辨識結果
```

## Design tokens

| Token | 用途 |
| --- | --- |
| `desk` / `paper` / `sunken` | 品牌色畫布、題卡、輸入區 |
| `ink` / `ink-2` / `ink-3` | 鉛筆：文字三階、考生塗黑的圈 |
| `brand` / `brand-strong` / `brand-soft` | 黑板綠：品牌、主要動作、正確答案 |
| `grade` / `grade-soft` | 紅筆：配分、未完成、刪除 |
| `marker` / `marker-ink` | 黃粉筆：AI 匯入待確認 |

全站使用同一組黑體；題目內容套用 `.exam-text`，編輯與作答共用。

## 尚未實作

後端與資料庫、真正的登入驗證、真正的 AI 解析與評分（皆為假資料）、考卷組卷、成績報表、防作弊與斷線續考。考試、閱卷、後台的資料只存在記憶體，重新整理後會還原。
