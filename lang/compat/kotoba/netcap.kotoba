(ns kotoba.netcap
  "Assembled from one repo per definition.

  This namespace holds no implementation. It re-exports the definitions
  that each live in their own repo, so a call site can require one name
  and a library can require only the definitions it actually uses.

  AN IMPLEMENTATION BUILT AGAINST kotoba.lang.netcap IS NOT ACCEPTED HERE.
  kotoba.lang.netcap still declares INet, and a protocol split into its own
  repo is a DIFFERENT protocol from the one the source namespace declares
  (ADR-2609091900). Measured 2026-09-09 on kotoba.lang.fs: a filesystem
  reified against the source protocol answers through the source namespace
  and fails through this one -- No implementation of method: :exists?.
  Build the implementation against the repo that declares the protocol here,
  or call through kotoba.lang.netcap.

  NOT re-exported here, on purpose: INet. A protocol's identity is what extend-type and reify dispatch on,
  and a copy would make an implementation silently extend nothing, so the
  protocol name stays in the one repo that declares it. Requiring that repo
  is a compile error away; a copy would not be.

  Value vars are not re-exported either: denied, surface-effects. `(def x other/x)` copies, which is harmless for a function and makes
  with-redefs through this namespace a SILENT no-op for a value -- measured
  on kotoba.lang.edn, where three assertions passed against nothing at all.
  Require the repo that defines the value.
"
  (:require [kotoba.netcap.net :as inet-ns]
            [kotoba.netcap.call :as call-ns]
            [kotoba.netcap.discover :as discover-ns]
            [kotoba.netcap.make-net-manager :as make-net-manager-ns]
            [kotoba.netcap.mock-net :as mock-net-ns]
            [kotoba.netcap.surface-cap :as surface-cap-ns]
            [kotoba.netcap.surfaces :as surfaces-ns]))

(def call "See kotoba.netcap.call/call." call-ns/call)
(def close-net "See kotoba.netcap.net/close-net." inet-ns/close-net)
(def connect "See kotoba.netcap.net/connect." inet-ns/connect)
(def discover "See kotoba.netcap.discover/discover." discover-ns/discover)
(def make-net-manager "See kotoba.netcap.make-net-manager/make-net-manager." make-net-manager-ns/make-net-manager)
(def mock-net "See kotoba.netcap.mock-net/mock-net." mock-net-ns/mock-net)
(def read-net "See kotoba.netcap.net/read-net." inet-ns/read-net)
(def recv-udp "See kotoba.netcap.net/recv-udp." inet-ns/recv-udp)
(def resolve "See kotoba.netcap.net/resolve." inet-ns/resolve)
(def send-udp "See kotoba.netcap.net/send-udp." inet-ns/send-udp)
(def surface-cap "See kotoba.netcap.surface-cap/surface-cap." surface-cap-ns/surface-cap)
(def surfaces "See kotoba.netcap.surfaces/surfaces." surfaces-ns/surfaces)
(def write-net "See kotoba.netcap.net/write-net." inet-ns/write-net)
