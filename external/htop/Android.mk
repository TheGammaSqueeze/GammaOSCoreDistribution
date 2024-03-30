LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_CFLAGS := \
    -pedantic -Wall -Wextra -std=c99 -DSYSCONFDIR=\"/system_ext/etc\"

LOCAL_CFLAGS += \
    -Wno-gnu-include-next -Wno-enum-conversion -Wno-deprecated-declarations

LOCAL_C_INCLUDES := \
    $(LOCAL_PATH) \
    $(LOCAL_PATH)/linux \
    $(LOCAL_PATH)/generic \
    external/libncurses/include

LOCAL_SRC_FILES := \
    Action.c \
    Affinity.c \
    AffinityPanel.c \
    AvailableColumnsPanel.c \
    AvailableMetersPanel.c \
    BatteryMeter.c \
    CategoriesPanel.c \
    ClockMeter.c \
    ColorsPanel.c \
    ColumnsPanel.c \
    CommandLine.c \
    CommandScreen.c \
    Compat.c \
    CPUMeter.c \
    CRT.c \
    DateMeter.c \
    DateTimeMeter.c \
    DiskIOMeter.c \
    DisplayOptionsPanel.c \
    DynamicColumn.c \
    DynamicMeter.c \
    EnvScreen.c \
    FunctionBar.c \
    Hashtable.c \
    Header.c \
    HeaderOptionsPanel.c \
    HostnameMeter.c \
    htop.c \
    IncSet.c \
    InfoScreen.c \
    ListItem.c \
    LoadAverageMeter.c \
    MainPanel.c \
    MemoryMeter.c \
    MemorySwapMeter.c \
    Meter.c \
    MetersPanel.c \
    NetworkIOMeter.c \
    Object.c \
    OpenFilesScreen.c \
    OptionItem.c \
    Panel.c \
    Process.c \
    ProcessList.c \
    ProcessLocksScreen.c \
    RichString.c \
    ScreenManager.c \
    ScreensPanel.c \
    Settings.c \
    SignalsPanel.c \
    SwapMeter.c \
    SysArchMeter.c \
    TasksMeter.c \
    TraceScreen.c \
    UptimeMeter.c \
    UsersTable.c \
    Vector.c \
    XUtils.c \
    generic/gettime.c \
    generic/hostname.c \
    generic/uname.c \
    linux/CGroupUtils.c \
    linux/HugePageMeter.c \
    linux/IOPriorityPanel.c \
    linux/LibSensors.c \
    linux/LinuxProcess.c \
    linux/LinuxProcessList.c \
    linux/Platform.c \
    linux/PressureStallMeter.c \
    linux/SELinuxMeter.c \
    linux/SystemdMeter.c \
    linux/ZramMeter.c \
    zfs/ZfsArcMeter.c \
    zfs/ZfsCompressedArcMeter.c

LOCAL_SHARED_LIBRARIES := libncurses

LOCAL_MODULE_TAGS := optional

LOCAL_MODULE := htop

LOCAL_SYSTEM_EXT_MODULE := true

include $(BUILD_EXECUTABLE)
