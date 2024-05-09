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
    fmt.Println("libeptz want to conditional Compile")
    android.RegisterModuleType("cc_libeptz_prebuilt_library_shared", LibeptzFactory)
    // android.RegisterModuleType("librknnrt_headers", LibrknnrtHeadersFactory)
}

func LibeptzFactory() (android.Module) {
    module := cc.PrebuiltSharedLibraryFactory()
    android.AddLoadHook(module, LibeptzPrebuiltLibrary)
    return module
}

func LibeptzPrebuiltLibrary(ctx android.LoadHookContext) {

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

    p.Multilib.Lib64.Srcs = getLibeptzLibrary(ctx, "arm64-v8a")
    p.Multilib.Lib32.Srcs = getLibeptzLibrary(ctx, "armeabi-v7a")
    p.Export_include_dirs = getLibeptzHeader(ctx)
    ctx.AppendProperties(p)
}

func checkEnabled(ctx android.LoadHookContext) bool {
    var soc string = getTargetSoc(ctx)
    for i := 0; i < len(SUPPORT_TARGET_PLATFORM); i++ {
        if (strings.EqualFold(SUPPORT_TARGET_PLATFORM[i], soc)) {
            fmt.Println("libeptz enabled on " + soc)
            return true
        }
    }
    fmt.Println("libeptz disabled on " + soc)
    return false
}

func getLibeptzLibrary(ctx android.LoadHookContext, arch string) ([]string) {
    var src []string
    var soc string = getTargetSoc(ctx)
    var prefix string = soc

    if (!checkEnabled(ctx)) {
        prefix = "RK356X"
    }

    // fmt.Println("soc=" + soc + " arch=" + arch)

    src = append(src, prefix + "/lib/libeptz/" + arch + "/libeptz.so")

    return src
}

func getLibeptzHeader(ctx android.LoadHookContext) ([]string) {
    var src []string
    var soc string = getTargetSoc(ctx)
    var prefix string = soc

    if (!checkEnabled(ctx)) {
        prefix = "RK356X"
    }

    src = append(src, prefix + "/lib/libeptz/include/")

    return src
}

func getTargetSoc(ctx android.LoadHookContext) (string) {
    var target_board_platform string = strings.ToUpper(ctx.AConfig().Getenv("TARGET_BOARD_PLATFORM"))
    return target_board_platform
}
