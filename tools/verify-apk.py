#!/usr/bin/env python3
"""Compare actual selection bytecode before/after patching. Requires JDK 21 and prepared deps."""
import pathlib
import re
import subprocess
import sys
import tempfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
CLI = ROOT / "deps/morphe-desktop-1.15.0-all.jar"
HELPER = "Llocal/newx/websearch/WebSearch;"


def inspect(apk, classes, prefix):
    result = subprocess.run(
        ["java", "-cp", f"{classes}:{CLI}", "InspectDex", str(apk), prefix],
        check=True, text=True, capture_output=True,
    )
    methods = {}
    current = None
    for line in result.stdout.splitlines():
        if line.startswith("METHOD "):
            current = line
            if current in methods:
                raise AssertionError(f"Duplicate method definition: {current}")
            methods[current] = []
        elif current is not None and re.match(r"\s*\d+ @", line):
            # Ignore addresses of the instructions themselves; retain all branch destinations.
            methods[current].append(re.sub(r"^\s*\d+ @[0-9a-f]+\s+", "", line).strip())
    return methods


def verify(before, after):
    with tempfile.TemporaryDirectory() as directory:
        subprocess.run(["javac", "-cp", str(CLI), "-d", directory,
                        str(ROOT / "tools/InspectDex.java")], check=True)
        original = inspect(before, directory, "/post/textselection/")
        patched = inspect(after, directory, "/post/textselection/")
        assert original and original.keys() == patched.keys(), "Selection methods changed unexpectedly"
        changed = [method for method in original if original[method] != patched[method]]
        assert len(changed) == 2, f"Expected two changed methods, found {changed}"
        constructor = next(method for method in changed if "-><init>" in method)
        menu = next(method for method in changed if "->onCreateActionMode" in method)

        code = patched[constructor]
        indexes = [index for index, instruction in enumerate(code)
                   if f"{HELPER}->create(" in instruction]
        assert len(indexes) == 1, "Expected exactly one search callback factory"
        index = indexes[0]
        assert code[index].startswith("invoke-static/range")
        assert code[index + 1].startswith("move-result-object")
        assert code[index + 2].startswith("iput-object")
        result_register = re.search(r"v(\d+)", code[index + 1]).group(1)
        stored_register = re.search(r"v(\d+)", code[index + 2]).group(1)
        assert result_register == stored_register, "Search callback is stored from the wrong register"
        assert code[:index] + code[index + 2:] == original[constructor], "Unrelated constructor code changed"

        differences = [(old, new) for old, new in zip(original[menu], patched[menu]) if old != new]
        assert len(original[menu]) == len(patched[menu]) and len(differences) == 1
        old, new = differences[0]
        assert "Landroid/view/Menu;->add(IIII)" in old
        assert new.startswith("invoke-static") and f"{HELPER}->addMenuItem(" in new
        assert re.search(r"\{([^}]+)\}", old).group(1).split(",")[:4] == \
            re.search(r"\{([^}]+)\}", new).group(1).split(",")[:4], "Menu identity/order registers changed"

        helper = inspect(after, directory, "local/newx/websearch")
        instructions = "\n".join(instruction for code in helper.values() for instruction in code)
        assert "android.intent.action.WEB_SEARCH" in instructions
        assert "Landroid/content/Context;->startActivity" in instructions
        assert "Lkotlin/Unit;" not in instructions, "Helper depends on X's renamed Unit singleton"
        assert "ClipboardManager;" not in instructions, "Helper should receive text without touching the clipboard"
        assert "Llocal/newx/websearch/WebSearch;->invoke(Ljava/lang/Object;)Ljava/lang/Object;" in "\n".join(helper)

    print(f"PASS {after.name}: two targeted methods changed; selection control paths and invoke/result pairing preserved")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit("Usage: tools/verify-apk.py ORIGINAL.apk PATCHED.apk")
    before, after = map(pathlib.Path, sys.argv[1:])
    verify(before, after)
