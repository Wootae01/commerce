# Triage Labels

This repo is a solo project and does **not** use triage (workflow-state) labels. Only GitHub's default type labels are used.

| Label in mattpocock/skills | Label in our tracker | Meaning                                  |
| -------------------------- | -------------------- | ---------------------------------------- |
| `needs-triage`             | _(none)_             | Not used: every issue is triaged by the owner on creation |
| `needs-info`               | `question`           | Waiting on more information              |
| `ready-for-agent`          | _(none)_             | Not used: an open issue is ready to work |
| `ready-for-human`          | _(none)_             | Not used                                 |
| `wontfix`                  | `wontfix`            | Will not be actioned                     |

When a skill says to apply a role mapped to _(none)_, skip the label. Do not create new labels.

Type labels may be applied as usual: `bug`, `enhancement`, `documentation`.

Progress is tracked by issue open/closed state and blocking relationships (`Blocked by`), not by labels.
