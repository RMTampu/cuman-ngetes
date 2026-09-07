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
    "Bcore/src/main/java/top/niunaijun/blackbox/fake/service/context/providers/SystemProviderStub.java",
    "import android.os.IInterface;\n",
    "import android.os.IInterface;\nimport android.os.Bundle;\n",
)

replace_once(
    "Bcore/src/main/java/top/niunaijun/blackbox/fake/service/context/providers/SystemProviderStub.java",
    "import top.niunaijun.blackbox.utils.compat.ContextCompat;\n",
    "import top.niunaijun.blackbox.utils.compat.ContextCompat;\nimport top.niunaijun.blackbox.virtual.VirtualIdentityManager;\n",
)

replace_once(
    "Bcore/src/main/java/top/niunaijun/blackbox/fake/service/context/providers/SystemProviderStub.java",
    '''        if ("call".equals(methodName)) {
            
            if (args != null) {
                Class<?> attributionSourceClass = BRAttributionSource.getRealClass();
                for (int i = 0; i < args.length; i++) {
                    Object arg = args[i];
                    
                    if (arg != null && attributionSourceClass != null && 
                            arg.getClass().getName().equals(attributionSourceClass.getName())) {
                        ContextCompat.fixAttributionSourceState(arg, BlackBoxCore.getHostUid());
                    }
                }
            }
            return method.invoke(mBase, args);
        }
''',
    '''        if ("call".equals(methodName)) {
            if (args != null) {
                for (Object arg : args) {
                    if (arg instanceof String && "android_id".equals(arg)) {
                        Bundle result = new Bundle();
                        result.putString("value", VirtualIdentityManager.getAndroidIdForCurrentGuest());
                        return result;
                    }
                }

                Class<?> attributionSourceClass = BRAttributionSource.getRealClass();
                for (int i = 0; i < args.length; i++) {
                    Object arg = args[i];

                    if (arg != null && attributionSourceClass != null &&
                            arg.getClass().getName().equals(attributionSourceClass.getName())) {
                        ContextCompat.fixAttributionSourceState(arg, BlackBoxCore.getHostUid());
                    }
                }
            }
            return method.invoke(mBase, args);
        }
''',
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


# Gunakan jaringan perangkat secara langsung. Upstream memiliki fallback konektivitas/DNS
# sintetis yang dapat menghasilkan Network/LinkProperties palsu dan memutus Private DNS.
connectivity_proxy = r'''package top.niunaijun.blackbox.fake.service;

import android.content.Context;

import black.android.net.BRIConnectivityManagerStub;
import black.android.os.BRServiceManager;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.fake.hook.ScanClass;

@ScanClass(VpnCommonProxy.class)
public class IConnectivityManagerProxy extends BinderInvocationStub {
    public static final String TAG = "IConnectivityManagerProxy";

    public IConnectivityManagerProxy() {
        super(BRServiceManager.get().getService(Context.CONNECTIVITY_SERVICE));
    }

    @Override
    protected Object getWho() {
        return BRIConnectivityManagerStub.get().asInterface(
                BRServiceManager.get().getService(Context.CONNECTIVITY_SERVICE));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(Context.CONNECTIVITY_SERVICE);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }
}
'''
write(
    "Bcore/src/main/java/top/niunaijun/blackbox/fake/service/IConnectivityManagerProxy.java",
    connectivity_proxy,
)

# VPN internal upstream hanya membuat interface TUN tanpa packet-forwarder lengkap.
# Paksa mode jaringan normal agar trafik guest tidak masuk ke jalur yang dapat menjadi black-hole.
replace_once(
    "app/src/main/java/top/niunaijun/blackboxa/view/main/BlackBoxLoader.kt",
    '''    fun useVpnNetwork(): Boolean {
        return try {
            mUseVpnNetwork
        } catch (e: Exception) {
            Log.e(TAG, "Error getting useVpnNetwork: ${e.message}")
            false
        }
    }

    fun invalidUseVpnNetwork(enable: Boolean) {
        try {
            this.mUseVpnNetwork = enable
        } catch (e: Exception) {
            Log.e(TAG, "Error setting useVpnNetwork: ${e.message}")
        }
    }
''',
    '''    fun useVpnNetwork(): Boolean {
        return false
    }

    fun invalidUseVpnNetwork(enable: Boolean) {
        try {
            this.mUseVpnNetwork = false
        } catch (e: Exception) {
            Log.e(TAG, "Gagal menonaktifkan jaringan VPN: ${e.message}")
        }
    }
''',
)

replace_once(
    "app/src/main/java/top/niunaijun/blackboxa/view/main/BlackBoxLoader.kt",
    '''                                override fun isUseVpnNetwork(): Boolean {
                                    return try {
                                        mUseVpnNetwork
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error checking useVpnNetwork: ${e.message}")
                                        false
                                    }
                                }
''',
    '''                                override fun isUseVpnNetwork(): Boolean {
                                    return false
                                }
''',
)

# Hilangkan switch VPN agar pengguna tidak dapat mengaktifkan jalur TUN yang tidak dipakai.
replace_once(
    "app/src/main/java/top/niunaijun/blackboxa/view/setting/SettingFragment.kt",
    '''        invalidHideState {
            val vpnPreference: Preference = (findPreference("use_vpn_network")!!)
            val mUseVpnNetwork = AppManager.mBlackBoxLoader.useVpnNetwork()
            vpnPreference.setDefaultValue(mUseVpnNetwork)
            vpnPreference
        }

''',
    "",
)

replace_once(
    "app/src/main/res/xml/setting.xml",
    '''        <SwitchPreferenceCompat
                app:key="use_vpn_network"
                app:title="@string/use_vpn_network"
                app:summary="@string/use_vpn_network_summary" />

''',
    '''        <Preference
                android:key="network_info"
                android:title="@string/network_mode"
                android:summary="@string/network_mode_summary"
                android:selectable="false" />

''',
)

replace_once(
    "app/src/main/res/xml/setting.xml",
    '''        <Preference
                android:key="send_logs"
                android:title="Send Logs"
                android:summary="Upload debug logs to developer" />
''',
    '''        <Preference
                android:key="send_logs"
                android:title="@string/send_logs"
                android:summary="@string/send_logs_summary" />
''',
)

replace_once(
    "app/src/main/java/top/niunaijun/blackboxa/view/setting/SettingFragment.kt",
    '            toast("Sending logs... (Check notifications for status)")',
    '            toast(R.string.sending_logs)',
)

# Bahasa Indonesia dijadikan resource dasar supaya seluruh UI host konsisten.
strings_id = r'''<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Space Lab</string>
    <string name="choose">Pilih</string>
    <string name="choose_app">Pilih Aplikasi</string>
    <string name="installed_app">Aplikasi Terpasang</string>
    <string name="installed_module">Modul Terpasang</string>
    <string name="empty_empty">Kosong</string>
    <string name="filter">Cari</string>
    <string name="open_source_path">Kode Sumber</string>
    <string name="xp_setting">Pengaturan Xposed</string>
    <string name="tg_group">Grup Telegram</string>
    <string name="fake_location">Lokasi Palsu (Pratinjau)</string>
    <string name="real_location">Lokasi Asli</string>
    <string name="set_location">Lokasi palsu berhasil diatur: %s-%s</string>
    <string name="close_fake_location">Nonaktifkan Lokasi Palsu</string>
    <string name="close_app_fake_location">Nonaktifkan lokasi palsu untuk %s</string>
    <string name="close_fake_location_success">Lokasi palsu untuk %s berhasil dinonaktifkan</string>
    <string name="setting">Pengaturan</string>
    <string name="jump_module">Buka Pengelola Modul</string>
    <string name="module_setting">Pengelola Modul</string>
    <string name="enable_xposed">Aktifkan Kerangka Xposed</string>
    <string name="hider">Sembunyikan</string>
    <string name="hide_root">Sembunyikan Root</string>
    <string name="hide_xposed">Sembunyikan Xposed</string>
    <string name="userRemark">Catatan Pengguna</string>
    <string name="done">Selesai</string>
    <string name="cancel">Batal</string>
    <string name="permission_setting">Pengaturan Izin</string>
    <string name="no_reminders">Jangan Ingatkan Lagi</string>
    <string name="app_stop">Hentikan Aplikasi</string>
    <string name="app_stop_hint">Paksa hentikan %s?</string>
    <string name="is_stop">%s telah dihentikan</string>
    <string name="app_clear">Hapus Data</string>
    <string name="app_clear_hint">Hapus data %s?</string>
    <string name="app_remove">Hapus Aplikasi</string>
    <string name="app_shortcut">Buat Pintasan</string>
    <string name="shortcut_name">Nama Pintasan</string>
    <string name="try_add_shortcut">Mencoba menambahkan ke layar utama</string>
    <string name="add_shortcut_fail_msg">Jika gagal, buka pengaturan sistem dan izinkan Space Lab membuat pintasan layar utama.</string>
    <string name="install_success">Berhasil dipasang</string>
    <string name="install_fail">Gagal memasang: %s</string>
    <string name="install_fail_no_msg">Gagal memasang</string>
    <string name="uninstall_success">Berhasil dihapus</string>
    <string name="uninstall_fail">Gagal menghapus</string>
    <string name="uninstall_app">Hapus Aplikasi</string>
    <string name="uninstall_app_hint">Hapus %s? Data aplikasi virtual terkait juga akan dihapus.</string>
    <string name="uninstall_module">Hapus Modul</string>
    <string name="uninstall_module_hint">Hapus modul ini? Modul tidak akan bekerja setelah dihapus.</string>
    <string name="remove_success">Berhasil dihapus</string>
    <string name="clear_success">Data berhasil dihapus</string>
    <string name="start_fail">Gagal menjalankan aplikasi</string>
    <string name="start_in_outside">Jalankan modul di luar ruang virtual</string>
    <string name="restart_module">Mulai ulang Space Lab agar perubahan diterapkan</string>
    <string name="cannot_create_shortcut">Peluncur ini tidak mendukung pembuatan pintasan</string>
    <string name="uninstall_module_toast">Hapus modul Xposed melalui Pengelola Modul</string>
    <string name="other">Lainnya</string>
    <string name="daemon_enable">Layanan Latar Belakang</string>
    <string name="use_vpn_network">Gunakan Jaringan VPN</string>
    <string name="use_vpn_network_summary">Arahkan trafik aplikasi virtual melalui VPN</string>
    <string name="network_mode">Jaringan</string>
    <string name="network_mode_summary">Menggunakan jaringan perangkat secara langsung. VPN internal dinonaktifkan agar DNS dan koneksi internet tetap mengikuti jaringan HP.</string>
    <string name="disable_flag_secure">Izinkan Tangkapan Layar</string>
    <string name="disable_flag_secure_summary">Izinkan tangkapan layar dan perekaman layar pada aplikasi virtual</string>
    <string name="gms_manager">Pengelola Layanan Google</string>
    <string name="jump_gms">Buka pengelola layanan Google</string>
    <string name="enable_gms">Aktifkan Layanan Google</string>
    <string name="enable_gms_hint">Mengaktifkan layanan Google membantu aplikasi yang bergantung pada Google Play Services.</string>
    <string name="disable_gms">Nonaktifkan Layanan Google</string>
    <string name="disable_gms_hint">Hapus lingkungan layanan Google saat ini? Data layanan Google di ruang virtual akan dihapus.</string>
    <string name="no_gms">Lingkungan layanan Google belum tersedia pada perangkat ini.</string>
    <string name="send_logs">Kirim Log</string>
    <string name="send_logs_summary">Kirim log debug untuk membantu pemeriksaan masalah</string>
    <string name="sending_logs">Mengirim log… Periksa notifikasi untuk statusnya.</string>
    <string name="virtual_id_reset">Ganti ID Virtual</string>
    <string name="virtual_id_reset_hint">Buat Android ID virtual baru untuk %s? APK dan data aplikasi tidak dihapus.</string>
    <string name="virtual_id_reset_done">ID virtual baru: %s</string>
</resources>
'''
write("app/src/main/res/values/strings.xml", strings_id)

# Hapus locale bawaan upstream agar tidak ada teks lama/branding BlackBox yang mengalahkan
# resource Indonesia ketika perangkat memakai locale Tionghoa.
for rel in (
    "app/src/main/res/values-zh-rCN/strings.xml",
    "app/src/main/res/values-zh-rTW/strings.xml",
):
    path = root / rel
    if path.exists():
        path.unlink()

print("Space Lab overlay applied")
