#!/usr/bin/env python3
"""Verify the exact owner and ABI of the shaded Guizhan 1.8.1 lifecycle shim."""

import hashlib
import re
import subprocess
import sys
from pathlib import Path
from zipfile import ZipFile


ORIGINAL = "net.guizhanss.guizhanlib.slimefun.addon.AbstractAddon"
RELOCATED = "net.guizhanss.gcereborn.libs.guizhanlib.slimefun.addon.AbstractAddon"
HELPER = "net.guizhanss.gcereborn.core.compat.AddonTickCounter"


def require(condition, message):
    if not condition:
        raise SystemExit(message)


def javap(classpath, class_name, *flags):
    return subprocess.check_output(
        ["javap", *flags, "-classpath", str(classpath), class_name], text=True
    )


def normalized(text):
    text = text.replace("net.guizhanss.gcereborn.libs.guizhanlib", "net.guizhanss.guizhanlib")
    text = text.replace("net/guizhanss/gcereborn/libs/guizhanlib", "net/guizhanss/guizhanlib")
    return text


def normalized_code(text):
    """Preserve instructions/control flow while allowing Shade's wider constants.

    Relocation can move a constant beyond index 255, widening ldc to ldc_w and
    shifting later offsets. Compare branch/switch/exception destinations by their
    instruction index, never by discarding the targets.
    """
    instruction = re.compile(r"^\s*(\d+):\s+([a-z][a-z0-9_]*)\b(.*)$")
    switch_target = re.compile(r"^\s*(-?\d+|default):\s+(\d+)\s*$")
    exception = re.compile(r"^\s*(\d+)\s+(\d+)\s+(\d+)\s+(.+)$")
    blocks = normalized(text).split("    Code:\n")
    result = [blocks[0]]
    for block in blocks[1:]:
        code, separator, remainder = block.partition("\n\n")
        lines = code.splitlines()
        offsets = {
            int(match.group(1)): index
            for index, match in enumerate(filter(None, map(instruction.match, lines)))
        }

        def label(value, allow_end=False):
            value = int(value)
            if value in offsets:
                return "@" + str(offsets[value])
            if allow_end and offsets and value > max(offsets):
                return "@end"
            raise SystemExit(f"invalid instruction destination in javap output: {value}")

        canonical = []
        in_exceptions = False
        for line in lines:
            match = instruction.match(line)
            case = switch_target.match(line)
            catch = exception.match(line) if in_exceptions else None
            if match:
                offset, opcode, operands = match.groups()
                opcode = {"ldc_w": "ldc", "goto_w": "goto", "jsr_w": "jsr"}.get(opcode, opcode)
                operands, comment_mark, comment = operands.partition("//")
                operands = re.sub(r"#\d+", "#constant", operands)
                operands = " ".join(operands.split())
                if opcode.startswith("if") or opcode in {"goto", "jsr"}:
                    operands = label(operands)
                suffix = (" //" + comment) if comment_mark else ""
                canonical.append(f"{label(offset)} {opcode} {operands}{suffix}")
            elif case:
                canonical.append(f"case {case.group(1)}: {label(case.group(2))}")
            elif catch:
                start, end, target, kind = catch.groups()
                canonical.append(f"catch {label(start)} {label(end, True)} {label(target)} {kind}")
            else:
                canonical.append(line.strip())
                in_exceptions = in_exceptions or line.strip() == "Exception table:"
        result.append("    Code:\n" + "\n".join(canonical) + separator + remainder)
    return "".join(result)


def main():
    require(len(sys.argv) == 3, "usage: verify_lifecycle_shim.py candidate.jar GuizhanLib-api-1.8.1.jar")
    jar, dependency = map(Path, sys.argv[1:])
    require(jar.is_file() and dependency.is_file(), "candidate and original dependency JARs are required")
    classes = jar.parent / "classes"
    original_abi = javap(dependency, ORIGINAL, "-protected", "-s")
    compiled_abi = javap(classes, ORIGINAL, "-protected", "-s")
    packaged_abi = javap(jar, RELOCATED, "-protected", "-s")
    require(original_abi == compiled_abi, "compiled lifecycle public/protected ABI differs from GuizhanLib 1.8.1")
    require(original_abi == normalized(packaged_abi), "packaged lifecycle ABI differs from the compiled source")

    compiled_code = javap(classes, ORIGINAL, "-p", "-s", "-c")
    packaged_code = javap(jar, RELOCATED, "-p", "-s", "-c")
    require(normalized_code(compiled_code) == normalized_code(packaged_code),
            "packaged lifecycle bytecode differs from owned source")
    require("AddonTickCounter.start" in packaged_code, "packaged lifecycle does not call the owned counter scheduler")
    helper_code = javap(jar, HELPER, "-p", "-s", "-c")
    require("getGlobalRegionScheduler" in helper_code and "runAtFixedRate" in helper_code,
            "packaged counter lacks the native Folia scheduler path")
    require("runTaskTimer" in helper_code, "packaged counter lost the original Paper scheduler path")

    with ZipFile(jar) as archive:
        names = archive.namelist()
        class_path = RELOCATED.replace(".", "/") + ".class"
        require(names.count(class_path) == 1, "the lifecycle class must have exactly one packaged owner")
        require(not any(name.startswith("net/guizhanss/guizhanlib/") for name in names),
                "unrelocated Guizhan classes leaked into the plugin")
        require(not any("AddonTickCounterTest" in name for name in names), "test code leaked into the plugin")
        require(archive.read(class_path)[6:8] == (65).to_bytes(2, "big"), "the owned lifecycle exceeds Java 21")

    report = (
        "PASS: original GuizhanLib 1.8.1 public/protected ABI retained\n"
        "PASS: compiled and packaged lifecycle instruction listings match after relocation\n"
        "PASS: one explicit shaded lifecycle owner; global counter and original Paper paths retained\n"
        f"candidate_sha256={hashlib.sha256(jar.read_bytes()).hexdigest()}\n"
        f"dependency_sha256={hashlib.sha256(dependency.read_bytes()).hexdigest()}\n"
    )
    (jar.parent / "lifecycle-shim-verification.txt").write_text(report, encoding="utf-8")
    print(report, end="")


if __name__ == "__main__":
    main()
