(ns kotoba.lang.pqh.kdf
  "Password-based key derivation seam (suite \"argon2id-v1\") -- port of
   pqh's src/kdf.ts. Argon2id (RFC 9106) is an injected `IKdf` capability --
   bind `*kdf*` to a host impl (JVM BouncyCastle in the test fixture
   `kotoba.lang.pqh.kdf-bc`; cljs @noble/hashes). This namespace imports no
   vendor SDK. Verified byte-identical to @noble/hashes' argon2id for the
   same password/salt/params in this port's test suite (kotoba.lang.pqh.kdf-test).

   .cljc, genuinely dual :clj/:cljs (ADR-2607012200, \"no unguarded
   java.*/js.* in core\"): this namespace is pure orchestration + validation
   over the injected IKdf seam. The only platform-specific piece (password
   string -> UTF-8 bytes) goes through util.cljc/utf8-bytes. No raw
   Argon2id math lives here -- and Web Crypto has no native Argon2id
   primitive either way, so a real cljs host impl is the host's problem,
   not this namespace's."
  (:require [kotoba.lang.pqh.util :as u]))

(def KDF-ARGON2ID-V1 "argon2id-v1")
;; Legacy suite id kept for read-compat envelope dispatch.
(def KDF-PBKDF2-SHA256 "pbkdf2-sha256")

(def KDF-KEY-BYTES 32)
(def KDF-SALT-BYTES 16)

;; OWASP Password Storage Cheat Sheet minimum recommended configuration
;; (19 MiB, t=2, p=1). Browser-friendly default.
(def ARGON2ID-DEFAULT-PARAMS {:m-kib 19456 :t 2 :p 1})

;; RFC 9106 second recommended option (64 MiB, t=3) for non-interactive use.
(def ARGON2ID-HIGH-PARAMS {:m-kib 65536 :t 3 :p 1})

;; ── KDF capability seam ───────────────────────────────────────────────────
;; The pure core never imports a vendor crypto SDK; the raw Argon2id primitive
;; is supplied by the host via IKdf. Bind `*kdf*` before calling
;; derive-key-argon2id.

(defprotocol IKdf
  "Raw password-based KDF primitive. Host supplies a vetted impl
   (JVM BouncyCastle Argon2id; cljs @noble/hashes)."
  (-argon2id [this password salt m-kib t p dk-len]))

(def ^:dynamic *kdf*
  "Current IKdf impl. Bind via `(binding [kdf/*kdf* impl] ...)` before any KDF
   call; nil by default (calling unbound throws).")

(defn- assert-kdf []
  (or *kdf*
      (throw (ex-info
               "[kotoba.lang.pqh/kdf] *kdf* not bound -- bind an IKdf impl (e.g. kotoba.lang.pqh.kdf-bc/bc-kdf) before KDF calls"
               {}))))

(defn derive-key-argon2id
  "opts: {:password (string or bytes), :salt bytes (>= 8 bytes),
          :params {:m-kib :t :p} (default ARGON2ID-DEFAULT-PARAMS),
          :dk-len int (default KDF-KEY-BYTES)}
   Returns {:suite KDF-ARGON2ID-V1, :key bytes, :params the-params-used}."
  [{:keys [password salt params dk-len]
    :or {params ARGON2ID-DEFAULT-PARAMS dk-len KDF-KEY-BYTES}}]
  (when (< (alength ^bytes salt) 8)
    (throw (ex-info "[kotoba.lang.pqh/kdf] salt must be at least 8 bytes" {})))
  (let [{:keys [m-kib t p]} params]
    (when (or (< m-kib (* 8 p)) (< t 1) (< p 1))
      (throw (ex-info "[kotoba.lang.pqh/kdf] invalid Argon2id parameters" {})))
    (let [pw-bytes (if (string? password) (u/utf8-bytes password) password)
          out (-argon2id (assert-kdf) pw-bytes salt m-kib t p dk-len)]
      {:suite KDF-ARGON2ID-V1 :key out :params params})))
