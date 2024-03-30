vim_src := $(call my-dir)

# ========================================================
# etc/vimrc
# ========================================================

LOCAL_PATH := $(vim_src)
include $(CLEAR_VARS)

LOCAL_MODULE := vimrc
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_CLASS := ETC

LOCAL_SYSTEM_EXT_MODULE := true

LOCAL_SRC_FILES := vimrc.android

include $(BUILD_PREBUILT)

# ========================================================
# vim
# ========================================================

LOCAL_PATH := $(vim_src)/src
include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
	auto/pathdef.c \
	alloc.c \
	arabic.c \
	arglist.c \
	autocmd.c \
	beval.c \
	blob.c \
	blowfish.c \
	buffer.c \
	bufwrite.c \
	change.c \
	channel.c \
	charset.c \
	cindent.c \
	clientserver.c \
	clipboard.c \
	cmdexpand.c \
	cmdhist.c \
	crypt.c \
	crypt_zip.c \
	debugger.c \
	dict.c \
	diff.c \
	digraph.c \
	drawline.c \
	drawscreen.c \
	edit.c \
	evalbuffer.c \
	eval.c \
	evalfunc.c \
	evalvars.c \
	evalwindow.c \
	ex_cmds2.c \
	ex_cmds.c \
	ex_docmd.c \
	ex_eval.c \
	ex_getln.c \
	fileio.c \
	filepath.c \
	findfile.c \
	float.c \
	fold.c \
	getchar.c \
	hardcopy.c \
	hashtab.c \
	help.c \
	highlight.c \
	if_cscope.c \
	if_xcmdsrv.c \
	iid_ole.c \
	indent.c \
	insexpand.c \
	iscygpty.c \
	job.c \
	json.c \
	list.c \
	locale.c \
	main.c \
	map.c \
	mark.c \
	match.c \
	mbyte.c \
	memfile.c \
	memline.c \
	menu.c \
	message.c \
	misc1.c \
	misc2.c \
	mouse.c \
	move.c \
	nbdebug.c \
	netbeans.c \
	normal.c \
	ops.c \
	option.c \
	optionstr.c \
	os_unix.c \
	popupmenu.c \
	popupwin.c \
	profiler.c \
	pty.c \
	quickfix.c \
	regexp.c \
	register.c \
	screen.c \
	scriptfile.c \
	search.c \
	session.c \
	sha256.c \
	sign.c \
	sound.c \
	spell.c \
	spellfile.c \
	spellsuggest.c \
	strings.c \
	syntax.c \
	tag.c \
	term.c \
	terminal.c \
	testing.c \
	textformat.c \
	textobject.c \
	textprop.c \
	time.c \
	typval.c \
	ui.c \
	undo.c \
	usercmd.c \
	userfunc.c \
	version.c \
	vim9cmds.c \
	vim9compile.c \
	vim9execute.c \
	vim9expr.c \
	vim9instr.c \
	vim9script.c \
	vim9type.c \
	viminfo.c \
	window.c

LOCAL_C_INCLUDES += \
	external/libselinux/include \
	external/libncurses/include \
	$(LOCAL_PATH)/proto \
	$(LOCAL_PATH)/auto

LOCAL_SHARED_LIBRARIES += \
	libselinux \
	libncurses \
	libm \
	libdl

LOCAL_CFLAGS += \
	-DHAVE_CONFIG_H \
	-DSYS_VIMRC_FILE=\"/system_ext/etc/vimrc\"

LOCAL_CFLAGS += -Wno-unused-variable -Wno-unused-parameter

LOCAL_MODULE := vim
LOCAL_MODULE_TAGS := optional
LOCAL_SYSTEM_EXT_MODULE := true
LOCAL_REQUIRED_MODULES := vimrc
include $(BUILD_EXECUTABLE)

# ========================================================
# vim runtime files
# ========================================================
ifeq (vim,$(filter vim, $(ALL_MODULES)))

vim_runtime_path := $(vim_src)/runtime

vim_runtime_files := \
	defaults.vim \
	filetype.vim \
	ftoff.vim \
	indent.vim \
	indoff.vim \
	scripts.vim

vim_doc_files := \
	help.txt intro.txt tags \
	motion.txt editing.txt scroll.txt \
	sponsor.txt options.txt term.txt \
	version8.txt

vim_colors_files := \
	default.vim \
	desert.vim

vim_syntax_files := \
	logcat.vim \
	awk.vim \
	config.vim \
	conf.vim \
	cpp.vim \
	c.vim \
	css.vim \
	diff.vim \
	doxygen.vim \
	html.vim vb.vim \
	xml.vim dtd.vim \
	context.vim \
	gitcommit.vim \
	help.vim \
	javascript.vim \
	java.vim \
	lua.vim \
	manual.vim \
	markdown.vim \
	pod.vim \
	sh.vim \
	syncolor.vim \
	synload.vim \
	syntax.vim \
	vim.vim

vim_plugin_files := \
	matchparen.vim \

vim_autoload_files := \
	dist/ft.vim \
	spacehi.vim

VIM_SHARED := $(TARGET_OUT_SYSTEM_EXT)/usr/share/vim

vim_runtime_files := \
  $(vim_runtime_files) \
  $(addprefix doc/, $(vim_doc_files)) \
  $(addprefix colors/, $(vim_colors_files)) \
  $(addprefix syntax/, $(vim_syntax_files)) \
  $(addprefix plugin/, $(vim_plugin_files)) \
  $(addprefix autoload/, $(vim_autoload_files)) \

vim_runtime_modules := $(addprefix $(VIM_SHARED)/, $(vim_runtime_files))
$(vim_runtime_modules): $(VIM_SHARED)/%: $(vim_runtime_path)/% | $(LOCAL_BUILT_MODULE)
	@echo "Install: $@"
	@mkdir -p $(dir $@)
	$(hide) cp $< $@

ALL_DEFAULT_INSTALLED_MODULES += $(vim_runtime_modules)

ALL_MODULES.$(LOCAL_MODULE).INSTALLED := \
  $(ALL_MODULES.$(LOCAL_MODULE).INSTALLED) \
  $(vim_runtime_modules)

endif
