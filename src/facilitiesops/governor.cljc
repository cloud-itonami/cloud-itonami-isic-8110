(ns facilitiesops.governor
  "FacilitiesSupportGovernor -- the independent compliance layer that
  earns the FacilitiesSupportAdvisor the right to commit. The advisor has
  no notion of whether a facility's combined-services contract is
  actually registered and verified, whether a named supply-order
  supplier is itself a registered/verified counterparty, whether its own
  proposed `:effect` secretly claims a direct actuation instead of a mere
  proposal, or whether it has silently drifted into a permanently
  out-of-scope decision area, so this MUST be a separate system able to
  *reject* a proposal and fall back to HOLD.

  This actor's scope is deliberately narrow -- COORDINATION ONLY
  (cleaning/maintenance/security-round service-record logging, combined
  cleaning/security/maintenance crew scheduling, facility-
  supplies/equipment procurement coordination, and facility-concern
  flagging). It NEVER performs or authorizes:
    - directly finalizing a building-access-credential grant or
      revocation (issuing, activating, revoking or deactivating a
      keycard/badge/door-code or any other access credential)
    - directly overriding, bypassing or disabling an emergency-response
      protocol (fire-alarm override, emergency-lockdown bypass,
      life-safety-system disablement)

  Four HARD checks, ALL permanent, un-overridable by any human approval:

    1. Facility unverified         -- the target facility's combined
                                       facility-services contract record
                                       must exist AND be independently
                                       confirmed `:registered?`/
                                       `:verified?` in the store before
                                       ANY proposal for it may commit or
                                       even escalate. Never trusts a
                                       proposal's own claim about the
                                       facility -- re-derived from the
                                       facility's own record, the same
                                       'ground truth, not self-report'
                                       discipline every sibling actor's
                                       governor uses.
    2. Supplier unverified         -- for `:coordinate-supply-order`
                                       ONLY, the proposal's own drafted
                                       `:value` must name a
                                       `:supplier-id` that resolves to an
                                       independently `:registered?`/
                                       `:verified?` supplier record. A
                                       missing supplier-id, or one that
                                       resolves to an unregistered or
                                       unverified supplier, is a HARD
                                       block.
    3. Effect not :propose         -- every proposal's `:effect` MUST be
                                       `:propose`. Any other effect value
                                       is, by construction, a claim to
                                       directly actuate/commit outside
                                       governance -- HARD block, not
                                       merely low-confidence.
    4. Scope exclusion             -- ANY proposal (regardless of op)
                                       whose op, summary, rationale,
                                       cites or draft value touches
                                       directly finalizing a building-
                                       access-credential grant/revocation
                                       or directly overriding/bypassing/
                                       disabling an emergency-response
                                       protocol is a HARD, PERMANENT
                                       block -- this actor's charter
                                       excludes that territory
                                       structurally, not as a rollout
                                       milestone. Evaluated
                                       UNCONDITIONALLY on every proposal.
                                       An op outside the closed four-op
                                       allowlist is the SAME failure mode
                                       (an advisor proposing something it
                                       was never authorized to propose)
                                       and is folded into this same
                                       check. `:flag-facility-concern`
                                       itself is never excluded by this
                                       check -- surfacing a security-
                                       incident/maintenance-hazard/
                                       access-control concern for a human
                                       is exactly this actor's job; only
                                       FINALIZING/enforcing/directly-
                                       acting-on that concern (granting
                                       or revoking access, overriding
                                       emergency response) is excluded
                                       (see `scope-excluded-terms` below
                                       -- phrased as the finalization/
                                       execution ACTION, never a bare
                                       noun like 'access' or 'security',
                                       so the default mock advisor's own
                                       `:flag-facility-concern` rationale
                                       never self-trips this check).

  Two ESCALATE (SOFT) gates, either forces human sign-off:
    - LLM confidence below the floor.
    - The op is `:flag-facility-concern` -- ALWAYS escalates to a human,
      regardless of confidence, regardless of how clean the proposal
      otherwise is. `facilitiesops.phase` independently agrees:
      `:flag-facility-concern` is never a member of any phase's `:auto`
      set either -- two layers, not one.
    - A `:coordinate-supply-order` whose drafted `:value` names an
      `:estimated-cost` above `supply-cost-threshold` -- a large-value
      facility-supplies/equipment procurement proposal always needs a
      human sign-off, even when the governor and phase would otherwise
      allow auto-commit."
  (:require [kotoba.lang.text :as str]
            [facilitiesops.store :as store]))

(def confidence-floor 0.6)

(def supply-cost-threshold
  "Example single-facility supplies/equipment procurement threshold
  (USD-equivalent units, domain-illustrative -- not a universal
  cross-domain constant). A `:coordinate-supply-order` proposal citing an
  `:estimated-cost` above this value ALWAYS escalates to human sign-off,
  regardless of confidence or rollout phase."
  2000.0)

(def allowed-ops
  "The closed proposal-op allowlist -- an op outside this set is a scope
  violation by construction (see `scope-exclusion-violations`). Note:
  neither a building-access-credential-grant/revocation op nor an
  emergency-response-override op is a member of this set -- they are
  structurally absent, not merely gated."
  #{:log-service-record :schedule-service-operation
    :coordinate-supply-order :flag-facility-concern})

(def always-escalate-ops
  "Ops that ALWAYS require human sign-off, clean or not."
  #{:flag-facility-concern})

(def scope-excluded-terms
  "Case-insensitive substrings that mark a proposal as touching a
  permanently out-of-scope decision area -- directly finalizing a
  building-access-credential grant/revocation, or directly overriding/
  bypassing/disabling an emergency-response protocol. Scanned across the
  proposal's op/summary/rationale/cites/value, never trusting the
  advisor's own framing of its intent.

  CRITICAL: every term here is phrased as the finalization/execution
  ACTION (e.g. 'granted building access', 'overrode the emergency
  response protocol'), never a bare noun like 'access', 'credential',
  'security' or 'emergency' -- a bare noun would accidentally match
  inside this actor's own legitimate `:flag-facility-concern` default
  proposal text (whose whole job is to talk about security-incident/
  maintenance-hazard/access-control concerns) and self-block the happy
  path. See
  `facilitiesops.governor-test/default-mock-advisor-proposals-never-self-trip-scope-exclusion`
  for the regression test."
  ["grant building access" "granted building access" "granting building access"
   "grant access credential" "granted the access credential" "granting the access credential"
   "issue an access credential" "issued an access credential" "issuing an access credential"
   "issue an access badge" "issued an access badge" "issuing an access badge"
   "activate the keycard" "activated the keycard" "activating the keycard"
   "revoke building access" "revoked building access" "revoking building access"
   "revoke the access credential" "revoked the access credential" "revoking the access credential"
   "revoke the access badge" "revoked the access badge" "revoking the access badge"
   "deactivate the keycard" "deactivated the keycard" "deactivating the keycard"
   "override the emergency response" "overrode the emergency response" "overriding the emergency response"
   "bypass the emergency response" "bypassed the emergency response" "bypassing the emergency response"
   "disable the emergency response" "disabled the emergency response" "disabling the emergency response"
   "override the fire alarm" "overrode the fire alarm" "overriding the fire alarm"
   "disable the life-safety system" "disabled the life-safety system" "disabling the life-safety system"
   "finalize the access-credential grant" "finalize the access-credential revocation"
   "finalize the emergency-response override"
   "入退室権限を付与した" "アクセス権限を発行した" "入退室権限を剥奪した" "アクセス権限を失効させた"
   "緊急対応を上書きした" "緊急時対応をバイパスした" "緊急対応システムを無効化した" "防災システムを無効化した"])

;; ----------------------------- checks -----------------------------

(defn- facility-unverified-violations
  "The target facility's combined-services contract must exist AND be
  independently `:registered?`/`:verified?` in the store -- never trust
  the proposal's own `:facility-id` claim without a facility lookup."
  [{:keys [facility-id]} st]
  (let [f (store/facility-record st facility-id)]
    (when-not (and f (:registered? f) (:verified? f))
      [{:rule :facility-unverified
        :detail (str facility-id " は未登録または未検証の施設契約 -- いかなる提案も進められない")}])))

(defn- supplier-unverified-violations
  "For `:coordinate-supply-order` ONLY, the proposal's own drafted
  `:value` must name a `:supplier-id` that resolves to an independently
  `:registered?`/`:verified?` supplier record. A missing supplier-id, or
  one that resolves to an unregistered/unverified supplier, is a HARD
  block -- never trust the proposal's own supplier claim without a store
  lookup, the SAME 'ground truth, not self-report' discipline as
  `facility-unverified-violations`, reapplied to the supply-chain
  counterparty."
  [proposal st]
  (when (= :coordinate-supply-order (:op proposal))
    (let [supplier-id (get-in proposal [:value :supplier-id])
          v (and supplier-id (store/supplier-record st supplier-id))]
      (when-not (and v (:registered? v) (:verified? v))
        [{:rule :supplier-unverified
          :detail (str (or supplier-id "(supplier-id missing)")
                        " は未登録または未検証の仕入先 -- 発注調整提案を進められない")}]))))

(defn- effect-not-propose-violations
  "`:effect` must ALWAYS be `:propose` -- any other value is a claim to
  directly actuate/commit outside governance."
  [proposal]
  (when (not= :propose (:effect proposal))
    [{:rule :effect-not-propose
      :detail (str ":effect は :propose のみ許可されるが " (pr-str (:effect proposal)) " が提案された")}]))

(defn- text-blob
  "Flatten every advisor-authored field on a proposal into one lower-cased
  blob the scope-exclusion scan checks."
  [proposal]
  (str/lower (pr-str (select-keys proposal [:op :summary :rationale :cites :value]))))

(defn- scope-exclusion-violations
  "HARD, PERMANENT block: a proposal outside the closed op allowlist, or
  one whose content touches directly finalizing a building-access-
  credential grant/revocation or directly overriding/bypassing/disabling
  an emergency-response protocol, regardless of confidence or how clean
  every other check is. Evaluated UNCONDITIONALLY on every proposal."
  [proposal]
  (let [op (:op proposal)
        blob (text-blob proposal)]
    (cond
      (not (contains? allowed-ops op))
      [{:rule :op-not-allowed
        :detail (str (pr-str op) " は許可された操作(closed allowlist)に含まれない")}]

      (some #(str/includes? blob %) scope-excluded-terms)
      [{:rule :scope-excluded
        :detail "入退室権限の発行/失効や緊急対応プロトコルの上書き/無効化など確定行為(access-credential/emergency-response finalization)に触れる提案は永久に禁止"}])))

(defn- high-cost-supply-order?
  "A `:coordinate-supply-order` proposal citing an `:estimated-cost` above
  `supply-cost-threshold` -- always needs human sign-off (SOFT escalate,
  not a hard block: the order itself is in scope, only its size requires
  a human)."
  [proposal]
  (and (= :coordinate-supply-order (:op proposal))
       (some-> proposal :value :estimated-cost (> supply-cost-threshold))))

(defn check
  "Censors a FacilitiesSupportAdvisor proposal against the governor
  rules. Returns {:ok? bool :violations [..] :confidence c :escalate?
  bool :high-stakes? bool :hard? bool}."
  [request _context proposal store]
  (let [facility-id (or (:facility-id proposal) (:facility-id request))
        hard (into []
                   (concat (facility-unverified-violations {:facility-id facility-id} store)
                           (supplier-unverified-violations proposal store)
                           (effect-not-propose-violations proposal)
                           (scope-exclusion-violations proposal)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (or (always-escalate-ops (:op proposal))
                              (high-cost-supply-order? proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t           :governor-hold
   :op          (:op request)
   :actor       (:actor-id context)
   :facility-id (:facility-id request)
   :disposition :hold
   :basis       (mapv :rule (:violations verdict))
   :violations  (:violations verdict)
   :confidence  (:confidence verdict)})
