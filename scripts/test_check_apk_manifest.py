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
