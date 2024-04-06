package librkvt_win

import (
    "android/soong/android"
    "android/soong/cc"
    "fmt"
    "strings"
)

func init() {
    //该打印会在执行mm命令时，打印在屏幕上
    fmt.Println("librkvt_win want to conditional Compile")
    android.RegisterModuleType("cc_librkvt_win", DefaultsFactory)
}

func DefaultsFactory() (android.Module) {
    module := cc.DefaultsFactory()
    android.AddLoadHook(module, Defaults)
    return module
}

func Defaults(ctx android.LoadHookContext) {
    type props struct {
        Srcs []string
        Cflags []string
        Shared_libs []string
        Include_dirs []string
    }
    p := &props{}
    p.Cflags = globalCflagsDefaults(ctx)
    p.Include_dirs = globalIncludeDefaults(ctx)
    p.Srcs = getSrcs(ctx)
    p.Shared_libs = getSharedLibs(ctx)

    ctx.AppendProperties(p)
}

func getSharedLibs(ctx android.BaseContext) ([]string) {
    var libs []string

    if (strings.Contains(ctx.AConfig().Getenv("TARGET_PRODUCT"),"rk3528_box")) {
        libs = append(libs, "libdrm")
        libs = append(libs, "libhardware")
        libs = append(libs, "libsync")
    }
    return libs
}

func getSrcs(ctx android.BaseContext) ([]string) {
    var src []string

    if (strings.Contains(ctx.AConfig().Getenv("TARGET_PRODUCT"),"rk3528_box")) {
        src = append(src, "common/drmgralloc.cpp")
        src = append(src, "vdpp/vdpp_proc.cpp")
        src = append(src, "vdpp/vdpp.cpp")
    }
    return src
}

func globalIncludeDefaults(ctx android.BaseContext) ([]string) {
    var include_dirs []string

    if (strings.Contains(ctx.AConfig().Getenv("TARGET_PRODUCT"),"rk3528_box")) {
        include_dirs = append(include_dirs,"external/libdrm")
        include_dirs = append(include_dirs,"external/libdrm/include/drm")
        include_dirs = append(include_dirs,"frameworks/av/include")
    }

    fmt.Println(include_dirs, ctx.Config().PlatformSdkVersion())
    return include_dirs

}

func globalCflagsDefaults(ctx android.BaseContext) ([]string) {
    var cppflags []string
    //该打印输出为: TARGET_PRODUCT:rk3328 fmt.Println("TARGET_PRODUCT:",ctx.AConfig().Getenv("TARGET_PRODUCT")) //通过 strings.EqualFold 比较字符串，可参考go语言字符串对比
    if (strings.Contains(ctx.AConfig().Getenv("TARGET_PRODUCT"),"rk3528_box")) {
        //添加 DEBUG 宏定义
        cppflags = append(cppflags,"-DHAVE_VDPP=0")
    }

    //将需要区分的环境变量在此区域添加 //....
    return cppflags
}
