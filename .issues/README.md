# Repository Issues

This repository keeps GitHub issue source material in-repo so language-profile
security findings can be reviewed, versioned, and synchronized.

## Layout

- `docs/issues/*.md`: human-readable issue body, suitable for `gh issue create`
  or `gh issue edit --body-file`.
- `.issues/issues.edn`: mapping from local issue body to GitHub issue URL,
  finding id, and current tracking state.

## Sync

GitHub is the live collaboration surface. The repo copy is the audit and review
copy.

```sh
gh issue edit <number> --repo kotoba-lang/kotoba-lang \
  --body-file docs/issues/<file>.md
```

Close issues only after the acceptance criteria are implemented and verified.

