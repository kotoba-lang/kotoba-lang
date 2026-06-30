//! Language profile constants for canonical Kotoba source.
//!
//! This crate is intentionally small and dependency-free. It defines the source
//! compatibility contract shared by tools that accept Kotoba language source;
//! compiler implementation details stay in `kotoba-clj`.

use std::path::Path;

pub const PROFILE_EDN: &str = include_str!("../resources/kotoba/lang/profile.edn");
pub const CONFORMANCE_MANIFEST_EDN: &str =
    include_str!("../resources/kotoba/lang/conformance/manifest.edn");
pub const COVERAGE_EDN: &str = include_str!("../../../docs/lang/coverage.edn");
pub const GATES_MD: &str = include_str!("../../../docs/lang/gates.md");

/// Reader conditional target used when normalizing `.cljc` source.
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash)]
pub enum ReaderTarget {
    /// Kotoba's authoring target. Selects `:kotoba`, then `:clj`, then `:default`.
    Kotoba,
    /// JVM Clojure compatibility target. Selects `:clj`, then `:default`.
    Clj,
    /// ClojureScript compatibility target. Selects `:cljs`, then `:default`.
    Cljs,
}

impl ReaderTarget {
    pub fn parse(s: &str) -> Option<Self> {
        match s {
            "kotoba" => Some(Self::Kotoba),
            "clj" => Some(Self::Clj),
            "cljs" => Some(Self::Cljs),
            _ => None,
        }
    }

    pub fn as_str(self) -> &'static str {
        match self {
            Self::Kotoba => "kotoba",
            Self::Clj => "clj",
            Self::Cljs => "cljs",
        }
    }

    /// Reader conditional branches checked, in order.
    pub fn reader_branches(self) -> &'static [&'static str] {
        match self {
            Self::Kotoba => &["kotoba", "clj", "default"],
            Self::Clj => &["clj", "default"],
            Self::Cljs => &["cljs", "default"],
        }
    }

    /// Source file extension priority for namespace resolution.
    pub fn namespace_extension_priority(self) -> &'static [&'static str] {
        match self {
            Self::Kotoba => &["kotoba", "cljc", "clj", "cljs"],
            Self::Clj => &["cljc", "clj", "kotoba", "cljs"],
            Self::Cljs => &["cljc", "cljs", "clj", "kotoba"],
        }
    }
}

pub const SUPPORTED_SOURCE_EXTENSIONS: &[&str] = &["kotoba", "clj", "cljc", "cljs"];

pub fn is_supported_source_extension(ext: &str) -> bool {
    SUPPORTED_SOURCE_EXTENSIONS.contains(&ext)
}

pub fn is_supported_source_path(path: &Path) -> bool {
    path.extension()
        .and_then(|ext| ext.to_str())
        .is_some_and(is_supported_source_extension)
}

pub const DEFAULT_READER_TARGET: ReaderTarget = ReaderTarget::Kotoba;

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn kotoba_reader_target_falls_back_to_clj_then_default() {
        assert_eq!(
            ReaderTarget::Kotoba.reader_branches(),
            &["kotoba", "clj", "default"]
        );
    }

    #[test]
    fn extension_priorities_are_stable() {
        assert_eq!(
            ReaderTarget::Kotoba.namespace_extension_priority(),
            &["kotoba", "cljc", "clj", "cljs"]
        );
        assert_eq!(
            ReaderTarget::Clj.namespace_extension_priority(),
            &["cljc", "clj", "kotoba", "cljs"]
        );
        assert_eq!(
            ReaderTarget::Cljs.namespace_extension_priority(),
            &["cljc", "cljs", "clj", "kotoba"]
        );
    }

    #[test]
    fn kotoba_extension_is_canonical_and_supported() {
        assert_eq!(SUPPORTED_SOURCE_EXTENSIONS.first().copied(), Some("kotoba"));
        assert!(is_supported_source_extension("kotoba"));
        assert!(is_supported_source_path(Path::new("cell.kotoba")));
        assert!(is_supported_source_path(Path::new("cell.clj")));
        assert!(!is_supported_source_path(Path::new("cell.edn")));
    }

    #[test]
    fn profile_manifest_matches_the_rust_contract() {
        assert!(PROFILE_EDN.contains(":kotoba.lang/profile-version 1"));
        assert!(PROFILE_EDN.contains(":kotoba.lang/default-reader-target :kotoba"));
        assert!(PROFILE_EDN
            .contains(":kotoba.lang/source-extensions [\"kotoba\" \"clj\" \"cljc\" \"cljs\"]"));
        assert!(PROFILE_EDN.contains(":kotoba {:reader-branches [\"kotoba\" \"clj\" \"default\"]"));
        assert!(PROFILE_EDN
            .contains(":namespace-extension-priority [\"kotoba\" \"cljc\" \"clj\" \"cljs\"]"));
        assert!(PROFILE_EDN.contains(":canonical-extension \"kotoba\""));
        assert!(PROFILE_EDN.contains(":portable-extension \"cljc\""));
        assert!(PROFILE_EDN.contains(":kotoba-branch \"kotoba\""));
    }

    #[test]
    fn coverage_declares_m6_and_all_stage_evidence() {
        assert!(COVERAGE_EDN.contains(":kotoba.lang.coverage/version 1"));
        assert!(COVERAGE_EDN.contains(":maturity :m6"));
        for stage in [":m0", ":m1", ":m2", ":m3", ":m4", ":m5", ":m6"] {
            assert!(COVERAGE_EDN.contains(stage), "missing stage {stage}");
        }
        for evidence in [
            "crates/kotoba-cli/tests/public_cli.rs",
            "crates/kotoba-cli/src/mesh.rs",
            "crates/kotoba-cli/src/extension.rs",
            "crates/kotoba-lattice/src/manifest.rs",
        ] {
            assert!(COVERAGE_EDN.contains(evidence), "missing {evidence}");
        }
        for required in [
            ":kotoba-eval",
            ":kotoba-wasm-build",
            ":kotoba-wasm-safe-policy",
            ":kotoba-wasm-selfhost-inspect",
            ":kotoba-wasm-safe-build",
            ":kotoba-wasm-kotoba-namespace-priority",
            ":kotoba-wasm-argument-surface",
            ":legacy-admission-gate-not-public",
            ":kotoba-component-build-canonical-extension",
            ":kotoba-component-clj-family-compatibility",
            ":kotoba-app-manifest-kotoba-default",
            ":kotoba-extension-artifact-kind",
            ":legacy-clojure-default-not-public",
        ] {
            assert!(COVERAGE_EDN.contains(required), "missing {required}");
        }
    }

    #[test]
    fn gates_include_public_cli_component_and_extension_defaults() {
        for command in [
            "cargo test -p kotoba-cli --test public_cli",
            "cargo test -p kotoba-cli wasm_cli_tests",
            "cargo test -p kotoba-cli mesh::tests",
            "cargo test -p kotoba-cli manifest_defaults_to_kotoba_extension_kind_with_clj_compat_host",
            "cargo test -p kotoba-lattice manifest::tests",
            "cargo run -p kotoba-cli -- wasm safe-policy examples/kotoba-shell-hello/src/policy.kotoba",
        ] {
            assert!(
                GATES_MD.contains(command),
                "missing gate command: {command}"
            );
        }
    }
}
