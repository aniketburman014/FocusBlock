"""Check the merged manifest extracted from the actual APK, not just source XML."""
import sys
import xml.etree.ElementTree as ET

ANDROID = "{http://schemas.android.com/apk/res/android}"
PACKAGE = "com.focusblock.app"


def check_manifest(root):
    if root.get("package") != PACKAGE:
        raise ValueError("Unexpected APK package")
    allowed_permissions = {f"{PACKAGE}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"}
    for element in root.iter():
        if element.tag in {"uses-permission", "uses-permission-sdk-23"}:
            name = element.get(ANDROID + "name")
            if name not in allowed_permissions:
                raise ValueError(f"Unexpected APK permission: {name}")
    app = root.find("application")
    if app is None:
        raise ValueError("Missing application")
    expected_exported = {
        f"{PACKAGE}.ui.MainActivity",
        f"{PACKAGE}.service.FocusBlockAccessibilityService",
    }
    exported = set()
    for component in app:
        if component.tag not in {"activity", "activity-alias", "service", "receiver", "provider"}:
            continue
        name = component.get(ANDROID + "name", "")
        if name.startswith("."):
            name = PACKAGE + name
        elif "." not in name:
            name = PACKAGE + "." + name
        if component.get(ANDROID + "exported") == "true":
            exported.add(name)
        if name == f"{PACKAGE}.service.FocusBlockAccessibilityService":
            if component.get(ANDROID + "permission") != "android.permission.BIND_ACCESSIBILITY_SERVICE":
                raise ValueError("Accessibility service is missing its binding permission")
    if exported != expected_exported:
        raise ValueError(f"Unexpected exported components: {sorted(exported)}")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: check_apk_manifest.py extracted-AndroidManifest.xml")
    try:
        check_manifest(ET.parse(sys.argv[1]).getroot())
    except (ValueError, ET.ParseError, OSError) as error:
        raise SystemExit(str(error))
    print("APK manifest check passed: expected permissions and exported components only.")
