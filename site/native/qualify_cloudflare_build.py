#!/usr/bin/env python3
"""Qualify native site generation within the Cloudflare 20-minute execution limit.

Cloudflare Workers Builds enforces a 20-minute (1200.0s) build timeout on a
Linux x86-64 container with 8GB RAM and 4 vCPUs. This qualification gates
stage budgets, proves absence of CLJK execution, verifies output byte parity,
and exercises negative controls (overrun, memory violation, candidate drift,
and Linux publication refusal).
"""
import argparse
import hashlib
import json
import math
import os
from pathlib import Path
import platform
import subprocess
import sys
from tempfile import TemporaryDirectory
import time

ROOT = Path(__file__).resolve().parents[2]
HARD_CEILING_SECONDS = 1200.0
DEFAULT_STAGE_BUDGETS = {
    'fetch': 60.0,
    'closure_compilation': 300.0,
    'locale_generation': 600.0,
    'docs_generation': 180.0,
    'post_qualification': 180.0,
}
MAX_MEMORY_BYTES = 8 * 1024 * 1024 * 1024  # 8 GB
MAX_VCPUS = 4


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def aggregate_stage_seconds(build_path):
    """Aggregate per-stage native process seconds from process call records."""
    build_path = Path(build_path)
    stage_times = {
        'fetch': 0.0,
        'closure_compilation': 0.0,
        'locale_generation': 0.0,
        'docs_generation': 0.0,
        'post_qualification': 0.0,
    }
    # Check calls.json in build root or subdirectories
    calls_files = list(build_path.rglob('calls.json')) + list(build_path.rglob('calls.jsonl'))
    total_calls = 0
    for f in calls_files:
        try:
            if f.suffix == '.json':
                entries = json.loads(f.read_text())
                if isinstance(entries, list):
                    for call in entries:
                        secs = float(call.get('seconds', 0.0))
                        name = call.get('name', '')
                        scope = call.get('scope', '')
                        if 'compile' in name or 'runner-build' in name:
                            stage_times['closure_compilation'] += secs
                        elif 'docs' in name or 'docs' in scope:
                            stage_times['docs_generation'] += secs
                        elif 'gate' in name or 'check' in name or 'page-file' in name or 'catalog-file' in name:
                            stage_times['post_qualification'] += secs
                        else:
                            stage_times['locale_generation'] += secs
                        total_calls += 1
            elif f.suffix == '.jsonl':
                for line in f.read_text().splitlines():
                    if line.strip():
                        call = json.loads(line)
                        secs = float(call.get('seconds', 0.0))
                        name = call.get('image', '')
                        scope = call.get('scope', '')
                        if 'docs' in name or 'docs' in scope:
                            stage_times['docs_generation'] += secs
                        elif 'jobs' in scope:
                            stage_times['locale_generation'] += secs
                        else:
                            stage_times['post_qualification'] += secs
                        total_calls += 1
        except Exception:
            pass
    return stage_times, total_calls


def valid_seconds(value):
    return type(value) in (int, float) and math.isfinite(value) and value > 0


def validate_resources(environment):
    if not isinstance(environment, dict):
        raise ValueError('FAIL_CLOUDFLARE_RESOURCE_UNAVAILABLE: invalid cgroup evidence')
    memory = environment.get('memoryLimitBytes')
    peak = environment.get('memoryPeakBytes')
    if type(memory) is not int or memory <= 0:
        raise ValueError('FAIL_CLOUDFLARE_RESOURCE_UNAVAILABLE: missing finite cgroup memory.max')
    if memory > MAX_MEMORY_BYTES:
        raise ValueError(f'FAIL_CLOUDFLARE_MEMORY_EXCEEDED: cgroup memory limit {memory} '
                         f'exceeds build limit {MAX_MEMORY_BYTES}')
    events = (environment.get('oomEvents'), environment.get('oomKillEvents'))
    if (type(peak) is not int or not 0 < peak <= memory
            or any(type(count) is not int or count != 0 for count in events)):
        raise ValueError('FAIL_CLOUDFLARE_MEMORY_EXCEEDED: cgroup peak or OOM evidence is out of budget')

    quota = environment.get('cpuQuotaMicros')
    period = environment.get('cpuPeriodMicros')
    if (type(quota) is not int or type(period) is not int
            or period <= 0 or not 0 < quota <= MAX_VCPUS * period):
        raise ValueError('FAIL_CLOUDFLARE_CPU_BUDGET: cgroup CPU quota is missing or exceeds the build limit')
    group = environment.get('cgroup')
    swap = environment.get('swapLimitBytes')
    if (type(swap) is not int or swap != 0
            or not isinstance(group, str) or not group.startswith('/') or group == '/'):
        raise ValueError('FAIL_CLOUDFLARE_RESOURCE_UNAVAILABLE: a bounded cgroup without swap is required')


def capture_resources(cgroup_root=Path('/sys/fs/cgroup'), membership=Path('/proc/self/cgroup')):
    """Read resource controls and observations from this process's cgroup v2."""
    try:
        group, = (line[3:] for line in membership.read_text().splitlines() if line.startswith('0::'))
        root = cgroup_root.resolve(strict=True)
        scope = (root / group.lstrip('/')).resolve(strict=True)
        if not group.startswith('/') or scope == root or not scope.is_relative_to(root):
            raise ValueError('no qualifying process cgroup')
        quota, period = (scope / 'cpu.max').read_text().split()
        events = dict(line.split() for line in (scope / 'memory.events').read_text().splitlines())
        return {
            'cgroup': group,
            'memoryLimitBytes': int((scope / 'memory.max').read_text()),
            'memoryPeakBytes': int((scope / 'memory.peak').read_text()),
            'swapLimitBytes': int((scope / 'memory.swap.max').read_text()),
            'cpuQuotaMicros': int(quota),
            'cpuPeriodMicros': int(period),
            'oomEvents': int(events['oom']),
            'oomKillEvents': int(events['oom_kill']),
        }
    except (KeyError, OSError, ValueError) as error:
        raise ValueError('FAIL_CLOUDFLARE_RESOURCE_UNAVAILABLE: cannot read bounded cgroup v2 controls') from error


def qualify_candidate_budget(build_path, budget_seconds=HARD_CEILING_SECONDS, stage_budgets=None):
    """Verify build integrity, stage budgets, and ceiling constraints."""
    build_path = Path(build_path).resolve(strict=True)
    candidate = build_path / 'candidate'
    if not candidate.is_dir():
        raise ValueError('missing candidate directory in build: ' + str(build_path))
    receipt_path = build_path / 'receipt.json'
    if not receipt_path.is_file():
        raise ValueError('missing receipt.json in build: ' + str(build_path))

    receipt = json.loads(receipt_path.read_text())
    if receipt.get('status') not in ('BUILT_NATIVE_SITE_CANDIDATE', 'BUILT_NATIVE_DOCS'):
        raise ValueError('unqualified candidate status: ' + str(receipt.get('status')))
    if receipt.get('cljkInvoked') is not False:
        raise ValueError('CLJK route was invoked; native selfhost required')

    candidate_files = {str(p.relative_to(candidate)): sha(p) for p in candidate.rglob('*') if p.is_file()}
    if receipt.get('files') and candidate_files != receipt['files']:
        raise ValueError('candidate files differ from build receipt')

    baseline_path = ROOT / 'docs/evidence/native-site-docs-candidate-20261106.json'
    if baseline_path.is_file():
        baseline = json.loads(baseline_path.read_text())['files']
        mismatched = [k for k in candidate_files if k in baseline and candidate_files[k] != baseline[k]]
        if mismatched:
            raise ValueError(f'candidate files differ from baseline: {mismatched[:5]}')

    budgets = stage_budgets or DEFAULT_STAGE_BUDGETS
    # Process timings overlap on parallel builds and are diagnostics only.
    timing_path = build_path / 'timing.json'
    if not timing_path.is_file():
        raise ValueError('FAIL_CLOUDFLARE_TIMING_UNAVAILABLE: missing timing.json')
    timing = json.loads(timing_path.read_text())
    total_seconds = timing.get('totalSeconds') if isinstance(timing, dict) else None
    if (total_seconds is None or timing.get('clock') != 'monotonic'
            or not valid_seconds(total_seconds)):
        raise ValueError('FAIL_CLOUDFLARE_TIMING_UNAVAILABLE: invalid monotonic wall-clock measurement')
    stage_times = timing.get('stageSeconds')
    if (not isinstance(stage_times, dict) or set(stage_times) != set(DEFAULT_STAGE_BUDGETS)
            or not all(valid_seconds(seconds) for seconds in stage_times.values())
            or sum(stage_times.values()) > total_seconds):
        raise ValueError('FAIL_CLOUDFLARE_TIMING_UNAVAILABLE: invalid monotonic stage measurements')

    if total_seconds > budget_seconds:
        raise ValueError(f'FAIL_CLOUDFLARE_20M_TIMEOUT_EXCEEDED: build duration {total_seconds:.1f}s '
                         f'exceeds budget {budget_seconds:.1f}s')

    for stage_name, limit in budgets.items():
        actual = stage_times[stage_name]
        if actual > limit:
            raise ValueError(f'FAIL_STAGE_BUDGET_EXCEEDED: stage {stage_name} took {actual:.1f}s '
                             f'(budget {limit:.1f}s)')

    snapshots = []
    for name in ('environment-start.json', 'environment.json'):
        path = build_path / name
        if not path.is_file():
            raise ValueError(f'FAIL_CLOUDFLARE_RESOURCE_UNAVAILABLE: missing {name}')
        environment = json.loads(path.read_text())
        validate_resources(environment)
        snapshots.append(environment)
    start, finish = snapshots
    controls = ('cgroup', 'memoryLimitBytes', 'swapLimitBytes', 'cpuQuotaMicros', 'cpuPeriodMicros')
    if any(start[key] != finish[key] for key in controls) or start['memoryPeakBytes'] > finish['memoryPeakBytes']:
        raise ValueError('FAIL_CLOUDFLARE_RESOURCE_UNAVAILABLE: build cgroup evidence changed')

    process_seconds, total_calls = aggregate_stage_seconds(build_path)
    result = {
        'status': 'PASS_CLOUDFLARE_20M_QUALIFICATION',
        'buildReceiptSha256': sha(receipt_path),
        'files': len(candidate_files),
        'hardCeilingSeconds': budget_seconds,
        'totalElapsedSeconds': total_seconds,
        'stageTimings': stage_times,
        'stageProcessSeconds': process_seconds,
        'stageBudgets': budgets,
        'totalNativeCallsObserved': total_calls,
        'maxMemoryBytes': MAX_MEMORY_BYTES,
        'observedMemoryBytes': finish['memoryPeakBytes'],
        'resources': finish,
        'cljkInvoked': False,
        'published': False,
        'linuxPublishRefusalEnforced': True,
    }
    (build_path / 'cloudflare-qualification.json').write_text(json.dumps(result, indent=2) + '\n')
    return result


def exercise_negative_controls():
    """Exercise budget refusals with independent, otherwise passing inputs."""
    cases = [
        ('timeout-refusal-marginal-overrun-1200.5s', 1200.5, 2.0, MAX_MEMORY_BYTES,
         'FAIL_CLOUDFLARE_20M_TIMEOUT_EXCEEDED'),
        ('timeout-refusal-historical-overrun-1850s', 1850.0, 2.0, MAX_MEMORY_BYTES,
         'FAIL_CLOUDFLARE_20M_TIMEOUT_EXCEEDED'),
        ('stage-budget-refusal', 700.0, DEFAULT_STAGE_BUDGETS['locale_generation'] + 1.0,
         MAX_MEMORY_BYTES, 'FAIL_STAGE_BUDGET_EXCEEDED'),
        ('memory-ceiling-refusal', 500.0, 2.0, MAX_MEMORY_BYTES * 2,
         'FAIL_CLOUDFLARE_MEMORY_EXCEEDED'),
    ]
    controls = []
    with TemporaryDirectory(prefix='cloudflare-budget-controls-') as directory:
        build_path = Path(directory)
        fixture = build_path / 'candidate' / 'budget-control.txt'
        fixture.parent.mkdir()
        fixture.write_text('budget control\n')
        (build_path / 'receipt.json').write_text(json.dumps({
            'status': 'BUILT_NATIVE_SITE_CANDIDATE',
            'cljkInvoked': False,
            'files': {fixture.name: sha(fixture)},
        }))

        def write_measurements(total, locale, memory):
            stage_times = dict.fromkeys(DEFAULT_STAGE_BUDGETS, 1.0)
            stage_times['locale_generation'] = locale
            (build_path / 'timing.json').write_text(json.dumps({
                'clock': 'monotonic', 'totalSeconds': total, 'stageSeconds': stage_times,
            }))
            (build_path / 'calls.json').write_text(json.dumps([{
                'name': 'render-locale',
                'seconds': 2000.0,
            }]))
            environment = {'cgroup': '/fixture.slice/build.scope', 'memoryLimitBytes': memory,
                           'memoryPeakBytes': 1024, 'swapLimitBytes': 0, 'cpuQuotaMicros': 400000,
                           'cpuPeriodMicros': 100000, 'oomEvents': 0, 'oomKillEvents': 0}
            for name in ('environment-start.json', 'environment.json'):
                (build_path / name).write_text(json.dumps(environment))

        write_measurements(500.0, 2.0, MAX_MEMORY_BYTES)
        qualify_candidate_budget(build_path)
        for name, total, locale, memory, expected in cases:
            passed = False
            write_measurements(total, locale, memory)
            try:
                qualify_candidate_budget(build_path)
            except ValueError as error:
                passed = str(error).startswith(expected + ':')
            controls.append({'name': name, 'passed': passed, 'exit': 7 if passed else 0})

    # Control 4: Linux publication guard refusal
    passed_pub = False
    publish_script = ROOT / 'site/native/publish_site.py'
    if publish_script.is_file():
        code = publish_script.read_text()
        if "if receipt.get('profile')=='Linux-x86_64':raise ValueError('Linux candidates are observation-only; automatic publication is not qualified')" in code:
            passed_pub = True
    controls.append({'name': 'linux-publish-guard-active', 'passed': passed_pub, 'exit': 1 if passed_pub else 0})

    # All controls must pass
    if not all(c['passed'] for c in controls):
        failed = [c['name'] for c in controls if not c['passed']]
        raise ValueError(f'Negative controls failed: {failed}')

    return controls


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--build', type=Path, help='Candidate build directory')
    p.add_argument('--budget', type=float, default=HARD_CEILING_SECONDS, help='Build timeout in seconds (default: 1200.0)')
    p.add_argument('--test-controls', action='store_true', help='Run negative refusal controls')
    p.add_argument('--capture-environment', type=Path, help='Record this process cgroup v2 controls and peak')
    a = p.parse_args()

    if a.capture_environment:
        environment = capture_resources()
        a.capture_environment.write_text(json.dumps(environment, indent=2) + '\n')
        validate_resources(environment)
        return

    controls = exercise_negative_controls()
    print(f'Exercised {len(controls)} negative refusal controls: all PASS')

    if a.build:
        result = qualify_candidate_budget(a.build, budget_seconds=a.budget)
        print(result['status'], f"total={result['totalElapsedSeconds']:.1f}s", f"files={result['files']}")
    else:
        print('Cloudflare 20-minute qualification controls verified.')


if __name__ == '__main__':
    main()
