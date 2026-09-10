(ns facilitiesops.store
  "SSoT for the ISIC-8110 'Combined facilities support activities'
  operations-COORDINATION actor, behind a `Store` protocol so the backend
  is a swap, not a rewrite -- the same seam every `cloud-itonami-isic-*`
  actor in this fleet uses.

  This actor coordinates the back-office operations of a bundled
  facility-management contract: a single contractor combining cleaning,
  security, maintenance and grounds-keeping for a building/campus under
  ONE contract. It logs service-round completion, schedules combined
  cleaning/security/maintenance crews, coordinates facility-
  supplies/equipment procurement with a registered supplier, and flags
  security-incident/maintenance-hazard/access-control concerns for a
  human. It NEVER directly grants or revokes a building-access credential
  and NEVER directly overrides an emergency-response protocol -- see
  `facilitiesops.governor`'s `scope-exclusion-violations`, a HARD,
  permanent, un-overridable block.

  `MemStore` -- atom of EDN. The deterministic default for dev/tests/demo
  (no deps). A `facilities` directory keyed by `:facility-id` STRING and a
  `suppliers` directory keyed by `:supplier-id` STRING (never keywords --
  consistent keying from the start, avoiding the silent-miss bug that has
  plagued earlier sibling actors).

  A registered/verified facility-contract record (the building/campus's
  combined-facility-services contract) must exist before ANY proposal
  targeting that facility may ever commit or escalate --
  `facilitiesops.governor`'s `facility-unverified-violations` re-derives
  this from the facility's own `:registered?`/`:verified?` fields, never
  from proposal self-report. A `:coordinate-supply-order` proposal
  additionally names a registered supplier via its own `:supplier-id`;
  the SAME 'ground truth, not self-report' discipline applies via
  `supplier-unverified-violations`.

  The ledger stays append-only: which facility a proposal targeted, which
  operation, on what basis, committed/held/escalated and approved by whom
  is always a query over an immutable log.")

(defprotocol Store
  (facility-record [s facility-id] "Registered facility-contract record, or nil.
    Facility map: {:facility-id .. :name .. :registered? bool :verified? bool}.")
  (all-facility-records [s])
  (supplier-record [s supplier-id] "Registered supplier record, or nil.
    Supplier map: {:supplier-id .. :name .. :registered? bool :verified? bool}.")
  (all-supplier-records [s])
  (ledger [s] "the append-only immutable decision-fact log")
  (coordination-log [s] "the append-only committed coordination-proposal history")
  (commit-record! [s record] "apply a committed proposal's record to the SSoT")
  (append-ledger! [s fact] "append one immutable decision fact")
  (with-facility-records [s facilities] "replace/seed the facility directory (map facility-id->facility)")
  (with-supplier-records [s suppliers] "replace/seed the supplier directory (map supplier-id->supplier)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained facility/supplier directory covering both the
  happy path and the governor's own hard checks, so the actor + tests run
  offline."
  []
  {:facilities
   {"facility-1" {:facility-id "facility-1" :name "Harborview Office Campus (combined FM contract)"
                   :registered? true :verified? true}
    "facility-2" {:facility-id "facility-2" :name "Meridian Logistics Park (combined FM contract)"
                   :registered? true :verified? true}
    "facility-3" {:facility-id "facility-3" :name "Northside Retail Center (contract in intake)"
                   :registered? true :verified? false}}
   :suppliers
   {"supplier-1" {:supplier-id "supplier-1" :name "CleanSupply Wholesale Ltd."
                   :registered? true :verified? true}
    "supplier-2" {:supplier-id "supplier-2" :name "Unverified Janitorial Imports Co."
                   :registered? true :verified? false}}})

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (facility-record [_ facility-id] (get-in @a [:facilities facility-id]))
  (all-facility-records [_] (sort-by :facility-id (vals (:facilities @a))))
  (supplier-record [_ supplier-id] (get-in @a [:suppliers supplier-id]))
  (all-supplier-records [_] (sort-by :supplier-id (vals (:suppliers @a))))
  (ledger [_] (:ledger @a))
  (coordination-log [_] (:coordination-log @a))
  (commit-record! [_ record]
    (swap! a update :coordination-log conj record)
    record)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-facility-records [s facilities] (when (seq facilities) (swap! a assoc :facilities facilities)) s)
  (with-supplier-records [s suppliers] (when (seq suppliers) (swap! a assoc :suppliers suppliers)) s))

(defn seed-db
  "A MemStore seeded with the demo facility/supplier directory. The
  deterministic default."
  []
  (->MemStore (atom (assoc (demo-data) :ledger [] :coordination-log []))))

(defn mem-store
  "A MemStore seeded with explicit `facilities`/`suppliers` maps
  (facility-id/supplier-id string -> record map) -- the primary test/dev
  entry point. Either may be empty (an unregistered-everywhere facility)."
  ([facilities] (mem-store facilities {}))
  ([facilities suppliers]
   (->MemStore (atom {:facilities (or facilities {}) :suppliers (or suppliers {})
                       :ledger [] :coordination-log []}))))
