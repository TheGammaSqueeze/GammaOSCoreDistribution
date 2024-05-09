package librknnrt

import (
    "android/soong/android"
    "android/soong/cc"
    "fmt"
    "strings"
)

var SUPPORT_TARGET_PLATFORM = [...]string{
    "rk356x",
    "rk3588",
}

func init() {
    fmt.Println("librockx want to conditional Compile")
    android.RegisterModuleType("cc_librockx_prebuilt_library_shared", LibrockxFactory)
}

func LibrockxFactory() (android.Module) {
    module := cc.PrebuiltSharedLibraryFactory()
    android.AddLoadHook(module, LibrockxPrebuiltLibrary)
    return module
}

func LibrockxPrebuiltLibrary(ctx android.LoadHookContext) {

    type props struct {
        Multilib struct {
            Lib64 struct {
                Srcs []string
            }
            Lib32 struct {
                Srcs []string
            }
        }
        Export_include_dirs []string
    }
    p := &props{}

    p.Multilib.Lib64.Srcs = getLibrockxLibrary(ctx, "arm64-v8a")
    p.Multilib.Lib32.Srcs = getLibrockxLibrary(ctx, "armeabi-v7a")
    p.Export_include_dirs = getLibrockxHeader(ctx)
    ctx.AppendProperties(p)
}

func checkEnabled(ctx android.LoadHookContext) bool {
    var soc string = getTargetSoc(ctx)
    for i := 0; i < len(SUPPORT_TARGET_PLATFORM); i++ {
        if (strings.EqualFold(SUPPORT_TARGET_PLATFORM[i], soc)) {
            fmt.Println("librockx enabled on " + soc)
            return true
        }
    }
    fmt.Println("librockx disabled on " + soc)
    return false
}

func getLibrockxLibrary(ctx android.LoadHookContext, arch string) ([]string) {
    var src []string
    var soc string = getTargetSoc(ctx)
    var prefix string = soc

    if (!checkEnabled(ctx)) {
        prefix = "RK356X"
    }


    src = append(src, prefix + "/lib/librockx/" + arch + "/librockx.so")

    return src
}

func getLibrockxHeader(ctx android.LoadHookContext) ([]string) {
    var src []string
    var soc string = getTargetSoc(ctx)
    var prefix string = soc

    if (!checkEnabled(ctx)) {
        prefix = "RK356X"
    }

    src = append(src, prefix + "/lib/librockx/include/")

    return src
}

func getTargetSoc(ctx android.LoadHookContext) (string) {
    var target_board_platform string = strings.ToUpper(ctx.AConfig().Getenv("TARGET_BOARD_PLATFORM"))
    return target_board_platform
}
