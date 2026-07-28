# W6 decision: SSH fleet exec stays host-forever

Status: **accepted** (2026-07-28)  
Related gap: `lang/w6-kbb-ability-gap.edn` `:ssh-or-remote-exec`

## Decision

**murakumo fleet SSH / Tailscale remote exec remains a permanent host
mechanism.** It is **not** a guest provider kit and is **not** on the kbb
qualification path for product cutover.

## Why

1. OpenSSH (or Tailscale SSH) is already the trusted remote plane on the fleet;
   re-embedding it inside a grant-scoped guest adds surface without removing
   the outer host boundary.
2. W6 murakumo inventory classified fleet control shells as **ops / host** —
   same bucket as process+scoped-fs consumers — and non-goals already allowed
   permanent host-mechanism for SSH.
3. process kit (provider#25/#28) can spawn a host-allowed `ssh` binary under
   `:binaries` if a script needs one-shot remote argv; that is **not** a
   general remote-exec capability and does not admit ambient OpenSSH.

## Consequences

- Gap `:ssh-or-remote-exec` status → **host-forever** (not “missing kit”).
- nbb/bb remain authorized for murakumo fleet CLIs.
- Guest product `.kotoba` must not depend on SSH; use provider http/object/
  state/llm kits instead.

## Non-goals

- No `provider.ssh` capability id.
- No plan to move `murakumo.ssh` into guest grammar.
