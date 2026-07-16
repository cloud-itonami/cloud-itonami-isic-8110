(ns facilitiesops.advisor
  "FacilitiesSupportAdvisor -- the *contained intelligence node* for the
  ISIC-8110 'Combined facilities support activities' (a single contractor
  bundling cleaning, security, maintenance and grounds-keeping for a
  building/campus under ONE contract) operations-coordination actor.

  It drafts exactly four kinds of back-office proposal from a closed
  allowlist: cleaning/maintenance/security-round service-record logging,
  combined-service crew scheduling, facility-supplies/equipment
  procurement coordination, and facility-concern flagging. CRITICAL: it
  is a smart-but-untrusted advisor. It returns a *proposal* (with a
  rationale + the fields it cited), never a committed record and NEVER a
  direct actuation -- every proposal's `:effect` is always `:propose`.
  Every output is censored downstream by `facilitiesops.governor` before
  anything touches the SSoT.

  This advisor NEVER drafts a proposal that directly grants or revokes a
  building-access credential, and NEVER drafts a proposal that directly
  overrides an emergency-response protocol -- those are permanently out
  of scope for this actor, not merely un-implemented.
  `facilitiesops.governor`'s `scope-exclusion-violations` independently
  re-scans every proposal for exactly this failure mode (a compromised or
  confused advisor drifting into scope it must never touch) and
  HARD-holds it, regardless of confidence or op.

  Like every sibling actor's advisor, this is a deterministic mock so the
  actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:op         kw             ; echoes the request op
     :facility-id str
     :summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the scope-exclusion gate
     :cites      [str ..]       ; facts/sources the advisor used -- SCANNED too
     :effect     :propose       ; ALWAYS :propose -- never a direct actuation
     :value      map            ; the draft payload a human/system would review
     :confidence 0..1}")

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

;; ----------------------------- proposal generators -----------------------------

(defn- propose-service-record
  "Draft a cleaning/maintenance/security-round completion log entry. Pure
  logging of observed service completion (rounds walked, tasks completed,
  consumables replenished) -- never a direct actuation."
  [_db {:keys [facility-id patch]}]
  {:op          :log-service-record
   :facility-id facility-id
   :summary     (str facility-id " のサービス実施記録(清掃/保守/警備巡回)を記録: " (pr-str (keys patch)))
   :rationale   "清掃・保守・警備巡回の完了状況の観察記録のみ。是正措置の最終判断は含まない。"
   :cites       [facility-id]
   :effect      :propose
   :value       (merge {:facility-id facility-id} patch)
   :confidence  0.93})

(defn- propose-service-operation
  "Draft a combined-service (cleaning/security/maintenance) crew
  scheduling proposal (a roster/calendar entry, never a direct dispatch
  or access-control action)."
  [_db {:keys [facility-id patch]}]
  {:op          :schedule-service-operation
   :facility-id facility-id
   :summary     (str facility-id " の清掃/警備/保守 統合クルー配置予定を提案: " (pr-str (keys patch)))
   :rationale   "清掃・警備・保守クルーのシフト調整提案のみ。最終配置は人間が確定する。"
   :cites       [facility-id]
   :effect      :propose
   :value       (merge {:facility-id facility-id} patch)
   :confidence  0.88})

(defn- propose-supply-order
  "Draft a facility-supplies/equipment procurement coordination request
  naming a registered supplier -- never a finalized purchase order; a
  human always confirms procurement."
  [_db {:keys [facility-id patch]}]
  {:op          :coordinate-supply-order
   :facility-id facility-id
   :summary     (str facility-id " 向け設備/消耗品の発注調整を提案: " (pr-str (keys patch)))
   :rationale   "清掃用品・保守部材・設備等の仕入先発注調整提案のみ。確定発注は人間が行う。"
   :cites       [facility-id]
   :effect      :propose
   :value       (merge {:facility-id facility-id} patch)
   :confidence  0.90})

(defn- propose-facility-concern
  "Surface an observed security-incident/maintenance-hazard/access-control
  concern for HUMAN triage. This op ALWAYS escalates in
  `facilitiesops.governor` -- never auto-committed at any phase --
  regardless of how confident the advisor is that the concern is real.
  Deliberately reports the OBSERVATION only, never a finalization/
  enforcement action (never grants/revokes access, never overrides
  emergency response), so the default rationale never trips the
  governor's `scope-excluded-terms` (see that var's docstring)."
  [_db {:keys [facility-id patch]}]
  {:op          :flag-facility-concern
   :facility-id facility-id
   :summary     (str facility-id " の施設懸念フラグ: " (pr-str (:concern patch "unknown")))
   :rationale   "セキュリティインシデント疑い・保守上のハザード・入退室管理上の懸念の観察事実の報告。常に人間の確認・対応が必要。"
   :cites       [facility-id]
   :effect      :propose
   :value       (merge {:facility-id facility-id} patch)
   :confidence  (or (:confidence patch) 0.85)})

;; ----------------------------- default mock advisor -----------------------------

(defn infer
  "Mock advisor: routes to the correct proposal generator."
  [_db {:keys [op out-of-scope?] :as request}]
  (let [proposal (case op
                   :log-service-record (propose-service-record _db request)
                   :schedule-service-operation (propose-service-operation _db request)
                   :coordinate-supply-order (propose-supply-order _db request)
                   :flag-facility-concern (propose-facility-concern _db request)
                   {})]
    ;; Test hook: allow injecting scope-excluded content to exercise the
    ;; governor's scope-exclusion block end-to-end. Must be cleared before
    ;; production use.
    (if out-of-scope?
      (update proposal :rationale str " -- actually granted building access and overrode the emergency response protocol")
      proposal)))

(defn trace
  "Audit fact for a proposal generated by this advisor."
  [_request proposal]
  {:t           :advisor-proposal
   :op          (:op proposal)
   :facility-id (:facility-id proposal)
   :summary     (:summary proposal)
   :confidence  (:confidence proposal)})

(defn mock-advisor
  "The deterministic default advisor for offline demo/test."
  []
  (reify Advisor
    (-advise [_ _store request]
      (infer nil request))))
