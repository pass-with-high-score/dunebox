// Intentionally left empty — this file is included via CMakeLists.txt
// but the JNI bridge code is embedded in io_redirect.cpp for simplicity.
// This file serves as a placeholder for future separation if needed.
//
// The JNI entry points are defined in io_redirect.cpp:
//   - Java_app_pwhs_dunebox_native_1engine_NativeEngine_nativeInit
//   - Java_app_pwhs_dunebox_native_1engine_NativeEngine_nativeAddRedirectRule
//   - Java_app_pwhs_dunebox_native_1engine_NativeEngine_nativeAddDenyRule
//   - Java_app_pwhs_dunebox_native_1engine_NativeEngine_nativeStartRedirect
//   - Java_app_pwhs_dunebox_native_1engine_NativeEngine_nativeStopRedirect
//   - Java_app_pwhs_dunebox_native_1engine_NativeEngine_nativeClearRules
