"""Query OSV for resolved Maven versions; errors never become a clean audit."""
import datetime
import json
from pathlib import Path
import sys
import urllib.parse
import urllib.request

API = "https://api.osv.dev/v1/"


def request_json(endpoint, payload=None):
    data = None if payload is None else json.dumps(payload).encode()
    request = urllib.request.Request(API + endpoint, data=data, headers={
        "Content-Type": "application/json", "User-Agent": "FocusBlock-dependency-audit/1"})
    with urllib.request.urlopen(request, timeout=45) as response:
        return json.load(response)


def scan(inventory, request=request_json):
    packages = {}
    for artifact in inventory["artifacts"]:
        key = (artifact["name"], artifact["version"])
        if not all(isinstance(value, str) and value for value in key):
            raise ValueError("Invalid resolved dependency coordinates")
        packages.setdefault(key, set()).add(artifact["scope"])
    if not packages or not any("runtime" in scopes for scopes in packages.values()):
        raise ValueError("Missing resolved runtime dependencies")
    keys = sorted(packages)
    found = {key: set() for key in keys}
    for start in range(0, len(keys), 100):
        batch = keys[start:start + 100]
        pending = [(key, {"package": {"ecosystem": "Maven", "name": key[0]},
                          "version": key[1]}) for key in batch]
        seen_tokens = set()
        while pending:
            response = request("querybatch", {"queries": [query for _, query in pending]})
            results = response.get("results")
            if not isinstance(results, list) or len(results) != len(pending):
                raise ValueError("Incomplete OSV batch response")
            next_page = []
            for (key, query), result in zip(pending, results):
                if not isinstance(result, dict) or "error" in result:
                    raise ValueError("Invalid OSV package response")
                for vulnerability in result.get("vulns", []):
                    identifier = vulnerability.get("id")
                    if not isinstance(identifier, str) or not identifier:
                        raise ValueError("Missing OSV advisory ID")
                    found[key].add(identifier)
                token = result.get("next_page_token")
                if token:
                    if not isinstance(token, str) or (key, token) in seen_tokens:
                        raise ValueError("Invalid or repeated OSV page token")
                    seen_tokens.add((key, token))
                    next_page.append((key, {**query, "page_token": token}))
            pending = next_page
    advisories = {}
    for identifier in sorted(set().union(*found.values())):
        advisory = request("vulns/" + urllib.parse.quote(identifier, safe=""))
        if advisory.get("id") != identifier:
            raise ValueError("Incomplete OSV advisory details")
        advisories[identifier] = advisory
    findings = []
    for key in keys:
        for identifier in sorted(found[key]):
            advisory = advisories[identifier]
            if advisory.get("withdrawn"):
                continue
            findings.append({"name": key[0], "version": key[1],
                             "scopes": sorted(packages[key]), "advisory": advisory})
    return {"status": "findings" if findings else "no-known-advisories-returned",
            "queried_packages": len(keys), "findings": findings,
            "limitations": "OSV coverage is incomplete. No matches do not prove absence of vulnerabilities, malware, or data sharing. Artifact hashes record resolved bytes; they are not independent provenance verification."}


def main():
    if len(sys.argv) != 3:
        raise SystemExit("Usage: audit_dependencies.py resolved.json report.json")
    output = Path(sys.argv[2])
    output.parent.mkdir(parents=True, exist_ok=True)
    try:
        report = scan(json.loads(Path(sys.argv[1]).read_text()))
    except Exception as error:
        report = {"status": "incomplete", "error": str(error)}
    report["checked_at_utc"] = datetime.datetime.now(datetime.timezone.utc).isoformat()
    output.write_text(json.dumps(report, indent=2) + "\n")
    print(f"Dependency audit: {report['status']}; report: {output}")
    for finding in report.get("findings", []):
        advisory = finding["advisory"]
        print(f"{finding['name']}@{finding['version']} [{', '.join(finding['scopes'])}]: "
              f"{advisory['id']} {advisory.get('summary', '')}")
    return 2 if report["status"] == "incomplete" else int(bool(report["findings"]))


if __name__ == "__main__":
    sys.exit(main())
