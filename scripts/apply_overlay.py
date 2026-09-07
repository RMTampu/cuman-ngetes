#!/usr/bin/env python3
from pathlib import Path
import sys

UPSTREAM_COMMIT = "89b59836c66f173756a4ae258cf379a957649820"

if len(sys.argv) != 2:
    raise SystemExit("usage: apply_overlay.py <upstream-root>")

root = Path(sys.argv[1]).resolve()


def read(rel: str) -> str:
    return (root / rel).read_text(encoding="utf-8")


def write(rel: str, data: str) -> None:
    path = root / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(data, encoding="utf-8")


def replace_once(rel: str, old: str, new: str) -> None:
    data = read(rel)
    count = data.count(old)
    if count != 1:
        raise RuntimeError(f"{rel}: expected exactly one match, found {count}")
    write(rel, data.replace(old, new, 1))


replace_once(
    "app/src/main/AndroidManifest.xml",
    'android:allowBackup="true"',
    'android:allowBackup="false"',
)

replace_once(
    "app/src/main/AndroidManifest.xml",
    'android:allowBackup="false"\n        android:icon=',
    'android:allowBackup="false"\n        tools:replace="android:allowBackup"\n        android:icon=',
)

replace_once(
    "app/src/main/res/values/strings.xml",
    '<string name="app_name">BlackBox</string>',
    '<string name="app_name">Space Lab</string>',
)

replace_once(
    "app/src/main/res/values/strings.xml",
    '    <string name="app_shortcut">Create Shortcut</string>',
    '''    <string name="app_shortcut">Create Shortcut</string>
    <string name="virtual_id_reset">Ganti Virtual ID</string>
    <string name="virtual_id_reset_hint">Buat Virtual Android ID baru untuk %s? APK dan data aplikasi tidak dihapus.</string>
    <string name="virtual_id_reset_done">Virtual ID baru: %s</string>''',
)

replace_once(
    "app/src/main/res/menu/app_menu.xml",
    '    <item android:id="@+id/app_shortcut" android:title="@string/app_shortcut"/>',
    '''    <item android:id="@+id/app_shortcut" android:title="@string/app_shortcut"/>
    <item android:id="@+id/app_identity_reset" android:title="@string/virtual_id_reset"/>''',
)

identity_manager = r'''package top.niunaijun.blackbox.virtual;

import android.content.Context;
import android.content.SharedPreferences;

import java.security.SecureRandom;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;

public final class VirtualIdentityManager {
    private static final String PREFS_NAME = "space_lab_virtual_identity";
    private static final String KEY_PREFIX = "android_id|";
    private static final SecureRandom RANDOM = new SecureRandom();

    private VirtualIdentityManager() {
    }

    public static String getAndroidIdForCurrentGuest() {
        String packageName = BActivityThread.getAppPackageName();
        int userId = BActivityThread.getUserId();
        return getAndroidId(packageName, userId);
    }

    public static synchronized String getAndroidId(String packageName, int userId) {
        String safePackage = packageName == null ? "unknown" : packageName;
        String key = keyFor(safePackage, userId);

        Context context = BlackBoxCore.getContext();
        if (context == null) {
            return deterministicFallback(key);
        }

        SharedPreferences preferences =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String existing = preferences.getString(key, null);
        if (isValid(existing)) {
            return existing;
        }

        String generated = generateAndroidId();
        if (!preferences.edit().putString(key, generated).commit()) {
            return deterministicFallback(key);
        }
        return generated;
    }

    public static synchronized String rotateAndroidId(String packageName, int userId) {
        String safePackage = packageName == null ? "unknown" : packageName;
        String key = keyFor(safePackage, userId);
        String generated = generateAndroidId();

        Context context = BlackBoxCore.getContext();
        if (context == null) {
            return generated;
        }

        SharedPreferences preferences =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (!preferences.edit().putString(key, generated).commit()) {
            throw new IllegalStateException("Unable to persist virtual Android ID");
        }
        return generated;
    }

    public static long getAndroidIdLongForCurrentGuest() {
        String value = getAndroidIdForCurrentGuest();
        long parsed = 0L;
        for (int i = 0; i < value.length(); i++) {
            int digit = Character.digit(value.charAt(i), 16);
            if (digit < 0) {
                continue;
            }
            parsed = (parsed << 4) | digit;
        }
        return parsed & Long.MAX_VALUE;
    }

    private static String keyFor(String packageName, int userId) {
        return KEY_PREFIX + userId + "|" + packageName;
    }

    private static boolean isValid(String value) {
        if (value == null || value.length() != 16) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (Character.digit(value.charAt(i), 16) < 0) {
                return false;
            }
        }
        return true;
    }

    private static String generateAndroidId() {
        byte[] bytes = new byte[8];
        RANDOM.nextBytes(bytes);
        StringBuilder result = new StringBuilder(16);
        for (byte b : bytes) {
            result.append(String.format("%02x", b & 0xff));
        }
        return result.toString();
    }

    private static String deterministicFallback(String key) {
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < key.length(); i++) {
            hash ^= key.charAt(i);
            hash *= 0x100000001b3L;
        }
        return String.format("%016x", hash);
    }
}
'''
write(
    "Bcore/src/main/java/top/niunaijun/blackbox/virtual/VirtualIdentityManager.java",
    identity_manager,
)

android_id_proxy = r'''package top.niunaijun.blackbox.fake.service;

import java.lang.reflect.Method;

import top.niunaijun.blackbox.fake.hook.ClassInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.virtual.VirtualIdentityManager;

public class AndroidIdProxy extends ClassInvocationStub {

    @Override
    protected Object getWho() {
        return null;
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    private static boolean isVirtualIdentityKey(Object[] args) {
        if (args == null || args.length == 0 || !(args[0] instanceof String)) {
            return false;
        }
        String key = ((String) args[0]).toLowerCase();
        return key.contains("android_id")
                || key.contains("secure_id")
                || key.contains("device_id");
    }

    @ProxyMethod("getAndroidId")
    public static class GetAndroidId extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) {
            return VirtualIdentityManager.getAndroidIdForCurrentGuest();
        }
    }

    @ProxyMethod("getString")
    public static class GetString extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (isVirtualIdentityKey(args)) {
                return VirtualIdentityManager.getAndroidIdForCurrentGuest();
            }
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("getLong")
    public static class GetLong extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (isVirtualIdentityKey(args)) {
                return VirtualIdentityManager.getAndroidIdLongForCurrentGuest();
            }
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("get")
    public static class Get extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (isVirtualIdentityKey(args)) {
                return VirtualIdentityManager.getAndroidIdForCurrentGuest();
            }
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("read")
    public static class Read extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (isVirtualIdentityKey(args)) {
                return VirtualIdentityManager.getAndroidIdForCurrentGuest();
            }
            return method.invoke(who, args);
        }
    }
}
'''
write(
    "Bcore/src/main/java/top/niunaijun/blackbox/fake/service/AndroidIdProxy.java",
    android_id_proxy,
)

replace_once(
    "app/src/main/java/top/niunaijun/blackboxa/view/apps/AppsFragment.kt",
    "import top.niunaijun.blackbox.BlackBoxCore\n",
    "import top.niunaijun.blackbox.BlackBoxCore\nimport top.niunaijun.blackbox.virtual.VirtualIdentityManager\n",
)

replace_once(
    "app/src/main/java/top/niunaijun/blackboxa/view/apps/AppsFragment.kt",
    '''                                    R.id.app_shortcut -> {
                                        ShortcutUtil.createShortcut(requireContext(), userID, data)
                                    }
''',
    '''                                    R.id.app_shortcut -> {
                                        ShortcutUtil.createShortcut(requireContext(), userID, data)
                                    }

                                    R.id.app_identity_reset -> {
                                        resetVirtualIdentity(data)
                                    }
''',
)

replace_once(
    "app/src/main/java/top/niunaijun/blackboxa/view/apps/AppsFragment.kt",
    '''    private fun clearApk(info: AppInfo) {
''',
    '''    private fun resetVirtualIdentity(info: AppInfo) {
        try {
            MaterialDialog(requireContext()).show {
                title(R.string.virtual_id_reset)
                message(text = getString(R.string.virtual_id_reset_hint, info.name))
                positiveButton(R.string.done) {
                    try {
                        BlackBoxCore.get().stopPackage(info.packageName, userID)
                        val newId =
                            VirtualIdentityManager.rotateAndroidId(info.packageName, userID)
                        toast(getString(R.string.virtual_id_reset_done, newId))
                    } catch (e: Exception) {
                        Log.e(TAG, "Error rotating virtual identity: " + e.message)
                        toast("Gagal mengganti Virtual ID")
                    }
                }
                negativeButton(R.string.cancel)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing virtual identity dialog: " + e.message)
        }
    }


    private fun clearApk(info: AppInfo) {
''',
)

print("Space Lab overlay applied")
