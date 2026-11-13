package tw.testa.unit;

/**
 * 單位內角色。MVP 的介面把它們合併成兩種：
 * 承辦人 = OWNER + PROCTOR，教師 = AUTHOR + GRADER；資料上仍分開存，之後要拆細不必改表。
 */
public enum Role {
    /** 單位負責人：建立考試、匯入名單、指派角色、公布成績 */
    OWNER,
    /** 出題者：建立與編輯題庫 */
    AUTHOR,
    /** 監考：登入單、即時狀態、重設登入碼 */
    PROCTOR,
    /** 評分者：只看被指派的匿名答卷 */
    GRADER
}
