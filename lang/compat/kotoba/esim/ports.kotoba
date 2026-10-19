(ns kotoba.esim.ports
  "Host-injected ports for eSIM provisioning.

  This namespace defines the protocols; the host (a governed actor such as
  cloud-itonami/cloud-itonami-esim) supplies concrete implementations backed by
  a real SM-DP+ / SM-DS, or by fixtures offline. Same posture as
  kotoba-lang/koe's voice ports, which this file is modeled on.

  No SDK, no endpoint, no certificate and no credential lives in this library --
  only the shapes an eSIM provisioning host is built from. An implementation
  that hardcodes an SM-DP+ address or a GSMA CI trust anchor here has put
  deployment configuration in a portable library and should carry it in the
  host instead.

  Every operation returns records from kotoba.esim, so a caller can reason about
  results without knowing which implementation answered. Implementations are
  expected to report refusal as data rather than throw, matching
  kotoba.esim.lifecycle/apply-operation.

  Portable (.cljc) across JVM / ClojureScript / SCI / GraalVM.")

(defprotocol IProfileProvisioning
  "Profile management against one eUICC. Reachability is the caller's
  responsibility: an implementation is entitled to assume
  kotoba.esim.lifecycle already admitted the operation, and is entitled to
  refuse again if its own view of the eUICC disagrees."
  (euicc-info [this eid]
    "Return the kotoba.esim/euicc record plus its installed profiles as
     {:esim/euicc r :esim/profiles [...]}, or nil when eid is unknown.")
  (download-profile [this eid activation-code]
    "Bind and install the profile the activation code points at. Returns the
     resulting kotoba.esim/profile in :disabled state, or a refusal map.")
  (enable-profile [this eid iccid]
    "Enable an installed, disabled profile. An implementation must not
     silently displace an incumbent enabled profile.")
  (disable-profile [this eid iccid]
    "Disable an enabled profile.")
  (delete-profile [this eid iccid]
    "Delete a disabled profile. Terminal."))

(defprotocol IEventRegistration
  "SM-DS event registration (SGP.22 Consumer only -- SGP.02 M2M has no
  discovery server, so an M2M host implements only IProfileProvisioning)."
  (register-event [this registration]
    "Register a kotoba.esim/event-registration so the eUICC discovers a
     pending download on its next poll.")
  (list-events [this eid]
    "Return the event registrations currently pending for eid.")
  (delete-event [this eid event-id]
    "Withdraw a pending event registration."))

(defprotocol IOwnershipTransfer
  "Subject-level ownership transfer.

  Deliberately a separate protocol rather than an operation on
  IProfileProvisioning: a transfer is the primary SIM-swap fraud path, so it is
  a distinct authority a host can decline to implement or can gate far more
  strictly, and a caller cannot reach it by holding a provisioning port
  (ADR-2607300300 D6)."
  (propose-transfer [this transfer]
    "Record a kotoba.esim/ownership-transfer as proposed. An implementation
     must NOT actuate here -- transfer always requires human escalation.")
  (transfer-status [this iccid]
    "Return the pending or last-recorded transfer for iccid, or nil."))
