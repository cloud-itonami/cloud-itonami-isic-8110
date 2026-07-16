(ns facilitiesops.governor-test
  "Pure unit tests of `facilitiesops.governor/check` against hand-built
  proposals -- the fast, focused complement to `governor-contract-test`'s
  full-graph integration coverage."
  (:require [clojure.test :refer [deftest is testing]]
            [facilitiesops.advisor :as adv]
            [facilitiesops.governor :as gov]
            [facilitiesops.store :as store]))

(def facility-1 {:facility-id "facility-1" :name "Harborview Office Campus" :registered? true :verified? true})
(def facility-3 {:facility-id "facility-3" :name "Northside Retail Center" :registered? true :verified? false})
(def supplier-1 {:supplier-id "supplier-1" :name "CleanSupply Wholesale Ltd." :registered? true :verified? true})
(def supplier-2 {:supplier-id "supplier-2" :name "Unverified Janitorial Imports Co." :registered? true :verified? false})

(defn- clean-proposal [op facility-id]
  {:op op :facility-id facility-id :summary "s" :rationale "routine facility-support coordination"
   :cites [facility-id] :effect :propose :value {} :confidence 0.85})

(defn- clean-supply-order [facility-id supplier-id cost]
  (assoc (clean-proposal :coordinate-supply-order facility-id)
         :value {:facility-id facility-id :supplier-id supplier-id :estimated-cost cost}))

(deftest facility-unregistered-is-hard
  (testing "no facility record at all -> HARD hold"
    (let [s (store/mem-store {"facility-1" facility-1})
          verdict (gov/check {} nil (clean-proposal :log-service-record "unknown-facility") s)]
      (is (true? (:hard? verdict)))
      (is (some #{:facility-unverified} (map :rule (:violations verdict)))))))

(deftest facility-unverified-is-hard
  (testing "facility registered but not yet verified -> HARD hold"
    (let [s (store/mem-store {"facility-3" facility-3})
          verdict (gov/check {} nil (clean-proposal :log-service-record "facility-3") s)]
      (is (true? (:hard? verdict)))
      (is (some #{:facility-unverified} (map :rule (:violations verdict)))))))

(deftest supplier-missing-on-supply-order-is-hard
  (testing "supply-order proposal with no :supplier-id at all -> HARD hold"
    (let [s (store/mem-store {"facility-1" facility-1} {"supplier-1" supplier-1})
          verdict (gov/check {} nil (clean-supply-order "facility-1" nil 100.0) s)]
      (is (true? (:hard? verdict)))
      (is (some #{:supplier-unverified} (map :rule (:violations verdict)))))))

(deftest supplier-unregistered-on-supply-order-is-hard
  (testing "supply-order proposal naming an unknown supplier -> HARD hold"
    (let [s (store/mem-store {"facility-1" facility-1} {"supplier-1" supplier-1})
          verdict (gov/check {} nil (clean-supply-order "facility-1" "unknown-supplier" 100.0) s)]
      (is (true? (:hard? verdict)))
      (is (some #{:supplier-unverified} (map :rule (:violations verdict)))))))

(deftest supplier-unverified-on-supply-order-is-hard
  (testing "supply-order proposal naming a registered-but-unverified supplier -> HARD hold"
    (let [s (store/mem-store {"facility-1" facility-1} {"supplier-1" supplier-1 "supplier-2" supplier-2})
          verdict (gov/check {} nil (clean-supply-order "facility-1" "supplier-2" 100.0) s)]
      (is (true? (:hard? verdict)))
      (is (some #{:supplier-unverified} (map :rule (:violations verdict)))))))

(deftest supplier-verified-on-supply-order-is-not-hard-on-supplier-check
  (testing "supply-order proposal naming a verified supplier never trips :supplier-unverified"
    (let [s (store/mem-store {"facility-1" facility-1} {"supplier-1" supplier-1})
          verdict (gov/check {} nil (clean-supply-order "facility-1" "supplier-1" 100.0) s)]
      (is (empty? (filter #(= :supplier-unverified (:rule %)) (:violations verdict)))))))

(deftest supplier-check-is-scoped-to-supply-order-only
  (testing "non-supply-order ops never trip :supplier-unverified, even with no suppliers registered at all"
    (let [s (store/mem-store {"facility-1" facility-1})]
      (doseq [op [:log-service-record :schedule-service-operation :flag-facility-concern]]
        (let [verdict (gov/check {} nil (clean-proposal op "facility-1") s)]
          (is (empty? (filter #(= :supplier-unverified (:rule %)) (:violations verdict)))
              (str "op " op " must never trip :supplier-unverified")))))))

(deftest effect-not-propose-is-hard
  (testing "any :effect other than :propose is a HARD, un-overridable block"
    (let [s (store/mem-store {"facility-1" facility-1})
          verdict (gov/check {} nil (assoc (clean-proposal :schedule-service-operation "facility-1") :effect :commit) s)]
      (is (true? (:hard? verdict)))
      (is (some #{:effect-not-propose} (map :rule (:violations verdict)))))))

(deftest op-outside-allowlist-is-hard
  (testing "an op outside the closed four-op allowlist is a scope violation"
    (let [s (store/mem-store {"facility-1" facility-1})
          verdict (gov/check {} nil (clean-proposal :finalize-access-credential-grant "facility-1") s)]
      (is (true? (:hard? verdict)))
      (is (some #{:op-not-allowed} (map :rule (:violations verdict)))))))

(deftest access-grant-finalization-content-is-hard-and-permanent
  (testing "a proposal whose rationale touches directly granting building access is HARD-blocked regardless of op/confidence"
    (let [s (store/mem-store {"facility-1" facility-1})
          poisoned (assoc (clean-proposal :log-service-record "facility-1")
                          :rationale "granted building access to the visiting contractor at the east entrance"
                          :confidence 0.99)
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest access-revocation-content-is-hard
  (testing "a proposal touching revoking the access credential is HARD-blocked, same as granting"
    (let [s (store/mem-store {"facility-1" facility-1})
          poisoned (assoc (clean-proposal :log-service-record "facility-1")
                          :rationale "revoked the access credential for the departed contractor before they left the building"
                          :confidence 0.90)
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest emergency-response-override-content-is-hard
  (testing "a proposal touching overriding the emergency response protocol is HARD-blocked"
    (let [s (store/mem-store {"facility-1" facility-1})
          poisoned (assoc (clean-proposal :schedule-service-operation "facility-1")
                          :summary "night crew should override the emergency response protocol to re-enter the sealed wing")
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest fire-alarm-override-content-is-hard
  (testing "a proposal touching overriding the fire alarm is HARD-blocked"
    (let [s (store/mem-store {"facility-1" facility-1} {"supplier-1" supplier-1})
          poisoned (assoc (clean-supply-order "facility-1" "supplier-1" 100.0)
                          :summary "maintenance overrode the fire alarm during the equipment test")
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest legitimate-facility-concern-is-not-scope-excluded
  (testing "flagging observed security-incident/maintenance-hazard/access-control concerns as a FACILITY CONCERN (not a finalization) never trips scope-exclusion -- this actor's core valid use case must not be self-blocked"
    (let [s (store/mem-store {"facility-1" facility-1})
          concern (assoc (clean-proposal :flag-facility-concern "facility-1")
                         :value {:concern "tailgating observed at loading-dock access point, smoke detector fault in stairwell B"})
          verdict (gov/check {} nil concern s)]
      (is (empty? (filter #(= :scope-excluded (:rule %)) (:violations verdict)))
          "raw observation content (access-control/security/maintenance) is exactly what this op exists to surface"))))

(deftest facility-concern-always-escalates-clean
  (testing ":flag-facility-concern is always high-stakes/escalate, even when otherwise clean and high confidence"
    (let [s (store/mem-store {"facility-1" facility-1})
          verdict (gov/check {} nil (assoc (clean-proposal :flag-facility-concern "facility-1") :confidence 0.99) s)]
      (is (false? (:hard? verdict)))
      (is (true? (:high-stakes? verdict)))
      (is (true? (:escalate? verdict))))))

(deftest high-cost-supply-order-always-escalates
  (testing "a :coordinate-supply-order above the cost threshold is high-stakes/escalate, even when otherwise clean and high confidence"
    (let [s (store/mem-store {"facility-1" facility-1} {"supplier-1" supplier-1})
          expensive (assoc (clean-supply-order "facility-1" "supplier-1" 5000.0) :confidence 0.97)
          verdict (gov/check {} nil expensive s)]
      (is (false? (:hard? verdict)))
      (is (true? (:high-stakes? verdict)))
      (is (true? (:escalate? verdict))))))

(deftest low-cost-supply-order-does-not-force-escalate
  (testing "a :coordinate-supply-order at or below the cost threshold does not trip the high-cost escalate gate"
    (let [s (store/mem-store {"facility-1" facility-1} {"supplier-1" supplier-1})
          cheap (assoc (clean-supply-order "facility-1" "supplier-1" 480.0) :confidence 0.9)
          verdict (gov/check {} nil cheap s)]
      (is (false? (:hard? verdict)))
      (is (false? (:high-stakes? verdict)))
      (is (false? (:escalate? verdict))))))

;; ----------------------------- self-trip regression -----------------------------
;;
;; A known bug class in this actor fleet: the governor's own
;; scope-exclusion term list is sometimes phrased as a bare noun (e.g.
;; "access" or "security"), which then accidentally matches inside the
;; mock advisor's own DEFAULT rationale/disclaimer text for a legitimate,
;; allowed proposal -- causing the actor to self-block its own happy
;; path. This is a dedicated regression test: every op the default mock
;; advisor can generate, with default (non-`out-of-scope?`) request
;; patches, must NEVER trip `:scope-excluded` or `:op-not-allowed`.
(deftest default-mock-advisor-proposals-never-self-trip-scope-exclusion
  (testing "the default mock advisor's own proposals for every allowed op never trip the governor's scope-exclusion check"
    (let [s (store/mem-store {"facility-1" facility-1} {"supplier-1" supplier-1})]
      (doseq [op [:log-service-record :schedule-service-operation :coordinate-supply-order
                  :flag-facility-concern]]
        (let [patch (if (= op :coordinate-supply-order)
                      {:item "janitorial consumables restock" :estimated-cost 480.0 :supplier-id "supplier-1"}
                      {})
              proposal (adv/infer nil {:op op :facility-id "facility-1" :patch patch})
              verdict (gov/check {:facility-id "facility-1"} nil proposal s)]
          (is (empty? (filter #(= :scope-excluded (:rule %)) (:violations verdict)))
              (str "default advisor proposal for " op " must never self-trip :scope-excluded -- rationale/summary: "
                   (pr-str (select-keys proposal [:summary :rationale]))))
          (is (empty? (filter #(= :op-not-allowed (:rule %)) (:violations verdict)))
              (str "default advisor proposal for " op " must always be inside the closed op allowlist")))))))

(deftest out-of-scope-injection-still-trips-scope-exclusion
  (testing "the advisor's own out-of-scope? test hook genuinely does trip scope-exclusion -- confirms the check is not accidentally a no-op"
    (let [s (store/mem-store {"facility-1" facility-1})
          proposal (adv/infer nil {:op :log-service-record :facility-id "facility-1" :out-of-scope? true :patch {}})
          verdict (gov/check {:facility-id "facility-1"} nil proposal s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))
