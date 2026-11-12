import unittest
import xml.etree.ElementTree as ET

from check_apk_manifest import ANDROID, PACKAGE, check_manifest


class ApkManifestTest(unittest.TestCase):
    def manifest(self):
        return ET.fromstring(f'''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="{PACKAGE}">
          <application>
            <activity android:name=".ui.MainActivity" android:exported="true" />
            <service android:name=".service.FocusBlockAccessibilityService" android:exported="true"
              android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE" />
            <provider android:name="androidx.startup.InitializationProvider" android:exported="false" />
          </application>
        </manifest>''')

    def test_expected_manifest_passes(self):
        root = self.manifest()
        ET.SubElement(root, "uses-permission", {ANDROID + "name": f"{PACKAGE}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"})
        check_manifest(root)

    def test_network_permission_is_rejected(self):
        for tag in ["uses-permission", "uses-permission-sdk-23"]:
            root = self.manifest()
            ET.SubElement(root, tag, {ANDROID + "name": "android.permission.INTERNET"})
            with self.assertRaises(ValueError):
                check_manifest(root)

    def test_new_exported_component_is_rejected(self):
        root = self.manifest()
        ET.SubElement(root.find("application"), "receiver", {
            ANDROID + "name": ".UnexpectedReceiver", ANDROID + "exported": "true"})
        with self.assertRaises(ValueError):
            check_manifest(root)

    def test_exported_profileinstaller_receiver_is_rejected(self):
        # Regression: AndroidX adds this through manifest merging. The app removes
        # the external tooling entry point; do not silently allow it back in.
        root = self.manifest()
        receiver = ET.SubElement(root.find("application"), "receiver", {
            ANDROID + "name": "androidx.profileinstaller.ProfileInstallReceiver",
            ANDROID + "exported": "true",
            ANDROID + "permission": "android.permission.DUMP",
        })
        actions = ET.SubElement(receiver, "intent-filter")
        ET.SubElement(actions, "action", {
            ANDROID + "name": "androidx.profileinstaller.action.INSTALL_PROFILE"})
        with self.assertRaisesRegex(ValueError, "Unexpected exported components"):
            check_manifest(root)
        root.find("application").remove(receiver)
        check_manifest(root)

    def test_unprotected_accessibility_service_is_rejected(self):
        root = self.manifest()
        del root.find("application/service").attrib[ANDROID + "permission"]
        with self.assertRaises(ValueError):
            check_manifest(root)

    def test_wrong_package_is_rejected(self):
        root = self.manifest()
        root.set("package", "com.example.other")
        with self.assertRaises(ValueError):
            check_manifest(root)


if __name__ == "__main__":
    unittest.main()
