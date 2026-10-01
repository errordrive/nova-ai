# Termux Integration Plan (Phase 4)

`run_terminal` is currently a stub that always reports unavailability. This note describes how
real shell execution will be added without rewriting the agent loop.

## Approach: Termux via intent

1. Require the Termux app (`com.termux`) installed, plus optionally Termux:API (`com.termux.api`)
   for richer results.
2. Execution path: send an explicit intent to Termux's `RunCommandService`
   (`com.termux.app.RunCommandService`) with extras:
   - `com.termux.execute.background` (boolean)
   - the command and working directory
   - a `PendingIntent` / result receiver for stdout, stderr and exit code
3. Fallback: `com.termux.api` `RUN_COMMAND` intent for devices where the app service is restricted.

## Permission model

- `run_terminal` stays `TERMINAL_EXEC` / `CRITICAL`: every invocation goes through
  `PermissionEngine`, and shell-dangerous patterns (`rm -rf`, `mkfs`, fork bombs, `dd ... of=/dev/`)
  always force an approval dialog.
- Add a per-command allowlist setting later (e.g. always allow `ls`, `git status`); the engine's
  `isDangerous()` is the hook.
- Commands run with the workspace root as cwd; `PathValidator` semantics still apply to any
  file arguments.

## Why a stub now

- Executing arbitrary shell on a user's device is the highest-risk capability in Nova; shipping
  the permission/approval/audit plumbing first (done) lets Phase 4 focus on the Termux bridge,
  output streaming, timeouts and kill switches.
- No new permissions are declared in the manifest until Phase 4 lands.

## Work items for Phase 4

- `TermuxRunner` (intent sender + result collector with timeout and cancellation).
- Wire `TerminalTool.execute()` to `TermuxRunner`; keep the stub error when Termux is absent.
- Stream long-running output as `ThinkingDelta`-style progress events.
- Add "kill process" support tied to agent cancellation.
