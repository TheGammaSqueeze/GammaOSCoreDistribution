package libaudioclient

import (
        "android/soong/android"
        "android/soong/cc"
        "fmt"
)

func init() {
    fmt.Println("libaudioclient want to conditional Compile")
    android.RegisterModuleType("cc_libaudioclient", DefaultsFactory)
}

func DefaultsFactory() (android.Module) {
    module := cc.DefaultsFactory()
    android.AddLoadHook(module, Defaults)
    return module
}

func Defaults(ctx android.LoadHookContext) {
    type props struct {
        Cflags []string
        Shared_libs []string
    }

    p := &props{}
    p.Cflags = getCflags(ctx)
    ctx.AppendProperties(p)
}

func getCflags(ctx android.BaseContext) ([]string) {
    var cppflags []string

    if (ctx.AConfig().IsEnvTrue("BOARD_SUPPORT_MULTIAUDIO")) {
        cppflags = append(cppflags,"-DSUPPORT_MULTIAUDIO=1")
    }

    return cppflags
}
